package net.hydra.jojomod.entity.objects;

import net.hydra.jojomod.entity.ModEntities;
import net.hydra.jojomod.entity.stand.StandEntity;
import net.hydra.jojomod.event.powers.ModDamageTypes;
import net.hydra.jojomod.stand.powers.PowersStickyFingers;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public class ZipperDebrisEntity extends Entity {

    protected static final EntityDataAccessor<BlockState> BLOCK = SynchedEntityData.defineId(ZipperDebrisEntity.class,
            EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Integer> ORIGIN_X = SynchedEntityData.defineId(ZipperDebrisEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ORIGIN_Y = SynchedEntityData.defineId(ZipperDebrisEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ORIGIN_Z = SynchedEntityData.defineId(ZipperDebrisEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HALF = SynchedEntityData.defineId(ZipperDebrisEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SPLIT_AXIS = SynchedEntityData.defineId(ZipperDebrisEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ZIPPER_FACE = SynchedEntityData.defineId(ZipperDebrisEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SPLIT_PIECE = SynchedEntityData.defineId(ZipperDebrisEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RETURNING = SynchedEntityData.defineId(ZipperDebrisEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> RETURN_TICKS = SynchedEntityData.defineId(ZipperDebrisEntity.class, EntityDataSerializers.INT);

    public static final int MAX_LIFE = 60;

    private UUID ownerUUID = null;
    private float damage = 1.5F;
    private int life = 0;
    private int returnTicks = 0;
    private Vec3 returnStart = Vec3.ZERO;

    public ZipperDebrisEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static void launch(ServerLevel level, UUID owner, BlockState state, Vec3 pos, Direction face, float damage) {
        ZipperDebrisEntity debris = new ZipperDebrisEntity(ModEntities.ZIPPER_DEBRIS, level);
        debris.ownerUUID = owner;
        debris.damage = damage;
        debris.entityData.set(BLOCK, state);
        debris.setPos(pos.x, pos.y, pos.z);
        if (face.getAxis() == Direction.Axis.Y) {
            double vertical = (0.35D + level.random.nextDouble() * 0.25D) * face.getStepY();
            debris.setDeltaMovement((level.random.nextDouble() - 0.5D) * 0.36D, vertical,
                    (level.random.nextDouble() - 0.5D) * 0.36D);
        } else {
            double speed = 0.55D + level.random.nextDouble() * 0.35D;
            Vec3 out = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ()).scale(speed);
            debris.setDeltaMovement(out.add((level.random.nextDouble() - 0.5D) * 0.35D,
                    0.12D + level.random.nextDouble() * 0.2D, (level.random.nextDouble() - 0.5D) * 0.35D));
        }
        level.addFreshEntity(debris);
    }

    /** Creates two harmless, persistent halves for a block opened by Unzip. */
    public static void splitBlock(ServerLevel level, BlockPos origin, BlockState state, Direction face) {
        if (state.isAir() || state.getDestroySpeed(level, origin) < 0) return;
        Direction.Axis axis = face.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        for (int half = 0; half < 2; half++) {
            ZipperDebrisEntity piece = new ZipperDebrisEntity(ModEntities.ZIPPER_DEBRIS, level);
            piece.entityData.set(BLOCK, state);
            piece.entityData.set(ORIGIN_X, origin.getX());
            piece.entityData.set(ORIGIN_Y, origin.getY());
            piece.entityData.set(ORIGIN_Z, origin.getZ());
            piece.entityData.set(HALF, half);
            piece.entityData.set(SPLIT_AXIS, axis == Direction.Axis.X ? 0 : 2);
            piece.entityData.set(ZIPPER_FACE, face.get3DDataValue());
            piece.entityData.set(SPLIT_PIECE, true);
            piece.refreshDimensions();
            double x = origin.getX() + (axis == Direction.Axis.X ? 0.25D + half * 0.5D : 0.5D);
            double z = origin.getZ() + (axis == Direction.Axis.Z ? 0.25D + half * 0.5D : 0.5D);
            piece.setPos(x, origin.getY(), z);
            piece.setDeltaMovement((half == 0 ? -0.06D : 0.06D), 0.18D, 0.0D);
            level.addFreshEntity(piece);
        }
    }

    public static void beginReturn(ServerLevel level, BlockPos origin) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ZipperDebrisEntity piece && piece.isSplitPiece()
                    && piece.getOrigin().equals(origin)) {
                piece.beginReturning();
            }
        }
    }

    private void beginReturning() {
        this.returnStart = this.position();
        this.entityData.set(RETURNING, true);
        this.returnTicks = 8;
        this.entityData.set(RETURN_TICKS, this.returnTicks);
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(BLOCK, Blocks.STONE.defaultBlockState());
        this.entityData.define(ORIGIN_X, 0);
        this.entityData.define(ORIGIN_Y, 0);
        this.entityData.define(ORIGIN_Z, 0);
        this.entityData.define(HALF, 0);
        this.entityData.define(SPLIT_AXIS, 0);
        this.entityData.define(ZIPPER_FACE, Direction.NORTH.get3DDataValue());
        this.entityData.define(SPLIT_PIECE, false);
        this.entityData.define(RETURNING, false);
        this.entityData.define(RETURN_TICKS, 0);
    }

    public BlockState getBlockState() {
        return this.entityData.get(BLOCK);
    }

    public boolean isSplitPiece() { return this.entityData.get(SPLIT_PIECE); }
    public boolean isReturning() { return this.entityData.get(RETURNING); }
    public int getReturnTicks() { return this.entityData.get(RETURN_TICKS); }
    public int getHalf() { return this.entityData.get(HALF); }
    public Direction.Axis getSplitAxis() { return this.entityData.get(SPLIT_AXIS) == 2 ? Direction.Axis.Z : Direction.Axis.X; }
    public Direction getZipperFace() { return Direction.from3DDataValue(this.entityData.get(ZIPPER_FACE)); }
    public BlockPos getOrigin() { return new BlockPos(this.entityData.get(ORIGIN_X), this.entityData.get(ORIGIN_Y), this.entityData.get(ORIGIN_Z)); }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return isSplitPiece() ? EntityDimensions.scalable(0.5F, 1.0F) : super.getDimensions(pose);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (isSplitPiece()) {
            if (isReturning()) {
                if (this.level().isClientSide()) return;
                BlockPos origin = getOrigin();
                double targetX = origin.getX() + (getSplitAxis() == Direction.Axis.X ? 0.25D + getHalf() * 0.5D : 0.5D);
                double targetY = origin.getY();
                double targetZ = origin.getZ() + (getSplitAxis() == Direction.Axis.Z ? 0.25D + getHalf() * 0.5D : 0.5D);
                double progress = Math.min(1.0D, (9 - returnTicks) / 8.0D);
                this.setPos(returnStart.x + (targetX - returnStart.x) * progress,
                        returnStart.y + (targetY - returnStart.y) * progress,
                        returnStart.z + (targetZ - returnStart.z) * progress);
                if (--returnTicks <= 0) this.discard();
                else this.entityData.set(RETURN_TICKS, returnTicks);
                return;
            }
            if (this.level().isClientSide()) return;
            if (this.level() instanceof ServerLevel serverLevel && this.tickCount % 8 == 0) {
                BlockPos origin = getOrigin();
                serverLevel.sendParticles(ParticleTypes.END_ROD, origin.getX() + 0.5D,
                        origin.getY() + 0.35D + this.random.nextDouble() * 0.3D,
                        origin.getZ() + 0.5D, 1, 0.03D, 0.08D, 0.03D, 0.0D);
            }
            Vec3 fallingMotion = this.getDeltaMovement();
            this.setDeltaMovement(fallingMotion.x * 0.96D, fallingMotion.y - 0.04D, fallingMotion.z * 0.96D);
            this.move(MoverType.SELF, this.getDeltaMovement());
            if (this.onGround() || this.verticalCollision) this.setDeltaMovement(this.getDeltaMovement().x, 0.0D, this.getDeltaMovement().z);
            return;
        }
        life++;
        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(motion.x * 0.99D, motion.y - 0.04D, motion.z * 0.99D);
        this.move(MoverType.SELF, this.getDeltaMovement());
        if (this.level().isClientSide()) {
            return;
        }
        List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(0.2D), this::canHit);
        if (!targets.isEmpty()) {
            hit(targets.get(0));
            burst();
            return;
        }
        if (life > 3 && (this.onGround() || this.horizontalCollision || this.verticalCollision || life > MAX_LIFE)) {
            burst();
        }
    }

    private boolean canHit(LivingEntity entity) {
        return entity.isAlive() && !entity.isSpectator() && !(entity instanceof StandEntity)
                && (ownerUUID == null || !ownerUUID.equals(entity.getUUID()));
    }

    private void hit(LivingEntity target) {
        Entity owner = ownerUUID == null ? null : this.level().getPlayerByUUID(ownerUUID);
        DamageSource source = owner != null
                ? ModDamageTypes.of(this.level(), ModDamageTypes.STAND, this, owner)
                : ModDamageTypes.of(this.level(), ModDamageTypes.STAND, this);
        target.hurt(source, PowersStickyFingers.applyZipModeDamage(owner, damage));
    }

    private void burst() {
        BlockState state = getBlockState();
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    this.getX(), this.getY() + 0.2D, this.getZ(), 10, 0.15D, 0.15D, 0.15D, 0.05D);
            serverLevel.playSound(null, this.blockPosition(), state.getSoundType().getBreakSound(),
                    SoundSource.BLOCKS, 0.6F, 1.1F);
        }
        this.discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
