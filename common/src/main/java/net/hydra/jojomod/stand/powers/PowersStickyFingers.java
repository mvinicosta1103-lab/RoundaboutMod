package net.hydra.jojomod.stand.powers;

import com.google.common.collect.Lists;
import net.hydra.jojomod.client.StandIcons;
import net.hydra.jojomod.entity.ModEntities;
import net.hydra.jojomod.block.ZipperSpaceBlock;
import net.hydra.jojomod.entity.objects.ZipperDebrisEntity;
import net.hydra.jojomod.entity.objects.ZipperEntity;
import net.hydra.jojomod.entity.objects.ZipperPouchEntity;
import net.hydra.jojomod.entity.objects.ZipperStorageEntity;
import net.hydra.jojomod.event.ModGamerules;
import net.hydra.jojomod.event.ModParticles;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**Sticky Fingers.
 * Model, animations and sounds are TEMPORARILY borrowed from King Crimson.
 *
 * Skill layout (key -> PowerContext):
 *   Z = SKILL_1, X = SKILL_2, C = SKILL_3, V = SKILL_4
 *   *_NORMAL = key, *_CROUCH = Sneak+key, *_GUARD / *_CROUCH_GUARD = Block+key
 *
 * DONE:  Z (unzip), Sneak+Z (zipper barrage), Block+Z (zipper storage), C (dash + vault), V (zipper guillotine)
 * TODO:  Sneak+Click kick, X, Sneak+X, Block+X,
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
        list.add(StickyFingersEntity.PART_5);
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
                massUnzipClient();
            }
            case SKILL_1_GUARD, SKILL_1_CROUCH_GUARD -> {
                storageClient();
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
     * SNEAK + Z: ZIPPER BARRAGE
     * Zippers appear all over the surface you are facing (a wide area) and rapidly mine it away, the blocks
     * crack from the middle outwards. When a block breaks, debris launches out of it and deals light stand
     * damage. Weaker blocks (dirt) mine faster and make weaker debris, so walls and floors become weapons.
     * With stand griefing off the blocks stay, only the debris is spawned. (Based on the Secco fight.)
     * ------------------------------------------------------------------*/

    public static final byte MASS_UNZIP = PowerIndex.POWER_2;
    public static final int MASS_UNZIP_COOLDOWN = 240;
    public static final double MASS_UNZIP_RANGE = 8.0D;
    /**Radius of the area, 3 = a 7x7 surface.*/
    public static final int MASS_RADIUS = 3;
    public static final int MASS_MAX_BLOCKS = 36;

    /**One block being mined by the barrage.*/
    private static class MassBlock {
        final BlockPos pos;
        final Direction face;
        final BlockState state;
        final float hardness;
        final int delay;
        final int total;
        final int breakerId;
        int age = 0;

        MassBlock(BlockPos pos, Direction face, BlockState state, float hardness, int delay, int total, int breakerId) {
            this.pos = pos;
            this.face = face;
            this.state = state;
            this.hardness = hardness;
            this.delay = delay;
            this.total = total;
            this.breakerId = breakerId;
        }
    }

    private final List<MassBlock> massBlocks = new ArrayList<>();

    public void massUnzipClient() {
        if (!this.onCooldown(PowerIndex.SKILL_1_SNEAK)) {
            this.tryPower(MASS_UNZIP, true);
            tryPowerPacket(MASS_UNZIP);
        }
    }

    private void startMassUnzip() {
        HitResult hit = this.self.pick(MASS_UNZIP_RANGE, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        Level level = this.self.level();
        this.setCooldown(PowerIndex.SKILL_1_SNEAK, MASS_UNZIP_COOLDOWN);
        if (level.isClientSide()) {
            return;
        }
        BlockPos center = blockHit.getBlockPos();
        Direction face = blockHit.getDirection();

        //every block of the surface around the one you aimed at that is exposed on that side
        List<BlockPos> found = new ArrayList<>();
        for (int a = -MASS_RADIUS; a <= MASS_RADIUS; a++) {
            for (int b = -MASS_RADIUS; b <= MASS_RADIUS; b++) {
                BlockPos p;
                switch (face.getAxis()) {
                    case X -> p = center.offset(0, a, b);
                    case Y -> p = center.offset(a, 0, b);
                    default -> p = center.offset(a, b, 0);
                }
                if (!ZipperEntity.canZip(level, p)) {
                    continue;
                }
                BlockPos front = p.relative(face);
                if (!level.getBlockState(front).getCollisionShape(level, front).isEmpty()) {
                    continue;
                }
                found.add(p);
            }
        }
        if (found.isEmpty()) {
            message("roundabout.zipper.nothing_to_unzip");
            return;
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        if (found.size() > MASS_MAX_BLOCKS) {
            found = new ArrayList<>(found.subList(0, MASS_MAX_BLOCKS));
        }
        if (!level.getGameRules().getBoolean(ModGamerules.ROUNDABOUT_STAND_GRIEFING)) {
            message("roundabout.zipper.mass_no_griefing");
        }

        massBlocks.clear();
        int index = 0;
        for (BlockPos p : found) {
            BlockState state = level.getBlockState(p);
            float hardness = state.getDestroySpeed(level, p);
            //soft blocks go fast, hard blocks take longer. The ripple starts in the middle
            int total = Mth.clamp(Math.round(hardness * 5.0F) + 6, 6, 36);
            int delay = (int) Math.round(Math.sqrt(p.distSqr(center)) * 2.0D);
            int breakerId = 100000 + this.self.getId() * 64 + index;
            massBlocks.add(new MassBlock(p, face, state, hardness, delay, total, breakerId));
            index++;
        }
        level.playSound(null, this.self.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.2F, 0.7F);
        level.playSound(null, this.self.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 1.2F, 0.5F);
    }

    private void tickMassBlocks() {
        if (massBlocks.isEmpty() || !(this.self.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean dead = !this.self.isAlive();
        Iterator<MassBlock> it = massBlocks.iterator();
        while (it.hasNext()) {
            MassBlock mb = it.next();
            if (dead || serverLevel.getBlockState(mb.pos) != mb.state) {
                serverLevel.destroyBlockProgress(mb.breakerId, mb.pos, -1);
                it.remove();
                continue;
            }
            mb.age++;
            if (mb.age < mb.delay) {
                continue;
            }
            int progress = mb.age - mb.delay;
            if (progress % 2 == 0) {
                serverLevel.destroyBlockProgress(mb.breakerId, mb.pos, Math.min(9, progress * 10 / mb.total));
            }
            if (progress % 4 == 0) {
                Vec3 surface = Vec3.atCenterOf(mb.pos).add(mb.face.getStepX() * 0.55D, mb.face.getStepY() * 0.55D,
                        mb.face.getStepZ() * 0.55D);
                serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, surface.x, surface.y, surface.z,
                        2, 0.25D, 0.25D, 0.25D, 0.01D);
                serverLevel.playSound(null, mb.pos, mb.state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.5F, 0.8F);
            }
            if (progress >= mb.total) {
                finishMassBlock(serverLevel, mb);
                it.remove();
            }
        }
    }

    private void finishMassBlock(ServerLevel level, MassBlock mb) {
        level.destroyBlockProgress(mb.breakerId, mb.pos, -1);
        boolean griefing = level.getGameRules().getBoolean(ModGamerules.ROUNDABOUT_STAND_GRIEFING);
        boolean drops = level.getGameRules().getBoolean(ModGamerules.ROUNDABOUT_STAND_GRIEFING_OBTAINMENT);

        //debris flies out of the surface that was mined
        Vec3 normal = new Vec3(mb.face.getStepX(), mb.face.getStepY(), mb.face.getStepZ());
        Vec3 start = Vec3.atCenterOf(mb.pos).add(normal.scale(0.6D));
        float damage = ZipperDebrisEntity.damageForBlock(mb.hardness);
        int count = 1 + level.random.nextInt(2) + (mb.hardness >= 2.0F ? 1 : 0);
        for (int i = 0; i < count; i++) {
            Vec3 velocity = normal.scale(0.45D + level.random.nextDouble() * 0.4D).add(
                    (level.random.nextDouble() - 0.5D) * 0.5D,
                    0.2D + level.random.nextDouble() * 0.25D,
                    (level.random.nextDouble() - 0.5D) * 0.5D);
            level.addFreshEntity(ZipperDebrisEntity.create(level, this.self, mb.state, start, velocity, damage));
        }

        if (griefing) {
            level.destroyBlock(mb.pos, drops, this.self);
        } else {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, mb.state),
                    start.x, start.y, start.z, 6, 0.25D, 0.25D, 0.25D, 0.05D);
            level.playSound(null, mb.pos, mb.state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.6F, 1.0F);
        }
    }

    /**-------------------------------------------------------------------
     * BLOCK + Z: ZIPPER STORAGE
     * Looking at a block: it becomes a one row chest. The block looks normal, but stand users that get close
     * see a zipper on the face you aimed at, which unzips when they open it (right click). Attacking the
     * zipper pops it, all the items fly out and the block is normal again. Good for temporary storage.
     *
     * Looking at a TAGGED mob (punch a mob with Sticky Fingers to tag it for a few seconds): the item in your
     * main hand is stuffed inside it. The item is held by a ZipperPouchEntity mounted on the mob, only for
     * a while, and acts on the mob. See ZipperPouchEntity for the item effects.
     * ------------------------------------------------------------------*/

    public static final byte ZIPPER_STORAGE = PowerIndex.POWER_3;
    public static final int STORAGE_COOLDOWN = 30;
    public static final double STORAGE_RANGE = 5.0D;
    /**How long a punched mob stays tagged (ticks).*/
    public static final int TAG_DURATION = 200;

    /**Mobs this user tagged, and the game time when the tag runs out (server only).*/
    private final Map<UUID, Long> taggedMobs = new HashMap<>();

    public void storageClient() {
        if (!this.onCooldown(PowerIndex.SKILL_1_GUARD)) {
            this.tryPower(ZIPPER_STORAGE, true);
            tryPowerPacket(ZIPPER_STORAGE);
        }
    }

    private void startStorage() {
        Level level = this.self.level();
        this.setCooldown(PowerIndex.SKILL_1_GUARD, STORAGE_COOLDOWN);
        if (level.isClientSide()) {
            return;
        }
        Entity target = MainUtil.getTargetEntity(this.self, (float) STORAGE_RANGE);
        boolean targetIsMob = target instanceof LivingEntity && !(target instanceof StandEntity) && target != this.self;
        if (targetIsMob && isTagged((LivingEntity) target)) {
            stuffMob((LivingEntity) target);
            return;
        }
        HitResult hit = this.self.pick(STORAGE_RANGE, 1.0F, false);
        if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            convertBlock(level, blockHit.getBlockPos(), blockHit.getDirection());
        } else if (targetIsMob) {
            message("roundabout.zipper.not_tagged");
        } else {
            message("roundabout.zipper.nothing_to_store");
        }
    }

    private void convertBlock(Level level, BlockPos pos, Direction face) {
        BlockState state = level.getBlockState(pos);
        BlockPos front = pos.relative(face);
        if (state.isAir() || !state.isCollisionShapeFullBlock(level, pos) || state.getDestroySpeed(level, pos) < 0
                || state.hasBlockEntity() || state.getBlock() instanceof ZipperSpaceBlock
                || !level.getBlockState(front).getCollisionShape(level, front).isEmpty()) {
            message("roundabout.zipper.cant_store");
            return;
        }
        if (ZipperStorageEntity.findAt(level, pos) != null) {
            message("roundabout.zipper.already_storage");
            return;
        }
        ZipperStorageEntity storage = ZipperStorageEntity.create(level, this.self, pos, face);
        level.addFreshEntity(storage);
        level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 0.9F);
        level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 1.0F, 0.7F);
        if (level instanceof ServerLevel serverLevel) {
            Vec3 c = storage.computeCenter();
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, c.x, c.y, c.z, 12, 0.25D, 0.25D, 0.25D, 0.02D);
        }
    }

    private void stuffMob(LivingEntity mob) {
        Level level = this.self.level();
        ItemStack held = this.self.getMainHandItem();
        if (held.isEmpty()) {
            message("roundabout.zipper.no_item");
            return;
        }
        if (ZipperPouchEntity.hostHasPouch(level, mob)) {
            message("roundabout.zipper.already_stuffed");
            return;
        }
        ItemStack moved = held.copy();
        boolean creative = this.self instanceof Player player && player.getAbilities().instabuild;
        if (!creative) {
            this.self.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        level.addFreshEntity(ZipperPouchEntity.create(level, this.self, mob, moved));
        level.playSound(null, mob.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.playSound(null, mob.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 1.0F, 1.2F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5D,
                    mob.getZ(), 14, 0.3D, 0.4D, 0.3D, 0.03D);
        }
    }

    /**Sticky Fingers' punches tag whoever they land on.*/
    @Override
    public void punchImpact(Entity entity) {
        super.punchImpact(entity);
        tagMob(entity);
    }

    @Override
    public void barrageImpact(Entity entity, int hitNumber) {
        super.barrageImpact(entity, hitNumber);
        tagMob(entity);
    }

    private void tagMob(Entity entity) {
        if (this.self.level().isClientSide() || !(entity instanceof LivingEntity living) || entity == this.self
                || entity instanceof StandEntity || !living.isAlive()) {
            return;
        }
        taggedMobs.put(entity.getUUID(), this.self.level().getGameTime() + TAG_DURATION);
    }

    private boolean isTagged(LivingEntity entity) {
        Long until = taggedMobs.get(entity.getUUID());
        return until != null && entity.level().getGameTime() < until;
    }

    /**Tags expire, and tagged mobs get a spark over their head so you can tell.*/
    private void tickTags() {
        if (taggedMobs.isEmpty() || !(this.self.level() instanceof ServerLevel serverLevel)
                || serverLevel.getGameTime() % 10 != 0) {
            return;
        }
        long now = serverLevel.getGameTime();
        Iterator<Map.Entry<UUID, Long>> it = taggedMobs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            Entity entity = serverLevel.getEntity(entry.getKey());
            if (entry.getValue() <= now || entity == null || !entity.isAlive()) {
                it.remove();
                continue;
            }
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, entity.getX(), entity.getY() + entity.getBbHeight() + 0.3D,
                    entity.getZ(), 1, 0.1D, 0.05D, 0.1D, 0.0D);
        }
    }

    /**Short message above the hotbar (players only).*/
    private void message(String key) {
        if (this.self instanceof Player player) {
            player.displayClientMessage(Component.translatable(key), true);
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
        } else if (move == MASS_UNZIP) {
            startMassUnzip();
        } else if (move == ZIPPER_STORAGE) {
            startStorage();
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
        if (!this.self.level().isClientSide()) {
            tickMassBlocks();
            tickTags();
        }
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
        //Z changes with the stance: Block = zipper storage, Sneak = zipper barrage, normal = unzip
        if (isGuarding()) {
            setSkillIcon(context, x, y, 1, StandIcons.STICKY_FINGERS_ZIPPER_STORAGE, PowerIndex.SKILL_1_GUARD);
        } else if (isHoldingSneak()) {
            setSkillIcon(context, x, y, 1, StandIcons.STICKY_FINGERS_ZIPPER_MASS, PowerIndex.SKILL_1_SNEAK);
        } else {
            setSkillIcon(context, x, y, 1, StandIcons.STICKY_FINGERS_UNZIP, PowerIndex.SKILL_1);
        }
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
                "instruction.roundabout.press_skill", StandIcons.STICKY_FINGERS_UNZIP, 1, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c2, r1, 0, "ability.roundabout.zipper_mass",
                "instruction.roundabout.press_skill_crouch", StandIcons.STICKY_FINGERS_ZIPPER_MASS, 1, level, bypass));
        list.add(drawSingleGUIIcon(context, 18, c2, r2, 0, "ability.roundabout.zipper_storage",
                "instruction.roundabout.press_skill_block", StandIcons.STICKY_FINGERS_ZIPPER_STORAGE, 1, level, bypass));

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
