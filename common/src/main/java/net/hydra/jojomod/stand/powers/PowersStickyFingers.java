package net.hydra.jojomod.stand.powers;

import com.google.common.collect.Lists;
import net.hydra.jojomod.client.StandIcons;
import net.hydra.jojomod.entity.ModEntities;
import net.hydra.jojomod.entity.objects.ZipperEntity;
import net.hydra.jojomod.entity.objects.ZipperStorageEntity;
import net.hydra.jojomod.entity.stand.StandEntity;
import net.hydra.jojomod.entity.stand.StickyFingersEntity;
import net.hydra.jojomod.event.AbilityIconInstance;
import net.hydra.jojomod.event.index.OffsetIndex;
import net.hydra.jojomod.event.index.PowerIndex;
import net.hydra.jojomod.event.ModParticles;
import net.hydra.jojomod.event.index.SoundIndex;
import net.hydra.jojomod.event.powers.StandPowers;
import net.hydra.jojomod.sound.ModSounds;
import net.hydra.jojomod.stand.powers.elements.PowerContext;
import net.hydra.jojomod.stand.powers.presets.NewPunchingStand;
import net.hydra.jojomod.util.MainUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PowersStickyFingers extends NewPunchingStand {

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
            return ModSounds.SUMMON_KING_CRIMSON_EVENT;
        }
        return super.getSoundFromByte(soundChoice);
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

            case SKILL_2_NORMAL -> {
            }
            case SKILL_2_CROUCH -> {
            }
            case SKILL_2_GUARD, SKILL_2_CROUCH_GUARD -> {
            }

            case SKILL_3_NORMAL -> {
                dashOrVaultClient();
            }
            case SKILL_3_CROUCH -> {
            }
            case SKILL_3_GUARD, SKILL_3_CROUCH_GUARD -> {
            }

            case SKILL_4_NORMAL -> {
                guillotineClient();
            }
            case SKILL_4_CROUCH -> {
            }
            case SKILL_4_GUARD, SKILL_4_CROUCH_GUARD -> {
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

    private ZipperEntity activeZipper = null;

    public void unzipClient() {
        if (!this.onCooldown(PowerIndex.SKILL_1)) {
            this.tryPower(UNZIP, true);
            tryPowerPacket(UNZIP);
        }
    }

    private void startUnzip() {
        HitResult hit = this.self.pick(UNZIP_RANGE, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        Level level = this.self.level();
        BlockPos anchor = blockHit.getBlockPos();
        Direction face = blockHit.getDirection();
        BlockPos front = anchor.relative(face);
        if (!level.getBlockState(front).getCollisionShape(level, front).isEmpty()) {
            return;
        }
        this.setCooldown(PowerIndex.SKILL_1, UNZIP_COOLDOWN);
        if (level.isClientSide()) {
            return;
        }
        int feetY = Mth.floor(this.self.getY());
        int baseY = anchor.getY() < feetY ? anchor.getY() : Math.max(feetY, anchor.getY() - 1);

        if (activeZipper != null && !activeZipper.isRemoved()) {
            activeZipper.close();
        }
        ZipperEntity zipper = ZipperEntity.create(level, this.self, anchor, face, baseY);
        level.addFreshEntity(zipper);
        activeZipper = zipper;

        level.playSound(null, this.self.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.playSound(null, this.self.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 1.0F, 0.6F);
        if (level instanceof ServerLevel serverLevel) {
            BlockState surface = serverLevel.getBlockState(anchor);
            if (!surface.isAir()) {
                Vec3 pos = zipper.position();
                serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, surface),
                        pos.x, pos.y + 1.0D, pos.z, 16, 0.3D, 0.6D, 0.3D, 0.05D);
            }
        }
    }

    private void message(String key) {
        if (this.self instanceof Player player) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }

    public static final int TAG_DURATION = 600;
    public static final int TAG_MAX_LEVEL = 10;

    private final Map<UUID, long[]> tags = new HashMap<>();

    public boolean isTagged(LivingEntity target) {
        long[] tag = tags.get(target.getUUID());
        return tag != null && tag[1] > target.level().getGameTime();
    }

    public int getTagLevel(LivingEntity target) {
        return isTagged(target) ? (int) tags.get(target.getUUID())[0] : 0;
    }

    public void addTag(LivingEntity target) {
        long[] tag = tags.computeIfAbsent(target.getUUID(), id -> new long[]{0L, 0L});
        long now = target.level().getGameTime();
        if (tag[1] <= now) {
            tag[0] = 0L;
        }
        tag[0] = Math.min(TAG_MAX_LEVEL, tag[0] + 1);
        tag[1] = now + TAG_DURATION;
    }

    @Override
    public boolean StandDamageEntityAttack(Entity target, float pow, float knockbackStrength, Entity attacker) {
        boolean hit = super.StandDamageEntityAttack(target, pow, knockbackStrength, attacker);
        if (hit && attacker == this.self && target instanceof LivingEntity living && !(target instanceof StandEntity)
                && !this.self.level().isClientSide()) {
            addTag(living);
        }
        return hit;
    }

    public static final byte MASS_UNZIP = PowerIndex.POWER_2;
    public static final int MASS_UNZIP_COOLDOWN = 200;
    public static final int MASS_UNZIP_MAX_ZIPPERS = 25;
    public static final int MASS_UNZIP_BARRAGE_TICKS = 40;

    private int barrageTimer = 0;
    private Vec3 barrageSpot = null;

    public void massUnzipClient() {
        if (!this.onCooldown(PowerIndex.SKILL_1_SNEAK)) {
            this.tryPower(MASS_UNZIP, true);
            tryPowerPacket(MASS_UNZIP);
        }
    }

    private List<BlockPos> massUnzipArea(BlockPos anchor, Direction face) {
        List<BlockPos> area = new ArrayList<>();
        if (face.getAxis() == Direction.Axis.Y) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    area.add(anchor.offset(dx, 0, dz));
                }
            }
        } else {
            Direction along = face.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
            for (int t = -2; t <= 2; t++) {
                for (int dy = -1; dy <= 1; dy++) {
                    area.add(anchor.relative(along, t).above(dy));
                }
            }
        }
        return area;
    }

    private void startMassUnzip() {
        HitResult hit = this.self.pick(UNZIP_RANGE, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        Level level = this.self.level();
        this.setCooldown(PowerIndex.SKILL_1_SNEAK, MASS_UNZIP_COOLDOWN);
        if (level.isClientSide()) {
            return;
        }
        BlockPos anchor = blockHit.getBlockPos();
        Direction face = blockHit.getDirection();
        int count = 0;
        for (BlockPos pos : massUnzipArea(anchor, face)) {
            if (count >= MASS_UNZIP_MAX_ZIPPERS) {
                break;
            }
            if (!ZipperEntity.canZip(level, pos)) {
                continue;
            }
            BlockPos front = pos.relative(face);
            if (!level.getBlockState(front).getCollisionShape(level, front).isEmpty()) {
                continue;
            }
            int distance = Math.abs(pos.getX() - anchor.getX()) + Math.abs(pos.getY() - anchor.getY())
                    + Math.abs(pos.getZ() - anchor.getZ());
            float hardness = Math.max(0.0F, level.getBlockState(pos).getDestroySpeed(level, pos));
            int mineTicks = Mth.clamp(Math.round(hardness * 4.0F), 2, 30);
            ZipperEntity zipper = ZipperEntity.createMini(level, this.self, pos, face, pos.getY());
            zipper.startMining(distance * 2 + level.random.nextInt(3), mineTicks);
            level.addFreshEntity(zipper);
            count++;
        }
        if (count == 0) {
            message("roundabout.zipper.nothing_to_unzip");
            return;
        }
        level.playSound(null, this.self.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.2F, 0.7F);
        level.playSound(null, this.self.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 1.2F, 0.5F);
        startBarrageAnimation(anchor, face);
    }

    private void startBarrageAnimation(BlockPos anchor, Direction face) {
        barrageTimer = MASS_UNZIP_BARRAGE_TICKS;
        barrageSpot = Vec3.atCenterOf(anchor).add(face.getStepX() * 0.5D, face.getStepY() * 0.5D, face.getStepZ() * 0.5D);
        this.poseStand(OffsetIndex.ATTACK);
        this.animateStand(StandEntity.BARRAGE);
        playBarrageCrySound();
    }

    private void tickBarrageAnimation() {
        barrageTimer--;
        if (barrageTimer <= 0) {
            barrageSpot = null;
            this.setPowerNone();
            return;
        }
        if (barrageTimer % 5 == 0) {
            this.poseStand(OffsetIndex.ATTACK);
            this.animateStand(StandEntity.BARRAGE);
        }
        if (barrageTimer % 3 == 0 && barrageSpot != null) {
            this.self.level().playSound(null, barrageSpot.x, barrageSpot.y, barrageSpot.z,
                    ModSounds.STAND_BARRAGE_HIT_EVENT, SoundSource.PLAYERS, 0.8F, 0.9F + this.self.getRandom().nextFloat() * 0.25F);
        }
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
        if (!ZipperEntity.canZip(level, pos)) {
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
        level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 1.0F, 0.9F);
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
        zipper.mountOn(mob);
        tags.remove(mob.getUUID());
        level.playSound(null, mob.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 1.0F, 1.2F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, mob.getX(), mob.getY() + mob.getBbHeight() / 2,
                    mob.getZ(), 12, 0.3D, 0.4D, 0.3D, 0.02D);
        }
    }

    public static final byte ZIPPER_GUILLOTINE = PowerIndex.POWER_4;
    public static final int GUILLOTINE_DELAY = 20;
    public static final int GUILLOTINE_COOLDOWN = 160;
    public static final double GUILLOTINE_RADIUS = 3.0D;
    public static final int GUILLOTINE_BLEED_LEVEL = 2;
    public static final int GUILLOTINE_BLEED_TICKS = 200;

    private int guillotineTimer = 0;
    private Vec3 guillotinePos = null;

    public void guillotineClient() {
        if (!this.onCooldown(PowerIndex.SKILL_4) && this.self.onGround() && guillotineTimer <= 0) {
            this.tryPower(ZIPPER_GUILLOTINE, true);
            tryPowerPacket(ZIPPER_GUILLOTINE);
        }
    }

    @Override
    public boolean setPowerOther(int move, int lastMove) {
        if (move == UNZIP) {
            startUnzip();
        } else if (move == MASS_UNZIP) {
            startMassUnzip();
        } else if (move == ZIPPER_STORE) {
            startStore();
        } else if (move == ZIPPER_GUILLOTINE) {
            startGuillotine();
        }
        return super.setPowerOther(move, lastMove);
    }

    private void startGuillotine() {
        this.setCooldown(PowerIndex.SKILL_4, GUILLOTINE_COOLDOWN);
        if (this.self.level().isClientSide()) {
            return;
        }
        guillotinePos = this.self.position();
        guillotineTimer = GUILLOTINE_DELAY;
        this.self.level().playSound(null, this.self.blockPosition(), SoundEvents.PISTON_EXTEND,
                SoundSource.PLAYERS, 1.0F, 0.6F);
        blockEruption(guillotinePos, 30);
    }

    private void blockEruption(Vec3 pos, int count) {
        if (!(this.self.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState state = serverLevel.getBlockState(this.self.blockPosition().below());
        if (state.isAir()) {
            return;
        }
        serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                pos.x, pos.y + 0.1D, pos.z, count,
                GUILLOTINE_RADIUS / 2, 0.15D, GUILLOTINE_RADIUS / 2, 0.15D);
    }

    private void closeGuillotine() {
        if (guillotinePos == null || !(this.self.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        AABB area = new AABB(guillotinePos, guillotinePos).inflate(GUILLOTINE_RADIUS, 1.5D, GUILLOTINE_RADIUS);
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, area)) {
            if (target == this.self || !target.isAlive() || target instanceof StandEntity) {
                continue;
            }
            if (StandDamageEntityAttack(target, getHeavyPunchStrength(target) * 1.5F, 0.2F, this.self)) {
                MainUtil.makeBleed(target, GUILLOTINE_BLEED_LEVEL, GUILLOTINE_BLEED_TICKS, this.self);
            }
        }
        serverLevel.playSound(null, this.self.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE,
                SoundSource.PLAYERS, 1.4F, 0.5F);
        serverLevel.playSound(null, this.self.blockPosition(), SoundEvents.SHEEP_SHEAR,
                SoundSource.PLAYERS, 1.2F, 0.7F);
        blockEruption(guillotinePos, 20);
        guillotinePos = null;
    }

    @Override
    public void tickPower() {
        super.tickPower();
        if (!this.self.level().isClientSide() && barrageTimer > 0) {
            tickBarrageAnimation();
        }
        if (!this.self.level().isClientSide() && this.self.tickCount % 100 == 0 && !tags.isEmpty()) {
            long now = this.self.level().getGameTime();
            tags.values().removeIf(tag -> tag[1] <= now);
        }
        if (!this.self.level().isClientSide() && guillotineTimer > 0) {
            guillotineTimer--;
            if (guillotineTimer <= 0) {
                closeGuillotine();
            }
        }
    }

    @Override
    public void renderIcons(GuiGraphics context, int x, int y) {
        boolean guarding = isGuarding();
        boolean sneaking = isHoldingSneak();

        if (guarding) {
            setSkillIcon(context, x, y, 1, StandIcons.STICKY_FINGERS_ZIPPER_STORAGE, PowerIndex.SKILL_1_GUARD);
        } else if (sneaking) {
            setSkillIcon(context, x, y, 1, StandIcons.STICKY_FINGERS_ZIPPER_MASS, PowerIndex.SKILL_1_SNEAK);
        } else {
            setSkillIcon(context, x, y, 1, StandIcons.STICKY_FINGERS_UNZIP, PowerIndex.SKILL_1);
        }

        if (guarding) {
            setSkillIcon(context, x, y, 2, StandIcons.KING_CRIMSON_HANDS_ACTIVE, PowerIndex.SKILL_2_GUARD);
        } else if (sneaking) {
            setSkillIcon(context, x, y, 2, StandIcons.KING_CRIMSON_HANDS_ACTIVE, PowerIndex.SKILL_2_SNEAK);
        } else {
            setSkillIcon(context, x, y, 2, StandIcons.STICKY_FINGERS_FIST, PowerIndex.SKILL_2);
        }

        if (guarding) {
            setSkillIcon(context, x, y, 3, StandIcons.KING_CRIMSON_HANDS_ACTIVE, PowerIndex.SKILL_3_GUARD);
        } else if (sneaking) {
            setSkillIcon(context, x, y, 3, StandIcons.WALL_WALK, PowerIndex.NO_CD);
        } else if (canVault()) {
            setSkillIcon(context, x, y, 3, StandIcons.STICKY_FINGERS_VAULT, PowerIndex.GLOBAL_DASH);
        } else {
            setSkillIcon(context, x, y, 3, StandIcons.DODGE, PowerIndex.GLOBAL_DASH);
        }

        if (sneaking && !guarding) {
            setSkillIcon(context, x, y, 4, StandIcons.GROUND_IMPLANT, PowerIndex.SKILL_4_SNEAK);
        } else {
            setSkillIcon(context, x, y, 4, StandIcons.KING_CRIMSON_FINAL_PUNCH, PowerIndex.SKILL_4);
        }
    }

    @Override
    public List<AbilityIconInstance> drawGUIIcons(GuiGraphics context, float delta, int mouseX, int mouseY,
                                                  int leftPos, int topPos, byte level, boolean bypass) {
        List<AbilityIconInstance> list = Lists.newArrayList();
        int c0 = leftPos + 20, c1 = leftPos + 39, c2 = leftPos + 58, c3 = leftPos + 77, c4 = leftPos + 96, c5 = leftPos + 115;
        int r0 = topPos + 80, r1 = topPos + 99, r2 = topPos + 118;

        list.add(drawSingleGUIIcon(context, 18, c0, r0, 0, "ability.roundabout.punch",
                "instruction.roundabout.press_attack", StandIcons.STICKY_FINGERS_PUNCH, 0, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c0, r1, 0, "ability.roundabout.guard",
                "instruction.roundabout.hold_block", StandIcons.STICKY_FINGERS_GUARD, 0, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c0, r2, 0, "ability.roundabout.sticky_kick",
                "instruction.roundabout.press_attack_crouch", StandIcons.KING_CRIMSON_FINAL_PUNCH, 0, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c1, r0, 0, "ability.roundabout.barrage",
                "instruction.roundabout.barrage", StandIcons.STICKY_FINGERS_BARRAGE, 0, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c1, r1, 0, "ability.roundabout.mining",
                "instruction.roundabout.hold_attack", StandIcons.KING_CRIMSON_MINING, 0, level, bypass));

        list.add(drawSingleGUIIcon(context, 18, c2, r0, 0, "ability.roundabout.zipper_unzip",
                "instruction.roundabout.press_skill", StandIcons.STICKY_FINGERS_UNZIP, 1, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c2, r1, 0, "ability.roundabout.zipper_mass",
                "instruction.roundabout.press_skill_crouch", StandIcons.STICKY_FINGERS_ZIPPER_MASS, 1, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c2, r2, 0, "ability.roundabout.zipper_storage",
                "instruction.roundabout.press_skill_block", StandIcons.STICKY_FINGERS_ZIPPER_STORAGE, 1, level, bypass));

        list.add(drawSingleGUIIcon(context, 18, c3, r0, 0, "ability.roundabout.zipper_fist",
                "instruction.roundabout.press_skill", StandIcons.STICKY_FINGERS_FIST, 2, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c3, r1, 0, "ability.roundabout.zipper_release",
                "instruction.roundabout.press_skill_crouch", StandIcons.KING_CRIMSON_HANDS_ACTIVE, 2, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c3, r2, 0, "ability.roundabout.zipper_disassemble",
                "instruction.roundabout.press_skill_block", StandIcons.KING_CRIMSON_HANDS_ACTIVE, 2, level, bypass));

        list.add(drawSingleGUIIcon(context, 18, c4, r0, 0, "ability.roundabout.dodge",
                "instruction.roundabout.press_skill", StandIcons.DODGE, 3, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c4, r1, 0, "ability.roundabout.zipper_ride",
                "instruction.roundabout.press_skill_crouch", StandIcons.WALL_WALK, 3, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c4, r2, 0, "ability.roundabout.zipper_hidden",
                "instruction.roundabout.press_skill_block", StandIcons.KING_CRIMSON_HANDS_ACTIVE, 3, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c5, r0, 0, "ability.roundabout.vault_kc",
                "instruction.roundabout.press_skill_air", StandIcons.STICKY_FINGERS_VAULT, 3, level, bypass));

        list.add(drawSingleGUIIcon(context, 18, c5, r1, 0, "ability.roundabout.zipper_guillotine",
                "instruction.roundabout.press_skill", StandIcons.KING_CRIMSON_FINAL_PUNCH, 4, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c5, r2, 0, "ability.roundabout.zipper_limb_swap",
                "instruction.roundabout.press_skill_crouch", StandIcons.GROUND_IMPLANT, 4, level, bypass));

        return list;
    }
}