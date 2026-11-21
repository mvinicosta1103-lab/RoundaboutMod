package net.hydra.jojomod.entity.objects;

import net.hydra.jojomod.entity.ModEntities;
import net.hydra.jojomod.entity.stand.StandEntity;
import net.hydra.jojomod.event.powers.StandUser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**A chunk of block launched by Sticky Fingers' Zipper Barrage (Sneak + Z).
 * It flies, deals light stand damage to the first time it touches each living thing, and shatters when it lands.
 * Weaker blocks (dirt) make weaker debris, see {@link #damageForBlock}.*/
public class ZipperDebrisEntity extends Entity {

    public static final int MAX_LIFE = 80;

    protected static final EntityDataAccessor<BlockState> BLOCK = SynchedEntityData.defineId(ZipperDebrisEntity.class,
            EntityDataSerializers.BLOCK_STATE);

    private UUID ownerUUID = null;
    private float damage = 1.0F;
    private final Set<UUID> alreadyHit = new HashSet<>();

    public ZipperDebrisEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    /**Debris damage grows with how hard the block was, but always stays "light".*/
    public static float damageForBlock(float hardness) {
        float h = Math.max(hardness, 0.0F);
        return Math.min(0.6F + h * 0.45F, 3.0F);
    }

    public static ZipperDebrisEntity create(Level level, LivingEntity owner, BlockState state, Vec3 pos, Vec3 velocity,
                                            float damage) {
        ZipperDebrisEntity debris = new ZipperDebrisEntity(ModEntities.ZIPPER_DEBRIS, level);
        debris.entityData.set(BLOCK, state);
        debris.ownerUUID = owner == null ? null : owner.getUUID();
        debris.damage = damage;
        debris.setPos(pos.x, pos.y, pos.z);
        debris.setDeltaMovement(velocity);
        return debris;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(BLOCK, Blocks.STONE.defaultBlockState());
    }

    public BlockState getBlockState() {
        return this.entityData.get(BLOCK);
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    private Entity getOwner() {
        if (ownerUUID != null && this.level() instanceof ServerLevel serverLevel) {
            return serverLevel.getEntity(ownerUUID);
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        //gravity is done by hand so the arc looks the same on both sides
        Vec3 motion = this.getDeltaMovement().add(0.0D, -0.05D, 0.0D);
        this.move(MoverType.SELF, motion);
        this.setDeltaMovement(motion.scale(0.98D));

        if (this.level().isClientSide()) {
            return;
        }
        if (this.tickCount > 2) {
            hitNearby();
        }
        if (this.onGround() || this.horizontalCollision || this.verticalCollision || this.tickCount > MAX_LIFE) {
            shatter();
        }
    }

    private void hitNearby() {
        Entity owner = getOwner();
        for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(0.2D))) {
            if (target == owner || target instanceof StandEntity || !target.isAlive()
                    || alreadyHit.contains(target.getUUID())) {
                continue;
            }
            alreadyHit.add(target.getUUID());
            if (owner instanceof LivingEntity livingOwner) {
                ((StandUser) livingOwner).roundabout$getStandPowers()
                        .StandDamageEntityAttack(target, damage, 0.1F, livingOwner);
            } else {
                target.hurt(this.damageSources().generic(), damage);
            }
        }
    }

    private void shatter() {
        if (this.level() instanceof ServerLevel serverLevel) {
            BlockState state = getBlockState();
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    this.getX(), this.getY() + 0.1D, this.getZ(), 8, 0.15D, 0.1D, 0.15D, 0.05D);
            serverLevel.playSound(null, BlockPos.containing(this.position()), state.getSoundType().getBreakSound(),
                    SoundSource.BLOCKS, 0.5F, 1.2F);
        }
        this.discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("owner")) {
            ownerUUID = tag.getUUID("owner");
        }
        damage = tag.getFloat("damage");
        Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation(tag.getString("block")));
        this.entityData.set(BLOCK, block.defaultBlockState());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUUID != null) {
            tag.putUUID("owner", ownerUUID);
        }
        tag.putFloat("damage", damage);
        tag.putString("block", BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).toString());
    }
}
