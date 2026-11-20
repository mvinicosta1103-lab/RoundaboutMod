package net.hydra.jojomod.entity.objects;

import net.hydra.jojomod.block.ModBlocks;
import net.hydra.jojomod.block.ZipperSpaceBlock;
import net.hydra.jojomod.block.ZipperSpaceBlockEntity;
import net.hydra.jojomod.entity.ModEntities;
import net.hydra.jojomod.entity.stand.StandEntity;
import net.hydra.jojomod.event.ModGamerules;
import net.hydra.jojomod.event.ModParticles;
import net.hydra.jojomod.util.MainUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**Sticky Fingers' zipper, created by the Unzip skill (Z).
 *
 * NEUTRAL  - visible zipper with an opening. Swallows small non-boss mobs that step next to it.
 *            Owner right click = hide inside, owner left click = open a tunnel,
 *            owner sneak + right click = free the swallowed mobs early.
 * HIDING   - owner is hidden inside (invisible, can't move, no mob aggro, can't heal). Zipper is not drawn.
 * TUNNEL   - zipper fully open, the blocks behind it are temporarily "unzipped" (ZipperSpaceBlock).
 * CLOSING  - short animation of the zipper closing, then the entity disappears.*/
public class ZipperEntity extends Entity {

    public static final int STATE_NEUTRAL = 0;
    public static final int STATE_HIDING = 1;
    public static final int STATE_TUNNEL = 2;
    public static final int STATE_CLOSING = 3;

    /**Ticks. Tweak these to balance the skill.*/
    public static final int NEUTRAL_LIFE = 400;
    public static final int HIDE_TICKS = 160;
    public static final int TUNNEL_TICKS = 100;
    public static final int OPEN_ANIM_TICKS = 8;
    public static final int CLOSE_ANIM_TICKS = 8;

    public static final int TUNNEL_DEPTH_WALL = 5;
    public static final int TUNNEL_DEPTH_FLOOR = 6;
    public static final int MAX_SWALLOWED = 3;

    protected static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(ZipperEntity.class,
            EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> FACE = SynchedEntityData.defineId(ZipperEntity.class,
            EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> STATE_START = SynchedEntityData.defineId(ZipperEntity.class,
            EntityDataSerializers.INT);

    private UUID ownerUUID = null;
    private BlockPos anchor = BlockPos.ZERO;
    private int baseY = 0;
    private int stateTicks = 0;
    private final List<UUID> swallowed = new ArrayList<>();
    private float lastHealth = 0;
    private int lastFood = 0;
    private float lastSaturation = 0;

    public ZipperEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**Creates a zipper stuck to the face of a block.
     * @param anchor the solid block that was aimed at
     * @param face the face of that block that was aimed at
     * @param baseY the y of the bottom of the opening (walls)*/
    public static ZipperEntity create(Level level, LivingEntity owner, BlockPos anchor, Direction face, int baseY) {
        ZipperEntity zipper = new ZipperEntity(ModEntities.ZIPPER, level);
        zipper.ownerUUID = owner.getUUID();
        zipper.anchor = anchor;
        zipper.baseY = baseY;
        zipper.entityData.set(FACE, face.get3DDataValue());
        zipper.entityData.set(STATE_START, 0);
        zipper.refreshDimensions();
        Vec3 pos = zipper.computeSurfacePos();
        zipper.setPos(pos.x, pos.y, pos.z);
        return zipper;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(STATE, STATE_NEUTRAL);
        this.entityData.define(FACE, Direction.UP.get3DDataValue());
        this.entityData.define(STATE_START, 0);
    }

    /**-------------------------------------------------------------------
     * BASIC GETTERS
     * ------------------------------------------------------------------*/

    public int getState() {
        return this.entityData.get(STATE);
    }

    public void setState(int state) {
        this.entityData.set(STATE, state);
        this.entityData.set(STATE_START, this.tickCount);
        this.stateTicks = 0;
    }

    /**tickCount at which the current state began (used by the renderer for the open/close animation)*/
    public int getStateStart() {
        return this.entityData.get(STATE_START);
    }

    public Direction getFace() {
        return Direction.from3DDataValue(this.entityData.get(FACE));
    }

    public boolean isOwner(Entity entity) {
        return entity != null && ownerUUID != null && ownerUUID.equals(entity.getUUID());
    }

    public Player getOwnerPlayer() {
        if (ownerUUID == null) {
            return null;
        }
        return this.level().getPlayerByUUID(ownerUUID);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (getFace().getAxis() == Direction.Axis.Y) {
            return EntityDimensions.scalable(1.0F, 0.2F);
        }
        return EntityDimensions.scalable(0.9F, 2.0F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (FACE.equals(key)) {
            this.refreshDimensions();
        }
        super.onSyncedDataUpdated(key);
    }

    /**Where the zipper is drawn, flush against the face of the anchor block.*/
    public Vec3 computeSurfacePos() {
        Direction face = getFace();
        double cx = anchor.getX() + 0.5D;
        double cz = anchor.getZ() + 0.5D;
        if (face == Direction.UP) {
            return new Vec3(cx, anchor.getY() + 1.01D, cz);
        }
        if (face == Direction.DOWN) {
            return new Vec3(cx, anchor.getY() - 0.21D, cz);
        }
        return new Vec3(cx + face.getStepX() * 0.51D, baseY, cz + face.getStepZ() * 0.51D);
    }

    /**Where someone hidden/swallowed stays: the free space right in front of the zipper.*/
    public Vec3 getHidePos() {
        Direction face = getFace();
        double cx = anchor.getX() + 0.5D;
        double cz = anchor.getZ() + 0.5D;
        if (face == Direction.UP) {
            return new Vec3(cx, anchor.getY() + 1.0D, cz);
        }
        if (face == Direction.DOWN) {
            return new Vec3(cx, anchor.getY() - 2.0D, cz);
        }
        return new Vec3(cx + face.getStepX(), baseY, cz + face.getStepZ());
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved() && getState() == STATE_NEUTRAL;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    /**-------------------------------------------------------------------
     * PLAYER INTERACTION
     * ------------------------------------------------------------------*/

    /**Right click*/
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!isOwner(player) || getState() != STATE_NEUTRAL) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            releaseSwallowed();
        } else {
            startHiding(player);
        }
        return InteractionResult.CONSUME;
    }

    /**Left click (vanilla attack or stand punch, both arrive here as damage from the owner)*/
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide() || this.isRemoved()) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker == null) {
            attacker = source.getDirectEntity();
        }
        if (attacker instanceof Player player && isOwner(player) && getState() == STATE_NEUTRAL) {
            startTunnel(player);
            return true;
        }
        return false;
    }

    /**-------------------------------------------------------------------
     * HIDING (right click)
     * ------------------------------------------------------------------*/

    private void startHiding(Player owner) {
        Vec3 hide = getHidePos();
        owner.teleportTo(hide.x, hide.y, hide.z);
        owner.setDeltaMovement(Vec3.ZERO);
        owner.hurtMarked = true;
        int duration = HIDE_TICKS + 10;
        owner.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, duration, 0, false, false, false));
        owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 6, false, false, false));
        owner.addEffect(new MobEffectInstance(MobEffects.JUMP, duration, 128, false, false, false));
        lastHealth = owner.getHealth();
        lastFood = owner.getFoodData().getFoodLevel();
        lastSaturation = owner.getFoodData().getSaturationLevel();
        setState(STATE_HIDING);
        this.level().playSound(null, this.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN,
                SoundSource.PLAYERS, 1.0F, 1.4F);
        sparkBurst(hide.add(0, 1.0D, 0), 14);
    }

    private void tickHiding() {
        Player owner = getOwnerPlayer();
        if (owner == null || !owner.isAlive() || stateTicks >= HIDE_TICKS) {
            endHiding(owner);
            return;
        }
        //hidden people can't heal or use saturation
        if (owner.getHealth() > lastHealth) {
            owner.setHealth(lastHealth);
        } else {
            lastHealth = owner.getHealth();
        }
        if (owner.getFoodData().getFoodLevel() > lastFood) {
            owner.getFoodData().setFoodLevel(lastFood);
        } else {
            lastFood = owner.getFoodData().getFoodLevel();
        }
        if (owner.getFoodData().getSaturationLevel() > lastSaturation) {
            owner.getFoodData().setSaturation(lastSaturation);
        } else {
            lastSaturation = owner.getFoodData().getSaturationLevel();
        }
        //mobs lose track of you, unless they are bosses
        if (stateTicks % 5 == 0) {
            AABB area = new AABB(owner.blockPosition()).inflate(32.0D);
            for (Mob mob : this.level().getEntitiesOfClass(Mob.class, area)) {
                if (mob.getTarget() == owner && !MainUtil.isBossMob(mob)) {
                    mob.setTarget(null);
                    mob.setLastHurtByMob(null);
                }
            }
        }
    }

    private void endHiding(Player owner) {
        if (owner != null) {
            owner.removeEffect(MobEffects.INVISIBILITY);
            owner.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            owner.removeEffect(MobEffects.JUMP);
        }
        close();
    }

    /**-------------------------------------------------------------------
     * TUNNEL (left click)
     * ------------------------------------------------------------------*/

    /**Blocks that the zipper can open: no air, no unbreakable blocks, nothing with a block entity or fluid.*/
    public static boolean canZip(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && !(state.getBlock() instanceof ZipperSpaceBlock)
                && state.getDestroySpeed(level, pos) >= 0
                && !state.hasBlockEntity()
                && state.getFluidState().isEmpty();
    }

    public List<BlockPos> computeTunnel() {
        List<BlockPos> list = new ArrayList<>();
        Direction inward = getFace().getOpposite();
        if (inward.getAxis() == Direction.Axis.Y) {
            for (int d = 0; d < TUNNEL_DEPTH_FLOOR; d++) {
                list.add(anchor.relative(inward, d));
            }
        } else {
            for (int d = 0; d < TUNNEL_DEPTH_WALL; d++) {
                BlockPos column = anchor.relative(inward, d);
                for (int dy = 0; dy < 2; dy++) {
                    list.add(new BlockPos(column.getX(), baseY + dy, column.getZ()));
                }
            }
        }
        return list;
    }

    private void startTunnel(Player owner) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!serverLevel.getGameRules().getBoolean(ModGamerules.ROUNDABOUT_STAND_GRIEFING)) {
            owner.displayClientMessage(Component.translatable("roundabout.zipper.no_griefing"), true);
            return;
        }
        int opened = 0;
        for (BlockPos pos : computeTunnel()) {
            if (!canZip(serverLevel, pos)) {
                continue;
            }
            BlockState original = serverLevel.getBlockState(pos);
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, original),
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 8, 0.3D, 0.3D, 0.3D, 0.05D);
            serverLevel.setBlock(pos, ModBlocks.ZIPPER_SPACE.defaultBlockState(), 3);
            BlockEntity be = serverLevel.getBlockEntity(pos);
            if (be instanceof ZipperSpaceBlockEntity space) {
                space.setup(original, TUNNEL_TICKS);
            }
            opened++;
        }
        if (opened == 0) {
            owner.displayClientMessage(Component.translatable("roundabout.zipper.nothing_to_unzip"), true);
            return;
        }
        releaseSwallowed();
        setState(STATE_TUNNEL);
        serverLevel.playSound(null, this.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.2F, 0.6F);
        serverLevel.playSound(null, this.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 1.0F, 0.5F);
        sparkBurst(this.position().add(0, 1.0D, 0), 24);
    }

    /**-------------------------------------------------------------------
     * SWALLOWING MOBS (neutral state)
     * ------------------------------------------------------------------*/

    private void swallowNearby() {
        if (swallowed.size() >= MAX_SWALLOWED) {
            return;
        }
        Vec3 hide = getHidePos();
        AABB area = new AABB(hide.x - 1.0D, hide.y - 0.5D, hide.z - 1.0D, hide.x + 1.0D, hide.y + 2.5D, hide.z + 1.0D);
        for (Mob mob : this.level().getEntitiesOfClass(Mob.class, area)) {
            if (swallowed.size() >= MAX_SWALLOWED) {
                return;
            }
            if (!mob.isAlive() || isOwner(mob) || mob instanceof StandEntity || swallowed.contains(mob.getUUID())
                    || MainUtil.isBossMob(mob) || mob.isNoAi() || mob.isInvulnerable()
                    || mob.getBbWidth() > 1.5F || mob.getBbHeight() > 3.0F) {
                continue;
            }
            swallowed.add(mob.getUUID());
            mob.setTarget(null);
            mob.setNoAi(true);
            mob.setInvulnerable(true);
            mob.setNoGravity(true);
            mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, NEUTRAL_LIFE + 60, 0, false, false, false));
            mob.moveTo(hide.x, hide.y, hide.z);
            mob.setDeltaMovement(Vec3.ZERO);
            sparkBurst(mob.position().add(0, mob.getBbHeight() / 2, 0), 8);
        }
    }

    private void holdSwallowed() {
        if (swallowed.isEmpty() || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 hide = getHidePos();
        for (UUID id : swallowed) {
            Entity entity = serverLevel.getEntity(id);
            if (entity != null && entity.isAlive()) {
                entity.moveTo(hide.x, hide.y, hide.z);
                entity.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    /**Frees every mob stuck in the zipper.*/
    public void releaseSwallowed() {
        if (swallowed.isEmpty() || !(this.level() instanceof ServerLevel serverLevel)) {
            swallowed.clear();
            return;
        }
        for (UUID id : swallowed) {
            Entity entity = serverLevel.getEntity(id);
            if (entity instanceof LivingEntity living) {
                living.removeEffect(MobEffects.INVISIBILITY);
                living.setInvulnerable(false);
                living.setNoGravity(false);
                if (living instanceof Mob mob) {
                    mob.setNoAi(false);
                }
                sparkBurst(living.position().add(0, living.getBbHeight() / 2, 0), 8);
            }
        }
        swallowed.clear();
        serverLevel.playSound(null, this.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 0.8F, 1.3F);
    }

    /**-------------------------------------------------------------------
     * LIFECYCLE
     * ------------------------------------------------------------------*/

    /**Starts the closing animation and cleans up whoever is inside.*/
    public void close() {
        if (this.level().isClientSide() || getState() == STATE_CLOSING) {
            return;
        }
        releaseSwallowed();
        setState(STATE_CLOSING);
        this.level().playSound(null, this.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN,
                SoundSource.PLAYERS, 0.8F, 0.8F);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            tickClientParticles();
            return;
        }
        stateTicks++;
        switch (getState()) {
            case STATE_NEUTRAL -> {
                Player owner = getOwnerPlayer();
                if (owner == null || !owner.isAlive() || stateTicks > NEUTRAL_LIFE) {
                    close();
                    return;
                }
                if (stateTicks % 4 == 0) {
                    swallowNearby();
                }
                holdSwallowed();
            }
            case STATE_HIDING -> tickHiding();
            case STATE_TUNNEL -> {
                if (stateTicks >= TUNNEL_TICKS) {
                    close();
                }
            }
            case STATE_CLOSING -> {
                if (stateTicks >= CLOSE_ANIM_TICKS) {
                    this.discard();
                }
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide() && reason.shouldDestroy()) {
            releaseSwallowed();
            if (getState() == STATE_HIDING) {
                Player owner = getOwnerPlayer();
                if (owner != null) {
                    owner.removeEffect(MobEffects.INVISIBILITY);
                    owner.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    owner.removeEffect(MobEffects.JUMP);
                }
            }
        }
        super.remove(reason);
    }

    /**-------------------------------------------------------------------
     * VFX
     * ------------------------------------------------------------------*/

    private void sparkBurst(Vec3 pos, int count) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, pos.x, pos.y, pos.z,
                    count, 0.3D, 0.5D, 0.3D, 0.02D);
        }
    }

    /**Sparks running along the opening while the zipper is open.*/
    private void tickClientParticles() {
        int state = getState();
        if ((state != STATE_NEUTRAL && state != STATE_TUNNEL) || this.tickCount % 3 != 0) {
            return;
        }
        Direction face = getFace();
        double along = (this.random.nextDouble() - 0.5D) * 0.5D;
        double height = face.getAxis() == Direction.Axis.Y ? 0.05D : this.random.nextDouble() * 2.0D;
        double x = this.getX();
        double y = this.getY() + height;
        double z = this.getZ();
        if (face.getAxis() == Direction.Axis.X) {
            z += along;
        } else if (face.getAxis() == Direction.Axis.Z) {
            x += along;
        } else {
            x += along;
            z += (this.random.nextDouble() - 0.5D) * 0.8D;
        }
        this.level().addParticle(ModParticles.ZIPPER_SPARK, x + face.getStepX() * 0.1D, y, z + face.getStepZ() * 0.1D,
                0.0D, 0.01D, 0.0D);
    }

    /**-------------------------------------------------------------------
     * SAVING
     * ------------------------------------------------------------------*/

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("owner")) {
            ownerUUID = tag.getUUID("owner");
        }
        anchor = new BlockPos(tag.getInt("ax"), tag.getInt("ay"), tag.getInt("az"));
        baseY = tag.getInt("baseY");
        stateTicks = tag.getInt("stateTicks");
        this.entityData.set(FACE, tag.getInt("face"));
        this.entityData.set(STATE, tag.getInt("state"));
        swallowed.clear();
        ListTag list = tag.getList("swallowed", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < list.size(); i++) {
            swallowed.add(NbtUtils.loadUUID(list.get(i)));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUUID != null) {
            tag.putUUID("owner", ownerUUID);
        }
        tag.putInt("ax", anchor.getX());
        tag.putInt("ay", anchor.getY());
        tag.putInt("az", anchor.getZ());
        tag.putInt("baseY", baseY);
        tag.putInt("stateTicks", stateTicks);
        tag.putInt("face", this.entityData.get(FACE));
        tag.putInt("state", getState());
        ListTag list = new ListTag();
        for (UUID id : swallowed) {
            list.add(NbtUtils.createUUID(id));
        }
        tag.put("swallowed", list);
    }
}
