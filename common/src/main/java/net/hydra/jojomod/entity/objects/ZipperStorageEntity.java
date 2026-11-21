package net.hydra.jojomod.entity.objects;

import net.hydra.jojomod.entity.ModEntities;
import net.hydra.jojomod.entity.stand.StandEntity;
import net.hydra.jojomod.event.ModParticles;
import net.hydra.jojomod.event.powers.StandUser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**Zipper Storage (Block + Z on a block).
 * The block you were facing stays exactly as it was, but a zipper is stuck to the face you aimed at and holds
 * one row (9 slots) of items. Only stand users can see the zipper (when they are close), and only they can
 * open it with a right click: the zipper unzips while the menu is open.
 * Attacking the zipper pops it: every item flies out and the block goes back to normal.
 * If the block itself disappears, the items are released as well.*/
public class ZipperStorageEntity extends Entity {

    public static final int SLOTS = 9;
    /**The hitbox is a small cube centered on the face of the block.*/
    public static final float SIZE = 0.7F;

    protected static final EntityDataAccessor<Integer> FACE = SynchedEntityData.defineId(ZipperStorageEntity.class,
            EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Boolean> OPEN = SynchedEntityData.defineId(ZipperStorageEntity.class,
            EntityDataSerializers.BOOLEAN);

    private UUID ownerUUID = null;
    private BlockPos anchor = BlockPos.ZERO;
    private String blockId = "";
    private final ZipperContainer container = new ZipperContainer(this);
    private int viewers = 0;

    /**Client only: 0 = closed, 1 = fully unzipped (the renderer smooths it with the previous value).*/
    private float openAnim = 0.0F;
    private float prevOpenAnim = 0.0F;

    public ZipperStorageEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**@param anchor the block that became storage
     * @param face the face of that block that was aimed at, the zipper is drawn there*/
    public static ZipperStorageEntity create(Level level, LivingEntity owner, BlockPos anchor, Direction face) {
        ZipperStorageEntity storage = new ZipperStorageEntity(ModEntities.ZIPPER_STORAGE, level);
        storage.ownerUUID = owner == null ? null : owner.getUUID();
        storage.anchor = anchor;
        storage.blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(anchor).getBlock()).toString();
        storage.entityData.set(FACE, face.get3DDataValue());
        Vec3 c = storage.computeCenter();
        storage.setPos(c.x, c.y - SIZE / 2.0D, c.z);
        return storage;
    }

    /**The storage zipper on that block, if there is one.*/
    public static ZipperStorageEntity findAt(Level level, BlockPos pos) {
        for (ZipperStorageEntity storage : level.getEntitiesOfClass(ZipperStorageEntity.class,
                new AABB(pos).inflate(1.0D))) {
            if (storage.anchor.equals(pos) && !storage.isRemoved()) {
                return storage;
            }
        }
        return null;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(FACE, Direction.NORTH.get3DDataValue());
        this.entityData.define(OPEN, false);
    }

    public Direction getFace() {
        return Direction.from3DDataValue(this.entityData.get(FACE));
    }

    public boolean isOpen() {
        return this.entityData.get(OPEN);
    }

    public BlockPos getAnchor() {
        return anchor;
    }

    public SimpleContainer getContainer() {
        return container;
    }

    public float getOpenAnim() {
        return openAnim;
    }

    public float getPrevOpenAnim() {
        return prevOpenAnim;
    }

    /**The block in front of the zipper (used by the renderer to read the light level).*/
    public BlockPos getFrontPos() {
        return anchor.relative(getFace());
    }

    /**Middle of the zipper: the face of the block, a hair outwards.*/
    public Vec3 computeCenter() {
        Direction face = getFace();
        return new Vec3(anchor.getX() + 0.5D + face.getStepX() * 0.515D,
                anchor.getY() + 0.5D + face.getStepY() * 0.515D,
                anchor.getZ() + 0.5D + face.getStepZ() * 0.515D);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(SIZE, SIZE);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
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

    public static boolean isStandUser(Player player) {
        return player.isSpectator() || !((StandUser) player).roundabout$getStandDisc().isEmpty();
    }

    /**-------------------------------------------------------------------
     * INTERACTION
     * ------------------------------------------------------------------*/

    /**Right click: open the storage (stand users only).*/
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!isStandUser(player)) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) ->
                new ChestMenu(MenuType.GENERIC_9x1, id, inventory, container, 1),
                Component.translatable("roundabout.zipper_storage.title")));
        return InteractionResult.CONSUME;
    }

    /**Attacked (vanilla hit or stand punch): the zipper pops and the items fly out.*/
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide() || this.isRemoved()) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker == null) {
            attacker = source.getDirectEntity();
        }
        if (attacker instanceof StandEntity stand && stand.getUser() != null) {
            attacker = stand.getUser();
        }
        if (attacker instanceof Player player && isStandUser(player)) {
            pop();
            return true;
        }
        return false;
    }

    /**Throws every item out and turns the block back to normal.*/
    public void pop() {
        if (this.level() instanceof ServerLevel serverLevel) {
            Vec3 c = computeCenter();
            Containers.dropContents(serverLevel, BlockPos.containing(c), container);
            container.clearContent();
            serverLevel.sendParticles(ModParticles.ZIPPER_SPARK, c.x, c.y, c.z, 14, 0.3D, 0.3D, 0.3D, 0.03D);
            serverLevel.playSound(null, BlockPos.containing(c), SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0F, 1.2F);
            serverLevel.playSound(null, BlockPos.containing(c), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.BLOCKS, 0.8F, 1.0F);
        }
        this.discard();
    }

    private void updateOpen() {
        boolean open = viewers > 0;
        if (open != isOpen()) {
            this.entityData.set(OPEN, open);
            this.level().playSound(null, this.blockPosition(), open ? SoundEvents.ARMOR_EQUIP_CHAIN : SoundEvents.SHEEP_SHEAR,
                    SoundSource.BLOCKS, 0.7F, open ? 0.9F : 1.3F);
        }
    }

    /**-------------------------------------------------------------------
     * LIFECYCLE
     * ------------------------------------------------------------------*/

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            prevOpenAnim = openAnim;
            openAnim = isOpen() ? Math.min(1.0F, openAnim + 0.2F) : Math.max(0.0F, openAnim - 0.2F);
            return;
        }
        //the block under the zipper has to still be the same block
        if (this.tickCount % 10 == 0) {
            BlockState state = this.level().getBlockState(anchor);
            String now = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if (state.isAir() || !now.equals(blockId)) {
                pop();
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide() && reason == RemovalReason.KILLED && !container.isEmpty()) {
            Containers.dropContents(this.level(), this.blockPosition(), container);
            container.clearContent();
        }
        super.remove(reason);
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
        blockId = tag.getString("block");
        this.entityData.set(FACE, tag.getInt("face"));
        container.fromTag(tag.getList("items", 10));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUUID != null) {
            tag.putUUID("owner", ownerUUID);
        }
        tag.putInt("ax", anchor.getX());
        tag.putInt("ay", anchor.getY());
        tag.putInt("az", anchor.getZ());
        tag.putString("block", blockId);
        tag.putInt("face", this.entityData.get(FACE));
        tag.put("items", container.createTag());
    }

    /**The one row of items. It tells the entity when somebody opens or closes it.*/
    private static class ZipperContainer extends SimpleContainer {
        private final ZipperStorageEntity storage;

        ZipperContainer(ZipperStorageEntity storage) {
            super(SLOTS);
            this.storage = storage;
        }

        @Override
        public boolean stillValid(Player player) {
            return !storage.isRemoved() && player.distanceToSqr(storage) <= 64.0D;
        }

        @Override
        public void startOpen(Player player) {
            storage.viewers++;
            storage.updateOpen();
        }

        @Override
        public void stopOpen(Player player) {
            storage.viewers = Math.max(0, storage.viewers - 1);
            storage.updateOpen();
        }
    }
}
