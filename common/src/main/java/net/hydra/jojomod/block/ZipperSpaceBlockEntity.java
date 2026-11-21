package net.hydra.jojomod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class ZipperSpaceBlockEntity extends BlockEntity {

    public static final int MAX_OVERTIME_CHECKS = 40;

    private BlockState original = Blocks.AIR.defaultBlockState();
    private int ticksLeft = 0;
    private int overtimeChecks = 0;

    public ZipperSpaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ZIPPER_SPACE_BLOCK_ENTITY, pos, state);
    }

    public void setup(BlockState original, int ticks) {
        this.original = original;
        this.ticksLeft = ticks;
        this.overtimeChecks = 0;
        this.setChanged();
    }

    public BlockState getOriginal() {
        return original;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("original", NbtUtils.writeBlockState(original));
        tag.putInt("ticksLeft", ticksLeft);
        tag.putInt("overtime", overtimeChecks);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("original")) {
            original = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("original"));
        }
        ticksLeft = tag.getInt("ticksLeft");
        overtimeChecks = tag.getInt("overtime");
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ZipperSpaceBlockEntity space) {
        if (level.isClientSide()) {
            return;
        }
        if (space.ticksLeft > 0) {
            space.ticksLeft--;
            return;
        }
        if (level.getGameTime() % 5 != 0) {
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
        if (level instanceof ServerLevel serverLevel && !toPlace.isAir()) {
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, toPlace),
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 6, 0.3D, 0.3D, 0.3D, 0.03D);
        }
        level.setBlock(pos, toPlace, 3);
    }
}
