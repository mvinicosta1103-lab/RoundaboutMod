package net.hydra.jojomod.entity.objects;

import net.hydra.jojomod.entity.ModEntities;
import net.hydra.jojomod.entity.stand.StandEntity;
import net.hydra.jojomod.event.powers.ModDamageTypes;
import net.hydra.jojomod.stand.powers.PowersStickyFingers;
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public class ZipperDebrisEntity extends Entity {

    protected static final EntityDataAccessor<BlockState> BLOCK = SynchedEntityData.defineId(ZipperDebrisEntity.class,
            EntityDataSerializers.BLOCK_STATE);

    public static final int MAX_LIFE = 60;

    private UUID ownerUUID = null;
    private float damage = 1.5F;
    private int life = 0;

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

    @Override
    protected void defineSynchedData() {
        this.entityData.define(BLOCK, Blocks.STONE.defaultBlockState());
    }

    public BlockState getBlockState() {
        return this.entityData.get(BLOCK);
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
