package net.hydra.jojomod.entity.projectile;

import net.hydra.jojomod.entity.ModEntities;
import net.hydra.jojomod.entity.stand.StandEntity;
import net.hydra.jojomod.event.powers.StandUser;
import net.hydra.jojomod.stand.powers.PowersStickyFingers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Projectile used by Sticky Fingers' launched zipper fist. */
public class StickyFingersFistEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> LATCHED = SynchedEntityData.defineId(
            StickyFingersFistEntity.class, EntityDataSerializers.BOOLEAN);
    private int latchedTicks;
    private Vec3 launchOrigin;

    public void setLaunchOrigin(Vec3 launchOrigin) {
        this.launchOrigin = launchOrigin;
    }

    public StickyFingersFistEntity(EntityType<? extends StickyFingersFistEntity> type, Level level) {
        super(type, level);
    }

    public StickyFingersFistEntity(LivingEntity owner, Level level) {
        super(ModEntities.STICKY_FINGERS_FIST, owner, level);
        this.setItem(Items.IRON_NUGGET.getDefaultInstance());
    }

    @Override
    protected Item getDefaultItem() {
        return Items.IRON_NUGGET;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(LATCHED, false);
    }

    public boolean isLatched() {
        return this.entityData.get(LATCHED);
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public void tick() {
        if (isLatched()) {
            if (++latchedTicks > 80) {
                this.discard();
                return;
            }
            this.setDeltaMovement(Vec3.ZERO);
            this.setNoGravity(true);
            return;
        }
        if (this.tickCount > 60) {
            this.discard();
            return;
        }
        super.tick();
        if (!this.isRemoved()) {
            if (this.launchOrigin == null) this.launchOrigin = this.position();
            if (this.position().distanceToSqr(this.launchOrigin) >= 15.0D * 15.0D) {
                this.discard();
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (this.level().isClientSide) return;

        Entity target = hit.getEntity();
        Entity owner = this.getOwner();
        if (target instanceof LivingEntity living && target != owner && !(target instanceof StandEntity)
                && owner instanceof LivingEntity attacker) {
            boolean damaged = living.hurt(this.damageSources().thrown(this, attacker),
                    PowersStickyFingers.applyZipModeDamage(attacker, 6.0F));
            if (damaged) {
                if (attacker instanceof StandUser standUser
                        && standUser.roundabout$getStandPowers() instanceof PowersStickyFingers powers) {
                    powers.addTag(living);
                }
                // Stronger inward impulse makes the special pull variant feel distinct from a normal hit.
                Vec3 pull = attacker.position().subtract(living.position()).normalize().scale(0.90D);
                living.push(pull.x, Math.max(0.22D, pull.y), pull.z);
                living.hurtMarked = true;
            }
        }
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        this.entityData.set(LATCHED, true);
        this.setPos(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        if (this.getOwner() instanceof LivingEntity owner && owner instanceof StandUser standUser
                && standUser.roundabout$getStandPowers() instanceof PowersStickyFingers powers) {
            powers.setLatchedFist(this);
        }
        if (this.level().isClientSide) return;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Latched", isLatched());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(LATCHED, tag.getBoolean("Latched"));
    }
}
