package net.hydra.jojomod.entity.objects;

import net.hydra.jojomod.entity.ModEntities;
import net.hydra.jojomod.client.ClientUtil;
import net.hydra.jojomod.event.ModGamerules;
import net.hydra.jojomod.event.ModParticles;
import net.hydra.jojomod.event.powers.StandUser;
import net.hydra.jojomod.event.powers.ModDamageTypes;
import net.hydra.jojomod.stand.powers.PowersStickyFingers;
import net.hydra.jojomod.item.KnifeItem;
import net.hydra.jojomod.util.MainUtil;
import net.hydra.jojomod.util.ZipperStorageData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ZipperStorageEntity extends ZipperEntity {

    public static final int MOB_ITEM_LIFE = 600;

    public static final float SIZE = 1.0F;

    private final SimpleContainer container = new SimpleContainer(1);
    private final Set<UUID> viewers = new HashSet<>();

    private boolean mobMode = false;
    private UUID mobUUID = null;
    private int mobTicks = 0;
    private boolean mounted = false;

    private float openAnim = 0.0F;
    private float prevOpenAnim = 0.0F;

    public ZipperStorageEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public static ZipperStorageEntity createForBlock(Level level, LivingEntity owner, BlockPos anchor, Direction face) {
        ZipperStorageEntity zipper = new ZipperStorageEntity(ModEntities.ZIPPER_STORAGE, level);
        zipper.setup(owner, anchor, face, anchor.getY(), true);
        zipper.setState(STATE_STORAGE);
        return zipper;
    }

    public static ZipperStorageEntity createForMob(Level level, LivingEntity owner, LivingEntity mob, ItemStack stack) {
        ZipperStorageEntity zipper = new ZipperStorageEntity(ModEntities.ZIPPER_STORAGE, level);
        zipper.mobMode = true;
        zipper.mobUUID = mob.getUUID();
        zipper.container.setItem(0, stack);
        zipper.setup(owner, mob.blockPosition(), Direction.NORTH, mob.blockPosition().getY(), true);
        zipper.setPos(mob.getX(), mob.getY() + mob.getBbHeight() * 0.3D, mob.getZ());
        zipper.setState(STATE_STORAGE);
        return zipper;
    }

    @Override
    public EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        EntityDimensions base = super.getDimensions(pose);
        return EntityDimensions.scalable(base.width * 1.35F, base.height * 1.35F);
    }

    public void mountOn(LivingEntity mob) {
        this.mounted = this.startRiding(mob, true);
    }

    public boolean isMobMode() {
        return mobMode;
    }

    public UUID getMobUUID() {
        return mobUUID;
    }

    public static boolean isStickyFingersUser(Player player) {
        return player instanceof StandUser standUser
                && standUser.roundabout$getStandPowers() instanceof PowersStickyFingers;
    }

    private boolean localPlayerHasStickyFingers() {
        return !this.level().isClientSide() || isStickyFingersUser(ClientUtil.getPlayer());
    }

    public BlockPos getFrontPos() {
        return mobMode ? this.blockPosition() : this.anchor.relative(this.getFace());
    }

    public float getOpenAnim() {
        return openAnim;
    }

    public float getPrevOpenAnim() {
        return prevOpenAnim;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            prevOpenAnim = openAnim;
            float target = getState() == STATE_STORAGE_OPEN ? 1.0F : 0.0F;
            float step = 1.0F / OPEN_ANIM_TICKS;
            openAnim = Mth.clamp(openAnim + Mth.clamp(target - openAnim, -step, step), 0.0F, 1.0F);
        }
    }

    @Override
    public boolean isHiddenFromLocalPlayer() {
        return !localPlayerHasStickyFingers();
    }

    @Override
    public boolean isPickable() {
        if (this.isRemoved() || mobMode || getState() == STATE_CLOSING) {
            return false;
        }
        return localPlayerHasStickyFingers();
    }

    @Override
    public boolean canBeHitByProjectile() {
        return !mobMode && !this.isRemoved() && getState() != STATE_CLOSING;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (mobMode || !isStickyFingersUser(player)) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer && this.level() instanceof ServerLevel serverLevel) {
            ZipperStorageData storageData = ZipperStorageData.get(serverLevel);
            int rows = Mth.clamp(getConnectedZippers().size(), 1, 6);
            SimpleContainer storage = storageData.getContainer(serverPlayer.getUUID(), rows * 9);
            MenuType<?> menuType = switch (rows) {
                case 2 -> MenuType.GENERIC_9x2;
                case 3 -> MenuType.GENERIC_9x3;
                case 4 -> MenuType.GENERIC_9x4;
                case 5 -> MenuType.GENERIC_9x5;
                case 6 -> MenuType.GENERIC_9x6;
                default -> MenuType.GENERIC_9x1;
            };
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new ChestMenu(menuType, id, inventory, storage, rows),
                    Component.translatable("roundabout.zipper_storage.title")));
            viewers.add(serverPlayer.getUUID());
            if (getState() == STATE_STORAGE) {
                setState(STATE_STORAGE_OPEN);
            }
            this.level().playSound(null, this.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN,
                    SoundSource.BLOCKS, 0.8F, 1.3F);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide() || this.isRemoved() || mobMode) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker == null) {
            attacker = source.getDirectEntity();
        }
        if (attacker instanceof Player || source.getDirectEntity() instanceof Projectile) {
            sparks(this.position().add(0, 0.5D, 0), 12);
            this.level().playSound(null, this.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0F, 1.2F);
            this.discard();
            return true;
        }
        return false;
    }

    @Override
    protected void serverTick() {
        if (getState() == STATE_CLOSING) {
            super.serverTick();
            return;
        }
        if (mobMode) {
            tickMob();
            return;
        }
        tickViewers();
        if (this.tickCount % 10 == 0 && this.level().getBlockState(anchor).isAir()) {
            this.discard();
        }
    }

    private void tickViewers() {
        if (!viewers.isEmpty() && this.tickCount % 5 == 0 && this.level() instanceof ServerLevel serverLevel) {
            ZipperStorageData data = ZipperStorageData.get(serverLevel);
            viewers.removeIf(id -> {
                Player viewer = serverLevel.getPlayerByUUID(id);
                return viewer == null || viewer.distanceToSqr(this) > 100.0D
                        || !(viewer.containerMenu instanceof ChestMenu menu)
                        || menu.getContainer() != data.getContainer(id);
            });
        }
        if (viewers.isEmpty() && getState() == STATE_STORAGE_OPEN) {
            setState(STATE_STORAGE);
        }
    }

    private void tickMob() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        mobTicks++;
        Entity found = mobUUID == null ? null : serverLevel.getEntity(mobUUID);
        if (!(found instanceof LivingEntity mob) || !mob.isAlive()) {
            popAll(this.position());
            this.discard();
            return;
        }
        if (!mounted || this.getVehicle() != mob) {
            this.setPos(mob.getX(), mob.getY() + mob.getBbHeight() * 0.3D, mob.getZ());
        }
        ItemStack stack = container.getItem(0);
        if (stack.isEmpty()) {
            finishMob(mob);
            return;
        }
        if (applyItemEffects(mob, stack)) {
            return;
        }
        if (mobTicks >= MOB_ITEM_LIFE) {
            onExpire(mob, stack);
            finishMob(mob);
        }
    }

    private boolean applyItemEffects(LivingEntity mob, ItemStack stack) {
        Item item = stack.getItem();
        Vec3 at = mob.position();
        if (item == Items.LAVA_BUCKET && mobTicks == 40) {
            mob.setSecondsOnFire(10);
            popStack(at, new ItemStack(Items.BUCKET));
            container.setItem(0, ItemStack.EMPTY);
            finishMob(mob);
            return true;
        }
        if (item == Items.WATER_BUCKET && mobTicks == 40) {
            mob.clearFire();
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
            popStack(at, new ItemStack(Items.BUCKET));
            container.setItem(0, ItemStack.EMPTY);
            finishMob(mob);
            return true;
        }
        if (item == Items.TNT && mobTicks == 60) {
            stack.shrink(1);
            boolean griefing = this.level().getGameRules().getBoolean(ModGamerules.ROUNDABOUT_STAND_GRIEFING);
            this.level().explode(null, mob.getX(), mob.getY() + 0.5D, mob.getZ(), 1.5F,
                    griefing ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
            finishMob(mob);
            return true;
        }
        if ((item == Items.SPIDER_EYE || item == Items.POISONOUS_POTATO || item == Items.PUFFERFISH) && mobTicks == 20) {
            stack.shrink(1);
            mob.addEffect(new MobEffectInstance(MobEffects.POISON, 140, 1));
            finishMob(mob);
            return true;
        }
        if (item == Items.GOLDEN_APPLE && mobTicks == 20) {
            stack.shrink(1);
            mob.heal(6.0F);
            mob.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
            finishMob(mob);
            return true;
        }
        return false;
    }

    private void onExpire(LivingEntity mob, ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof KnifeItem || item instanceof SwordItem || item instanceof AxeItem) {
            Player owner = getOwnerPlayer();
            DamageSource source = owner != null
                    ? ModDamageTypes.of(this.level(), ModDamageTypes.STAND, this, owner)
                    : ModDamageTypes.of(this.level(), ModDamageTypes.STAND, this);
            mob.hurt(source, 3.0F);
            MainUtil.makeBleed(mob, 2, 200, owner != null ? owner : this);
        }
    }

    private void finishMob(LivingEntity mob) {
        popAll(mob.position());
        sparks(mob.position().add(0, mob.getBbHeight() / 2, 0), 10);
        this.level().playSound(null, mob.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 0.9F, 1.1F);
        close();
    }

    private void popStack(Vec3 pos, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity item = new ItemEntity(this.level(), pos.x, pos.y + 0.5D, pos.z, stack);
        item.setDefaultPickUpDelay();
        this.level().addFreshEntity(item);
    }

    private void popAll(Vec3 pos) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            popStack(pos, container.getItem(i));
        }
        container.clearContent();
    }

    private void sparks(Vec3 pos, int count) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, pos.x, pos.y, pos.z, count, 0.3D, 0.4D, 0.3D, 0.02D);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide() && reason.shouldDestroy() && !container.isEmpty()) {
            popAll(this.position());
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        mobMode = tag.getBoolean("mobMode");
        mobTicks = tag.getInt("mobTicks");
        if (tag.hasUUID("mobUUID")) {
            mobUUID = tag.getUUID("mobUUID");
        }
        container.fromTag(tag.getList("items", Tag.TAG_COMPOUND));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("mobMode", mobMode);
        tag.putInt("mobTicks", mobTicks);
        if (mobUUID != null) {
            tag.putUUID("mobUUID", mobUUID);
        }
        tag.put("items", container.createTag());
    }
}
