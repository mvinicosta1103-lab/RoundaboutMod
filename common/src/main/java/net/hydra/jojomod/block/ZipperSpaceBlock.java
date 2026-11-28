package net.hydra.jojomod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.hydra.jojomod.access.IStickyFingersDismemberment;
import net.hydra.jojomod.entity.objects.ZipperEntity;
import net.hydra.jojomod.entity.stand.StandEntity;
import net.hydra.jojomod.stand.powers.PowersStickyFingers;

import javax.annotation.Nullable;

public class ZipperSpaceBlock extends BaseEntityBlock {

    public ZipperSpaceBlock(Properties properties) {
        super(properties);
    }

    /** Right-clicking any opened block closes its zipper group, which starts the pieces' return animation. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (PowersStickyFingers.isInZipperDimension(player)) {
            return ZipperEntity.exitOwnerAtCurrentPosition(player)
                    ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        AABB search = new AABB(pos).inflate(32.0D);
        for (ZipperEntity zipper : level.getEntitiesOfClass(ZipperEntity.class, search, ZipperEntity::isOpenPortal)) {
            if (zipper.forceClosePortal()) return InteractionResult.CONSUME;
        }
        if (level.getBlockEntity(pos) instanceof ZipperSpaceBlockEntity space) {
            space.closeSoon(1);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ZipperSpaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlocks.ZIPPER_SPACE_BLOCK_ENTITY, ZipperSpaceBlockEntity::tick);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context) {
        BlockState original = getOriginal(getter, pos);
        return original == null ? Shapes.block() : original.getShape(getter, pos, context);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context) {
        if (isInsideZipper(context)) return Shapes.empty();
        if (context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof Player player
                && getter.getBlockEntity(pos) instanceof ZipperSpaceBlockEntity space
                && space.isEntrance() && isOnApproachSide(player, pos, space.getEntranceFace())) {
            return Shapes.empty();
        }
        BlockState original = getOriginal(getter, pos);
        return original == null ? Shapes.block() : original.getCollisionShape(getter, pos, context);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        // Stands are no-physics followers, so collision alone cannot keep them out
        // of the opened blocks. Eject them to the outside face before handling players.
        if (entity instanceof StandEntity stand) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ZipperSpaceBlockEntity space) {
                Direction face = space.getEntranceFace();
                BlockPos outside = pos;
                for (int i = 0; i < 6
                        && level.getBlockState(outside).getBlock() instanceof ZipperSpaceBlock; i++) {
                    outside = outside.relative(face);
                }
                double x = outside.getX() + 0.5D + face.getStepX() * (stand.getBbWidth() * 0.5D + 0.1D);
                double y = face == Direction.UP ? outside.getY()
                        : face == Direction.DOWN ? outside.getY() - stand.getBbHeight() : outside.getY();
                double z = outside.getZ() + 0.5D + face.getStepZ() * (stand.getBbWidth() * 0.5D + 0.1D);
                stand.setPos(x, y, z);
                stand.setDeltaMovement(Vec3.ZERO);
                stand.hurtMarked = true;
            }
            return;
        }
        if (!(entity instanceof Player player)
                || !(entity instanceof IStickyFingersDismemberment zipperState)
                || !(level.getBlockEntity(pos) instanceof ZipperSpaceBlockEntity space)
                || !space.isEntrance()) {
            return;
        }
        Direction face = space.getEntranceFace();
        double movementAlongFace = player.getDeltaMovement().x * face.getStepX()
                + player.getDeltaMovement().y * face.getStepY()
                + player.getDeltaMovement().z * face.getStepZ();
        double currentSide = signedDistanceFromPortalCenter(player, pos, face, false);
        double previousSide = signedDistanceFromPortalCenter(player, pos, face, true);
        if (zipperState.roundabout$isInZipperDimension()) {
            if (movementAlongFace > 0.01D && previousSide <= 0.0D && currentSide > 0.0D) {
                zipperState.roundabout$setInZipperDimension(false);
            }
        } else if (movementAlongFace < -0.01D && previousSide > 0.0D && currentSide <= 0.0D) {
            zipperState.roundabout$setInZipperDimension(true);
        }
    }

    private static boolean isInsideZipper(CollisionContext context) {
        return context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof LivingEntity living
                && !(living instanceof StandEntity)
                && PowersStickyFingers.isInZipperDimension(living);
    }

    @Nullable
    private static BlockState getOriginal(BlockGetter getter, BlockPos pos) {
        BlockEntity blockEntity = getter.getBlockEntity(pos);
        if (!(blockEntity instanceof ZipperSpaceBlockEntity space)) return null;
        BlockState original = space.getOriginal();
        return original.isAir() || original.getBlock() instanceof ZipperSpaceBlock ? null : original;
    }

    private static boolean isOnApproachSide(Entity entity, BlockPos pos, Direction face) {
        Vec3 center = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        Vec3 relative = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D).subtract(center);
        return isOnApproachSide(entity, relative, face);
    }

    private static double signedDistanceFromPortalCenter(Entity entity, BlockPos pos, Direction face,
                                                         boolean previousPosition) {
        Vec3 center = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        Vec3 entityPosition = previousPosition
                ? new Vec3(entity.xo, entity.yo, entity.zo)
                : entity.position();
        Vec3 relative = entityPosition.add(0.0D, entity.getBbHeight() * 0.5D, 0.0D).subtract(center);
        return relative.x * face.getStepX() + relative.y * face.getStepY() + relative.z * face.getStepZ();
    }

    private static boolean isOnApproachSide(Entity entity, Vec3 relative, Direction face) {
        double alongFace = relative.x * face.getStepX()
                + relative.y * face.getStepY()
                + relative.z * face.getStepZ();
        if (alongFace <= 0.0D) return false;
        double halfWidth = 0.5D + entity.getBbWidth() * 0.5D;
        double halfHeight = 0.5D + entity.getBbHeight() * 0.5D;
        return switch (face.getAxis()) {
            case X -> Math.abs(relative.y) <= halfHeight && Math.abs(relative.z) <= halfWidth;
            case Y -> Math.abs(relative.x) <= halfWidth && Math.abs(relative.z) <= halfWidth;
            case Z -> Math.abs(relative.x) <= halfWidth && Math.abs(relative.y) <= halfHeight;
        };
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter getter, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter getter, BlockPos pos) {
        return 1.0F;
    }
}
