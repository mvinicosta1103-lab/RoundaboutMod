package net.hydra.jojomod.stand.powers;

import com.google.common.collect.Lists;
import net.hydra.jojomod.client.StandIcons;
import net.hydra.jojomod.client.ClientUtil;
import net.hydra.jojomod.client.ZipperDetachedLimbRenderer;
import net.hydra.jojomod.access.IStickyFingersDismemberment;
import net.hydra.jojomod.entity.ModEntities;
import net.hydra.jojomod.entity.objects.ZipperEntity;
import net.hydra.jojomod.entity.objects.ZipperStorageEntity;
import net.hydra.jojomod.entity.projectile.StickyFingersFistEntity;
import net.hydra.jojomod.entity.stand.StandEntity;
import net.hydra.jojomod.entity.stand.StickyFingersEntity;
import net.hydra.jojomod.event.AbilityIconInstance;
import net.hydra.jojomod.event.index.OffsetIndex;
import net.hydra.jojomod.event.index.PacketDataIndex;
import net.hydra.jojomod.event.index.PowerIndex;
import net.hydra.jojomod.event.ModParticles;
import net.hydra.jojomod.event.index.SoundIndex;
import net.hydra.jojomod.event.powers.StandPowers;
import net.hydra.jojomod.event.powers.ModDamageTypes;
import net.hydra.jojomod.sound.ModSounds;
import net.hydra.jojomod.stand.powers.elements.PowerContext;
import net.hydra.jojomod.stand.powers.presets.NewPunchingStand;
import net.hydra.jojomod.util.MainUtil;
import net.hydra.jojomod.util.C2SPacketUtil;
import net.hydra.jojomod.util.S2CPacketUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PowersStickyFingers extends NewPunchingStand {

    private static final SoundEvent[] STICKY_BARRAGE_HITS = {
            ModSounds.SF_HIT_1_EVENT, ModSounds.SF_HIT_2_EVENT,
            ModSounds.SF_HIT_3_EVENT, ModSounds.SF_HIT_4_EVENT
    };
    private int stickyBarrageHitIndex;

    public PowersStickyFingers(LivingEntity self) {
        super(self);
    }

    @Override
    public StandPowers generateStandPowers(LivingEntity entity) {
        return new PowersStickyFingers(entity);
    }

    @Override
    public boolean isStandEnabled() {
        return true;
    }

    @Override
    public boolean isWip() {
        return true;
    }

    @Override
    public Component ifWipListDevStatus() {
        return Component.translatable("roundabout.dev_status.active").withStyle(ChatFormatting.AQUA);
    }

    @Override
    public Component ifWipListDev() {
        return Component.literal("Yagami").withStyle(ChatFormatting.YELLOW);
    }

    @Override
    protected Byte getSummonSound() {
        return SoundIndex.SUMMON_SOUND;
    }

    @Override
    public SoundEvent getSoundFromByte(byte soundChoice) {
        if (soundChoice == SoundIndex.SUMMON_SOUND) {
            return ModSounds.SF_SUMMON_EVENT;
        }
        return super.getSoundFromByte(soundChoice);
    }

    @Override
    public void playBarrageCrySound() {
        stickyBarrageHitIndex = 0;
        if (!this.self.level().isClientSide()) {
            this.self.level().playSound(null, this.self.blockPosition(), ModSounds.SF_BRUNO_ARI_EVENT,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    @Override
    public SoundEvent getPunchLandSound() {
        return isStickyBarrageActive() ? nextStickyBarrageHit() : super.getPunchLandSound();
    }

    @Override
    public SoundEvent getPunchLandLastSound() {
        return isStickyBarrageActive() ? ModSounds.SF_HEAVY_HIT_EVENT : super.getPunchLandLastSound();
    }

    private boolean isStickyBarrageActive() {
        return barrageTimer > 0 || forcedUnzipTargetId != -1 || this.getActivePower() == PowerIndex.BARRAGE;
    }

    private SoundEvent nextStickyBarrageHit() {
        SoundEvent sound = STICKY_BARRAGE_HITS[stickyBarrageHitIndex % STICKY_BARRAGE_HITS.length];
        stickyBarrageHitIndex++;
        return sound;
    }

    private void playStickyBarrageHit(double x, double y, double z) {
        if (this.self.level().isClientSide()) return;
        SoundEvent sound = nextStickyBarrageHit();
        this.self.level().playSound(null, x, y, z, sound, SoundSource.PLAYERS,
                0.95F, 0.92F + this.self.getRandom().nextFloat() * 0.16F);
    }

    private void playStickyHeavyHit(double x, double y, double z) {
        if (!this.self.level().isClientSide()) {
            this.self.level().playSound(null, x, y, z, ModSounds.SF_HEAVY_HIT_EVENT,
                    SoundSource.PLAYERS, 1.2F, 1.0F);
        }
    }

    @Override
    public StandEntity getNewStandEntity() {
        return ModEntities.STICKY_FINGERS.create(this.getSelf().level());
    }

    @Override
    public List<Byte> getSkinList() {
        List<Byte> list = Lists.newArrayList();
        list.add(StickyFingersEntity.PART_5);
        list.add(StickyFingersEntity.MANGA_SKIN);
        list.add(StickyFingersEntity.BLACK_AND_WHITE);
        list.add(StickyFingersEntity.SO_RETRO_SKIN);
        return list;
    }

    @Override
    public Component getSkinName(byte skinId) {
        if (skinId == StickyFingersEntity.MANGA_SKIN) {
            return Component.translatable("skins.roundabout.sticky_fingers.manga");
        }
        if (skinId == StickyFingersEntity.BLACK_AND_WHITE) {
            return Component.translatable("skins.roundabout.sticky_fingers.black_and_white");
        }
        if (skinId == StickyFingersEntity.SO_RETRO_SKIN) {
            return Component.translatable("skins.roundabout.sticky_fingers.so_retro");
        }
        return Component.translatable("skins.roundabout.sticky_fingers.base");
    }

    @Override
    public void powerActivate(PowerContext context) {
        switch (context) {
            case SKILL_1_NORMAL -> {
                unzipClient();
            }
            case SKILL_1_CROUCH -> {
                massUnzipClient();
            }
            case SKILL_1_GUARD, SKILL_1_CROUCH_GUARD -> {
                storeClient();
            }

            case SKILL_2_NORMAL -> primeFistClient();
            case SKILL_2_CROUCH -> releaseZippersClient();
            case SKILL_2_GUARD -> {
                if (this.self.level().isClientSide()) ClientUtil.openStickyFingersAutoUnzipScreen();
            }

            case SKILL_3_NORMAL -> {
                if (hasLatchedFist()) grappleClient();
                else dashOrVaultClient();
            }
            case SKILL_3_CROUCH -> {
                if (this.self.onGround()) latchZipperClient();
                else dashOrVaultClient();
            }
            case SKILL_3_GUARD, SKILL_3_CROUCH_GUARD -> toggleZipMarksClient();

            case SKILL_4_NORMAL -> {
                guillotineClient();
            }
            case SKILL_4_CROUCH -> {
                if (this.self.level().isClientSide()) ClientUtil.openStickyFingersUnzipScreen();
            }
            case SKILL_4_GUARD -> collapsePoleClient();
            case SKILL_4_CROUCH_GUARD -> {
                if (this.self.level().isClientSide()) ClientUtil.openStickyFingersLimbModeScreen();
            }
        }
    }

    public void dashOrVaultClient() {
        if (!doVault()) {
            dash();
        }
    }

    public static final byte UNZIP = PowerIndex.POWER_1;
    public static final int UNZIP_COOLDOWN = 60;
    public static final double UNZIP_RANGE = 6.0D;

    public static boolean isInZipperDimension(Entity entity) {
        return entity instanceof IStickyFingersDismemberment state && state.roundabout$isInZipperDimension();
    }

    public void unzipClient() {
        boolean zipperExit = isInZipperDimension(this.self);
        boolean invisibleRecovery = this.self instanceof Player
                && this.self.isInvisible();
        if (zipperExit || invisibleRecovery) {
            if (zipperExit) this.tryPower(UNZIP, true);
            // Send Z even when the client-side zipper flag became stale; the
            // server validates the Sticky Fingers state before forcing recovery.
            tryPowerPacket(UNZIP);
            return;
        }
        // aiming at one of your open portals closes it, even while Unzip is on cooldown
        if (!this.onCooldown(PowerIndex.SKILL_1)
                || findAimedOpenPortal() != null) {
            this.tryPower(UNZIP, true);
            tryPowerPacket(UNZIP);
        }
    }

    /** The owner's open portal the user is looking straight at, or null. */
    private ZipperEntity findAimedOpenPortal() {
        if (isInZipperDimension(this.self)) return null;
        Vec3 eye = this.self.getEyePosition();
        Vec3 end = eye.add(this.self.getViewVector(1.0F).scale(UNZIP_RANGE));
        ZipperEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ZipperEntity zipper : this.self.level().getEntitiesOfClass(ZipperEntity.class,
                new AABB(eye, end).inflate(2.0D), z -> z.isOpenPortal() && z.isOwner(this.self))) {
            AABB box = zipper.getPortalAimBox();
            java.util.Optional<Vec3> hit = box.clip(eye, end);
            double distance = hit.isPresent() ? eye.distanceToSqr(hit.get()) : box.contains(eye) ? 0.0D : -1.0D;
            if (distance >= 0.0D && distance < bestDistance) {
                best = zipper;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void startUnzip() {
        // Closing an open portal ignores the cooldown and does not start a new one.
        ZipperEntity aimedPortal = findAimedOpenPortal();
        if (aimedPortal != null) {
            if (!this.self.level().isClientSide()) aimedPortal.forceClosePortal();
            return;
        }
        boolean insideZipperDimension = isInZipperDimension(this.self);
        if (insideZipperDimension) {
            if (!this.self.level().isClientSide() && this.self instanceof Player player) {
                ZipperEntity.exitOwnerAtCurrentPosition(player);
            }
            return;
        }
        // Keep the exit zipper within normal reach. The old 64-block range could
        // place an exit far away and then teleport the user to that distant block.
        double range = UNZIP_RANGE;
        HitResult hit = this.self.pick(range, 1.0F, false);
        Level level = this.self.level();
        EntityHitResult entityHit = MainUtil.rayCastEntityHitResult(this.self, (float) range);
        boolean entityIsInFrontOfBlock = entityHit != null
                && (!(hit instanceof BlockHitResult blockHit)
                || entityHit.getLocation().distanceToSqr(this.self.getEyePosition())
                <= blockHit.getLocation().distanceToSqr(this.self.getEyePosition()));
        if (!insideZipperDimension && entityIsInFrontOfBlock && entityHit.getEntity() instanceof LivingEntity target
                && target != this.self && !(target instanceof StandEntity)) {
            this.setCooldown(PowerIndex.SKILL_1, UNZIP_COOLDOWN);
            if (!this.self.level().isClientSide()) unzipTarget(target);
            return;
        }

        if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) return;
        BlockPos anchor = blockHit.getBlockPos();
        Direction face = blockHit.getDirection();
        if (!ZipperEntity.canZipOnFace(level, anchor, face)) {
            message("roundabout.zipper.invalid_surface");
            return;
        }
        BlockPos front = anchor.relative(face);
        if (!level.getBlockState(front).getCollisionShape(level, front).isEmpty()) {
            return;
        }
        this.setCooldown(PowerIndex.SKILL_1, UNZIP_COOLDOWN);
        if (level.isClientSide()) {
            return;
        }
        playZipperCollapseAnimation();
        // Keep the segment on the block that was actually hit. Deriving this
        // from the hit point's midpoint shifted lower hits onto the block below.
        int baseY = anchor.getY();

        ZipperEntity zipper = ZipperEntity.create(level, this.self, anchor, face, baseY);
        level.addFreshEntity(zipper);

        level.playSound(null, this.self.blockPosition(), ModSounds.SF_ZIPPER_BREAK_EVENT,
                SoundSource.PLAYERS, 1.0F, 0.9F);
        if (level instanceof ServerLevel serverLevel) {
            BlockState surface = serverLevel.getBlockState(anchor);
            if (!surface.isAir()) {
                Vec3 pos = zipper.position();
                serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, surface),
                        pos.x, pos.y + 1.0D, pos.z, 16, 0.3D, 0.6D, 0.3D, 0.05D);
            }
        }
    }

    private void unzipTarget(LivingEntity target) {
        if (!target.isAlive() || target instanceof StandEntity) return;
        playZipperCollapseAnimation();
        addTag(target);
        int level = Math.max(1, getTagLevel(target));
        float damage = getUnzipBodyDamageLimit(target);
        float dealt = applyUnzipBodyDamage(target, damage, damage);
        boolean hit = damage <= 0.0F || dealt > 0.0F;
        if (hit) {
            applyDismembermentForLevel(target, level);
            applyConfiguredAttackDismemberment(target);
            if (dealt > 0.0F) {
                target.knockback(0.25D, this.self.getX() - target.getX(), this.self.getZ() - target.getZ());
            }
        }
        if (this.self.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, target.getX(),
                    target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    12 + level * 4, 0.35D, 0.45D, 0.35D, 0.03D);
        }
        this.self.level().playSound(null, target.blockPosition(), ModSounds.SF_ZIPPER_BREAK_EVENT,
                SoundSource.PLAYERS, 1.0F, 0.9F + level * 0.04F);
    }

    /** Caps one Unzip Body activation at 35% max health and preserves a 50% health floor. */
    private float getUnzipBodyDamageLimit(LivingEntity target) {
        float maxHealth = target.getMaxHealth();
        float floor = maxHealth * 0.50F;
        float roomAboveFloor = Math.max(0.0F, target.getHealth() - floor);
        return Math.min(maxHealth * 0.35F, roomAboveFloor);
    }

    /** Applies part of a cumulative Unzip Body budget, never crossing the half-health floor. */
    private float applyUnzipBodyDamage(LivingEntity target, float requested, float budgetRemaining) {
        if (!target.isAlive() || requested <= 0.0F || budgetRemaining <= 0.0F) return 0.0F;
        float damage = Math.min(Math.min(requested, budgetRemaining), getUnzipBodyDamageLimit(target));
        if (damage <= 0.0F || !target.hurt(
                ModDamageTypes.of(target.level(), ModDamageTypes.STAND, this.self), damage)) {
            return 0.0F;
        }
        float floor = target.getMaxHealth() * 0.50F;
        if (target.isAlive() && target.getHealth() < floor) target.setHealth(floor);
        return damage;
    }

    private void message(String key) {
        if (this.self instanceof Player player) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }

    public static final int TAG_DURATION = 600;
    public static final int TAG_MAX_LEVEL = 3;
    private static final int BARRAGE_TAG_INTERVAL_TICKS = 8;

    public static final int ZIPPER_PART_HEAD = 1;
    public static final int ZIPPER_PART_LEFT_ARM = 2;
    public static final int ZIPPER_PART_RIGHT_ARM = 4;
    public static final int ZIPPER_PART_LEFT_LEG = 8;
    public static final int ZIPPER_PART_RIGHT_LEG = 16;
    public static final byte ZIPPER_UNZIP_PART = PowerIndex.POWER_4_SNEAK;
    public static final byte ZIPPER_PICKUP_DROPPED_LIMB = PowerIndex.POWER_4_SNEAK_EXTRA;
    private static final int ZIPPER_PART_DURATION = 100;

    private record ClientTagState(Level level, int zipperLevel, long expiresAt, boolean forced) {}
    /** Server -> client flag OR'd into the mark level: the marks belong to a forced Unzip Body (Zip Mode OFF). */
    public static final int FORCED_MARK_FLAG = 1 << 10;
    private record ClientDismembermentState(Level level, int partMask, long expiresAt) {}
    private static final Map<Integer, ClientTagState> CLIENT_TAG_LEVELS = new ConcurrentHashMap<>();
    private static final Map<Integer, ClientDismembermentState> CLIENT_DISMEMBERMENT = new ConcurrentHashMap<>();

    private final Map<UUID, long[]> tags = new HashMap<>();
    private final Map<UUID, Long> barrageTagNextTick = new HashMap<>();
    private boolean zipperUseButtonWasDown;
    private static final Map<UUID, Long> STOLEN_LIMB_LOCKS = new ConcurrentHashMap<>();

    public static void updateClientTagLevel(int entityId, int zipperLevel) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) return;
        boolean forced = (zipperLevel & FORCED_MARK_FLAG) != 0;
        zipperLevel &= ~FORCED_MARK_FLAG;
        if (zipperLevel <= 0) {
            CLIENT_TAG_LEVELS.remove(entityId);
            return;
        }
        CLIENT_TAG_LEVELS.put(entityId,
                new ClientTagState(level, zipperLevel, level.getGameTime() + TAG_DURATION, forced));
    }

    /** True while the marks on this entity come from a forced Unzip Body, so they render even with Zip Mode OFF. */
    public static boolean isClientForcedTag(Entity entity) {
        ClientTagState state = CLIENT_TAG_LEVELS.get(entity.getId());
        return state != null && state.forced() && state.level() == entity.level()
                && state.expiresAt() > entity.level().getGameTime();
    }

    public static int getClientTagLevel(Entity entity) {
        ClientTagState state = CLIENT_TAG_LEVELS.get(entity.getId());
        if (state == null) return 0;
        if (state.level() != entity.level() || state.expiresAt() <= entity.level().getGameTime()) {
            CLIENT_TAG_LEVELS.remove(entity.getId(), state);
            return 0;
        }
        return state.zipperLevel();
    }

    public static void clearClientTagLevels() {
        CLIENT_TAG_LEVELS.clear();
    }

    public static void updateClientDismemberment(int entityId, int part, int duration) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) return;
        if (part == 0 || duration <= 0) {
            CLIENT_DISMEMBERMENT.remove(entityId);
            return;
        }
        CLIENT_DISMEMBERMENT.compute(entityId, (id, old) -> {
            int mask = old != null && old.level() == level ? old.partMask() | part : part;
            return new ClientDismembermentState(level, mask, level.getGameTime() + duration);
        });
    }

    public static int getClientDismembermentMask(Entity entity) {
        if (entity instanceof IStickyFingersDismemberment dismemberment) {
            return dismemberment.roundabout$getStickyFingersDismemberment();
        }
        ClientDismembermentState state = CLIENT_DISMEMBERMENT.get(entity.getId());
        if (state == null) return 0;
        if (state.level() != entity.level() || state.expiresAt() <= entity.level().getGameTime()) {
            CLIENT_DISMEMBERMENT.remove(entity.getId(), state);
            return 0;
        }
        return state.partMask();
    }

    public static boolean hasBothArmsRemoved(Entity entity) {
        int arms = ZIPPER_PART_LEFT_ARM | ZIPPER_PART_RIGHT_ARM;
        return (getClientDismembermentMask(entity) & arms) == arms;
    }

    public static boolean hasBothLegsRemoved(Entity entity) {
        int legs = ZIPPER_PART_LEFT_LEG | ZIPPER_PART_RIGHT_LEG;
        return (getClientDismembermentMask(entity) & legs) == legs;
    }

    public static boolean hasNoLimbs(Entity entity) {
        int limbs = ZIPPER_PART_LEFT_ARM | ZIPPER_PART_RIGHT_ARM
                | ZIPPER_PART_LEFT_LEG | ZIPPER_PART_RIGHT_LEG;
        return (getClientDismembermentMask(entity) & limbs) == limbs;
    }

    private void syncTagLevel(LivingEntity target, int zipperLevel) {
        if (this.self instanceof ServerPlayer player) {
            S2CPacketUtil.sendGenericIntIntToClientPacket(player, PacketDataIndex.S2C_ZIPPER_MARK,
                    target.getId(), zipperLevel);
        }
    }

    private void applyDismembermentForLevel(LivingEntity target, int level) {
        if (!target.isAlive()) return;
        showDismemberedPart(target, ZIPPER_PART_LEFT_ARM | ZIPPER_PART_RIGHT_ARM);
        if (level >= 2) {
            showDismemberedPart(target, ZIPPER_PART_LEFT_LEG | ZIPPER_PART_RIGHT_LEG);
        }
        if (level >= 3) {
            showDismemberedPart(target, ZIPPER_PART_HEAD);
        }
    }

    public void applyConfiguredAttackDismemberment(LivingEntity target) {
        if (this.self.level().isClientSide() || target == this.self || !target.isAlive()
                || target instanceof StandEntity || !areZipMarksEnabled()
                || !(this.self instanceof IStickyFingersDismemberment state)) return;
        int config = state.roundabout$getStickyFingersAutoUnzipConfig();
        if ((config & ZIPPER_AUTO_UNZIP_ENABLED) == 0) return;
        int selected = config & (ZIPPER_PART_HEAD | ZIPPER_PART_LEFT_ARM | ZIPPER_PART_RIGHT_ARM
                | ZIPPER_PART_LEFT_LEG | ZIPPER_PART_RIGHT_LEG);
        if (selected != 0) showDismemberedPart(target, selected);
    }

    private void showDismemberedPart(LivingEntity target, int part) {
        if (target == this.self || !(this.self.level() instanceof ServerLevel)
                || !(target instanceof IStickyFingersDismemberment dismemberment)) return;
        int oldMask = dismemberment.roundabout$getStickyFingersDismemberment();
        int addedParts = part & ~oldMask;
        if (addedParts == 0) return;
        dismemberment.roundabout$setStickyFingersDismemberment(oldMask | part);
        int graftMask = dismemberment.roundabout$getStickyFingersGraftMask() & ~part;
        dismemberment.roundabout$setStickyFingersGraftMask(graftMask);
        setGraftOrigins(dismemberment, graftMask, dismemberment.roundabout$getStickyFingersGraftData());

        S2CPacketUtil.sendGenericIntIntToAll(PacketDataIndex.S2C_ZIPPER_LIMB_ANIMATION,
                target.getId(), addedParts);
        if (target.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, target.getX(),
                    target.getY() + target.getBbHeight() * 0.52D, target.getZ(),
                    12, 0.25D, 0.35D, 0.25D, 0.025D);
        }
    }

    @Override
    public boolean tryIntPower(int move, boolean forced, int partMask) {
        if (move == ZIPPER_AUTO_UNZIP_CONFIG) {
            if (!this.self.level().isClientSide()
                    && this.self instanceof IStickyFingersDismemberment configState) {
                configState.roundabout$setStickyFingersAutoUnzipConfig(partMask);
            }
            return true;
        }
        if (move == ZIPPER_PICKUP_DROPPED_LIMB) {
            takeDroppedLimb(partMask);
            return true;
        }
        if (move == ZIPPER_UNZIP_PART) {
            unzipOwnLimb(partMask);
            return true;
        }
        return super.tryIntPower(move, forced, partMask);
    }

    private void unzipOwnLimb(int partMask) {
        int limbParts = ZIPPER_PART_LEFT_ARM | ZIPPER_PART_RIGHT_ARM
                | ZIPPER_PART_LEFT_LEG | ZIPPER_PART_RIGHT_LEG;
        if (this.self.level().isClientSide() || !(this.self instanceof Player)
                || !(this.self instanceof IStickyFingersDismemberment state)
                || Integer.bitCount(partMask) != 1 || (partMask & limbParts) != partMask) return;

        int oldMask = state.roundabout$getStickyFingersDismemberment();
        if ((oldMask & partMask) != 0) return;
        state.roundabout$setStickyFingersDismemberment(oldMask | partMask);
        int graftMask = state.roundabout$getStickyFingersGraftMask() & ~partMask;
        state.roundabout$setStickyFingersGraftMask(graftMask);
        setGraftOrigins(state, graftMask, state.roundabout$getStickyFingersGraftData());

        S2CPacketUtil.sendGenericIntIntToAll(PacketDataIndex.S2C_ZIPPER_LIMB_ANIMATION,
                this.self.getId(), partMask);
        this.self.level().playSound(null, this.self.blockPosition(), ModSounds.SF_ZIPPER_BREAK_EVENT,
                SoundSource.PLAYERS, 1.0F, 0.9F);
        if (this.self.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, this.self.getX(),
                    this.self.getY() + this.self.getBbHeight() * 0.5D, this.self.getZ(),
                    16, 0.3D, 0.4D, 0.3D, 0.025D);
        }
    }

    public boolean isTagged(LivingEntity target) {
        long[] tag = tags.get(target.getUUID());
        return tag != null && tag[1] > target.level().getGameTime();
    }

    public int getTagLevel(LivingEntity target) {
        return isTagged(target) ? (int) tags.get(target.getUUID())[0] : 0;
    }

    public void addTag(LivingEntity target) {
        if (!areZipMarksEnabled() || target == this.self || target instanceof StandEntity) return;
        long[] tag = tags.computeIfAbsent(target.getUUID(), id -> new long[]{0L, 0L});
        long now = target.level().getGameTime();
        if (tag[1] <= now) {
            tag[0] = 0L;
        }
        tag[0] = Math.min(TAG_MAX_LEVEL, tag[0] + 1);
        tag[1] = now + TAG_DURATION;
        syncTagLevel(target, (int) tag[0]);
    }

    private void addBarrageTagGradually(LivingEntity target) {
        long now = target.level().getGameTime();
        long nextAllowedTick = barrageTagNextTick.getOrDefault(target.getUUID(), Long.MIN_VALUE);
        if (now < nextAllowedTick) return;
        addTag(target);
        barrageTagNextTick.put(target.getUUID(), now + BARRAGE_TAG_INTERVAL_TICKS);
    }

    private void removeTag(LivingEntity target) {
        tags.remove(target.getUUID());
        barrageTagNextTick.remove(target.getUUID());
        syncTagLevel(target, 0);
    }

    @Override
    public boolean StandDamageEntityAttack(Entity target, float pow, float knockbackStrength, Entity attacker) {
        boolean hit = super.StandDamageEntityAttack(target, applyZipModeDamage(attacker, pow), knockbackStrength, attacker);
        if (hit && attacker == this.self && target instanceof LivingEntity living && !(target instanceof StandEntity)
                && !this.self.level().isClientSide()) {
            addTag(living);
            applyConfiguredAttackDismemberment(living);
        }
        return hit;
    }

    @Override
    public boolean StandRushDamageEntityAttack(Entity target, float pow, float knockbackStrength, Entity attacker) {
        if (attacker == this.self && areZipMarksEnabled() && target instanceof LivingEntity living) {
            // Barrage damage bypasses StandDamageEntityAttack, so cap it here and keep it nonlethal.
            float nonlethalChip = Math.max(0.0F, Math.min(0.05F, living.getHealth() - 0.01F));
            boolean hit = super.StandRushDamageEntityAttack(target, nonlethalChip, knockbackStrength, attacker);
            if (!this.self.level().isClientSide() && !(target instanceof StandEntity)) {
                // Grow marks gradually during the barrage instead of reaching max level on its first few hits.
                addBarrageTagGradually(living);
                if (hit) applyConfiguredAttackDismemberment(living);
            }
            return hit;
        }
        return super.StandRushDamageEntityAttack(target, pow, knockbackStrength, attacker);
    }

    // Sticky Fingers' launch-punch and zipper-release state.
    // Keep these unique to Sticky Fingers: POWER_2 is already used by mass unzip above.
    public static final byte ZIPPER_FIST = 90;
    public static final byte ZIPPER_RELEASE = 91;
    public static final byte ZIPPER_GRAPPLE = 93;
    public static final byte ZIPPER_LATCH = 98;
    public static final byte ZIPPER_TOGGLE_MARKS = 97;
    private static final byte ZIPPER_FIST_FIRE = 94;
    private static final byte ZIPPER_FIST_CANCEL = 95;
    public static final byte ZIPPER_AUTO_UNZIP_CONFIG = 99;
    public static final int ZIPPER_AUTO_UNZIP_ENABLED = 1 << 8;
    private static final int FIST_COOLDOWN = 70;
    private static final int RELEASE_COOLDOWN = 180;
    private static final int GRAPPLE_COOLDOWN = 50;
    private static final int LATCH_COOLDOWN = 100;
    private static final int MAX_RELEASE_WINDUP = 24;
    private static final int FIST_PREPARE_ANIM_TICKS = 7;
    private static final int FIST_IMPACT_ANIM_TICKS = 26;

    private StickyFingersFistEntity latchedFist;
    private int grappleTicks;
    private int maxReleaseTicks;
    private int zipperCollapseAnimTicks;
    private int maxReleaseTargetId = -1;
    private int maxReleaseLevel;
    private float maxReleaseDamageRemaining;
    private boolean fistPrimed;
    private int fistPrimeTicks;
    private int fistPrimeElapsedTicks;
    private int fistImpactTicks;

    public boolean areZipMarksEnabled() {
        return this.self instanceof net.hydra.jojomod.event.powers.StandUser standUser
                && standUser.roundabout$getUniqueStandModeToggle();
    }

    /** Zip Mode trades direct combat damage for faster zipper setup. */
    public static float applyZipModeDamage(Entity attacker, float damage) {
        if (attacker instanceof net.hydra.jojomod.event.powers.StandUser standUser
                && standUser.roundabout$getUniqueStandModeToggle()) {
            return damage * 0.10F;
        }
        return damage;
    }

    private void toggleZipMarksClient() {
        if (!(this.self instanceof net.hydra.jojomod.event.powers.StandUser standUser)) return;
        boolean enabled = !standUser.roundabout$getUniqueStandModeToggle();
        standUser.roundabout$setUniqueStandModeToggle(enabled);
        if (!enabled) clearClientTagLevels();
        this.tryPowerPacket(ZIPPER_TOGGLE_MARKS);
        message(enabled ? "roundabout.zipper_marks_on" : "roundabout.zipper_marks_off");
    }

    private void clearAllTags() {
        for (UUID targetId : new ArrayList<>(tags.keySet())) {
            Entity entity = this.self.level() instanceof ServerLevel serverLevel
                    ? serverLevel.getEntity(targetId) : null;
            if (entity instanceof LivingEntity target) syncTagLevel(target, 0);
        }
        tags.clear();
        barrageTagNextTick.clear();
    }

    public void primeFistClient() {
        if (!onCooldown(PowerIndex.SKILL_2)) {
            fistPrimed = true;
            fistPrimeTicks = 80;
            fistPrimeElapsedTicks = 0;
            tryPower(ZIPPER_FIST, true);
            tryPowerPacket(ZIPPER_FIST);
        }
    }

    private void firePrimedFistClient() {
        if (!fistPrimed) return;
        fistPrimed = false;
        fistPrimeTicks = 0;
        fistPrimeElapsedTicks = 0;
        tryPower(ZIPPER_FIST_FIRE, true);
        tryPowerPacket(ZIPPER_FIST_FIRE);
    }

    private void cancelPrimedFistClient() {
        if (!fistPrimed) return;
        fistPrimed = false;
        fistPrimeTicks = 0;
        fistPrimeElapsedTicks = 0;
        tryPower(ZIPPER_FIST_CANCEL, true);
        tryPowerPacket(ZIPPER_FIST_CANCEL);
    }

    @Override
    public void buttonInputAttack(boolean keyIsDown, Options options) {
        if (isInZipperDimension(this.self)) return;
        if (keyIsDown && fistPrimed) {
            firePrimedFistClient();
            return;
        }
        super.buttonInputAttack(keyIsDown, options);
    }

    @Override
    public void buttonInputUse(boolean keyIsDown, Options options) {
        // InputEvents polls keyUse.isDown() continuously; consume only the press edge so
        // held M2 cannot resend unzip/reattach packets every client tick.
        if (!keyIsDown) {
            zipperUseButtonWasDown = false;
            super.buttonInputUse(false, options);
            return;
        }
        if (zipperUseButtonWasDown) return;
        zipperUseButtonWasDown = true;
        if (isInZipperDimension(this.self)) {
            // M2 is an explicit exit control while inside; do not rely on the
            // invisible tunnel block receiving a world interaction raycast.
            unzipClient();
            return;
        }
        if (keyIsDown && fistPrimed) {
            cancelPrimedFistClient();
            return;
        }
        if (keyIsDown && tryReattachClient()) return;
        super.buttonInputUse(keyIsDown, options);
    }

    private boolean tryReattachClient() {
        int ownMask = getClientDismembermentMask(this.self) & LIMB_MASK;
        if (ownMask != 0) {
            ZipperDetachedLimbRenderer.DroppedLimbHit limbHit = ZipperDetachedLimbRenderer.rayPick(
                    this.self, ZIPPER_REATTACH_RANGE, ownMask);
            if (limbHit != null) {
                HitResult obstacle = this.self.pick(ZIPPER_REATTACH_RANGE, 1.0F, false);
                if (obstacle instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
                        && block.getLocation().distanceToSqr(this.self.getEyePosition())
                        < limbHit.hitPosition().distanceToSqr(this.self.getEyePosition())) return false;
                int bitIndex = Integer.numberOfTrailingZeros(limbHit.partMask());
                int packed = (limbHit.ownerId() << 3) | bitIndex;
                C2SPacketUtil.tryIntPowerPacket(ZIPPER_PICKUP_DROPPED_LIMB, packed);
                return true;
            }
        }
        EntityHitResult hit = MainUtil.rayCastEntityHitResult(this.self, ZIPPER_REATTACH_RANGE);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)
                || !isTorsoHit(target, hit.getLocation())
                || target == this.self || target instanceof StandEntity
                || getClientDismembermentMask(target) == 0) return false;
        HitResult blockHit = this.self.pick(ZIPPER_REATTACH_RANGE, 1.0F, false);
        if (blockHit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
                && block.getLocation().distanceToSqr(this.self.getEyePosition())
                < hit.getLocation().distanceToSqr(this.self.getEyePosition())) return false;
        this.tryPower(ZIPPER_REATTACH, true);
        tryPowerPacket(ZIPPER_REATTACH);
        return true;
    }

    private void primeFist() {
        this.fistPrimed = true;
        this.fistPrimeTicks = 80;
        this.fistPrimeElapsedTicks = 0;
        this.setActivePower(ZIPPER_FIST);
        this.poseStand(OffsetIndex.ATTACK);
        this.animateStand(StickyFingersEntity.ZIPPER_FIST_PREPARE);
        if (!this.self.level().isClientSide()) {
            this.self.level().playSound(null, this.self.blockPosition(), ModSounds.SF_ZIPPER_BREAK_EVENT,
                    SoundSource.PLAYERS, 0.7F, 1.1F);
        }
    }

    public void releaseZippersClient() {
        if (!onCooldown(PowerIndex.SKILL_2_SNEAK)) {
            tryPower(ZIPPER_RELEASE, true);
            tryPowerPacket(ZIPPER_RELEASE);
        }
    }

    public void grappleClient() {
        if (!onCooldown(PowerIndex.SKILL_3)) {
            tryPower(ZIPPER_GRAPPLE, true);
            tryPowerPacket(ZIPPER_GRAPPLE);
        }
    }

    public void latchZipperClient() {
        if (!onCooldown(PowerIndex.SKILL_3)) {
            tryPower(ZIPPER_LATCH, true);
            tryPowerPacket(ZIPPER_LATCH);
        }
    }

    private void startLatchZipper() {
        if (this.self.level().isClientSide()) return;
        HitResult hit = this.self.pick(UNZIP_RANGE, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        Level level = this.self.level();
        BlockPos anchor = blockHit.getBlockPos();
        Direction face = blockHit.getDirection();
        if (!ZipperEntity.canZipOnFace(level, anchor, face)) {
            message("roundabout.zipper.invalid_surface");
            return;
        }
        BlockPos front = anchor.relative(face);
        if (!level.getBlockState(front).getCollisionShape(level, front).isEmpty()) {
            return;
        }

        int feetY = Mth.floor(this.self.getY());
        int baseY = anchor.getY() < feetY ? anchor.getY() : Math.max(feetY, anchor.getY() - 1);
        Vec3 look = this.self.getLookAngle().normalize();
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() < 1.0E-4D) {
            horizontalLook = new Vec3(-Mth.sin(this.self.getYRot() * Mth.DEG_TO_RAD), 0.0D,
                    Mth.cos(this.self.getYRot() * Mth.DEG_TO_RAD));
        }
        Vec3 rideDirection;
        if (face.getAxis() == Direction.Axis.Y) {
            rideDirection = horizontalLook.normalize().scale(0.42D);
        } else {
            Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
            Vec3 tangent = look.subtract(normal.scale(look.dot(normal)));
            if (tangent.lengthSqr() < 1.0E-4D) {
                tangent = new Vec3(0.0D, 1.0D, 0.0D);
            }
            rideDirection = tangent.normalize().scale(0.42D);
        }

        this.setCooldown(PowerIndex.SKILL_3, LATCH_COOLDOWN);
        ZipperEntity zipper = ZipperEntity.createLatch(level, this.self, anchor, face, baseY, rideDirection);
        level.addFreshEntity(zipper);
        level.playSound(null, anchor, ModSounds.SF_HIT_ZIPPER_EVENT,
                SoundSource.PLAYERS, 1.0F, 0.95F);
    }

    private void launchFist() {
        this.setCooldown(PowerIndex.SKILL_2, FIST_COOLDOWN);
        this.fistPrimeElapsedTicks = 0;
        this.fistImpactTicks = FIST_IMPACT_ANIM_TICKS;
        this.poseStand(OffsetIndex.ATTACK);
        this.animateStand(StickyFingersEntity.ZIPPER_FIST_IMPACT);
        if (this.self.level().isClientSide()) return;

        StickyFingersFistEntity fist = new StickyFingersFistEntity(this.self, this.self.level());
        Vec3 look = this.self.getLookAngle().normalize();
        fist.setPos(this.self.getX() + look.x * 0.7D,
                this.self.getEyeY() - 0.25D + look.y * 0.7D,
                this.self.getZ() + look.z * 0.7D);
        fist.setLaunchOrigin(fist.position());
        fist.setDeltaMovement(look.scale(1.0D).add(this.self.getDeltaMovement().scale(0.25D)));
        fist.setNoGravity(true);
        this.self.level().addFreshEntity(fist);
        this.self.level().playSound(null, this.self.blockPosition(), ModSounds.SF_HIT_ZIPPER_EVENT,
                SoundSource.PLAYERS, 0.9F, 1.0F);
    }

    public void setLatchedFist(StickyFingersFistEntity fist) {
        this.latchedFist = fist;
        if (fist != null && fist.isLatched()) {
            // Hold the extended arm in its reaching pose while the fist grips the block.
            this.fistImpactTicks = 0;
            this.animateStand(StickyFingersEntity.ZIPPER_FIST_IDLE);
            this.poseStand(OffsetIndex.ATTACK);
        }
    }

    private boolean hasLatchedFist() {
        return latchedFist != null && !latchedFist.isRemoved() && latchedFist.isLatched();
    }

    private void startGrapple() {
        this.setCooldown(PowerIndex.SKILL_3, GRAPPLE_COOLDOWN);
        if (this.self.level().isClientSide() || latchedFist == null || latchedFist.isRemoved()
                || !latchedFist.isLatched()) return;
        this.grappleTicks = 50;
        this.setActivePower(ZIPPER_GRAPPLE);
    }

    private void startZipperRelease() {
        this.setCooldown(PowerIndex.SKILL_2_SNEAK, RELEASE_COOLDOWN);
        if (this.self.level().isClientSide()) return;

        Entity hit = MainUtil.rayCastEntity(this.self, 8.0F);
        if (!(hit instanceof LivingEntity target) || target instanceof StandEntity || target == this.self) {
            message("roundabout.zipper.not_tagged");
            return;
        }
        if (!isTagged(target)) {
            if (!areZipMarksEnabled()) {
                // Zip Mode OFF: no marks needed, the stand pins the target and zips it open piece by piece.
                startForcedUnzip(target);
            } else {
                message("roundabout.zipper.not_tagged");
            }
            return;
        }

        int level = getTagLevel(target);
        if (level >= TAG_MAX_LEVEL) {
            maxReleaseTargetId = target.getId();
            maxReleaseLevel = level;
            maxReleaseTicks = MAX_RELEASE_WINDUP;
            maxReleaseDamageRemaining = getUnzipBodyDamageLimit(target);
            this.animateStand(StickyFingersEntity.DEATH_BY_DISASSEMBLY);
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, MAX_RELEASE_WINDUP, 0, false, true));
            this.self.level().playSound(null, target.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN,
                    SoundSource.PLAYERS, 1.2F, 0.55F);
            return;
        }

        releaseZippersOn(target, level);
    }

    private void releaseZippersOn(LivingEntity target, int level) {
        if (!target.isAlive()) return;
        float damage = getUnzipBodyDamageLimit(target);
        float dealt = applyUnzipBodyDamage(target, damage, damage);
        if (damage <= 0.0F || dealt > 0.0F) {
            applyDismembermentForLevel(target, level);
            int duration = 30 + level * 12;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration,
                    level >= 6 ? 2 : 1, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, level >= 6 ? 1 : 0,
                    false, true));
            if (this.self.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, target.getX(),
                        target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                        14 + level * 2, 0.35D, 0.45D, 0.35D, 0.03D);
            }
        }
        removeTag(target);
        this.self.level().playSound(null, target.blockPosition(), ModSounds.SF_ZIPPER_BREAK_EVENT,
                SoundSource.PLAYERS, 1.0F, 0.9F);
    }

    // ------------------------------------------------------------------------------------------------
    // Forced Unzip Body (Zip Mode OFF)
    // Unzip Body normally needs zipper marks (which only exist while Zip Mode is ON). With Zip Mode OFF the
    // stand instead pins the target and barrages it with zippers one by one until the whole body is unzipped.
    // Like the Zipper Barrage, the stand is anchored at the target and the user is free to walk away.
    // ------------------------------------------------------------------------------------------------
    /** Ticks of "binding" (first zipper locks the target) before the first body zipper opens. */
    private static final int FORCED_UNZIP_BIND_TICKS = 10;
    /** Ticks between each zipper being placed/opened on the target's body. */
    private static final int FORCED_UNZIP_STEP_TICKS = 8;
    /** Ticks between the last zipper opening and the final burst; the target glows during it. */
    private static final int FORCED_UNZIP_FINISH_DELAY = 12;
    private static final double FORCED_UNZIP_STAND_DISTANCE = 1.6D;
    private static final int[] FORCED_UNZIP_PARTS = {
            ZIPPER_PART_RIGHT_ARM, ZIPPER_PART_LEFT_ARM, ZIPPER_PART_RIGHT_LEG,
            ZIPPER_PART_LEFT_LEG, ZIPPER_PART_HEAD };

    private int forcedUnzipTargetId = -1;
    private int forcedUnzipElapsed = 0;
    private Vec3 forcedUnzipDir = null;
    private float forcedUnzipDamageRemaining;

    private void startForcedUnzip(LivingEntity target) {
        if (!target.isAlive() || forcedUnzipTargetId != -1 || barrageTimer > 0
                || this.getStandEntity(this.self) == null) return;
        Vec3 away = this.self.position().subtract(target.position());
        Vec3 flat = new Vec3(away.x, 0.0D, away.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            float yaw = target.getYRot() * 0.017453292F;
            flat = new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
        }
        forcedUnzipDir = flat.normalize();
        forcedUnzipTargetId = target.getId();
        forcedUnzipElapsed = 0;
        forcedUnzipDamageRemaining = getUnzipBodyDamageLimit(target);

        anchorForcedUnzipStand(target);
        this.animateStand(StandEntity.BARRAGE);
        playBarrageCrySound();
        this.self.level().playSound(null, target.blockPosition(), ModSounds.SF_HIT_ZIPPER_EVENT,
                SoundSource.PLAYERS, 1.0F, 0.9F);
        // first zipper: the one that pins the target in place
        syncTagLevel(target, FORCED_MARK_FLAG | 1);
        if (this.self.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, target.getX(),
                    target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    16, 0.35D, 0.45D, 0.35D, 0.04D);
        }
    }

    /** Keeps the stand in front of the target in LOOSE mode, so it ignores the user's position and rotation. */
    private void anchorForcedUnzipStand(LivingEntity target) {
        StandEntity stand = this.getStandEntity(this.self);
        if (stand == null || forcedUnzipDir == null) return;
        double distance = FORCED_UNZIP_STAND_DISTANCE + target.getBbWidth() * 0.5D;
        double middleY = target.getY() + target.getBbHeight() * 0.5D;
        double y = Math.max(target.getY(), middleY - BARRAGE_WALL_STAND_LOWER);
        //the stand looks at the target, i.e. against the direction it was placed in
        float yaw = (float) (Mth.atan2(forcedUnzipDir.x, -forcedUnzipDir.z) * 57.29577951308232D);
        this.poseStand(OffsetIndex.LOOSE);
        stand.setPos(target.getX() + forcedUnzipDir.x * distance, y, target.getZ() + forcedUnzipDir.z * distance);
        stand.setXRot(BARRAGE_WALL_STAND_PITCH);
        stand.setYRot(yaw);
        stand.setYBodyRot(yaw);
    }

    /** Pins the target: no walking, no AI pathing. It can still fall. */
    private void lockForcedUnzipTarget(LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 6, false, false));
        Vec3 motion = target.getDeltaMovement();
        target.setDeltaMovement(0.0D, Math.min(0.0D, motion.y), 0.0D);
        target.hurtMarked = true;
        if (target instanceof Mob mob) mob.getNavigation().stop();
    }

    private void tickForcedUnzip() {
        Entity candidate = this.self.level().getEntity(forcedUnzipTargetId);
        if (!(candidate instanceof LivingEntity target) || !target.isAlive() || !this.self.isAlive()
                || target.level() != this.self.level() || this.getStandEntity(this.self) == null) {
            endForcedUnzip();
            return;
        }
        forcedUnzipElapsed++;
        //other stand logic may try to pull the stand back to the user, so it is re-anchored every tick
        anchorForcedUnzipStand(target);
        lockForcedUnzipTarget(target);
        if (forcedUnzipElapsed % 5 == 0) {
            this.animateStand(StandEntity.BARRAGE);
        }
        if (forcedUnzipElapsed % 3 == 0) {
            playStickyBarrageHit(target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ());
        }

        int sinceBind = forcedUnzipElapsed - FORCED_UNZIP_BIND_TICKS;
        if (sinceBind >= 0 && sinceBind % FORCED_UNZIP_STEP_TICKS == 0
                && sinceBind / FORCED_UNZIP_STEP_TICKS < FORCED_UNZIP_PARTS.length) {
            placeForcedZipper(target, sinceBind / FORCED_UNZIP_STEP_TICKS);
        }

        int finishAt = FORCED_UNZIP_BIND_TICKS + FORCED_UNZIP_STEP_TICKS * FORCED_UNZIP_PARTS.length
                + FORCED_UNZIP_FINISH_DELAY;
        if (forcedUnzipElapsed == finishAt - FORCED_UNZIP_FINISH_DELAY) {
            // short warning before the burst, same as the level 3 Unzip Body finisher
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, FORCED_UNZIP_FINISH_DELAY, 0, false, true));
            this.self.level().playSound(null, target.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN,
                    SoundSource.PLAYERS, 1.2F, 0.55F);
        }
        if (forcedUnzipElapsed >= finishAt) {
            finishForcedUnzip(target);
        }
    }

    /** Puts one more zipper on the body and opens the matching part (limb dismemberment). */
    private void placeForcedZipper(LivingEntity target, int index) {
        showDismemberedPart(target, FORCED_UNZIP_PARTS[index]);
        int pulsesLeft = FORCED_UNZIP_PARTS.length - index;
        float pulseDamage = forcedUnzipDamageRemaining / pulsesLeft;
        forcedUnzipDamageRemaining -= applyUnzipBodyDamage(target, pulseDamage, forcedUnzipDamageRemaining);
        syncTagLevel(target, FORCED_MARK_FLAG | (index + 2));
        if (this.self.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    10, 0.35D, 0.45D, 0.35D, 0.04D);
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    5, 0.25D, 0.35D, 0.25D, 0.08D);
        }
        this.self.level().playSound(null, target.blockPosition(), ModSounds.SF_ZIPPER_BREAK_EVENT,
                SoundSource.PLAYERS, 0.9F, 0.9F + index * 0.04F);
    }

    private void finishForcedUnzip(LivingEntity target) {
        //every part is already open by now; this is a safety net in case one was skipped
        applyDismembermentForLevel(target, TAG_MAX_LEVEL);
        // The final pose is cosmetic; Unzip Body is capped and nonlethal.

        if (this.self.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    48, 0.65D, 0.8D, 0.65D, 0.08D);
        }
        playStickyHeavyHit(target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ());
        endForcedUnzip();
    }

    private void endForcedUnzip() {
        if (forcedUnzipTargetId != -1 && this.self.level() instanceof ServerLevel serverLevel
                && serverLevel.getEntity(forcedUnzipTargetId) instanceof LivingEntity target) {
            syncTagLevel(target, 0);
        }
        forcedUnzipTargetId = -1;
        forcedUnzipElapsed = 0;
        forcedUnzipDir = null;
        forcedUnzipDamageRemaining = 0.0F;
        //back to following the user: pose FOLLOW + idle animation
        this.setPowerNone();
    }

    public static final byte MASS_UNZIP = PowerIndex.POWER_2;
    public static final int MASS_UNZIP_COOLDOWN = 200;
    public static final int MASS_UNZIP_BARRAGE_SIDE = 7;
    public static final int MASS_UNZIP_MAX_ZIPPERS = MASS_UNZIP_BARRAGE_SIDE * MASS_UNZIP_BARRAGE_SIDE;
    public static final int MASS_UNZIP_BARRAGE_TICKS = ZipperEntity.OPEN_ANIM_TICKS;

    /** Longest the stand can stay anchored to the surface, even if some zipper takes very long to finish. */
    public static final int MASS_UNZIP_BARRAGE_MAX_TICKS = 120;

    /** Floor/ceiling: how far above (below) the surface the stand hovers while it punches it. */
    public static final double BARRAGE_FLOOR_STAND_HEIGHT = 2.2D;
    /** Wall: how far in front of the wall the stand stands. */
    public static final double BARRAGE_WALL_STAND_DISTANCE = 1.8D;
    /** Wall: how far under the middle of the zippered area the stand's feet are (so its fists hit the middle). */
    public static final double BARRAGE_WALL_STAND_LOWER = 1.2D;
    /** Wall: slight downward tilt of the stand while it punches the wall. */
    public static final float BARRAGE_WALL_STAND_PITCH = 8.0F;

    private int barrageTimer = 0;
    private Vec3 barrageSpot = null;
    private BlockPos barrageAnchor = null;
    /** Where the stand is anchored while the barrage runs (it does NOT follow the user meanwhile). */
    private Vec3 barrageStandPos = null;
    private float barrageStandPitch = 0.0F;
    private float barrageStandYaw = 0.0F;

    public void massUnzipClient() {
        if (!this.onCooldown(PowerIndex.SKILL_1_SNEAK)) {
            this.tryPower(MASS_UNZIP, true);
            tryPowerPacket(MASS_UNZIP);
        }
    }

    private List<BlockPos> massUnzipArea(BlockPos anchor, Direction face) {
        List<BlockPos> area = new ArrayList<>();
        Direction across = face.getAxis() == Direction.Axis.Y ? Direction.EAST
                : face.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
        Direction vertical = face.getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.UP;
        int radius = MASS_UNZIP_BARRAGE_SIDE / 2;
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                area.add(anchor.relative(across, a).relative(vertical, b));
            }
        }
        // Animate from the impact point outward, one zipper at a time.
        area.sort(java.util.Comparator
                .comparingInt((BlockPos pos) -> Math.max(Math.abs(pos.getX() - anchor.getX()),
                        Math.max(Math.abs(pos.getY() - anchor.getY()), Math.abs(pos.getZ() - anchor.getZ()))))
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return area;
    }

    private void startMassUnzip() {
        HitResult hit = this.self.pick(UNZIP_RANGE, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        Level level = this.self.level();
        boolean zipMode = areZipMarksEnabled();
        this.setCooldown(PowerIndex.SKILL_1_SNEAK, zipMode ? MASS_UNZIP_COOLDOWN / 2 : MASS_UNZIP_COOLDOWN);
        if (level.isClientSide()) {
            return;
        }
        BlockPos anchor = blockHit.getBlockPos();
        Direction face = blockHit.getDirection();
        int count = 0;
        int longestZipper = 0;
        List<BlockPos> barrageArea = massUnzipArea(anchor, face);
        for (BlockPos pos : barrageArea) {
            if (!ZipperEntity.canZipOnFace(level, pos, face)) continue;
            ZipperEntity zipper = ZipperEntity.createMini(level, this.self, pos, face, pos.getY());
            int stagger = count;
            zipper.startSynchronizedMining(stagger);
            level.addFreshEntity(zipper);
            longestZipper = Math.max(longestZipper, stagger + ZipperEntity.OPEN_ANIM_TICKS);
            count++;
        }
        if (count == 0) {
            message("roundabout.zipper.nothing_to_unzip");
            return;
        }
        level.playSound(null, this.self.blockPosition(), ModSounds.SF_ZIPPER_BREAK_EVENT,
                SoundSource.PLAYERS, 1.2F, 0.85F);
        startBarrageAnimation(anchor, face, longestZipper);
    }

    /**
     * The stand stops following its user and goes to the surface: over the floor it hovers above the target
     * and punches straight down, in front of a wall it faces the wall. It stays there until the barrage and the
     * zippers are done (the user is free to walk away meanwhile), then it goes back to the user.
     */
    private void startBarrageAnimation(BlockPos anchor, Direction face, int zipperTicks) {
        barrageTimer = Mth.clamp(zipperTicks, MASS_UNZIP_BARRAGE_TICKS, MASS_UNZIP_BARRAGE_MAX_TICKS);
        barrageAnchor = anchor.immutable();
        barrageSpot = Vec3.atCenterOf(anchor).add(face.getStepX() * 0.5D, face.getStepY() * 0.5D, face.getStepZ() * 0.5D);
        computeBarrageStandPlacement(anchor, face);
        anchorBarrageStand();
        this.animateStand(StandEntity.BARRAGE);
        playBarrageCrySound();
    }

    private void computeBarrageStandPlacement(BlockPos anchor, Direction face) {
        if (face.getAxis() == Direction.Axis.Y) {
            //floor: hover over the target looking straight down. Ceiling: hang below it looking straight up.
            boolean floor = face == Direction.UP;
            double y = floor ? barrageSpot.y + BARRAGE_FLOOR_STAND_HEIGHT
                    : barrageSpot.y - BARRAGE_FLOOR_STAND_HEIGHT - 1.8D;
            barrageStandPos = new Vec3(barrageSpot.x, y, barrageSpot.z);
            barrageStandPitch = floor ? 90.0F : -90.0F;
            barrageStandYaw = this.self.getYRot();
        } else {
            //wall: stand in front of the wall, facing it (forward = into the wall)
            double nx = face.getStepX();
            double nz = face.getStepZ();
            double middleY = anchor.getY() + 1.5D;
            barrageStandPos = new Vec3(barrageSpot.x + nx * BARRAGE_WALL_STAND_DISTANCE,
                    middleY - BARRAGE_WALL_STAND_LOWER,
                    barrageSpot.z + nz * BARRAGE_WALL_STAND_DISTANCE);
            barrageStandYaw = (float) (Mth.atan2(nx, -nz) * 57.29577951308232D);
            barrageStandPitch = BARRAGE_WALL_STAND_PITCH;
        }
    }

    /** Puts the stand on its anchor in LOOSE mode, which makes it ignore the user's position and rotation. */
    private void anchorBarrageStand() {
        if (barrageStandPos == null) {
            return;
        }
        StandEntity stand = this.getStandEntity(this.self);
        if (stand == null) {
            return;
        }
        this.poseStand(OffsetIndex.LOOSE);
        stand.setPos(barrageStandPos.x, barrageStandPos.y, barrageStandPos.z);
        stand.setXRot(barrageStandPitch);
        stand.setYRot(barrageStandYaw);
        stand.setYBodyRot(barrageStandYaw);
    }

    private void endBarrageAnimation() {
        barrageSpot = null;
        barrageAnchor = null;
        barrageStandPos = null;
        //back to following the user: pose FOLLOW + idle animation
        this.setPowerNone();
    }

    private void tickBarrageAnimation() {
        barrageTimer--;
        if (barrageTimer <= 0) {
            if (barrageSpot != null) {
                playStickyHeavyHit(barrageSpot.x, barrageSpot.y, barrageSpot.z);
                performBarrageImpact();
            }
            endBarrageAnimation();
            barrageTimer = 0;
            return;
        }
        if (!this.self.isAlive() || this.getStandEntity(this.self) == null) {
            endBarrageAnimation();
            barrageTimer = 0;
            return;
        }
        //other stand logic may try to pull the stand back to the user, so it is re-anchored every tick
        anchorBarrageStand();
        if (barrageTimer % 5 == 0) {
            this.animateStand(StandEntity.BARRAGE);
        }
        if (barrageTimer % 3 == 0 && barrageSpot != null) {
            playStickyBarrageHit(barrageSpot.x, barrageSpot.y, barrageSpot.z);
        }
    }

    /** Explosion damage belongs only to Sticky Fingers' zipper barrage finisher. */
    private void performBarrageImpact() {
        if (!(this.self.level() instanceof ServerLevel serverLevel) || barrageAnchor == null) return;

        BlockPos anchor = barrageAnchor;
        Vec3 center = barrageSpot == null ? Vec3.atCenterOf(anchor) : barrageSpot;
        double blastRadius = 4.5D;
        AABB blastArea = new AABB(center.x - blastRadius, center.y - blastRadius, center.z - blastRadius,
                center.x + blastRadius, center.y + blastRadius, center.z + blastRadius);
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, blastArea,
                entity -> entity.isAlive() && entity != this.self && !(entity instanceof StandEntity))) {
            double distance = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).distanceTo(center);
            if (distance > blastRadius) continue;
            float damage = (float) (2.0D + 6.0D * (1.0D - distance / blastRadius));
            damage = applyZipModeDamage(this.self, damage);
            target.hurt(ModDamageTypes.of(serverLevel, ModDamageTypes.EXPLOSIVE_STAND, this.self), damage);
            Vec3 knockback = target.position().subtract(center).normalize();
            target.setDeltaMovement(target.getDeltaMovement().add(knockback.x * 0.55D, 0.35D,
                    knockback.z * 0.55D));
            target.hurtMarked = true;
        }

        serverLevel.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z,
                1, 0.25D, 0.2D, 0.25D, 0.0D);
        serverLevel.sendParticles(ParticleTypes.CLOUD, center.x, center.y, center.z,
                MASS_UNZIP_MAX_ZIPPERS, 1.8D, 0.45D, 1.8D, 0.12D);
    }

    public static final byte ZIPPER_STORE = PowerIndex.POWER_3;
    public static final int STORE_COOLDOWN = 20;
    public static final float STORE_MOB_RANGE = 4.0F;

    public void storeClient() {
        if (!this.onCooldown(PowerIndex.SKILL_1_GUARD)) {
            this.tryPower(ZIPPER_STORE, true);
            tryPowerPacket(ZIPPER_STORE);
        }
    }

    private void startStore() {
        this.setCooldown(PowerIndex.SKILL_1_GUARD, STORE_COOLDOWN);
        Level level = this.self.level();
        if (level.isClientSide()) {
            return;
        }
        Entity target = MainUtil.rayCastEntity(this.self, STORE_MOB_RANGE);
        if (target instanceof LivingEntity mob && !(target instanceof StandEntity)) {
            ZipperStorageEntity existingStorage = level.getEntitiesOfClass(ZipperStorageEntity.class,
                            mob.getBoundingBox().inflate(3.0D), zipper -> zipper.storesItemFor(mob))
                    .stream().findFirst().orElse(null);
            if (existingStorage != null && this.self instanceof Player player) {
                existingStorage.returnMobItemTo(player, mob);
                removeTag(mob);
                return;
            }
            if (isTagged(mob)) {
                stuffMob(level, mob);
            } else {
                message("roundabout.zipper.not_tagged");
            }
            return;
        }
        storeBlock(level);
    }

    private void storeBlock(Level level) {
        HitResult hit = this.self.pick(UNZIP_RANGE, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        if (!ZipperEntity.canZipOnFace(level, pos, blockHit.getDirection())) {
            message("roundabout.zipper.cant_store");
            return;
        }
        boolean already = !level.getEntitiesOfClass(ZipperStorageEntity.class, new AABB(pos).inflate(1.0D),
                zipper -> !zipper.isMobMode() && zipper.getAnchor().equals(pos)).isEmpty();
        if (already) {
            message("roundabout.zipper.already_storage");
            return;
        }
        ZipperStorageEntity zipper = ZipperStorageEntity.createForBlock(level, this.self, pos, blockHit.getDirection());
        level.addFreshEntity(zipper);
        level.playSound(null, pos, ModSounds.SF_ZIPPER_BREAK_EVENT,
                SoundSource.PLAYERS, 1.0F, 0.95F);
        if (level instanceof ServerLevel serverLevel) {
            Vec3 at = zipper.position().add(0.0D, 0.5D, 0.0D);
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, at.x, at.y, at.z, 10, 0.3D, 0.3D, 0.3D, 0.02D);
        }
    }

    private void stuffMob(Level level, LivingEntity mob) {
        ItemStack held = this.self.getMainHandItem();
        if (held.isEmpty()) {
            message("roundabout.zipper.need_item");
            return;
        }
        boolean full = !level.getEntitiesOfClass(ZipperStorageEntity.class, mob.getBoundingBox().inflate(1.5D),
                zipper -> zipper.isMobMode() && mob.getUUID().equals(zipper.getMobUUID())).isEmpty();
        if (full) {
            message("roundabout.zipper.mob_full");
            return;
        }
        ItemStack stuffed = held.copy();
        this.self.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ZipperStorageEntity zipper = ZipperStorageEntity.createForMob(level, this.self, mob, stuffed);
        level.addFreshEntity(zipper);
        level.playSound(null, mob.blockPosition(), ModSounds.SF_ZIPPER_BREAK_EVENT,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, mob.getX(), mob.getY() + mob.getBbHeight() / 2,
                    mob.getZ(), 12, 0.3D, 0.4D, 0.3D, 0.02D);
        }
    }

    public static final byte ZIPPER_GUILLOTINE = PowerIndex.POWER_4;
    public static final byte ZIPPER_REATTACH = PowerIndex.POWER_4_EXTRA;
    public static final byte ZIPPER_POLE_COLLAPSE = PowerIndex.POWER_4_BLOCK;
    public static final float ZIPPER_REATTACH_RANGE = 3.5F;
    public static final int ZIPPER_LIMB_GRAFT_COOLDOWN = 100;
    public static final int STOLEN_LIMB_LOCK_TICKS = 100;
    public static final int ZIPPER_POLE_GROUND_MAX_BLOCKS = 9;
    public static final int ZIPPER_POLE_WALL_MAX_BLOCKS = 16;
    public static final int ZIPPER_POLE_COOLDOWN = 100;
    public static final int GUILLOTINE_DELAY = 20;
    public static final int GUILLOTINE_COOLDOWN = 160;
    public static final double GUILLOTINE_RADIUS = 3.0D;
    public static final int GUILLOTINE_BLEED_LEVEL = 2;
    public static final int GUILLOTINE_BLEED_TICKS = 200;

    private static boolean isTorsoHit(LivingEntity target, Vec3 hitPosition) {
        double height = target.getBbHeight();
        double relativeY = hitPosition.y - target.getY();
        if (relativeY < height * 0.30D || relativeY > height * 0.78D) return false;
        double centerX = (target.getBoundingBox().minX + target.getBoundingBox().maxX) * 0.5D;
        double centerZ = (target.getBoundingBox().minZ + target.getBoundingBox().maxZ) * 0.5D;
        double horizontalLimit = Math.max(0.30D, Math.min(target.getBbWidth() * 0.70D, 0.65D));
        return Math.abs(hitPosition.x - centerX) <= horizontalLimit
                && Math.abs(hitPosition.z - centerZ) <= horizontalLimit;
    }

    private record LimbTransfer(int receiverPart, int donorPart) { }

    private record GraftOrigin(String entityType, String ownerUuid, String standItem) { }

    private int guillotineTimer = 0;
    private int guillotineApproachTicks = 0;
    private int guillotinePhase = 0;
    private Vec3 guillotinePos = null;
    private LivingEntity guillotineTarget = null;
    private Vec3 guillotineTargetPosition = null;
    private Vec3 guillotineStandPosition = null;
    private boolean guillotineTargetWasNoAi;

    public void guillotineClient() {
        if (!this.onCooldown(PowerIndex.SKILL_4) && this.self.onGround() && guillotineTimer <= 0) {
            this.tryPower(ZIPPER_GUILLOTINE, true);
            tryPowerPacket(ZIPPER_GUILLOTINE);
        }
    }

    private void collapsePoleClient() {
        if (!this.onCooldown(PowerIndex.SKILL_4_GUARD)) {
            this.tryPower(ZIPPER_POLE_COLLAPSE, true);
            tryPowerPacket(ZIPPER_POLE_COLLAPSE);
        }
    }

    @Override
    public boolean setPowerOther(int move, int lastMove) {
        if (move == ZIPPER_REATTACH) {
            startReattach();
        } else if (move == ZIPPER_POLE_COLLAPSE) {
            startPoleCollapse();
        } else if (move == UNZIP) {
            startUnzip();
        } else if (move == MASS_UNZIP) {
            startMassUnzip();
        } else if (move == ZIPPER_STORE) {
            startStore();
        } else if (move == ZIPPER_GUILLOTINE) {
            startGuillotine();
        } else if (move == ZIPPER_FIST) {
            primeFist();
        } else if (move == ZIPPER_FIST_FIRE) {
            if (fistPrimed) {
                fistPrimed = false;
                fistPrimeTicks = 0;
                launchFist();
            }
            // Clear the active skill without resetting the impact animation we just started.
            this.setActivePower(PowerIndex.NONE);
            this.poseStand(OffsetIndex.FOLLOW);
        } else if (move == ZIPPER_FIST_CANCEL) {
            fistPrimed = false;
            fistPrimeTicks = 0;
            fistPrimeElapsedTicks = 0;
            if (getAnimation() == StickyFingersEntity.ZIPPER_FIST_PREPARE
                    || getAnimation() == StickyFingersEntity.ZIPPER_FIST_IDLE) {
                this.animateStand(StandEntity.IDLE);
                this.poseStand(OffsetIndex.FOLLOW);
            }
            this.setPowerNone();
        } else if (move == ZIPPER_RELEASE) {
            startZipperRelease();
        } else if (move == ZIPPER_GRAPPLE) {
            startGrapple();
        } else if (move == ZIPPER_LATCH) {
            startLatchZipper();
        } else if (move == ZIPPER_TOGGLE_MARKS) {
            if (this.self instanceof net.hydra.jojomod.event.powers.StandUser standUser) {
                boolean enabled = !standUser.roundabout$getUniqueStandModeToggle();
                standUser.roundabout$setUniqueStandModeToggle(enabled);
                if (!enabled) clearAllTags();
            }
        }
        return super.setPowerOther(move, lastMove);
    }

    private void startReattach() {
        if (this.self.level().isClientSide()) return;
        EntityHitResult hit = MainUtil.rayCastEntityHitResult(this.self, ZIPPER_REATTACH_RANGE);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)
                || !isTorsoHit(target, hit.getLocation())
                || target == this.self || target instanceof StandEntity
                || !(target instanceof IStickyFingersDismemberment dismemberment)
                || dismemberment.roundabout$getStickyFingersDismemberment() == 0) return;
        long lockedUntil = STOLEN_LIMB_LOCKS.getOrDefault(target.getUUID(), 0L);
        if (target.level().getGameTime() < lockedUntil) {
            message("roundabout.zipper.limb_stolen");
            return;
        }

        HitResult blockHit = this.self.pick(ZIPPER_REATTACH_RANGE, 1.0F, false);
        if (blockHit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
                && block.getLocation().distanceToSqr(this.self.getEyePosition())
                < hit.getLocation().distanceToSqr(this.self.getEyePosition())) return;

        int restoredParts = dismemberment.roundabout$getStickyFingersDismemberment();
        dismemberment.roundabout$setStickyFingersDismemberment(0);
        dismemberment.roundabout$setStickyFingersGraftMask(0);
        dismemberment.roundabout$setStickyFingersGraftData("");

        S2CPacketUtil.sendGenericIntIntToAll(PacketDataIndex.S2C_ZIPPER_GRAFT_CLEAR, target.getId(), 0);
        S2CPacketUtil.sendGenericIntIntToAll(PacketDataIndex.S2C_ZIPPER_LIMB_ANIMATION,
                target.getId(), restoredParts);
        target.level().playSound(null, target.blockPosition(), ModSounds.SF_ZIPPER_BREAK_EVENT,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        if (target.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, target.getX(),
                    target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    20, 0.35D, 0.45D, 0.35D, 0.03D);
        }
    }

    private void takeDroppedLimb(int packedSelection) {
        int donorId = packedSelection >>> 3;
        int donorPart = 1 << (packedSelection & 7);
        if ((donorPart & LIMB_MASK) == 0 || !(this.self instanceof Player player)
                || !(this.self instanceof IStickyFingersDismemberment receiver)
                || !(this.self.level() instanceof ServerLevel serverLevel)) return;
        Entity candidate = serverLevel.getEntity(donorId);
        if (!(candidate instanceof LivingEntity donor) || donor.level() != this.self.level()
                || donor.distanceToSqr(this.self) > 64.0D
                || !(donor instanceof IStickyFingersDismemberment source)) return;

        int recipientMask = receiver.roundabout$getStickyFingersDismemberment();
        int donorMask = source.roundabout$getStickyFingersDismemberment();

        // If the targeted piece came from this player, restore the native limb in place and
        // consume the loose copy. This avoids creating a graft duplicate of their own part.
        if (donor == this.self) {
            int restoredPart = donorMask & recipientMask & donorPart & LIMB_MASK;
            if (Integer.bitCount(restoredPart) != 1) return;
            receiver.roundabout$setStickyFingersDismemberment(recipientMask & ~restoredPart);
            int graftMask = receiver.roundabout$getStickyFingersGraftMask() & ~restoredPart;
            receiver.roundabout$setStickyFingersGraftMask(graftMask);
            setGraftOrigins(receiver, graftMask, receiver.roundabout$getStickyFingersGraftData());
            this.setCooldown(PowerIndex.SKILL_4_SNEAK, ZIPPER_LIMB_GRAFT_COOLDOWN);
            S2CPacketUtil.sendGenericIntIntToAll(PacketDataIndex.S2C_ZIPPER_LIMB_CONSUMED,
                    this.self.getId(), restoredPart);
            S2CPacketUtil.sendGenericIntIntToAll(PacketDataIndex.S2C_ZIPPER_LIMB_ANIMATION,
                    this.self.getId(), restoredPart);
            serverLevel.playSound(null, this.self.blockPosition(), ModSounds.SF_ZIPPER_BREAK_EVENT,
                    SoundSource.PLAYERS, 1.0F, 0.95F);
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, this.self.getX(),
                    this.self.getY() + this.self.getBbHeight() * 0.5D, this.self.getZ(),
                    16, 0.3D, 0.4D, 0.3D, 0.025D);
            return;
        }

        LimbTransfer transfer = chooseLimbPart(recipientMask, donorMask & donorPart);
        if (transfer == null) {
            message("roundabout.zipper.limb_wrong_side");
            return;
        }
        long lockedUntil = STOLEN_LIMB_LOCKS.getOrDefault(donor.getUUID(), 0L);
        if (serverLevel.getGameTime() < lockedUntil) {
            message("roundabout.zipper.limb_stolen");
            return;
        }

        this.setCooldown(PowerIndex.SKILL_4_SNEAK, ZIPPER_LIMB_GRAFT_COOLDOWN);
        STOLEN_LIMB_LOCKS.put(donor.getUUID(), serverLevel.getGameTime() + STOLEN_LIMB_LOCK_TICKS);

        receiver.roundabout$setStickyFingersGraftMask(receiver.roundabout$getStickyFingersGraftMask() | transfer.receiverPart());
        String donorStand = "";
        if (donor instanceof Player donorPlayer && donorPlayer instanceof net.hydra.jojomod.event.powers.StandUser donorStandUser
                && !donorStandUser.roundabout$getStandDisc().isEmpty()) {
            donorStand = BuiltInRegistries.ITEM.getKey(donorStandUser.roundabout$getStandDisc().getItem()).toString().replace(":", "/");
        }
        setGraftOrigin(receiver, transfer.receiverPart(), BuiltInRegistries.ENTITY_TYPE.getKey(donor.getType()).toString().replace(":", "/"),
                donor instanceof Player ? donor.getUUID().toString() : "", donorStand);
        applyGraftAcquisitionBoost(player, BuiltInRegistries.ENTITY_TYPE.getKey(donor.getType()).toString(), donorStand);
        int donorCode = Integer.numberOfTrailingZeros(transfer.donorPart());
        int receiverCode = Integer.numberOfTrailingZeros(transfer.receiverPart());
        S2CPacketUtil.sendGenericIntIntToAll(PacketDataIndex.S2C_ZIPPER_GRAFT_ATTACH,
                this.self.getId(), (donor.getId() << 6) | (receiverCode << 3) | donorCode);
        S2CPacketUtil.sendGenericIntIntToAll(PacketDataIndex.S2C_ZIPPER_LIMB_ANIMATION,
                this.self.getId(), transfer.receiverPart());
        S2CPacketUtil.sendGenericIntIntToAll(PacketDataIndex.S2C_ZIPPER_LIMB_CONSUMED,
                donor.getId(), transfer.donorPart());

        reduceBleedByOne(player);
        serverLevel.playSound(null, this.self.blockPosition(), ModSounds.SF_ZIPPER_BREAK_EVENT,
                SoundSource.PLAYERS, 1.0F, 0.95F);
        serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, this.self.getX(),
                this.self.getY() + this.self.getBbHeight() * 0.55D, this.self.getZ(),
                18, 0.3D, 0.4D, 0.3D, 0.025D);
    }

    private static final int ARM_MASK = ZIPPER_PART_LEFT_ARM | ZIPPER_PART_RIGHT_ARM;
    private static final int LEG_MASK = ZIPPER_PART_LEFT_LEG | ZIPPER_PART_RIGHT_LEG;
    private static final int LIMB_MASK = ARM_MASK | LEG_MASK;

    private static void setGraftOrigin(IStickyFingersDismemberment target, int part, String entityType, String ownerUuid, String standItem) {
        StringBuilder data = new StringBuilder();
        for (int bit : new int[]{ZIPPER_PART_LEFT_ARM, ZIPPER_PART_RIGHT_ARM, ZIPPER_PART_LEFT_LEG, ZIPPER_PART_RIGHT_LEG}) {
            if (bit == part) {
                data.append(bit).append(":").append(entityType).append(":").append(ownerUuid).append(":").append(standItem).append(";");
            } else {
                GraftOrigin origin = getGraftOrigin(target, bit);
                if (origin != null) data.append(bit).append(":").append(origin.entityType()).append(":")
                        .append(origin.ownerUuid()).append(":").append(origin.standItem()).append(";");
            }
        }
        target.roundabout$setStickyFingersGraftData(data.toString());
    }

    private static void setGraftOrigins(IStickyFingersDismemberment target, int mask, String ignored) {
        StringBuilder data = new StringBuilder();
        String existing = target.roundabout$getStickyFingersGraftData();
        if (existing == null || existing.isEmpty()) return;
        for (String entry : existing.split(";")) {
            String[] fields = entry.split(":", -1);
            if (fields.length == 4) {
                try { if ((mask & Integer.parseInt(fields[0])) != 0) data.append(entry).append(";"); }
                catch (NumberFormatException ignoredPart) { }
            }
        }
        target.roundabout$setStickyFingersGraftData(data.toString());
    }

    private static GraftOrigin getGraftOrigin(IStickyFingersDismemberment target, int part) {
        String data = target.roundabout$getStickyFingersGraftData();
        if (data == null || data.isEmpty()) return null;
        for (String entry : data.split(";")) {
            String[] fields = entry.split(":", -1);
            if (fields.length == 4) try {
                if (Integer.parseInt(fields[0]) == part) return new GraftOrigin(fields[1], fields[2], fields[3]);
            } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    private static void applyGraftAcquisitionBoost(Player player, String donorType, String standItem) {
        String type = donorType.toLowerCase(java.util.Locale.ROOT);
        if (!standItem.isEmpty()) {
            String stand = standItem.toLowerCase(java.util.Locale.ROOT);
            MobEffectInstance boost = stand.contains("star_platinum") || stand.contains("the_world")
                    ? new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 20, 0)
                    : stand.contains("silver_chariot") || stand.contains("tusk")
                    ? new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 20, 0)
                    : stand.contains("white_album") || stand.contains("justice")
                    ? new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 20, 0)
                    : new MobEffectInstance(MobEffects.DIG_SPEED, 20 * 20, 0);
            player.addEffect(boost);
            return;
        }
        else if (type.contains("zombie")) player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0));
        else if (type.contains("spider")) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0));
        else if (type.contains("iron_golem")) player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0));
        else if (type.contains("skeleton")) player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 100, 0));
    }

    public static boolean hasHuskGraft(Player player) {
        if (!(player instanceof IStickyFingersDismemberment state)) return false;
        int mask = state.roundabout$getStickyFingersGraftMask();
        for (int bit : new int[]{ZIPPER_PART_LEFT_ARM, ZIPPER_PART_RIGHT_ARM}) {
            GraftOrigin origin = (mask & bit) != 0 ? getGraftOrigin(state, bit) : null;
            if (origin != null && origin.entityType().toLowerCase(java.util.Locale.ROOT).contains("husk")) return true;
        }
        return false;
    }

    public static void applyGraftedArmAttackEffect(Player attacker, LivingEntity target) {
        if (attacker.level().isClientSide() || !(attacker instanceof IStickyFingersDismemberment state)) return;
        int mask = state.roundabout$getStickyFingersGraftMask();
        for (int bit : new int[]{ZIPPER_PART_RIGHT_ARM, ZIPPER_PART_LEFT_ARM}) {
            GraftOrigin origin = (mask & bit) != 0 ? getGraftOrigin(state, bit) : null;
            if (origin == null) continue;
            String type = origin.entityType().toLowerCase(java.util.Locale.ROOT);
            if (type.contains("husk")) target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 0));
            else if (type.contains("spider")) target.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
            else if (type.contains("stray")) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0));
            else if (type.contains("skeleton")) target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
            else if (type.contains("blaze")) target.setSecondsOnFire(2);
            else if (type.contains("witch")) target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
            else if (type.contains("zombie")) attacker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0));
            else if (type.contains("iron_golem")) attacker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 0));
            else if (type.contains("enderman")) attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 0));
            else if (type.contains("creeper")) target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
            else if (type.contains("slime") || type.contains("magma_cube")) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
            else if (type.contains("guardian")) target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 80, 0));
            else if (type.contains("bee")) target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
            else if (type.contains("wolf")) attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0));
            else if (type.contains("ravager")) attacker.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0));
            else target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
            return;
        }
    }

    public static boolean redirectGraftedLimbDamage(LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (REDIRECTING_GRAFT_DAMAGE.get() || !(target instanceof IStickyFingersDismemberment state)
                || !(target instanceof Player) || source.getSourcePosition() == null) return false;
        int mask = state.roundabout$getStickyFingersGraftMask();
        Vec3 hitFrom = source.getSourcePosition();
        double yaw = Math.toRadians(target.getYRot());
        Vec3 left = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
        for (int bit : new int[]{ZIPPER_PART_LEFT_ARM, ZIPPER_PART_RIGHT_ARM}) {
            GraftOrigin origin = (mask & bit) != 0 ? getGraftOrigin(state, bit) : null;
            if (origin == null || origin.ownerUuid().isEmpty() || !origin.standItem().isEmpty()) continue;
            Vec3 anchor = target.position().add(left.scale((bit == ZIPPER_PART_LEFT_ARM ? 1.0D : -1.0D) * 0.30D))
                    .add(0.0D, target.getBbHeight() * 0.70D, 0.0D);
            if (anchor.distanceToSqr(hitFrom) > 0.80D) continue;
            if (!(target.level() instanceof ServerLevel serverLevel)) return false;
            Player owner = serverLevel.getServer().getPlayerList().getPlayer(UUID.fromString(origin.ownerUuid()));
            if (owner == null || owner == target || !owner.isAlive()) return false;
            REDIRECTING_GRAFT_DAMAGE.set(true);
            try { owner.hurt(source, amount); } finally { REDIRECTING_GRAFT_DAMAGE.set(false); }
            return true;
        }
        return false;
    }

    private static final ThreadLocal<Boolean> REDIRECTING_GRAFT_DAMAGE = ThreadLocal.withInitial(() -> false);

    private static LimbTransfer chooseLimbPart(int receiverMask, int donorMask) {
        int missing = receiverMask & LIMB_MASK;
        int available = donorMask & LIMB_MASK;
        if (missing == 0 || available == 0) return null;

        // Preserve anatomical side: a right limb can only replace the right limb.
        int availableArms = available & ARM_MASK;
        if (availableArms != 0) {
            int matchingArms = missing & availableArms;
            return matchingArms == 0 ? null
                    : new LimbTransfer(Integer.lowestOneBit(matchingArms), Integer.lowestOneBit(matchingArms));
        }
        int availableLegs = available & LEG_MASK;
        if (availableLegs != 0) {
            int matchingLegs = missing & availableLegs;
            return matchingLegs == 0 ? null
                    : new LimbTransfer(Integer.lowestOneBit(matchingLegs), Integer.lowestOneBit(matchingLegs));
        }
        return null;
    }

    private static void reduceBleedByOne(Player player) {
        if (!player.hasEffect(net.hydra.jojomod.event.ModEffects.BLEED)) return;
        MobEffectInstance bleed = player.getEffect(net.hydra.jojomod.event.ModEffects.BLEED);
        if (bleed == null) return;
        if (bleed.getAmplifier() <= 0) {
            player.removeEffect(net.hydra.jojomod.event.ModEffects.BLEED);
        } else {
            player.addEffect(new MobEffectInstance(net.hydra.jojomod.event.ModEffects.BLEED,
                    bleed.getDuration(), bleed.getAmplifier() - 1));
        }
    }

    private void startPoleCollapse() {
        if (this.self.level().isClientSide() || !(this.self.level() instanceof ServerLevel serverLevel)) return;
        BlockHitResult hit = this.self.pick(UNZIP_RANGE, 1.0F, false) instanceof BlockHitResult result
                ? result : null;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos anchor = hit.getBlockPos();
        BlockState baseState = serverLevel.getBlockState(anchor);
        if (baseState.isAir() || baseState.getDestroySpeed(serverLevel, anchor) < 0.0F
                || baseState.hasBlockEntity() || !baseState.getFluidState().isEmpty()) return;

        Direction zipperFace = hit.getDirection();
        boolean ground = zipperFace.getAxis() == Direction.Axis.Y;
        int maxBlocks = ground ? ZIPPER_POLE_GROUND_MAX_BLOCKS : ZIPPER_POLE_WALL_MAX_BLOCKS;
        Direction across = ground
                ? Direction.EAST
                : (zipperFace.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST);
        Direction[] planeNeighbors = ground
                ? new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}
                : new Direction[]{Direction.UP, Direction.DOWN, across, across.getOpposite()};
        // Gather the connected, same-material patch in deterministic top-down order.
        Set<BlockPos> patch = new HashSet<>();
        if (ground) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = anchor.offset(dx, 0, dz);
                    if (isPoleCollapseCandidate(serverLevel, pos, baseState, zipperFace)) patch.add(pos);
                }
            }
        } else {
            for (int offset = -1; offset <= 2; offset++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos pos = anchor.relative(across, offset).above(dy);
                    if (isPoleCollapseCandidate(serverLevel, pos, baseState, zipperFace)) patch.add(pos);
                }
            }
        }
        List<BlockPos> zipperPositions = new ArrayList<>();
        ArrayDeque<BlockPos> connected = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        if (isPoleCollapseCandidate(serverLevel, anchor, baseState, zipperFace)) {
            connected.add(anchor);
            seen.add(anchor);
        }
        while (!connected.isEmpty() && zipperPositions.size() < maxBlocks) {
            BlockPos pos = connected.removeFirst();
            zipperPositions.add(pos);
            for (Direction step : planeNeighbors) {
                BlockPos next = pos.relative(step);
                if (patch.contains(next) && seen.add(next)) connected.addLast(next);
            }
        }
        zipperPositions.sort(java.util.Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
        if (zipperPositions.size() < 2) {
            message("roundabout.zipper.no_pole_blocks");
            return;
        }

        int created = 0;
        for (BlockPos pos : zipperPositions) {
            float hardness = Math.max(0.0F, serverLevel.getBlockState(pos).getDestroySpeed(serverLevel, pos));
            int mineTicks = Mth.clamp(Math.round(hardness * 4.0F), 2, 30);
            ZipperEntity zipper = ZipperEntity.createMini(serverLevel, this.self, pos, zipperFace, pos.getY());
            zipper.startMining(created * 2, mineTicks);
            serverLevel.addFreshEntity(zipper);
            created++;
        }
        this.setCooldown(PowerIndex.SKILL_4_GUARD, ZIPPER_POLE_COOLDOWN);
        playZipperCollapseAnimation();
        serverLevel.playSound(null, anchor, ModSounds.SF_ZIPPER_BREAK_EVENT,
                SoundSource.PLAYERS, 1.0F, 0.9F);
    }

    private void playZipperCollapseAnimation() {
        this.animateStand(StickyFingersEntity.ZIPPER_POLE_COLLAPSE);
        zipperCollapseAnimTicks = 8;
    }

    private boolean isPoleCollapseCandidate(ServerLevel level, BlockPos pos, BlockState baseState,
                                            Direction face) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(baseState.getBlock()) || state.isAir() || state.hasBlockEntity()
                || state.getDestroySpeed(level, pos) < 0.0F || !state.getFluidState().isEmpty()
                || !ZipperEntity.canZipOnFace(level, pos, face)) return false;
        BlockPos front = pos.relative(face);
        return level.getBlockState(front).getCollisionShape(level, front).isEmpty();
    }

    private void startGuillotine() {
        if (this.self.level().isClientSide()) {
            return;
        }
        EntityHitResult hit = MainUtil.rayCastEntityHitResult(this.self, 6.0F);
        LivingEntity target = hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
        if (!isValidGuillotineTarget(target)) {
            AABB reach = this.self.getBoundingBox().inflate(GUILLOTINE_RADIUS, 1.5D, GUILLOTINE_RADIUS);
            target = this.self.level().getEntitiesOfClass(LivingEntity.class, reach,
                    this::isValidGuillotineTarget).stream()
                    .min(java.util.Comparator.comparingDouble(this.self::distanceToSqr)).orElse(null);
        }
        if (target == null || this.getStandEntity(this.self) == null) return;

        this.setCooldown(PowerIndex.SKILL_4, GUILLOTINE_COOLDOWN);
        guillotinePos = this.self.position();
        guillotineTarget = target;
        guillotineTargetPosition = target.position();
        Vec3 approach = new Vec3(this.self.getX() - target.getX(), 0.0D,
                this.self.getZ() - target.getZ());
        if (approach.lengthSqr() < 1.0E-4D) approach = new Vec3(0.0D, 0.0D, 1.0D);
        approach = approach.normalize();
        guillotineStandPosition = target.position().add(approach.scale(0.9D + target.getBbWidth() * 0.5D));
        guillotineApproachTicks = 0;
        guillotinePhase = 0;
        if (target instanceof Mob mob) {
            guillotineTargetWasNoAi = mob.isNoAi();
            mob.setNoAi(true);
            mob.getNavigation().stop();
        }
        guillotineTarget.setDeltaMovement(Vec3.ZERO);
        guillotineTarget.hurtMarked = true;
        guillotineTimer = 0;
        this.animateStand(StickyFingersEntity.GUILLOTINE_PUNCH);
        this.poseStand(OffsetIndex.LOOSE);
        this.self.level().playSound(null, this.self.blockPosition(), ModSounds.SF_HIT_ZIPPER_EVENT,
                SoundSource.PLAYERS, 1.0F, 0.9F);
        blockEruption(guillotinePos, 30);
        renderGuillotineWarning(guillotinePos);
    }

    private void blockEruption(Vec3 pos, int count) {
        if (!(this.self.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0D * i) / count;
            double x = pos.x + Math.cos(angle) * GUILLOTINE_RADIUS;
            double z = pos.z + Math.sin(angle) * GUILLOTINE_RADIUS;
            BlockPos floorPos = BlockPos.containing(x, pos.y - 0.05D, z);
            BlockState state = serverLevel.getBlockState(floorPos);
            if (!state.isAir()) {
                serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        x, pos.y + 0.05D, z, Math.max(1, count / 15), 0.12D, 0.18D, 0.12D, 0.08D);
            }
        }
        renderGuillotineWarning(pos);
    }

    private void renderGuillotineWarning(Vec3 pos) {
        if (!(this.self.level() instanceof ServerLevel serverLevel)) return;
        int points = 32;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0D * i) / points;
            double x = pos.x + Math.cos(angle) * GUILLOTINE_RADIUS;
            double z = pos.z + Math.sin(angle) * GUILLOTINE_RADIUS;
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, x, pos.y + 0.08D, z,
                    1, 0.015D, 0.015D, 0.015D, 0.0D);
        }
    }

    private void closeGuillotine() {
        if (guillotinePos == null || !(this.self.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        LivingEntity target = guillotineTarget;
        if (isValidGuillotineTarget(target)) {
            float halfHealth = Math.max(1.0F, target.getMaxHealth() * 0.5F);
            target.setHealth(Math.min(target.getHealth(), halfHealth));
            showDismemberedPart(target, ZIPPER_PART_HEAD);
        }
        releaseGuillotineTarget();
        serverLevel.playSound(null, this.self.blockPosition(), ModSounds.SF_HIT_ZIPPER_EVENT,
                SoundSource.PLAYERS, 1.4F, 0.9F);
        blockEruption(guillotinePos, 20);
        guillotinePos = null;
        if (getAnimation() == StickyFingersEntity.GUILLOTINE_PUNCH
                || getAnimation() == StickyFingersEntity.GUILLOTINE_CHOP
                || getAnimation() == StickyFingersEntity.GUILLOTINE_FULL) {
            this.animateStand(StandEntity.IDLE);
            this.poseStand(OffsetIndex.FOLLOW);
        }
    }

    private boolean isValidGuillotineTarget(LivingEntity target) {
        return target != null && target != this.self && target.isAlive()
                && !(target instanceof StandEntity) && target.level() == this.self.level();
    }

    private void releaseGuillotineTarget() {
        if (guillotineTarget instanceof Mob mob) mob.setNoAi(guillotineTargetWasNoAi);
        guillotineTarget = null;
        guillotineTargetPosition = null;
        guillotineStandPosition = null;
        guillotineTargetWasNoAi = false;
        guillotineApproachTicks = 0;
        guillotinePhase = 0;
        guillotineTimer = 0;
    }

    @Override
    public void tickPower() {
        if (this.self.level().isClientSide() && !Minecraft.getInstance().options.keyUse.isDown()) {
            zipperUseButtonWasDown = false;
        }
        super.tickPower();
        if (fistPrimed && fistPrimeTicks > 0) {
            fistPrimeTicks--;
            fistPrimeElapsedTicks++;
            if (fistPrimeElapsedTicks >= FIST_PREPARE_ANIM_TICKS
                    && getAnimation() == StickyFingersEntity.ZIPPER_FIST_PREPARE) {
                this.animateStand(StickyFingersEntity.ZIPPER_FIST_IDLE);
            }
            if (fistPrimeTicks <= 0) {
                fistPrimed = false;
                fistPrimeElapsedTicks = 0;
                if (getAnimation() == StickyFingersEntity.ZIPPER_FIST_PREPARE
                        || getAnimation() == StickyFingersEntity.ZIPPER_FIST_IDLE) {
                    this.animateStand(StandEntity.IDLE);
                    this.poseStand(OffsetIndex.FOLLOW);
                }
                this.setPowerNone();
            }
        }
        if (fistImpactTicks > 0 && --fistImpactTicks <= 0
                && getAnimation() == StickyFingersEntity.ZIPPER_FIST_IMPACT) {
            this.animateStand(StandEntity.IDLE);
            this.poseStand(OffsetIndex.FOLLOW);
        }
        if (!this.self.level().isClientSide() && zipperCollapseAnimTicks > 0
                && --zipperCollapseAnimTicks <= 0
                && getAnimation() == StickyFingersEntity.ZIPPER_POLE_COLLAPSE) {
            this.animateStand(StandEntity.IDLE);
        }
        // The stand can keep following a latched fist while the grapple pulls the user.
        // When the fist is recalled or times out, ease the segmented arm back to its normal pose.
        if (getAnimation() == StickyFingersEntity.ZIPPER_FIST_IDLE
                && !fistPrimed && !hasLatchedFist()) {
            this.animateStand(StandEntity.IDLE);
            this.poseStand(OffsetIndex.FOLLOW);
        }
        if (!this.self.level().isClientSide()) {
            if (grappleTicks > 0) {
                if (this.self.hurtTime > 0 || latchedFist == null || latchedFist.isRemoved()
                        || !latchedFist.isLatched()) {
                    finishFistGrapple();
                } else {
                    Vec3 toFist = latchedFist.position().subtract(this.self.position());
                    if (toFist.lengthSqr() < 1.4D) {
                        finishFistGrapple();
                    } else {
                        Vec3 pull = toFist.normalize().scale(0.42D);
                        this.self.setDeltaMovement(pull);
                        this.self.hurtMarked = true;
                        if (--grappleTicks <= 0) {
                            finishFistGrapple();
                        }
                    }
                }
            }
            if (maxReleaseTicks > 0) {
                Entity candidate = this.self.level().getEntity(maxReleaseTargetId);
                if (!(candidate instanceof LivingEntity target) || !target.isAlive()
                        || target.distanceTo(this.self) > 12.0F) {
                    maxReleaseTicks = 0;
                    maxReleaseTargetId = -1;
                    maxReleaseLevel = 0;
                    maxReleaseDamageRemaining = 0.0F;
                    finishDisassemblyAnimation();
                } else if (--maxReleaseTicks <= 0) {
                    applyDismembermentForLevel(target, maxReleaseLevel);
                    removeTag(target);
                    maxReleaseLevel = 0;
                    maxReleaseDamageRemaining = 0.0F;
                    if (this.self.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ModParticles.ZIPPER_SPARK,
                                target.getX(), target.getY() + target.getBbHeight() * 0.5D,
                                target.getZ(), 48, 0.65D, 0.8D, 0.65D, 0.08D);
                    }
                    playStickyHeavyHit(target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ());
                    maxReleaseTargetId = -1;
                    finishDisassemblyAnimation();
                } else if (maxReleaseTicks % 4 == 0) {
                    int openedPartIndex = (MAX_RELEASE_WINDUP - maxReleaseTicks) / 4 - 1;
                    int[] zipperParts = { ZIPPER_PART_RIGHT_ARM, ZIPPER_PART_LEFT_ARM,
                            ZIPPER_PART_RIGHT_LEG, ZIPPER_PART_LEFT_LEG, ZIPPER_PART_HEAD };
                    int partCount = maxReleaseLevel >= 3 ? 5 : maxReleaseLevel >= 2 ? 4 : 2;
                    if (openedPartIndex >= 0 && openedPartIndex < partCount) {
                        showDismemberedPart(target, zipperParts[openedPartIndex]);
                    }
                    int pulsesLeft = Math.max(1, (maxReleaseLevel >= 3 ? 5 : maxReleaseLevel >= 2 ? 4 : 2)
                            - Math.max(0, openedPartIndex));
                    float pulseDamage = maxReleaseDamageRemaining / pulsesLeft;
                    maxReleaseDamageRemaining -= applyUnzipBodyDamage(target, pulseDamage, maxReleaseDamageRemaining);
                    if (this.self.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ModParticles.ZIPPER_SPARK,
                                target.getX(), target.getY() + target.getBbHeight() * 0.5D,
                                target.getZ(), 10, 0.35D, 0.45D, 0.35D, 0.04D);
                        serverLevel.sendParticles(ParticleTypes.CRIT,
                                target.getX(), target.getY() + target.getBbHeight() * 0.5D,
                                target.getZ(), 5, 0.25D, 0.35D, 0.25D, 0.08D);
                    }
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 24, 0, false, true));
                }
            }
        }
        if (!this.self.level().isClientSide() && barrageTimer > 0) {
            tickBarrageAnimation();
        }
        if (!this.self.level().isClientSide() && forcedUnzipTargetId != -1) {
            tickForcedUnzip();
        }
        if (!this.self.level().isClientSide() && this.self.tickCount % 100 == 0 && !tags.isEmpty()) {
            long now = this.self.level().getGameTime();
            tags.values().removeIf(tag -> tag[1] <= now);
        }
        if (!this.self.level().isClientSide() && this.self.tickCount % 100 == 0) {
            long now = this.self.level().getGameTime();
            STOLEN_LIMB_LOCKS.entrySet().removeIf(entry -> entry.getValue() <= now);
        }
        if (!this.self.level().isClientSide() && guillotineTarget != null) {
            if (isValidGuillotineTarget(guillotineTarget) && this.getStandEntity(this.self) != null) {
                guillotineTarget.setPos(guillotineTargetPosition.x,
                        guillotineTargetPosition.y, guillotineTargetPosition.z);
                guillotineTarget.setDeltaMovement(Vec3.ZERO);
                guillotineTarget.hurtMarked = true;
                if (guillotineTarget instanceof Mob mob) mob.getNavigation().stop();
                StandEntity stand = this.getStandEntity(this.self);
                if (stand != null && guillotineStandPosition != null) {
                    Vec3 offset = guillotineStandPosition.subtract(stand.position());
                    double distance = offset.length();
                    if (guillotinePhase == 0 && distance > 1.35D) {
                        Vec3 step = offset.scale(Math.min(0.30D, distance) / distance);
                        stand.setPos(stand.getX() + step.x, stand.getY() + step.y, stand.getZ() + step.z);
                    }
                    float yaw = (float) (Mth.atan2(guillotineTarget.getZ() - stand.getZ(),
                            guillotineTarget.getX() - stand.getX()) * 57.29577951308232D) - 90.0F;
                    stand.setYRot(yaw);
                    stand.setYBodyRot(yaw);
                    stand.setXRot(0.0F);
                    this.poseStand(OffsetIndex.LOOSE);
                    guillotineApproachTicks++;
                    if (guillotinePhase == 0 && (distance <= 1.35D || guillotineApproachTicks >= 60)) {
                        stand.setPos(guillotineStandPosition.x, guillotineStandPosition.y,
                                guillotineStandPosition.z);
                        guillotinePhase = 1;
                        guillotineTimer = 10;
                    }
                }
            } else {
                releaseGuillotineTarget();
                guillotinePos = null;
                this.animateStand(StandEntity.IDLE);
                this.poseStand(OffsetIndex.FOLLOW);
            }
            if (guillotinePhase > 0 && guillotineTimer > 0) {
                guillotineTimer--;
                if (guillotineTimer <= 0) {
                    if (guillotinePhase == 1) {
                        this.animateStand(StickyFingersEntity.GUILLOTINE_CHOP);
                        guillotinePhase = 2;
                        guillotineTimer = 16;
                    } else if (guillotinePhase == 2) {
                        this.animateStand(StickyFingersEntity.GUILLOTINE_FULL);
                        guillotinePhase = 3;
                        guillotineTimer = 26;
                    } else {
                        closeGuillotine();
                    }
                }
            }
            if (guillotineApproachTicks % 3 == 0 && guillotinePos != null) {
                renderGuillotineWarning(guillotinePos);
            }
        }
    }

    private void finishDisassemblyAnimation() {
        if (getAnimation() == StickyFingersEntity.DEATH_BY_DISASSEMBLY) {
            this.animateStand(StandEntity.IDLE);
            this.poseStand(OffsetIndex.FOLLOW);
        }
    }

    private void finishFistGrapple() {
        grappleTicks = 0;
        if (latchedFist != null) {
            if (!latchedFist.isRemoved()) {
                latchedFist.discard();
            }
            latchedFist = null;
        }
        this.setPowerNone();
        this.animateStand(StandEntity.IDLE);
        this.poseStand(OffsetIndex.FOLLOW);
    }

    @Override
    public void renderIcons(GuiGraphics context, int x, int y) {
        boolean guarding = isGuarding();
        boolean sneaking = isHoldingSneak();

        if (guarding) {
            setSkillIcon(context, x, y, 1, StandIcons.STICKY_FINGERS_ZIPPER_STORAGE_SKILL, PowerIndex.SKILL_1_GUARD);
        } else if (sneaking) {
            setSkillIcon(context, x, y, 1, StandIcons.STICKY_FINGERS_ZIPPER_BARRAGE_SKILL, PowerIndex.SKILL_1_SNEAK);
        } else {
            setSkillIcon(context, x, y, 1, StandIcons.STICKY_FINGERS_UNZIP_SKILL, PowerIndex.SKILL_1);
        }

        if (guarding) {
            setSkillIcon(context, x, y, 2, StandIcons.STICKY_FINGERS_LIMB_GRAFT, PowerIndex.SKILL_2_GUARD);
        } else if (sneaking) {
            setSkillIcon(context, x, y, 2, StandIcons.STICKY_FINGERS_ZIPPER_MASS, PowerIndex.SKILL_2_SNEAK);
        } else {
            setSkillIcon(context, x, y, 2, StandIcons.STICKY_FINGERS_FIST_LAUNCH_SKILL, PowerIndex.SKILL_2);
        }

        if (guarding) {
            setSkillIcon(context, x, y, 3, areZipMarksEnabled()
                    ? StandIcons.STICKY_FINGERS_ZIP_TOGGLE_ON : StandIcons.STICKY_FINGERS_ZIP_TOGGLE_OFF,
                    PowerIndex.SKILL_3_GUARD);
        } else if (sneaking) {
            setSkillIcon(context, x, y, 3, StandIcons.STICKY_FINGERS_UNZIP, PowerIndex.SKILL_3);
        } else {
            setSkillIcon(context, x, y, 3, StandIcons.STICKY_FINGERS_FIST, PowerIndex.SKILL_3);
        }

        if (sneaking && guarding) {
            setSkillIcon(context, x, y, 4, StandIcons.STICKY_FINGERS_UNZIP, PowerIndex.SKILL_4_CROUCH_GUARD);
        } else if (guarding) {
            setSkillIcon(context, x, y, 4, StandIcons.STICKY_FINGERS_POLE_COLLAPSE, PowerIndex.SKILL_4_GUARD);
        } else if (sneaking) {
            setSkillIcon(context, x, y, 4, StandIcons.STICKY_FINGERS_UNZIP, PowerIndex.SKILL_4_SNEAK);
        } else {
            setSkillIcon(context, x, y, 4, StandIcons.STICKY_FINGERS_GUILLOTINE, PowerIndex.SKILL_4);
        }
    }

    @Override
    public List<AbilityIconInstance> drawGUIIcons(GuiGraphics context, float delta, int mouseX, int mouseY,
                                                  int leftPos, int topPos, byte level, boolean bypass) {
        List<AbilityIconInstance> list = Lists.newArrayList();
        int c0 = leftPos + 20, c1 = leftPos + 39, c2 = leftPos + 58, c3 = leftPos + 77,
                c4 = leftPos + 96, c5 = leftPos + 115, c6 = leftPos + 134;
        int r0 = topPos + 80, r1 = topPos + 99, r2 = topPos + 118;

        list.add(drawSingleGUIIcon(context, 18, c0, r0, 0, "ability.roundabout.punch",
                "instruction.roundabout.press_attack", StandIcons.STICKY_FINGERS_PUNCH, 0, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c0, r1, 0, "ability.roundabout.guard",
                "instruction.roundabout.hold_block", StandIcons.STICKY_FINGERS_GUARD, 0, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c1, r0, 0, "ability.roundabout.barrage",
                "instruction.roundabout.barrage", StandIcons.STICKY_FINGERS_BARRAGE_SKILL, 0, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c1, r1, 0, "ability.roundabout.mining",
                "instruction.roundabout.hold_attack", StandIcons.KING_CRIMSON_MINING, 0, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c1, r2, 0, "ability.roundabout.zipper_limb_graft",
                "instruction.roundabout.right_click", StandIcons.STICKY_FINGERS_LIMB_GRAFT, 0, level, bypass));

        list.add(drawSingleGUIIcon(context, 18, c2, r0, 0, "ability.roundabout.zipper_unzip",
                "instruction.roundabout.press_skill", StandIcons.STICKY_FINGERS_UNZIP_SKILL, 1, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c2, r1, 0, "ability.roundabout.zipper_mass",
                "instruction.roundabout.press_skill_crouch", StandIcons.STICKY_FINGERS_ZIPPER_BARRAGE_SKILL, 1, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c2, r2, 0, "ability.roundabout.zipper_storage",
                "instruction.roundabout.press_skill_block", StandIcons.STICKY_FINGERS_ZIPPER_STORAGE_SKILL, 1, level, bypass));

        list.add(drawSingleGUIIcon(context, 18, c3, r0, 0, "ability.roundabout.zipper_fist",
                "instruction.roundabout.press_skill", StandIcons.STICKY_FINGERS_FIST_LAUNCH_SKILL, 2, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c3, r1, 0, "ability.roundabout.zipper_release",
                "instruction.roundabout.press_skill_crouch", StandIcons.STICKY_FINGERS_ZIPPER_MASS, 2, level, bypass));

        list.add(drawSingleGUIIcon(context, 18, c4, r0, 0, "ability.roundabout.zipper_ride",
                "instruction.roundabout.press_skill", StandIcons.STICKY_FINGERS_FIST, 3, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c4, r1, 0, "ability.roundabout.zipper_latch",
                "instruction.roundabout.press_skill_crouch", StandIcons.STICKY_FINGERS_UNZIP, 3, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c4, r2, 0, "ability.roundabout.zipper_toggle",
                "instruction.roundabout.press_skill_block", areZipMarksEnabled()
                        ? StandIcons.STICKY_FINGERS_ZIP_TOGGLE_ON : StandIcons.STICKY_FINGERS_ZIP_TOGGLE_OFF,
                3, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c5, r0, 0, "ability.roundabout.vault_kc",
                "instruction.roundabout.press_skill_air", StandIcons.STICKY_FINGERS_VAULT, 3, level, bypass));

        list.add(drawSingleGUIIcon(context, 18, c5, r1, 0, "ability.roundabout.zipper_guillotine",
                "instruction.roundabout.press_skill", StandIcons.STICKY_FINGERS_GUILLOTINE, 4, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c5, r2, 0, "ability.roundabout.zipper_self_unzip",
                "instruction.roundabout.press_skill_crouch", StandIcons.STICKY_FINGERS_UNZIP, 4, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c6, r0, 0, "ability.roundabout.zipper_pole_collapse",
                "instruction.roundabout.press_skill_block", StandIcons.STICKY_FINGERS_POLE_COLLAPSE, 4, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c6, r1, 0, "ability.roundabout.zipper_auto_unzip",
                "instruction.roundabout.press_skill_block", StandIcons.STICKY_FINGERS_LIMB_GRAFT,
                2, level, bypass));
        return list;
    }
}
