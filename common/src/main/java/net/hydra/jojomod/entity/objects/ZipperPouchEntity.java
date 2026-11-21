package net.hydra.jojomod.entity.objects;

import net.hydra.jojomod.entity.ModEntities;
import net.hydra.jojomod.event.ModParticles;
import net.hydra.jojomod.item.ModItems;
import net.hydra.jojomod.util.MainUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;

import java.util.UUID;

/**The zipper Sticky Fingers sticks on a tagged mob with Block + Z.
 * It holds the item slot that was stuffed into the mob and follows the mob around (it is "mounted" on it),
 * the item is not in the mob's inventory, only in this entity.
 * The item acts on the mob while it is inside, and whatever is left pops out when the zipper expires
 * (or when the mob dies).
 *
 * Effects (see {@link #tickItem} and {@link #expire}):
 *   lava bucket  - sets the mob on fire after a short while, an empty bucket pops out
 *   water bucket - puts the mob out, hurts mobs that hate water, an empty bucket pops out
 *   knife        - nothing until the zipper expires, then it cuts the mob up and makes it bleed
 *   TNT          - blows up inside the mob after a short fuse (no block damage)
 *   potion       - the mob drinks it, a glass bottle pops out
 *   anything else - just stored for a while, then it pops out*/
public class ZipperPouchEntity extends Entity {

    /**Ticks the zipper lasts. Tweak to balance.*/
    public static final int DURATION = 400;
    public static final int LAVA_DELAY = 30;
    public static final int WATER_DELAY = 30;
    public static final int POTION_DELAY = 20;
    public static final int TNT_FUSE = 60;
    /**If the mob can't be found (unloaded chunk) for this long, the item is released.*/
    private static final int MAX_MISSING = 100;

    private UUID hostUUID = null;
    private UUID ownerUUID = null;
    private ItemStack stack = ItemStack.EMPTY;
    private int life = 0;
    private int missing = 0;

    public ZipperPouchEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static ZipperPouchEntity create(Level level, LivingEntity owner, LivingEntity host, ItemStack stack) {
        ZipperPouchEntity pouch = new ZipperPouchEntity(ModEntities.ZIPPER_POUCH, level);
        pouch.ownerUUID = owner == null ? null : owner.getUUID();
        pouch.hostUUID = host.getUUID();
        pouch.stack = stack;
        pouch.setPos(host.getX(), host.getY() + host.getBbHeight() * 0.5D, host.getZ());
        return pouch;
    }

    /**Does this mob already have a zipper stuck on it?*/
    public static boolean hostHasPouch(Level level, LivingEntity host) {
        for (ZipperPouchEntity pouch : level.getEntitiesOfClass(ZipperPouchEntity.class,
                host.getBoundingBox().inflate(3.0D))) {
            if (!pouch.isRemoved() && host.getUUID().equals(pouch.hostUUID)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(0.2F, 0.2F);
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    public ItemStack getStack() {
        return stack;
    }

    private LivingEntity getHost() {
        if (hostUUID != null && this.level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(hostUUID);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                return living;
            }
        }
        return null;
    }

    private LivingEntity getOwner() {
        if (ownerUUID != null && this.level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(ownerUUID);
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (stack.isEmpty()) {
            this.discard();
            return;
        }
        LivingEntity host = getHost();
        if (host == null) {
            //the mob is dead, removed, or its chunk is not loaded yet
            missing++;
            Entity gone = hostUUID == null ? null : serverLevel.getEntity(hostUUID);
            boolean dead = gone instanceof LivingEntity living && !living.isAlive();
            if (dead || missing > MAX_MISSING) {
                popOut(null);
            }
            return;
        }
        missing = 0;
        life++;
        //mounted on the mob: it follows it everywhere
        this.setPos(host.getX(), host.getY() + host.getBbHeight() * 0.5D, host.getZ());

        if (life % 10 == 0) {
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, host.getX(), host.getY() + host.getBbHeight() * 0.6D,
                    host.getZ(), 1, host.getBbWidth() * 0.3D, 0.2D, host.getBbWidth() * 0.3D, 0.01D);
        }
        if (tickItem(host)) {
            return;
        }
        if (life >= DURATION) {
            expire(host);
        }
    }

    /**Item effects that happen while the item is inside.
     * @return true if the zipper is done (it popped or discarded itself)*/
    private boolean tickItem(LivingEntity host) {
        if (stack.is(Items.LAVA_BUCKET) && life == LAVA_DELAY) {
            host.setSecondsOnFire(8);
            host.level().playSound(null, host.blockPosition(), SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.NEUTRAL, 1.0F, 1.0F);
            stack = new ItemStack(Items.BUCKET);
            popOut(host);
            return true;
        }
        if (stack.is(Items.WATER_BUCKET) && life == WATER_DELAY) {
            host.clearFire();
            if (host instanceof EnderMan || host instanceof Blaze || host instanceof SnowGolem) {
                host.hurt(host.damageSources().drown(), 4.0F);
            }
            host.level().playSound(null, host.blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 1.0F, 1.0F);
            stack = new ItemStack(Items.BUCKET);
            popOut(host);
            return true;
        }
        if (stack.is(Items.POTION) && life == POTION_DELAY) {
            LivingEntity owner = getOwner();
            for (MobEffectInstance effect : PotionUtils.getMobEffects(stack)) {
                host.addEffect(new MobEffectInstance(effect), owner);
            }
            host.level().playSound(null, host.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.NEUTRAL, 1.0F, 1.0F);
            stack = new ItemStack(Items.GLASS_BOTTLE);
            popOut(host);
            return true;
        }
        if (stack.is(Items.TNT) && life == TNT_FUSE) {
            //it blows up inside: hurts the mob and whoever is next to it, but never breaks blocks
            host.level().explode(getOwner(), host.getX(), host.getY() + host.getBbHeight() * 0.5D, host.getZ(),
                    1.5F, ExplosionInteraction.NONE);
            stack = ItemStack.EMPTY;
            this.discard();
            return true;
        }
        return false;
    }

    /**Zipper timed out.*/
    private void expire(LivingEntity host) {
        if (stack.is(ModItems.KNIFE) || stack.is(ModItems.KNIFE_BUNDLE)) {
            //the knives were cutting the whole time: the wound opens now
            LivingEntity owner = getOwner();
            float damage = 2.0F + Math.min(stack.getCount(), 4);
            DamageSource source = owner instanceof Player player ? host.damageSources().playerAttack(player)
                    : owner != null ? host.damageSources().mobAttack(owner) : host.damageSources().generic();
            if (host.hurt(source, damage)) {
                MainUtil.makeBleed(host, 1, 200, owner);
            }
        }
        popOut(host);
    }

    /**Throws the item out of the mob (or where the zipper is if the mob is gone) and ends the zipper.*/
    private void popOut(LivingEntity host) {
        if (this.level() instanceof ServerLevel serverLevel) {
            double x = host != null ? host.getX() : this.getX();
            double y = host != null ? host.getY() + host.getBbHeight() * 0.5D : this.getY();
            double z = host != null ? host.getZ() : this.getZ();
            if (!stack.isEmpty()) {
                net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(
                        serverLevel, x, y, z, stack.copy());
                item.setDefaultPickUpDelay();
                item.setDeltaMovement((this.random.nextDouble() - 0.5D) * 0.2D, 0.25D, (this.random.nextDouble() - 0.5D) * 0.2D);
                serverLevel.addFreshEntity(item);
            }
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, x, y, z, 10, 0.3D, 0.3D, 0.3D, 0.03D);
            serverLevel.playSound(null, BlockPos.containing(x, y, z), SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 0.9F, 1.2F);
        }
        stack = ItemStack.EMPTY;
        this.discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("host")) {
            hostUUID = tag.getUUID("host");
        }
        if (tag.hasUUID("owner")) {
            ownerUUID = tag.getUUID("owner");
        }
        stack = ItemStack.of(tag.getCompound("stack"));
        life = tag.getInt("life");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (hostUUID != null) {
            tag.putUUID("host", hostUUID);
        }
        if (ownerUUID != null) {
            tag.putUUID("owner", ownerUUID);
        }
        tag.put("stack", stack.save(new CompoundTag()));
        tag.putInt("life", life);
    }
}
