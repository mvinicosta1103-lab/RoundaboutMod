package net.hydra.jojomod.entity.objects;

import net.hydra.jojomod.block.ModBlocks;
import net.hydra.jojomod.block.ZipperSpaceBlock;
import net.hydra.jojomod.block.ZipperSpaceBlockEntity;
import net.hydra.jojomod.entity.ModEntities;
import net.hydra.jojomod.entity.stand.StandEntity;
import net.hydra.jojomod.event.ModGamerules;
import net.hydra.jojomod.event.ModParticles;
import net.hydra.jojomod.event.powers.ModDamageTypes;
import net.hydra.jojomod.client.ClientUtil;
import net.hydra.jojomod.event.powers.StandUser;
import net.hydra.jojomod.access.IStickyFingersDismemberment;
import net.hydra.jojomod.stand.powers.PowersStickyFingers;
import net.hydra.jojomod.sound.ModSounds;
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
import net.minecraft.util.Mth;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ZipperEntity extends Entity {

    public static final int STATE_NEUTRAL = 0;
    public static final int STATE_HIDING = 1;
    public static final int STATE_TUNNEL = 2;
    public static final int STATE_CLOSING = 3;
    public static final int STATE_MINING = 4;
    public static final int STATE_STORAGE = 5;
    public static final int STATE_STORAGE_OPEN = 6;
    public static final int STATE_OPENING = 7;
    public static final int STATE_LATCH_ARMING = 8;
    public static final int STATE_LATCH_READY = 9;
    public static final int STATE_LATCH_RIDING = 10;
    public static final int STATE_LATCH_RETRACTING = 11;

    private static final int OPEN_ACTION_TUNNEL = 2;
    private static final int OPEN_ACTION_SWALLOW = 3;

    public static final int NEUTRAL_LIFE = 400;
    public static final int HIDE_TICKS = 160;
    public static final int TUNNEL_TICKS = 100;
    public static final int OPEN_ANIM_TICKS = 8;
    /** Zipper time before it retracts (20 ticks = 1 second) **/
    public static final int CLOSE_ANIM_TICKS = 20;
    private static final int LATCH_ARM_TICKS = 20;
    private static final int LATCH_READY_TICKS = 400;
    private static final int LATCH_RIDE_TICKS = 20;

    public static final int TUNNEL_DEPTH_WALL = 4;
    public static final int TUNNEL_DEPTH_FLOOR = 4;
    public static final int MAX_SWALLOWED = 3;
    private static final int MAX_CONNECTED_ZIPPERS = 64;

    protected static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(ZipperEntity.class,
            EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> FACE = SynchedEntityData.defineId(ZipperEntity.class,
            EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> STATE_START = SynchedEntityData.defineId(ZipperEntity.class,
            EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Boolean> MINI = SynchedEntityData.defineId(ZipperEntity.class,
            EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(ZipperEntity.class,
            EntityDataSerializers.OPTIONAL_UUID);
    protected static final EntityDataAccessor<BlockPos> ANCHOR = SynchedEntityData.defineId(ZipperEntity.class,
            EntityDataSerializers.BLOCK_POS);
    protected static final EntityDataAccessor<Integer> BASE_Y = SynchedEntityData.defineId(ZipperEntity.class,
            EntityDataSerializers.INT);

    protected UUID ownerUUID = null;
    protected BlockPos anchor = BlockPos.ZERO;
    protected int baseY = 0;
    protected int stateTicks = 0;
    private int mineDelay = 0;
    private int biteTick = -1;
    private boolean vanishOnMine = false;
    private int pendingOpenAction = 0;
    private UUID pendingCapture = null;
    private boolean retainAfterClose = false;
    private Vec3 pendingReleaseExit = null;
    private Direction pendingReleaseFace = Direction.NORTH;
    private UUID pendingReleaseOwner = null;
    private int pendingReleaseTicks = 0;
    private Vec3 latchVelocity = Vec3.ZERO;
    private int latchRiderId = -1;
    private final List<UUID> swallowed = new ArrayList<>();
    private final java.util.Map<UUID, StoredEntityState> swallowedStates = new java.util.HashMap<>();
    private float lastHealth = 0;
    private int lastFood = 0;
    private float lastSaturation = 0;
    private boolean savedMayfly;
    private boolean savedFlying;
    private float savedFlyingSpeed = 0.05F;
    private boolean savedAbilities;
    private MobEffectInstance savedSpeedEffect;
    private boolean savedWasSwimming;

    public ZipperEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static ZipperEntity create(Level level, LivingEntity owner, BlockPos anchor, Direction face, int baseY) {
        ZipperEntity zipper = new ZipperEntity(ModEntities.ZIPPER, level);
        zipper.setup(owner, anchor, face, baseY, false);
        return zipper;
    }

    public static ZipperEntity createMini(Level level, LivingEntity owner, BlockPos anchor, Direction face, int baseY) {
        ZipperEntity zipper = new ZipperEntity(ModEntities.ZIPPER, level);
        zipper.setup(owner, anchor, face, baseY, true);
        return zipper;
    }

    public static ZipperEntity createLatch(Level level, LivingEntity owner, BlockPos anchor,
                                           Direction face, int baseY, Vec3 rideVelocity) {
        ZipperEntity zipper = create(level, owner, anchor, face, baseY);
        zipper.latchVelocity = rideVelocity;
        zipper.setState(STATE_LATCH_ARMING);
        return zipper;
    }

    protected void setup(LivingEntity owner, BlockPos anchor, Direction face, int baseY, boolean mini) {
        this.ownerUUID = owner.getUUID();
        this.anchor = anchor;
        this.baseY = baseY;
        this.entityData.set(OWNER, Optional.of(ownerUUID));
        this.entityData.set(ANCHOR, anchor);
        this.entityData.set(BASE_Y, baseY);
        this.entityData.set(FACE, face.get3DDataValue());
        this.entityData.set(MINI, mini);
        this.entityData.set(STATE_START, 0);
        this.refreshDimensions();
        Vec3 pos = this.computeSurfacePos();
        this.setPos(pos.x, pos.y, pos.z);
    }

    public void startMining(int appearDelay, int mineTicks) {
        startMining(appearDelay, mineTicks, false);
    }

    /** Barrage zippers break their block exactly as the opening animation finishes, then vanish. */
    public void startSynchronizedMining(int appearDelay) {
        startMining(appearDelay, 0, true);
    }

    private void startMining(int appearDelay, int mineTicks, boolean vanishOnMine) {
        setState(STATE_MINING);
        this.entityData.set(STATE_START, this.tickCount + appearDelay);
        this.mineDelay = appearDelay + OPEN_ANIM_TICKS + mineTicks;
        this.biteTick = appearDelay + OPEN_ANIM_TICKS;
        this.vanishOnMine = vanishOnMine;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(STATE, STATE_NEUTRAL);
        this.entityData.define(FACE, Direction.UP.get3DDataValue());
        this.entityData.define(STATE_START, 0);
        this.entityData.define(MINI, false);
        this.entityData.define(OWNER, Optional.empty());
        this.entityData.define(ANCHOR, BlockPos.ZERO);
        this.entityData.define(BASE_Y, 0);
    }

    public int getState() {
        return this.entityData.get(STATE);
    }

    public void setState(int state) {
        this.entityData.set(STATE, state);
        this.entityData.set(STATE_START, this.tickCount);
        this.stateTicks = 0;
    }

    public int getStateStart() {
        return this.entityData.get(STATE_START);
    }

    public boolean isMini() {
        return this.entityData.get(MINI);
    }

    public BlockPos getAnchor() {
        return this.entityData.get(ANCHOR);
    }

    public int getBaseY() {
        return this.entityData.get(BASE_Y);
    }

    public UUID getOwnerUUID() {
        return this.entityData.get(OWNER).orElse(ownerUUID);
    }

    public List<ZipperEntity> getConnectedZippers() {
        if (!canJoinZipperGroup(this)) {
            return List.of(this);
        }

        Set<ZipperEntity> found = new HashSet<>();
        List<ZipperEntity> pending = new ArrayList<>();
        found.add(this);
        pending.add(this);
        for (int index = 0; index < pending.size() && found.size() < MAX_CONNECTED_ZIPPERS; index++) {
            ZipperEntity current = pending.get(index);
            AABB search = current.getBoundingBox().inflate(2.1D);
            for (ZipperEntity candidate : this.level().getEntitiesOfClass(ZipperEntity.class, search)) {
                if (found.size() >= MAX_CONNECTED_ZIPPERS) {
                    break;
                }
                if (!found.contains(candidate) && canConnectTo(current, candidate)) {
                    found.add(candidate);
                    pending.add(candidate);
                }
            }
        }
        List<ZipperEntity> result = new ArrayList<>(found);
        result.sort(Comparator.comparingInt(Entity::getId));
        return result;
    }

    private List<ZipperEntity> getUnzipGroup() {
        if (this instanceof ZipperStorageEntity || isMini()) {
            return List.of(this);
        }
        return getConnectedZippers();
    }

    private void setUnzipGroupState(int state, int openAction) {
        for (ZipperEntity zipper : getUnzipGroup()) {
            zipper.pendingOpenAction = openAction;
            zipper.setState(state);
        }
    }

    public ZipperEntity getMergedRenderLeader() {
        List<ZipperEntity> connected = getConnectedZippers();
        ZipperEntity leader = this;
        int bestPriority = mergeRenderPriority(this);
        for (ZipperEntity candidate : connected) {
            int priority = mergeRenderPriority(candidate);
            if (priority > bestPriority || (priority == bestPriority && candidate.getId() < leader.getId())) {
                leader = candidate;
                bestPriority = priority;
            }
        }
        return leader;
    }

    public MergeRenderLayout getMergeRenderLayout(float baseHalfWidth, float baseLength,
                                                   boolean geometryStartsAtBase) {
        Direction face = getFace();
        double localXAxisX = face.getAxis() == Direction.Axis.Y ? 1.0D : face.getCounterClockWise().getStepX();
        double localXAxisZ = face.getAxis() == Direction.Axis.Y ? 0.0D : face.getCounterClockWise().getStepZ();
        double localYAxisX = 0.0D;
        double localYAxisY = 0.0D;
        double localYAxisZ;
        if (face == Direction.UP) {
            localYAxisZ = -1.0D;
        } else if (face == Direction.DOWN) {
            localYAxisZ = 1.0D;
        } else {
            localYAxisY = 1.0D;
            localYAxisZ = 0.0D;
        }

        List<double[]> centers = new ArrayList<>();
        for (ZipperEntity member : getConnectedZippers()) {
            Vec3 offset = member.position().subtract(this.position());
            double centerX = offset.x * localXAxisX + offset.z * localXAxisZ;
            double centerY;
            if (face.getAxis() == Direction.Axis.Y) {
                centerY = offset.x * localYAxisX + offset.y * localYAxisY + offset.z * localYAxisZ;
            } else {
                centerY = member.getBaseY() - this.getBaseY()
                        + (geometryStartsAtBase ? baseLength / 2.0D : 0.0D);
            }
            centers.add(new double[] {centerX, centerY});
        }

        double meanX = centers.stream().mapToDouble(point -> point[0]).average().orElse(0.0D);
        double meanY = centers.stream().mapToDouble(point -> point[1]).average().orElse(0.0D);
        double covarianceXX = 0.0D;
        double covarianceYY = 0.0D;
        double covarianceXY = 0.0D;
        for (double[] point : centers) {
            double dx = point[0] - meanX;
            double dy = point[1] - meanY;
            covarianceXX += dx * dx;
            covarianceYY += dy * dy;
            covarianceXY += dx * dy;
        }
        double variance = covarianceXX + covarianceYY;
        double principalAngle = variance < 1.0E-6D
                ? Math.PI / 2.0D
                : 0.5D * Math.atan2(2.0D * covarianceXY, covarianceXX - covarianceYY);
        double rotationRadians = principalAngle - Math.PI / 2.0D;
        double cos = Math.cos(rotationRadians);
        double sin = Math.sin(rotationRadians);
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (double[] point : centers) {
            double centerX = point[0] * cos + point[1] * sin;
            double centerY = -point[0] * sin + point[1] * cos;
            minX = Math.min(minX, centerX - baseHalfWidth);
            maxX = Math.max(maxX, centerX + baseHalfWidth);
            minY = Math.min(minY, centerY - baseLength / 2.0D);
            maxY = Math.max(maxY, centerY + baseLength / 2.0D);
        }
        return new MergeRenderLayout((float) ((minX + maxX) / 2.0D),
                (float) ((minY + maxY) / 2.0D),
                (float) Math.max(baseHalfWidth * 2.0D, maxX - minX),
                (float) Math.max(baseLength, maxY - minY),
                (float) Math.toDegrees(rotationRadians));
    }

    public List<ZipperPathCell> getMergedFloorPath() {
        if (getFace().getAxis() != Direction.Axis.Y) {
            return List.of();
        }
        List<ZipperEntity> connected = getConnectedZippers();
        if (connected.size() <= 1) {
            return List.of();
        }

        int zSign = getFace() == Direction.UP ? -1 : 1;
        int originU = getAnchor().getX();
        int originV = getAnchor().getZ() * zSign;
        Set<ZipperPathCell> actual = new HashSet<>();
        for (ZipperEntity zipper : connected) {
            BlockPos zipperAnchor = zipper.getAnchor();
            actual.add(new ZipperPathCell(zipperAnchor.getX(), zipperAnchor.getZ() * zSign));
        }

        // Only render cells that still have a live zipper entity. Bridging gaps
        // here made a removed middle zipper reappear as part of the merged artwork.
        Set<ZipperPathCell> path = new HashSet<>(actual);

        List<ZipperPathCell> relative = new ArrayList<>();
        for (ZipperPathCell cell : path) {
            relative.add(new ZipperPathCell(cell.u() - originU, cell.v() - originV));
        }
        relative.sort(Comparator.comparingInt(ZipperPathCell::u).thenComparingInt(ZipperPathCell::v));
        return relative;
    }


    public List<ZipperPathCell> getMergedWallPath() {
        Direction face = getFace();
        if (face.getAxis() == Direction.Axis.Y) {
            return List.of();
        }
        List<ZipperEntity> connected = getConnectedZippers();
        if (connected.size() <= 1) {
            return List.of();
        }
        Direction localX = face.getCounterClockWise();
        Set<ZipperPathCell> actual = new HashSet<>();
        for (ZipperEntity zipper : connected) {
            BlockPos pos = zipper.getAnchor();
            int u = pos.getX() * localX.getStepX() + pos.getZ() * localX.getStepZ();
            actual.add(new ZipperPathCell(u, zipper.getBaseY()));
        }
        // Keep this path limited to cells that still have a zipper entity.
        Set<ZipperPathCell> path = new HashSet<>(actual);
        int originU = anchor.getX() * localX.getStepX() + anchor.getZ() * localX.getStepZ();
        List<ZipperPathCell> relative = new ArrayList<>();
        for (ZipperPathCell cell : path) {
            relative.add(new ZipperPathCell(cell.u() - originU, cell.v() - baseY));
        }
        relative.sort(Comparator.comparingInt(ZipperPathCell::u).thenComparingInt(ZipperPathCell::v));
        return relative;
    }

    public record ZipperPathCell(int u, int v) {}

    private boolean canJoinZipperGroup(ZipperEntity zipper) {
        if (zipper instanceof ZipperStorageEntity storage) {
            return !storage.isMobMode() && (zipper.getState() == STATE_STORAGE
                    || zipper.getState() == STATE_STORAGE_OPEN || zipper.getState() == STATE_CLOSING);
        }
        return !zipper.isMini() && (zipper.getState() == STATE_NEUTRAL || zipper.getState() == STATE_OPENING
                || zipper.getState() == STATE_TUNNEL || zipper.getState() == STATE_CLOSING);
    }

    private boolean canConnectTo(ZipperEntity source, ZipperEntity candidate) {
        if (candidate == source || !source.canJoinZipperGroup(source) || !source.canJoinZipperGroup(candidate)
                || source.getOwnerUUID() == null || !source.getOwnerUUID().equals(candidate.getOwnerUUID())
                || source.getFace() != candidate.getFace()) {
            return false;
        }
        boolean sourceStorage = source instanceof ZipperStorageEntity;
        boolean candidateStorage = candidate instanceof ZipperStorageEntity;
        if (sourceStorage != candidateStorage) {
            return false;
        }
        Direction face = source.getFace();
        BlockPos sourceAnchor = source.getAnchor();
        BlockPos candidateAnchor = candidate.getAnchor();
        int dx = sourceAnchor.getX() - candidateAnchor.getX();
        int dy = sourceAnchor.getY() - candidateAnchor.getY();
        int dz = sourceAnchor.getZ() - candidateAnchor.getZ();
        int normalOffset = dx * face.getStepX() + dy * face.getStepY() + dz * face.getStepZ();
        // A merged wall/floor path is rendered on one surface plane. If anchors are
        // one block deeper or higher, that single plane can be behind the other wall
        // and the connected zipper appears to vanish. Only merge coplanar zippers.
        if ((dx == 0 && dy == 0 && dz == 0) || normalOffset != 0) {
            return false;
        }

        Direction localX = face.getAxis() == Direction.Axis.Y ? Direction.EAST : face.getCounterClockWise();
        int tangentU = dx * localX.getStepX() + dz * localX.getStepZ();
        int tangentV = face.getAxis() == Direction.Axis.Y
                ? dz * (face == Direction.UP ? -1 : 1)
                : dy;
        // Tile links are strictly one block to the side on the same surface.
        // Distance-based links bridged empty cells and merged unrelated zippers.
        return Math.abs(tangentU) + Math.abs(tangentV) == 1;
    }

    private static int mergeRenderPriority(ZipperEntity zipper) {
        return switch (zipper.getState()) {
            case STATE_OPENING -> 3;
            case STATE_TUNNEL, STATE_STORAGE_OPEN -> 2;
            case STATE_CLOSING -> 1;
            default -> 0;
        };
    }

    public record MergeRenderLayout(float centerX, float centerY, float width, float length,
                                    float rotationDegrees) {}

    public boolean isHiddenFromLocalPlayer() {
        return false;
    }

    protected boolean localPlayerHasStand() {
        if (!this.level().isClientSide()) {
            return true;
        }
        Player player = ClientUtil.getPlayer();
        return player != null && ((StandUser) player).roundabout$hasAStand();
    }

    public Direction getFace() {
        return Direction.from3DDataValue(this.entityData.get(FACE));
    }

    public boolean isOwner(Entity entity) {
        UUID owner = getOwnerUUID();
        return entity != null && owner != null && owner.equals(entity.getUUID());
    }

    public Player getOwnerPlayer() {
        UUID owner = getOwnerUUID();
        if (owner == null) {
            return null;
        }
        return this.level().getPlayerByUUID(owner);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (getFace().getAxis() == Direction.Axis.Y) {
            return EntityDimensions.scalable(1.0F, 0.2F);
        }
        return EntityDimensions.scalable(0.9F, isMini() ? 1.0F : 2.0F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (FACE.equals(key) || MINI.equals(key)) {
            this.refreshDimensions();
        }
        super.onSyncedDataUpdated(key);
    }

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
        return new Vec3(cx + face.getStepX() * 0.51D, baseY + 0.5D,
                cz + face.getStepZ() * 0.51D);
    }

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
        return !this.isRemoved() && (getState() == STATE_NEUTRAL || getState() == STATE_LATCH_READY);
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

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (getState() == STATE_LATCH_READY) {
            if (!this.level().isClientSide()) {
                triggerLatchRide(player);
            }
            return InteractionResult.SUCCESS;
        }
        if (!isOwner(player) || getState() != STATE_NEUTRAL) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        startHiding(player);
        return InteractionResult.CONSUME;
    }

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
            startOpening(OPEN_ACTION_TUNNEL);
            return true;
        }
        if (getState() == STATE_LATCH_READY && attacker instanceof LivingEntity living) {
            triggerLatchRide(living);
            return true;
        }
        return false;
    }

    private void triggerLatchRide(Entity rider) {
        if (this.level().isClientSide() || getState() != STATE_LATCH_READY
                || rider == null || rider == this || !rider.isAlive()
                || rider instanceof StandEntity || rider instanceof ZipperEntity) {
            return;
        }
        latchRiderId = rider.getId();
        setState(STATE_LATCH_RIDING);
        this.level().playSound(null, this.blockPosition(), ModSounds.SF_HIT_ZIPPER_EVENT,
                SoundSource.PLAYERS, 0.9F, 1.0F);
        sparkBurst(this.position().add(0.0D, 0.5D, 0.0D), 10);
    }

    /** True while this zipper is an open (or opening) passage the owner can close: not storage, not mini. */
    public boolean isOpenPortal() {
        if (this.isRemoved() || this instanceof ZipperStorageEntity || isMini()) {
            return false;
        }
        int state = getState();
        return state == STATE_TUNNEL || state == STATE_OPENING;
    }

    /** Area the owner has to aim at to close the portal by hand. */
    public AABB getPortalAimBox() {
        return getBoundingBox().inflate(0.3D);
    }

    /**
     * Closes an open portal right now, ignoring the remaining tunnel time. The tunnel blocks keep their own restore
     * timer, so they are shortened to the zipper's closing animation; otherwise the passage would stay open.
     */
    public boolean forceClosePortal() {
        if (this.level().isClientSide() || !isOpenPortal()) {
            return false;
        }
        if (this.level() instanceof ServerLevel serverLevel) {
            //computed before close() so the whole connected group is still found
            for (BlockPos pos : computeMergedTunnel()) {
                if (serverLevel.getBlockEntity(pos) instanceof ZipperSpaceBlockEntity space) {
                    space.closeSoon(CLOSE_ANIM_TICKS - ZipperSpaceBlockEntity.RETURN_ANIMATION_TICKS);
                }
            }
        }
        close();
        return true;
    }

    private void startOpening(int action) {
        List<ZipperEntity> group = getUnzipGroup();
        if (group.stream().anyMatch(zipper -> zipper.getState() != STATE_NEUTRAL)) {
            return;
        }
        for (ZipperEntity zipper : group) {
            zipper.pendingCapture = null;
            zipper.pendingOpenAction = action;
            zipper.setState(STATE_OPENING);
        }
        this.level().playSound(null, this.blockPosition(), ModSounds.SF_ZIPPER_BREAK_EVENT,
                SoundSource.PLAYERS, 0.9F, 1.0F);
    }

    private void startHiding(Player owner) {
        Vec3 hide = getHidePos();
        owner.teleportTo(hide.x, hide.y, hide.z);
        owner.setDeltaMovement(Vec3.ZERO);
        owner.hurtMarked = true;
        owner.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
        MobEffectInstance existingSpeed = owner.getEffect(MobEffects.MOVEMENT_SPEED);
        savedSpeedEffect = existingSpeed == null ? null : new MobEffectInstance(existingSpeed);
        savedWasSwimming = owner.isSwimming();
        owner.setSwimming(true);
        owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 2, false, false, false));
        if (!level().isClientSide() && owner instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            var abilities = serverPlayer.getAbilities();
            savedMayfly = abilities.mayfly;
            savedFlying = abilities.flying;
            savedFlyingSpeed = abilities.getFlyingSpeed();
            savedAbilities = true;
            abilities.mayfly = false;
            abilities.flying = false;
            serverPlayer.onUpdateAbilities();
        }
        if (owner instanceof IStickyFingersDismemberment state) state.roundabout$setInZipperDimension(true);
        lastHealth = owner.getHealth();
        lastFood = owner.getFoodData().getFoodLevel();
        lastSaturation = owner.getFoodData().getSaturationLevel();
        setState(STATE_HIDING);
        this.level().playSound(null, this.blockPosition(), ModSounds.SF_ZIPPER_BREAK_EVENT,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        sparkBurst(hide.add(0, 1.0D, 0), 14);
    }

    private void tickHiding() {
        Player owner = getOwnerPlayer();
        if (owner == null || !owner.isAlive()) {
            endHiding(owner);
            return;
        }
        owner.setSwimming(true);
        applyZipperGlide(owner);
        if (!owner.hasEffect(MobEffects.INVISIBILITY) || owner.getEffect(MobEffects.INVISIBILITY).getDuration() < 10) {
            owner.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
        }
        if (!owner.hasEffect(MobEffects.MOVEMENT_SPEED) || owner.getEffect(MobEffects.MOVEMENT_SPEED).getDuration() < 10) {
            owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 2, false, false, false));
        }
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

    /** Applies a short, inertial slide while the owner is hidden in the zipper. */
    private void applyZipperGlide(Player owner) {
        if (this.level().isClientSide() && owner != ClientUtil.getPlayer()) return;

        float inputX = owner.xxa;
        float inputZ = owner.zza;
        float inputLength = (float) Math.sqrt(inputX * inputX + inputZ * inputZ);
        if (inputLength > 1.0F) {
            inputX /= inputLength;
            inputZ /= inputLength;
        }

        Vec3 velocity = owner.getDeltaMovement();
        Vec3 horizontal = new Vec3(velocity.x, 0.0D, velocity.z);
        Vec3 nextHorizontal;
        if (inputLength > 0.01F) {
            double yaw = Math.toRadians(owner.getYRot());
            double sin = Math.sin(yaw);
            double cos = Math.cos(yaw);
            Vec3 direction = new Vec3(inputX * cos - inputZ * sin, 0.0D,
                    inputZ * cos + inputX * sin).normalize();
            Vec3 desired = direction.scale(0.34D);
            nextHorizontal = horizontal.lerp(desired, 0.28D);
        } else {
            // Keep a little momentum after the player lets go, then ease to a stop.
            nextHorizontal = horizontal.scale(0.78D);
        }

        double speed = nextHorizontal.horizontalDistance();
        if (speed > 0.34D) nextHorizontal = nextHorizontal.scale(0.34D / speed);
        owner.setDeltaMovement(nextHorizontal.x, velocity.y, nextHorizontal.z);
        if (!this.level().isClientSide()) owner.hurtMarked = true;
    }

    /** Leaves the zipper state at the player's current location, nudging only if their box is inside tunnel blocks. */
    public static boolean exitOwnerAtCurrentPosition(Player owner) {
        if (!(owner.level() instanceof ServerLevel serverLevel)) return false;

        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof ZipperEntity zipper && zipper.getState() == STATE_HIDING
                    && zipper.isOwner(owner)) {
                zipper.endHiding(owner, findEmergencyExit(serverLevel, owner));
                return true;
            }
        }
        // Keep the player's current location. Only nudge them to the nearest
        // adjacent clear spot if their bounding box is still inside tunnel cells.
        exitOwnerFromZipperDimension(owner, findEmergencyExit(serverLevel, owner));
        return true;
    }

    private static Vec3 findEmergencyExit(ServerLevel level, Player owner) {
        Vec3 origin = owner.position();
        AABB currentBox = owner.getBoundingBox();
        if (!touchesZipperSpace(level, currentBox) && level.noCollision(owner, currentBox)) return origin;
        for (int radius = 1; radius <= 12; radius++) {
            Vec3 closestExit = null;
            double closestDistance = Double.MAX_VALUE;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz)) != radius) continue;
                        Vec3 candidate = origin.add(dx, dy, dz);
                        AABB box = owner.getBoundingBox().move(candidate.subtract(owner.position()));
                        if (touchesZipperSpace(level, box) || !level.noCollision(owner, box)) continue;
                        double distance = candidate.distanceToSqr(origin);
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            closestExit = candidate;
                        }
                    }
                }
            }
            if (closestExit != null) return closestExit;
        }
        return origin.add(0.0D, 8.0D, 0.0D);
    }

    private static boolean touchesZipperSpace(Level level, AABB bounds) {
        BlockPos min = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        BlockPos max = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(pos).getBlock() instanceof ZipperSpaceBlock) return true;
        }
        return false;
    }

    private void endHiding(Player owner) {
        restoreHidingOwner(owner);
        close();
    }

    private void restoreHidingOwner(Player owner) {
        if (owner != null) {
            if (owner instanceof IStickyFingersDismemberment state) state.roundabout$setInZipperDimension(false);
            owner.setInvisible(false);
            owner.setNoGravity(false);
            owner.removeEffect(MobEffects.INVISIBILITY);
            owner.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            owner.removeEffect(MobEffects.JUMP);
            owner.removeEffect(MobEffects.MOVEMENT_SPEED);
            owner.setSwimming(savedWasSwimming);
            owner.setPose(savedWasSwimming ? Pose.SWIMMING : Pose.STANDING);
            if (savedSpeedEffect != null) {
                owner.addEffect(new MobEffectInstance(savedSpeedEffect));
            }
            if (savedAbilities && owner instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                var abilities = serverPlayer.getAbilities();
                abilities.mayfly = savedMayfly;
                abilities.flying = savedFlying && savedMayfly;
                abilities.setFlyingSpeed(savedFlyingSpeed);
                serverPlayer.onUpdateAbilities();
            }
        }
        savedAbilities = false;
        savedSpeedEffect = null;
    }

    private void endHiding(Player owner, Vec3 exit) {
        if (owner != null && exit != null) {
            owner.teleportTo(exit.x, exit.y, exit.z);
            owner.setNoGravity(false);
            owner.setDeltaMovement(0.0D, -0.08D, 0.0D);
            owner.fallDistance = 0.0F;
            owner.hurtMarked = true;
        }
        endHiding(owner);
    }

    public static void exitOwnerFromZipperDimension(Player owner, Vec3 exit) {
        if (!(owner.level() instanceof ServerLevel serverLevel)) return;
        ZipperEntity hidingZipper = null;
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof ZipperEntity zipper && zipper.getState() == STATE_HIDING
                    && owner.getUUID().equals(zipper.getOwnerUUID())) {
                hidingZipper = zipper;
                break;
            }
        }
        if (hidingZipper != null) {
            hidingZipper.endHiding(owner, exit);
            return;
        }
        owner.teleportTo(exit.x, exit.y, exit.z);
        owner.setNoGravity(false);
        owner.setDeltaMovement(0.0D, -0.08D, 0.0D);
        owner.fallDistance = 0.0F;
        owner.hurtMarked = true;
        if (owner instanceof IStickyFingersDismemberment state) state.roundabout$setInZipperDimension(false);
        owner.setInvisible(false);
        owner.removeEffect(MobEffects.INVISIBILITY);
        owner.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        owner.removeEffect(MobEffects.JUMP);
        owner.removeEffect(MobEffects.MOVEMENT_SPEED);
        owner.setSwimming(false);
        owner.setPose(Pose.STANDING);
    }

    public static boolean canZip(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && !(state.getBlock() instanceof ZipperSpaceBlock)
                && state.getDestroySpeed(level, pos) >= 0
                && !state.hasBlockEntity()
                && state.getFluidState().isEmpty();
    }

    public static boolean canZipOnFace(Level level, BlockPos pos, Direction face) {
        BlockState state = level.getBlockState(pos);
        return canZip(level, pos) && !state.canBeReplaced() && state.isFaceSturdy(level, pos, face);
    }

    public List<BlockPos> computeTunnel() {
        List<BlockPos> list = new ArrayList<>();
        Direction inward = getFace().getOpposite();
        int maxDepth = getFace().getAxis() == Direction.Axis.Y
                ? TUNNEL_DEPTH_FLOOR : TUNNEL_DEPTH_WALL;
        for (int d = 0; d < maxDepth; d++) {
            list.add(anchor.relative(inward, d));
        }
        return list;
    }

    private List<BlockPos> computeMergedTunnel() {
        List<ZipperEntity> connected = getConnectedZippers();
        if (connected.size() <= 1) {
            return computeTunnel();
        }

        // Connections affect the zipper artwork, not blocks between or around it.
        // Each connected zipper opens only its own anchor block.
        Set<BlockPos> positions = new LinkedHashSet<>();
        for (ZipperEntity zipper : connected) {
            positions.add(zipper.getAnchor());
        }
        return new ArrayList<>(positions);
    }

    private void startTunnel(Player owner) {

        if (getState() != STATE_OPENING) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!serverLevel.getGameRules().getBoolean(ModGamerules.ROUNDABOUT_STAND_GRIEFING)) {
            owner.displayClientMessage(Component.translatable("roundabout.zipper.no_griefing"), true);
            close();
            return;
        }
        int opened = 0;
        for (BlockPos pos : computeMergedTunnel()) {
            if (!canZip(serverLevel, pos)) {
                continue;
            }
            BlockState original = serverLevel.getBlockState(pos);
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, original),
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 8, 0.3D, 0.3D, 0.3D, 0.05D);
            serverLevel.setBlock(pos, ModBlocks.ZIPPER_SPACE.defaultBlockState(), 3);
            BlockEntity be = serverLevel.getBlockEntity(pos);
            if (be instanceof ZipperSpaceBlockEntity space) {

                space.setup(original, TUNNEL_TICKS + CLOSE_ANIM_TICKS
                                - ZipperSpaceBlockEntity.RETURN_ANIMATION_TICKS,
                        isOnEntrancePlane(pos, anchor, getFace()), getFace());
            }
            opened++;
        }
        if (opened == 0) {
            owner.displayClientMessage(Component.translatable("roundabout.zipper.nothing_to_unzip"), true);
            close();
            return;
        }
        releaseOwnerSwallowedAtExit(owner);
        setUnzipGroupState(STATE_TUNNEL, 0);
        serverLevel.playSound(null, this.blockPosition(), ModSounds.SF_ZIPPER_BREAK_EVENT,
                SoundSource.PLAYERS, 1.2F, 0.9F);
        sparkBurst(this.position().add(0, 1.0D, 0), 24);
    }

    private static boolean isOnEntrancePlane(BlockPos pos, BlockPos anchor, Direction face) {
        int projection = (pos.getX() - anchor.getX()) * face.getStepX()
                + (pos.getY() - anchor.getY()) * face.getStepY()
                + (pos.getZ() - anchor.getZ()) * face.getStepZ();
        return projection == 0;
    }

    private Entity findApproachingEntity(Player owner) {
        BlockPos front = anchor.relative(getFace());
        AABB opening = new AABB(front).inflate(0.45D);
        return this.level().getEntitiesOfClass(Entity.class, opening, entity ->
                        entity.isAlive() && entity != owner && !isOwner(entity)
                                && !(entity instanceof StandEntity)
                                && !(entity instanceof ZipperEntity))
                .stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    private void startCaptureOpening(Entity target) {
        if (target == null || !target.isAlive() || target instanceof StandEntity
                || target instanceof ZipperEntity || isOwner(target)) {
            return;
        }
        List<ZipperEntity> group = getUnzipGroup();
        if (group.stream().anyMatch(zipper -> zipper.getState() != STATE_NEUTRAL)) {
            return;
        }
        for (ZipperEntity zipper : group) {
            zipper.pendingCapture = null;
            zipper.pendingOpenAction = OPEN_ACTION_SWALLOW;
            zipper.setState(STATE_OPENING);
        }
        this.pendingCapture = target.getUUID();
        this.level().playSound(null, this.blockPosition(), ModSounds.SF_HIT_ZIPPER_EVENT,
                SoundSource.PLAYERS, 0.9F, 0.95F);
    }

    private void swallowPendingEntity() {
        if (!(this.level() instanceof ServerLevel serverLevel) || pendingCapture == null) {
            return;
        }
        Entity target = serverLevel.getEntity(pendingCapture);
        pendingCapture = null;
        // Recheck before damage: stands must never be pulled into or hurt by the portal.
        if (target == null || !target.isAlive() || target instanceof StandEntity
                || target instanceof ZipperEntity || isOwner(target)) {
            close();
            return;
        }
        if (target instanceof LivingEntity living && living.isAlive()) {
            int marks = getOwnerTagLevel(living);
            DamageSource entryDamage = getOwnerPlayer() != null
                    ? ModDamageTypes.of(serverLevel, ModDamageTypes.STAND, this, getOwnerPlayer())
                    : ModDamageTypes.of(serverLevel, ModDamageTypes.STAND, this);
            living.hurt(entryDamage, PowersStickyFingers.applyZipModeDamage(getOwnerPlayer(), getZipperDamage(marks)));
        }
        if (target != null && target.isAlive() && swallowed.size() < MAX_SWALLOWED && swallowEntity(target)) {
            retainAfterClose = true;
            for (ZipperEntity zipper : getUnzipGroup()) {
                zipper.retainAfterClose = true;
            }
            sparkBurst(target.position().add(0, Math.max(0.25D, target.getBbHeight() * 0.5D), 0), 12);
            serverLevel.playSound(null, this.blockPosition(), ModSounds.SF_ZIPPER_BREAK_EVENT,
                    SoundSource.PLAYERS, 1.0F, 0.9F);
        }
        close();
    }

    private boolean swallowEntity(Entity entity) {
        if (!entity.isAlive() || isOwner(entity) || entity instanceof StandEntity || entity instanceof ZipperEntity
                || swallowed.size() >= MAX_SWALLOWED || swallowed.contains(entity.getUUID())) {
            return false;
        }

        UUID id = entity.getUUID();
        swallowed.add(id);
        int tagLevel = entity instanceof LivingEntity living ? getOwnerTagLevel(living) : 0;
        boolean wasInZipperDimension = entity instanceof IStickyFingersDismemberment state
                && state.roundabout$isInZipperDimension();
        swallowedStates.put(id, new StoredEntityState(entity.isNoGravity(), entity.isInvisible(),
                entity.isInvulnerable(), entity instanceof Mob mob && mob.isNoAi(), tagLevel,
                wasInZipperDimension));
        entity.setNoGravity(true);
        entity.setInvisible(true);
        if (entity instanceof LivingEntity living) {
            living.setInvulnerable(true);
            if (living instanceof Mob mob) {
                mob.setTarget(null);
                mob.setLastHurtByMob(null);
                mob.setNoAi(true);
            }
        }
        if (entity instanceof IStickyFingersDismemberment zipperState) {
            zipperState.roundabout$setInZipperDimension(true);
        }
        Vec3 hide = getHidePos();
        entity.moveTo(hide.x, hide.y, hide.z);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hurtMarked = true;
        return true;
    }

    private int getOwnerTagLevel(LivingEntity target) {
        Player owner = getOwnerPlayer();
        if (owner instanceof StandUser standUser
                && standUser.roundabout$getStandPowers() instanceof PowersStickyFingers powers) {
            return powers.getTagLevel(target);
        }
        return 0;
    }

    private float getZipperDamage(int tagLevel) {
        return 2.0F + Mth.clamp(tagLevel, 0, PowersStickyFingers.TAG_MAX_LEVEL) * 2.0F;
    }

    private void holdSwallowed() {
        if (swallowed.isEmpty() || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 hide = getHidePos();
        for (UUID id : swallowed) {
            Entity entity = serverLevel.getEntity(id);
            if (entity != null && entity.isAlive()) {
                entity.setNoGravity(true);
                entity.setInvisible(true);
                if (entity instanceof LivingEntity living) {
                    living.setInvulnerable(true);
                    if (living instanceof Mob mob) mob.setNoAi(true);
                }
                entity.moveTo(hide.x, hide.y, hide.z);
                entity.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    public void releaseSwallowed() {
        releaseSwallowedAt(computeReleasePosition(), getOwnerPlayer(), true, getFace());
    }

    private Vec3 computeReleasePosition() {
        Direction face = getFace();
        return computeSurfacePos().add(face.getStepX() * 0.85D,
                face.getStepY() * 0.85D, face.getStepZ() * 0.85D);
    }

    private void releaseOwnerSwallowedAtExit(Player owner) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        UUID ownerId = getOwnerUUID();
        if (ownerId == null) return;

        // Release only from a different zipper, after this destination has completed
        // its opening animation and created the tunnel. Each stored entity is then
        // emitted separately with a short interval, like a zipper opening in sequence.
        Vec3 exit = computeReleasePosition();

        int remainingCooldown = Math.max(0, PowersStickyFingers.UNZIP_COOLDOWN - this.tickCount);
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof ZipperEntity source && source != this
                    && ownerId.equals(source.getOwnerUUID()) && !source.swallowed.isEmpty()) {
                source.beginStaggeredRelease(exit, owner, getFace(), remainingCooldown);
            }
        }
    }

    private void beginStaggeredRelease(Vec3 exit, Player owner, Direction exitFace, int cooldownTicks) {
        if (swallowed.isEmpty() || pendingReleaseExit != null) return;
        this.pendingReleaseExit = exit;
        this.pendingReleaseFace = exitFace;
        this.pendingReleaseOwner = owner == null ? null : owner.getUUID();

        this.pendingReleaseTicks = cooldownTicks;
    }

    private void tickStaggeredRelease() {
        if (pendingReleaseExit == null || !(this.level() instanceof ServerLevel serverLevel)) return;
        if (pendingReleaseTicks > 0) {
            pendingReleaseTicks--;
            return;
        }

        UUID id = swallowed.isEmpty() ? null : swallowed.remove(0);
        if (id != null) {
            Player owner = pendingReleaseOwner == null ? null : serverLevel.getPlayerByUUID(pendingReleaseOwner);
            releaseOneSwallowed(id, pendingReleaseExit, owner, true, pendingReleaseFace);
            swallowedStates.remove(id);
            pendingReleaseTicks = 10;
        }
        if (swallowed.isEmpty()) {
            pendingReleaseExit = null;
            pendingReleaseOwner = null;
            pendingReleaseTicks = 0;
        }
    }

    private void releaseOneSwallowed(UUID id, Vec3 exit, Player owner, boolean damageOnExit, Direction exitFace) {
        if (!(this.level() instanceof ServerLevel serverLevel)) return;
        Entity entity = serverLevel.getEntity(id);
        if (entity == null || !entity.isAlive()) return;

        StoredEntityState flags = swallowedStates.getOrDefault(id, StoredEntityState.DEFAULT);
        entity.moveTo(exit.x, exit.y, exit.z, entity.getYRot(), entity.getXRot());
        // Every entity ejected from the zipper returns to ordinary world gravity,
        // even if it had noGravity enabled before it was swallowed.
        entity.setNoGravity(false);
        entity.setInvisible(flags.invisible());
        if (entity instanceof LivingEntity living) {
            living.setInvulnerable(false);
            if (living instanceof Mob mob) mob.setNoAi(flags.noAi());
            if (damageOnExit) {
                DamageSource source = owner != null
                        ? ModDamageTypes.of(serverLevel, ModDamageTypes.STAND, this, owner)
                        : ModDamageTypes.of(serverLevel, ModDamageTypes.STAND, this);
                living.hurt(source, PowersStickyFingers.applyZipModeDamage(owner,
                        getZipperDamage(flags.tagLevel())));
            }
            living.setInvulnerable(flags.invulnerable());
        }
        if (entity instanceof IStickyFingersDismemberment zipperState) {
            zipperState.roundabout$setInZipperDimension(false);
        }
        Vec3 motion = new Vec3(exitFace.getStepX() * 0.45D,
                Math.min(-0.08D, exitFace.getStepY() * 0.45D + 0.2D), exitFace.getStepZ() * 0.45D);
        if (entity instanceof LivingEntity living) living.fallDistance = 0.0F;
        entity.setDeltaMovement(motion);
        entity.hurtMarked = true;
        sparkBurst(entity.position().add(0, Math.max(0.25D, entity.getBbHeight() * 0.5D), 0), 10);
        serverLevel.playSound(null, this.blockPosition(), ModSounds.SF_ZIPPER_BREAK_EVENT,
                SoundSource.PLAYERS, 0.9F, 1.05F);
    }

    private void releaseSwallowedAt(Vec3 exit, Player owner, boolean damageOnExit, Direction exitFace) {
        if (swallowed.isEmpty()) {
            swallowedStates.clear();
            return;
        }
        for (UUID id : new ArrayList<>(swallowed)) {
            releaseOneSwallowed(id, exit, owner, damageOnExit, exitFace);
        }
        swallowed.clear();
        swallowedStates.clear();
        pendingReleaseExit = null;
        pendingReleaseOwner = null;
        pendingReleaseTicks = 0;
    }

    private record StoredEntityState(boolean noGravity, boolean invisible, boolean invulnerable, boolean noAi,
                                     int tagLevel, boolean inZipperDimension) {
        private static final StoredEntityState DEFAULT = new StoredEntityState(false, false, false, false, 0, false);
    }

    public void close() {
        if (this.level().isClientSide()) {
            return;
        }
        if (this.level() instanceof ServerLevel serverLevel) {
            for (BlockPos pos : computeMergedTunnel()) {
                if (serverLevel.getBlockEntity(pos) instanceof ZipperSpaceBlockEntity space) {
                    space.closeSoon(CLOSE_ANIM_TICKS - ZipperSpaceBlockEntity.RETURN_ANIMATION_TICKS);
                }
            }
        }
        boolean changed = false;
        for (ZipperEntity zipper : getUnzipGroup()) {
            zipper.pendingCapture = null;
            if (zipper.getState() != STATE_CLOSING) {
                zipper.pendingOpenAction = 0;
                zipper.setState(STATE_CLOSING);
                changed = true;
            }
        }
        if (changed) {
            this.level().playSound(null, this.blockPosition(), ModSounds.SF_ZIPPER_BREAK_EVENT,
                    SoundSource.PLAYERS, 0.8F, 0.85F);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            if (getState() == STATE_HIDING) {
                Player owner = getOwnerPlayer();
                if (owner != null && owner == ClientUtil.getPlayer()) {
                    owner.setSwimming(true);
                    applyZipperGlide(owner);
                }
            }
            tickClientParticles();
            return;
        }
        stateTicks++;
        serverTick();
    }

    protected void serverTick() {
        holdSwallowed();
        tickStaggeredRelease();
        int currentState = getState();
        if (currentState != STATE_TUNNEL && currentState != STATE_CLOSING
                && currentState != STATE_HIDING
                && !canZipOnFace(this.level(), getAnchor(), getFace())) {
            discard();
            return;
        }
        switch (getState()) {
            case STATE_NEUTRAL -> {
                Player owner = getOwnerPlayer();
                if (owner == null || !owner.isAlive()) {
                    if (!swallowed.isEmpty()) releaseSwallowed();
                    close();
                    return;
                }
                if (stateTicks > NEUTRAL_LIFE && swallowed.isEmpty()) {
                    close();
                    return;
                }
                if (swallowed.size() < MAX_SWALLOWED) {
                    Entity approaching = findApproachingEntity(owner);
                    if (approaching != null) startCaptureOpening(approaching);
                }
            }
            case STATE_OPENING -> {
                if (stateTicks >= OPEN_ANIM_TICKS) {
                    int action = pendingOpenAction;
                    pendingOpenAction = 0;
                    Player owner = getOwnerPlayer();
                    if (owner == null || !owner.isAlive()) {
                        close();
                    } else if (action == OPEN_ACTION_TUNNEL) {
                        startTunnel(owner);
                    } else if (action == OPEN_ACTION_SWALLOW) {
                        if (pendingCapture != null) swallowPendingEntity();
                    } else if (action != 0) {
                        close();
                    }
                }
            }
            case STATE_HIDING -> tickHiding();
            case STATE_LATCH_ARMING -> {
                if (stateTicks >= LATCH_ARM_TICKS) {
                    setState(STATE_LATCH_READY);
                    this.level().playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                            SoundSource.PLAYERS, 1.0F, 1.4F);
                    sparkBurst(this.position().add(0.0D, 0.5D, 0.0D), 6);
                }
            }
            case STATE_LATCH_READY -> {
                if (stateTicks >= LATCH_READY_TICKS) {
                    setState(STATE_LATCH_RETRACTING);
                    break;
                }
                AABB triggerArea = getBoundingBox().inflate(0.35D, 0.2D, 0.35D);
                for (Entity candidate : this.level().getEntitiesOfClass(Entity.class, triggerArea,
                        entity -> entity != this && entity.isAlive() && !(entity instanceof StandEntity)
                                && !(entity instanceof ZipperEntity))) {
                    triggerLatchRide(candidate);
                    break;
                }
            }
            case STATE_LATCH_RIDING -> tickLatchRide();
            case STATE_LATCH_RETRACTING -> {
                if (stateTicks >= CLOSE_ANIM_TICKS) {
                    discard();
                }
            }
            case STATE_TUNNEL -> {
                if (stateTicks >= TUNNEL_TICKS) {
                    close();
                }
            }
            case STATE_MINING -> {
                if (stateTicks == biteTick) {
                    bite();
                }
                if (stateTicks >= mineDelay) {
                    performMine();
                }
            }
            case STATE_CLOSING -> {
                if (stateTicks >= CLOSE_ANIM_TICKS) {
                    if (retainAfterClose) {
                        retainAfterClose = false;
                        setState(STATE_NEUTRAL);
                    } else {
                        this.discard();
                    }
                }
            }
        }
    }

    private void tickLatchRide() {
        Entity rider = this.level().getEntity(latchRiderId);
        if (rider == null || !rider.isAlive() || stateTicks > LATCH_RIDE_TICKS) {
            if (rider != null && rider.isAlive()) {
                rider.setDeltaMovement(latchVelocity.scale(0.65D));
                rider.hurtMarked = true;
            }
            latchRiderId = -1;
            setState(STATE_LATCH_RETRACTING);
            return;
        }
        Vec3 rideMovement = getLatchRideMovement(rider);
        rider.setDeltaMovement(rideMovement);
        if (rider instanceof LivingEntity living) living.fallDistance = 0.0F;
        rider.hurtMarked = true;
        this.setPos(this.position().add(rideMovement));
        this.setDeltaMovement(Vec3.ZERO);
    }

    private Vec3 getLatchRideMovement(Entity rider) {
        AABB body = rider.getBoundingBox();
        if (this.level().noCollision(rider, body.move(latchVelocity))) {
            return latchVelocity;
        }

        // When the path meets a stair or wall, add a short upward zip so the rider clears it.
        // Test headroom first to avoid pushing the rider through ceilings.
        for (double lift = 0.25D; lift <= 0.75D; lift += 0.25D) {
            Vec3 stepMotion = new Vec3(latchVelocity.x,
                    Math.max(0.0D, latchVelocity.y) + lift, latchVelocity.z);
            if (this.level().noCollision(rider, body.move(stepMotion))) {
                return stepMotion;
            }
        }
        Vec3 upwardStep = new Vec3(latchVelocity.x, 0.42D, latchVelocity.z);
        if (this.level().noCollision(rider, body.move(0.0D, 0.42D, 0.0D))) {
            return upwardStep;
        }
        return latchVelocity;
    }

    public static final float BITE_DAMAGE = 2.0F;

    private void bite() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos front = anchor.relative(getFace());
        Player owner = getOwnerPlayer();
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, new AABB(front).inflate(0.15D))) {
            if (!target.isAlive() || isOwner(target) || target instanceof StandEntity) {
                continue;
            }
            DamageSource source = owner != null
                    ? ModDamageTypes.of(serverLevel, ModDamageTypes.STAND, this, owner)
                    : ModDamageTypes.of(serverLevel, ModDamageTypes.STAND, this);
            if (target.hurt(source, PowersStickyFingers.applyZipModeDamage(owner, BITE_DAMAGE))) {
                if (owner instanceof StandUser standUser
                        && standUser.roundabout$getStandPowers() instanceof PowersStickyFingers powers) {
                    powers.addTag(target);
                }
                Vec3 motion = target.getDeltaMovement();
                target.setDeltaMovement(motion.x, Math.max(motion.y, 0.4D), motion.z);
                target.hurtMarked = true;
            }
        }
        serverLevel.playSound(null, front, ModSounds.SF_HIT_ZIPPER_EVENT,
                SoundSource.PLAYERS, 0.6F, 1.1F);
    }

    private void performMine() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState state = serverLevel.getBlockState(anchor);
        if (!canZip(serverLevel, anchor)) {
            if (vanishOnMine) discard();
            else close();
            return;
        }
        float hardness = Math.max(0.0F, state.getDestroySpeed(serverLevel, anchor));
        float damage = Mth.clamp(1.0F + hardness * 1.2F, 1.0F, 5.0F);
        Direction face = getFace();
        Vec3 center = Vec3.atCenterOf(anchor).add(face.getStepX() * 0.55D, face.getStepY() * 0.55D, face.getStepZ() * 0.55D);
        if (serverLevel.getGameRules().getBoolean(ModGamerules.ROUNDABOUT_STAND_GRIEFING)) {
            // The break debris launches on the same tick the zipper finishes
            // opening, including each zipper in the outer 7x7 ring.
            ZipperDebrisEntity.launch(serverLevel, ownerUUID, state, center, face, damage);
            serverLevel.destroyBlock(anchor, false);
        } else {
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    center.x, center.y, center.z, 8, 0.25D, 0.25D, 0.25D, 0.05D);
        }
        if (vanishOnMine) discard();
        else close();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide() && reason.shouldDestroy()) {
            releaseSwallowed();
            if (getState() == STATE_HIDING) {
                Player owner = getOwnerPlayer();
                if (owner != null) {
                    restoreHidingOwner(owner);
                }
            }
        }
        super.remove(reason);
    }

    private void sparkBurst(Vec3 pos, int count) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, pos.x, pos.y, pos.z,
                    count, 0.3D, 0.5D, 0.3D, 0.02D);
        }
    }

    private void tickClientParticles() {
        int state = getState();
        boolean sparkling = state == STATE_OPENING || state == STATE_TUNNEL || state == STATE_STORAGE_OPEN
                || state == STATE_LATCH_READY || state == STATE_LATCH_RIDING
                || (state == STATE_MINING && this.tickCount >= getStateStart() + OPEN_ANIM_TICKS);
        if (!sparkling || this.tickCount % 3 != 0 || isHiddenFromLocalPlayer()) {
            return;
        }
        Direction face = getFace();
        double along = (this.random.nextDouble() - 0.5D) * 0.5D;
        double height = face.getAxis() == Direction.Axis.Y ? 0.05D : this.random.nextDouble() * (isMini() ? 1.0D : 2.0D);
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

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("owner")) {
            ownerUUID = tag.getUUID("owner");
        }
        anchor = new BlockPos(tag.getInt("ax"), tag.getInt("ay"), tag.getInt("az"));
        baseY = tag.getInt("baseY");
        this.entityData.set(OWNER, Optional.ofNullable(ownerUUID));
        this.entityData.set(ANCHOR, anchor);
        this.entityData.set(BASE_Y, baseY);
        stateTicks = tag.getInt("stateTicks");
        pendingOpenAction = tag.getInt("pendingOpenAction");
        pendingCapture = tag.hasUUID("pendingCapture") ? tag.getUUID("pendingCapture") : null;
        retainAfterClose = tag.getBoolean("retainAfterClose");
        pendingReleaseExit = tag.contains("releaseX")
                ? new Vec3(tag.getDouble("releaseX"), tag.getDouble("releaseY"), tag.getDouble("releaseZ")) : null;
        pendingReleaseFace = Direction.from3DDataValue(tag.getInt("releaseFace"));
        pendingReleaseOwner = tag.hasUUID("releaseOwner") ? tag.getUUID("releaseOwner") : null;
        pendingReleaseTicks = tag.getInt("releaseTicks");
        latchVelocity = new Vec3(tag.getDouble("latchVelocityX"), tag.getDouble("latchVelocityY"),
                tag.getDouble("latchVelocityZ"));
        latchRiderId = tag.getInt("latchRiderId");
        mineDelay = tag.getInt("mineDelay");
        vanishOnMine = tag.getBoolean("vanishOnMine");
        this.entityData.set(MINI, tag.getBoolean("mini"));
        this.entityData.set(FACE, tag.getInt("face"));
        this.entityData.set(STATE, tag.getInt("state"));
        swallowed.clear();
        ListTag list = tag.getList("swallowed", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < list.size(); i++) {
            swallowed.add(NbtUtils.loadUUID(list.get(i)));
        }
        swallowedStates.clear();
        ListTag stateList = tag.getList("swallowedStates", Tag.TAG_COMPOUND);
        for (int i = 0; i < stateList.size(); i++) {
            CompoundTag state = stateList.getCompound(i);
            if (state.hasUUID("id")) {
                swallowedStates.put(state.getUUID("id"), new StoredEntityState(
                        state.getBoolean("noGravity"), state.getBoolean("invisible"),
                        state.getBoolean("invulnerable"), state.getBoolean("noAi"), state.getInt("tagLevel"),
                        state.getBoolean("inZipperDimension")));
            }
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
        tag.putInt("pendingOpenAction", pendingOpenAction);
        if (pendingCapture != null) tag.putUUID("pendingCapture", pendingCapture);
        tag.putBoolean("retainAfterClose", retainAfterClose);
        if (pendingReleaseExit != null) {
            tag.putDouble("releaseX", pendingReleaseExit.x);
            tag.putDouble("releaseY", pendingReleaseExit.y);
            tag.putDouble("releaseZ", pendingReleaseExit.z);
        }
        tag.putInt("releaseFace", pendingReleaseFace.get3DDataValue());
        if (pendingReleaseOwner != null) tag.putUUID("releaseOwner", pendingReleaseOwner);
        tag.putInt("releaseTicks", pendingReleaseTicks);
        tag.putDouble("latchVelocityX", latchVelocity.x);
        tag.putDouble("latchVelocityY", latchVelocity.y);
        tag.putDouble("latchVelocityZ", latchVelocity.z);
        tag.putInt("latchRiderId", latchRiderId);
        tag.putInt("mineDelay", mineDelay);
        tag.putBoolean("vanishOnMine", vanishOnMine);
        tag.putBoolean("mini", isMini());
        tag.putInt("face", this.entityData.get(FACE));
        tag.putInt("state", getState());
        ListTag list = new ListTag();
        for (UUID id : swallowed) {
            list.add(NbtUtils.createUUID(id));
        }
        tag.put("swallowed", list);
        ListTag stateList = new ListTag();
        for (UUID id : swallowed) {
            StoredEntityState state = swallowedStates.getOrDefault(id, StoredEntityState.DEFAULT);
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            entry.putBoolean("noGravity", state.noGravity());
            entry.putBoolean("invisible", state.invisible());
            entry.putBoolean("invulnerable", state.invulnerable());
            entry.putBoolean("noAi", state.noAi());
            entry.putInt("tagLevel", state.tagLevel());
            entry.putBoolean("inZipperDimension", state.inZipperDimension());
            stateList.add(entry);
        }
        tag.put("swallowedStates", stateList);
    }
}
