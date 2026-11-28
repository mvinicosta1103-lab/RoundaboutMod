package net.hydra.jojomod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.hydra.jojomod.access.IStickyFingersDismemberment;
import net.hydra.jojomod.entity.objects.ZipperDebrisEntity;
import net.hydra.jojomod.entity.objects.ZipperEntity;
import net.hydra.jojomod.entity.stand.StandEntity;

public class ZipperSpaceBlockEntity extends BlockEntity {

    public static final int MAX_OVERTIME_CHECKS = 40;
    public static final int RETURN_ANIMATION_TICKS = 8;

    private BlockState original = Blocks.AIR.defaultBlockState();
    private int ticksLeft = 0;
    private int overtimeChecks = 0;
    private boolean entrance = false;
    private Direction entranceFace = Direction.NORTH;
    private boolean returnAnimationStarted = false;

    public ZipperSpaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ZIPPER_SPACE_BLOCK_ENTITY, pos, state);
    }

    public void setup(BlockState original, int ticks) {
        setup(original, ticks, false, Direction.NORTH);
    }

    public void setup(BlockState original, int ticks, boolean entrance, Direction entranceFace) {
        this.original = original;
        this.ticksLeft = ticks;
        this.overtimeChecks = 0;
        this.entrance = entrance;
        this.entranceFace = entranceFace;
        this.returnAnimationStarted = false;
        this.setChanged();
        if (this.level != null) {
            if (this.level instanceof ServerLevel serverLevel) {
                ZipperDebrisEntity.splitBlock(serverLevel, this.worldPosition, original, entranceFace);
            }
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    /** Makes the tunnel block restore sooner (used when the owner closes the portal by hand). */
    public void closeSoon(int ticks) {
        if (this.ticksLeft > ticks) {
            this.ticksLeft = Math.max(0, ticks);
            this.setChanged();
        }
    }

    public BlockState getOriginal() {
        return original;
    }

    public boolean isEntrance() {
        return entrance;
    }

    public Direction getEntranceFace() {
        return entranceFace;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("original", NbtUtils.writeBlockState(original));
        tag.putInt("ticksLeft", ticksLeft);
        tag.putInt("overtime", overtimeChecks);
        tag.putBoolean("entrance", entrance);
        tag.putInt("entranceFace", entranceFace.get3DDataValue());
        tag.putBoolean("returnAnimationStarted", returnAnimationStarted);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("original")) {
            original = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("original"));
        }
        ticksLeft = tag.getInt("ticksLeft");
        overtimeChecks = tag.getInt("overtime");
        entrance = tag.getBoolean("entrance");
        if (tag.contains("entranceFace")) {
            entranceFace = Direction.from3DDataValue(tag.getInt("entranceFace"));
        }
        returnAnimationStarted = tag.getBoolean("returnAnimationStarted");
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ZipperSpaceBlockEntity space) {
        if (level.isClientSide()) {
            return;
        }
        if (space.ticksLeft > 0) {
            space.ticksLeft--;
            return;
        }
        if (!space.returnAnimationStarted && space.overtimeChecks < MAX_OVERTIME_CHECKS
                && !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty()) {
            space.overtimeChecks++;
            return;
        }
        if (!space.returnAnimationStarted) {
            space.returnAnimationStarted = true;
            space.ticksLeft = RETURN_ANIMATION_TICKS;
            space.setChanged();
            if (level instanceof ServerLevel serverLevel) {
                ZipperDebrisEntity.beginReturn(serverLevel, pos);
            }
            return;
        }
        if (space.ticksLeft > 0) {
            space.ticksLeft--;
            return;
        }
        if (space.overtimeChecks < MAX_OVERTIME_CHECKS
                && !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty()) {
            space.overtimeChecks++;
            return;
        }
        space.restore(level, pos);
    }

    private void restore(Level level, BlockPos pos) {
        BlockState toPlace = original;
        forceEjectOccupants(level, pos);
        if (level instanceof ServerLevel serverLevel && !toPlace.isAir()) {
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, toPlace),
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 6, 0.3D, 0.3D, 0.3D, 0.03D);
        }
        level.setBlock(pos, toPlace, 3);
    }

    /** Damages and forcibly moves anyone still occupying the closing zipper cell. */
    private void forceEjectOccupants(Level level, BlockPos pos) {
        Direction face = entranceFace;
        BlockPos mouth = pos;
        for (int i = 0; i < 8
                && level.getBlockState(mouth).getBlock() instanceof ZipperSpaceBlock; i++) {
            mouth = mouth.relative(face);
        }

        for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(pos), candidate -> candidate.isAlive()
                && !(candidate instanceof ZipperEntity) && !(candidate instanceof ZipperDebrisEntity)
                && isActuallyInsideClosingCell(candidate, pos))) {
            double x = mouth.getX() + 0.5D + face.getStepX() * (entity.getBbWidth() * 0.5D + 0.2D);
            double y = face == Direction.UP ? mouth.getY()
                    : face == Direction.DOWN ? mouth.getY() - entity.getBbHeight() : mouth.getY();
            double z = mouth.getZ() + 0.5D + face.getStepZ() * (entity.getBbWidth() * 0.5D + 0.2D);

            // If the mouth itself is obstructed, move the entity upward until its
            // full bounding box is clear. Never leave it in the block being restored.
            Vec3 baseDestination = new Vec3(x, y, z);
            Vec3 destination = baseDestination.add(0.0D, 4.0D, 0.0D);
            for (int up = 0; up <= 4; up++) {
                Vec3 candidate = baseDestination.add(0.0D, up, 0.0D);
                AABB candidateBox = entity.getBoundingBox().move(candidate.subtract(entity.position()));
                if (level.noCollision(entity, candidateBox)) {
                    destination = candidate;
                    break;
                }
            }

            if (entity instanceof LivingEntity living) {
                if (living instanceof IStickyFingersDismemberment zipperState) {
                    zipperState.roundabout$setInZipperDimension(false);
                }
                if (!(living instanceof StandEntity)) {
                    living.setNoGravity(false);
                    living.fallDistance = 0.0F;
                    living.hurt(level.damageSources().inWall(), 4.0F);
                }
            }
            entity.teleportTo(destination.x, destination.y, destination.z);
            if (!(entity instanceof StandEntity)) entity.setNoGravity(false);
            entity.setDeltaMovement(face.getStepX() * 0.35D, Math.min(-0.08D, face.getStepY() * 0.35D),
                    face.getStepZ() * 0.35D);
            entity.hurtMarked = true;
        }
    }

    /** Ignore entities whose bounding boxes only brush the portal from outside. */
    private static boolean isActuallyInsideClosingCell(Entity entity, BlockPos pos) {
        AABB bounds = entity.getBoundingBox();
        double verticalOverlap = Math.min(bounds.maxY, pos.getY() + 1.0D)
                - Math.max(bounds.minY, pos.getY());
        return entity.getX() >= pos.getX() && entity.getX() < pos.getX() + 1.0D
                && entity.getZ() >= pos.getZ() && entity.getZ() < pos.getZ() + 1.0D
                && verticalOverlap > 0.35D;
    }
}
