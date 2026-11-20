package net.hydra.jojomod.stand.powers;

import com.google.common.collect.Lists;
import net.hydra.jojomod.client.StandIcons;
import net.hydra.jojomod.entity.ModEntities;
import net.hydra.jojomod.entity.objects.ZipperEntity;
import net.hydra.jojomod.entity.stand.StandEntity;
import net.hydra.jojomod.entity.stand.StickyFingersEntity;
import net.hydra.jojomod.event.AbilityIconInstance;
import net.hydra.jojomod.event.index.PowerIndex;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**Sticky Fingers (Bruno Bucciarati).
 * Model, animations and sounds are TEMPORARILY borrowed from King Crimson.
 *
 * Skill layout (key -> PowerContext):
 *   Z = SKILL_1, X = SKILL_2, C = SKILL_3, V = SKILL_4
 *   *_NORMAL = key, *_CROUCH = Sneak+key, *_GUARD / *_CROUCH_GUARD = Block+key
 *
 * DONE:  Z (unzip), C (dash + vault), V (zipper guillotine)
 * TODO:  Sneak+Click kick, Sneak+Z, Block+Z, X, Sneak+X, Block+X,
 *        Sneak+C, Block+C, Sneak+V*/
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
        return Component.literal("Vin").withStyle(ChatFormatting.YELLOW);
    }

    @Override
    protected Byte getSummonSound() {
        return SoundIndex.SUMMON_SOUND;
    }

    @Override
    public SoundEvent getSoundFromByte(byte soundChoice) {
        if (soundChoice == SoundIndex.SUMMON_SOUND) {
            //TEMPORARY: King Crimson summon sound
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
        list.add(StickyFingersEntity.RED);
        return list;
    }

    @Override
    public Component getSkinName(byte skinId) {
        return Component.translatable("skins.roundabout.sticky_fingers.base");
    }

    /**-------------------------------------------------------------------
     * KEY ROUTING
     * ------------------------------------------------------------------*/

    @Override
    public void powerActivate(PowerContext context) {
        switch (context) {
            //Z
            case SKILL_1_NORMAL -> {
                unzipClient();
            }
            case SKILL_1_CROUCH -> {
                //TODO: Mass zipper mining with debris
            }
            case SKILL_1_GUARD, SKILL_1_CROUCH_GUARD -> {
                //TODO: Turn block / tagged mob into zipper storage
            }

            //X
            case SKILL_2_NORMAL -> {
                //TODO: Prime and launch the fist (projectile)
            }
            case SKILL_2_CROUCH -> {
                //TODO: Release zipper levels / dismember
            }
            case SKILL_2_GUARD, SKILL_2_CROUCH_GUARD -> {
                //TODO: Disassemble self into pieces
            }

            //C
            case SKILL_3_NORMAL -> {
                dashOrVaultClient();
            }
            case SKILL_3_CROUCH -> {
                //TODO: Latch zipper ride
            }
            case SKILL_3_GUARD, SKILL_3_CROUCH_GUARD -> {
                //TODO: Hidden mode
            }

            //V
            case SKILL_4_NORMAL -> {
                guillotineClient();
            }
            case SKILL_4_CROUCH -> {
                //TODO: Swap arm/leg
            }
            case SKILL_4_GUARD, SKILL_4_CROUCH_GUARD -> {
            }
        }
    }

    /**C: dash on the ground, vault in the air (the vault is built into the dash preset)*/
    public void dashOrVaultClient() {
        if (!doVault()) {
            dash();
        }
    }

    /**-------------------------------------------------------------------
     * Z: UNZIP
     * Creates a zipper on the wall/floor you are facing. See ZipperEntity:
     * right click = hide inside, left click = open a tunnel, crouch + right click = free swallowed mobs.
     * ------------------------------------------------------------------*/

    public static final byte UNZIP = PowerIndex.POWER_1;
    public static final int UNZIP_COOLDOWN = 60;
    public static final double UNZIP_RANGE = 6.0D;

    /**The zipper this user has out right now (server only). A new Unzip closes the old one.*/
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
        //the space in front of the surface has to be free, that is where you will hide
        BlockPos front = anchor.relative(face);
        if (!level.getBlockState(front).getCollisionShape(level, front).isEmpty()) {
            return;
        }
        this.setCooldown(PowerIndex.SKILL_1, UNZIP_COOLDOWN);
        if (level.isClientSide()) {
            return;
        }
        //bottom of the opening on a wall: 2 blocks tall, containing the block that was aimed at
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

    /**-------------------------------------------------------------------
     * V: ZIPPER GUILLOTINE
     * Block particles erupt, after a second the guillotine closes, dealing
     * heavy damage + Bleed 2 in an area. Stationary, can be dodged.
     * ------------------------------------------------------------------*/

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

    /**Block particles of the ground around the given position.*/
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
        if (!this.self.level().isClientSide() && guillotineTimer > 0) {
            guillotineTimer--;
            if (guillotineTimer <= 0) {
                closeGuillotine();
            }
        }
    }

    /**-------------------------------------------------------------------
     * HUD + SKILL MENU (icons are TEMPORARY King Crimson ones)
     * ------------------------------------------------------------------*/

    @Override
    public void renderIcons(GuiGraphics context, int x, int y) {
        setSkillIcon(context, x, y, 1, StandIcons.KING_CRIMSON_MINING, PowerIndex.SKILL_1);
        setSkillIcon(context, x, y, 2, StandIcons.KING_CRIMSON_HANDS_ACTIVE, PowerIndex.SKILL_2);
        setSkillIcon(context, x, y, 3, StandIcons.DODGE, PowerIndex.SKILL_3);
        setSkillIcon(context, x, y, 4, StandIcons.KING_CRIMSON_FINAL_PUNCH, PowerIndex.SKILL_4);
    }

    @Override
    public List<AbilityIconInstance> drawGUIIcons(GuiGraphics context, float delta, int mouseX, int mouseY,
                                                  int leftPos, int topPos, byte level, boolean bypass) {
        List<AbilityIconInstance> list = Lists.newArrayList();
        int c0 = leftPos + 20, c1 = leftPos + 39, c2 = leftPos + 58, c3 = leftPos + 77, c4 = leftPos + 96, c5 = leftPos + 115;
        int r0 = topPos + 80, r1 = topPos + 99, r2 = topPos + 118;

        //basics
        list.add(drawSingleGUIIcon(context, 18, c0, r0, 0, "ability.roundabout.punch",
                "instruction.roundabout.press_attack", StandIcons.KING_CRIMSON_PUNCH, 0, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c0, r1, 0, "ability.roundabout.guard",
                "instruction.roundabout.hold_block", StandIcons.KING_CRIMSON_GUARD, 0, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c0, r2, 0, "ability.roundabout.sticky_kick",
                "instruction.roundabout.press_attack_crouch", StandIcons.KING_CRIMSON_FINAL_PUNCH, 0, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c1, r0, 0, "ability.roundabout.barrage",
                "instruction.roundabout.barrage", StandIcons.KING_CRIMSON_BARRAGE, 0, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c1, r1, 0, "ability.roundabout.mining",
                "instruction.roundabout.hold_attack", StandIcons.KING_CRIMSON_MINING, 0, level, bypass));

        //Z
        list.add(drawSingleGUIIcon(context, 18, c2, r0, 0, "ability.roundabout.zipper_unzip",
                "instruction.roundabout.press_skill", StandIcons.KING_CRIMSON_MINING, 1, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c2, r1, 0, "ability.roundabout.zipper_mass",
                "instruction.roundabout.press_skill_crouch", StandIcons.KING_CRIMSON_MINING, 1, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c2, r2, 0, "ability.roundabout.zipper_storage",
                "instruction.roundabout.press_skill_block", StandIcons.KING_CRIMSON_ITEM_GRAB, 1, level, bypass));

        //X
        list.add(drawSingleGUIIcon(context, 18, c3, r0, 0, "ability.roundabout.zipper_fist",
                "instruction.roundabout.press_skill", StandIcons.KING_CRIMSON_HANDS_ACTIVE, 2, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c3, r1, 0, "ability.roundabout.zipper_release",
                "instruction.roundabout.press_skill_crouch", StandIcons.KING_CRIMSON_HANDS_ACTIVE, 2, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c3, r2, 0, "ability.roundabout.zipper_disassemble",
                "instruction.roundabout.press_skill_block", StandIcons.KING_CRIMSON_HANDS_ACTIVE, 2, level, bypass));

        //C
        list.add(drawSingleGUIIcon(context, 18, c4, r0, 0, "ability.roundabout.dodge",
                "instruction.roundabout.press_skill", StandIcons.DODGE, 3, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c4, r1, 0, "ability.roundabout.zipper_ride",
                "instruction.roundabout.press_skill_crouch", StandIcons.WALL_WALK, 3, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c4, r2, 0, "ability.roundabout.zipper_hidden",
                "instruction.roundabout.press_skill_block", StandIcons.KING_CRIMSON_HANDS_ACTIVE, 3, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c5, r0, 0, "ability.roundabout.vault_kc",
                "instruction.roundabout.press_skill_air", StandIcons.KING_CRIMSON_LEDGE_GRAB, 3, level, bypass));

        //V
        list.add(drawSingleGUIIcon(context, 18, c5, r1, 0, "ability.roundabout.zipper_guillotine",
                "instruction.roundabout.press_skill", StandIcons.KING_CRIMSON_FINAL_PUNCH, 4, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c5, r2, 0, "ability.roundabout.zipper_limb_swap",
                "instruction.roundabout.press_skill_crouch", StandIcons.GROUND_IMPLANT, 4, level, bypass));

        return list;
    }
}
