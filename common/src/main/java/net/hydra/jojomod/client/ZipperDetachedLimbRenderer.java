package net.hydra.jojomod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.hydra.jojomod.mixin.access.models.AccessModelPart;
import net.hydra.jojomod.stand.powers.PowersStickyFingers;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ZipperDetachedLimbRenderer {
    private static final ResourceLocation ZIPPER_TEXTURE =
            new ResourceLocation("roundabout", "textures/entity/sticky_fingers/zipper_body_overlay_high.png");
    private static final Map<Integer, Map<String, DroppedLimb>> DROPPED_LIMBS = new HashMap<>();

    private ZipperDetachedLimbRenderer() { }

    public static void capture(LivingEntity entity, ModelPart[] parts, String[] names, ResourceLocation skin) {
        if (parts == null || names == null || skin == null || !(entity.level() instanceof ClientLevel level)) return;
        Map<String, DroppedLimb> entityLimbs = DROPPED_LIMBS.computeIfAbsent(entity.getId(), ignored -> new HashMap<>());
        Vec3 forward = new Vec3(entity.getLookAngle().x, 0.0D, entity.getLookAngle().z);
        if (forward.lengthSqr() < 1.0E-5D) forward = new Vec3(0.0D, 0.0D, 1.0D);
        else forward = forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        for (int i = 0; i < parts.length && i < names.length; i++) {
            ModelPart part = parts[i];
            String name = names[i];
            if (part == null || name == null || !isPhysicalPart(name) || entityLimbs.containsKey(name)) continue;

            double side = name.contains("left") ? -1.0D : name.contains("right") ? 1.0D : 0.0D;
            double forwardSpacing = isHead(name) ? -0.30D : isArm(name) ? -0.18D : 0.18D;
            float startHeight = Math.max(0.18F, entity.getBbHeight() * (isHead(name) ? 0.88F : isArm(name) ? 0.60F : 0.28F));
            Vec3 origin = entity.position().add(right.scale(side * 0.10D)).add(forward.scale(forwardSpacing * 0.25D));
            Vec3 start = origin.add(0.0D, startHeight, 0.0D);
            int seed = entity.getId() * 37 + name.hashCode();
            java.util.Random random = new java.util.Random(seed);
            Vec3 initialVelocity = right.scale(side * (0.05D + random.nextDouble() * 0.07D))
                    .add(forward.scale((random.nextDouble() - 0.5D) * 0.12D))
                    .add(0.0D, 0.12D + random.nextDouble() * 0.08D, 0.0D);
            ModelPart detachedCopy = copyPart(part);
            entityLimbs.put(name, new DroppedLimb(level, entity.getUUID(), entity.getId(), name,
                    detachedCopy, skin, start, initialVelocity, entity.getYRot(),
                    Math.floorMod(seed, 360), (float) (random.nextDouble() * 20.0D - 10.0D),
                    (float) (random.nextDouble() * 20.0D - 10.0D), level.getGameTime()));
        }
    }

    public static void renderWorld(PoseStack poseStack, float partialTick, Camera camera,
                                   MultiBufferSource buffers, ClientLevel level) {
        if (level == null || DROPPED_LIMBS.isEmpty()) return;
        Vec3 cameraPos = camera.getPosition();
        long gameTime = level.getGameTime();
        var entityIterator = DROPPED_LIMBS.entrySet().iterator();
        while (entityIterator.hasNext()) {
            Map.Entry<Integer, Map<String, DroppedLimb>> entityEntry = entityIterator.next();
            Entity target = level.getEntity(entityEntry.getKey());
            if (!(target instanceof LivingEntity living) || !living.getUUID().equals(entityEntry.getValue().values()
                    .stream().findFirst().map(DroppedLimb::ownerUuid).orElse(null))
                    || PowersStickyFingers.getClientDismembermentMask(living) == 0) {
                entityIterator.remove();
                continue;
            }

            var limbIterator = entityEntry.getValue().entrySet().iterator();
            while (limbIterator.hasNext()) {
                DroppedLimb limb = limbIterator.next().getValue();
                if (limb.level() != level) {
                    limbIterator.remove();
                    continue;
                }
                Vec3 toCamera = limb.position().subtract(cameraPos);
                if (toCamera.lengthSqr() > 96.0D * 96.0D) continue;

                simulatePhysics(limb, level, gameTime);
                Vec3 position = limb.position();
                int packedLight = getLight(level, position.add(0.0D, 0.05D, 0.0D));
                renderLimb(limb, position, cameraPos,
                        poseStack, buffers, packedLight);
            }
            if (entityEntry.getValue().isEmpty()) entityIterator.remove();
        }
    }

    private static void renderLimb(DroppedLimb limb, Vec3 position, Vec3 cameraPos,
                                   PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        ModelPart part = limb.part();
        float oldX = part.x, oldY = part.y, oldZ = part.z;
        float oldXRot = part.xRot, oldYRot = part.yRot, oldZRot = part.zRot;
        boolean oldVisible = part.visible;
        poseStack.pushPose();
        try {
            Vec3 relative = position.subtract(cameraPos);
            poseStack.translate(relative.x, relative.y, relative.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(limb.bodyYaw() + limb.yaw()));
            poseStack.mulPose(Axis.XP.rotationDegrees(limb.pitch()));
            poseStack.mulPose(Axis.ZP.rotationDegrees(limb.roll()));
            part.setPos(0.0F, 0.0F, 0.0F);
            part.xRot = 0.0F;
            part.yRot = 0.0F;
            part.zRot = 0.0F;
            part.visible = true;
            part.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(limb.skin())),
                    packedLight, OverlayTexture.NO_OVERLAY);
            part.render(poseStack, buffers.getBuffer(RenderType.entityTranslucent(ZIPPER_TEXTURE)),
                    packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 0.82F, 0.22F, 0.96F);
        } finally {
            poseStack.popPose();
            part.setPos(oldX, oldY, oldZ);
            part.xRot = oldXRot;
            part.yRot = oldYRot;
            part.zRot = oldZRot;
            part.visible = oldVisible;
        }
    }

    private static void simulatePhysics(DroppedLimb limb, ClientLevel level, long gameTime) {
        long elapsed = Math.min(8L, Math.max(0L, gameTime - limb.lastPhysicsTick()));
        if (elapsed == 0L) return;
        limb.setLastPhysicsTick(gameTime);

        for (int tick = 0; tick < elapsed; tick++) {
            Vec3 position = limb.position();
            double halfHeight = limb.halfHeight();
            BlockPos supportPos = BlockPos.containing(position.x, position.y - halfHeight - 0.035D, position.z);
            VoxelShape support = level.getBlockState(supportPos).getCollisionShape(level, supportPos);
            if (limb.isResting()) {
                if (!support.isEmpty() && position.y - halfHeight <= supportPos.getY() + support.max(net.minecraft.core.Direction.Axis.Y) + 0.06D) {
                    continue;
                }
                limb.setResting(false);
            }

            Vec3 velocity = limb.velocity();
            velocity = new Vec3(velocity.x * 0.985D, velocity.y - 0.08D, velocity.z * 0.985D);
            Vec3 next = position.add(velocity);
            BlockPos below = BlockPos.containing(next.x, next.y - halfHeight, next.z);
            VoxelShape floor = level.getBlockState(below).getCollisionShape(level, below);
            if (!floor.isEmpty()) {
                double floorTop = below.getY() + floor.max(net.minecraft.core.Direction.Axis.Y) + halfHeight;
                if (velocity.y <= 0.0D && next.y <= floorTop) {
                    next = new Vec3(next.x, floorTop, next.z);
                    velocity = new Vec3(velocity.x * 0.58D, Math.abs(velocity.y) * 0.16D, velocity.z * 0.58D);
                    limb.setPitch(limb.pitch() + (float) (velocity.x * 85.0D));
                    limb.setRoll(limb.roll() - (float) (velocity.z * 85.0D));
                    if (velocity.y < 0.035D && velocity.horizontalDistanceSqr() < 0.0025D) {
                        limb.setResting(true);
                        velocity = Vec3.ZERO;
                    }
                }
            }
            if (next.y < level.getMinBuildHeight() + halfHeight) {
                next = new Vec3(next.x, level.getMinBuildHeight() + halfHeight, next.z);
                velocity = Vec3.ZERO;
                limb.setResting(true);
            }
            limb.setPosition(next);
            limb.setVelocity(velocity);
            if (!limb.isResting()) {
                limb.setYaw(limb.yaw() + limb.yawSpeed());
                limb.setPitch(limb.pitch() + limb.pitchSpeed());
                limb.setRoll(limb.roll() + limb.rollSpeed());
                limb.setYawSpeed(limb.yawSpeed() * 0.96F);
                limb.setPitchSpeed(limb.pitchSpeed() * 0.96F);
                limb.setRollSpeed(limb.rollSpeed() * 0.96F);
            }
        }
    }

    public static void clear(int entityId) {
        DROPPED_LIMBS.remove(entityId);
    }

    private static ModelPart copyPart(ModelPart source) {
        AccessModelPart access = (AccessModelPart) (Object) source;
        Map<String, ModelPart> children = new HashMap<>();
        access.roundabout$getChildren().forEach((name, child) -> children.put(name, copyPart(child)));
        ModelPart copy = new ModelPart(new ArrayList<>(access.roundabout$getCubes()), children);
        copy.setPos(source.x, source.y, source.z);
        copy.xRot = source.xRot;
        copy.yRot = source.yRot;
        copy.zRot = source.zRot;
        copy.visible = true;
        copy.skipDraw = source.skipDraw;
        return copy;
    }

    private static int getLight(ClientLevel level, Vec3 position) {
        BlockPos blockPos = BlockPos.containing(position);
        return LightTexture.pack(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, blockPos),
                level.getBrightness(net.minecraft.world.level.LightLayer.SKY, blockPos));
    }

    private static boolean isPhysicalPart(String name) {
        String part = normalize(name);
        if (part.contains("jacket")) return false;
        return isHead(part) || isArm(part) || isLeg(part);
    }

    private static boolean isHead(String part) {
        return part.equals("head") || part.equals("skull") || part.equals("hat") || part.contains("headwear");
    }

    private static boolean isArm(String part) {
        return part.contains("arm") || part.contains("hand") || part.contains("frontleg")
                || part.contains("foreleg") || part.contains("wings") || part.contains("sleeve");
    }

    private static boolean isLeg(String part) {
        return part.contains("leg") || part.contains("hind") || part.contains("foot")
                || part.contains("feet") || part.contains("paw") || part.contains("pants");
    }

    private static String normalize(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private static final class DroppedLimb {
        private final ClientLevel level;
        private final UUID ownerUuid;
        private final int ownerId;
        private final String name;
        private final ModelPart part;
        private final ResourceLocation skin;
        private final float bodyYaw;
        private final float halfHeight;
        private Vec3 position;
        private Vec3 velocity;
        private float yaw;
        private float pitch;
        private float roll;
        private float yawSpeed;
        private float pitchSpeed;
        private float rollSpeed;
        private long lastPhysicsTick;
        private boolean resting;

        private DroppedLimb(ClientLevel level, UUID ownerUuid, int ownerId, String name, ModelPart part,
                            ResourceLocation skin, Vec3 position, Vec3 velocity, float bodyYaw,
                            float yaw, float pitchSpeed, float rollSpeed, long lastPhysicsTick) {
            this.level = level;
            this.ownerUuid = ownerUuid;
            this.ownerId = ownerId;
            this.name = name;
            this.part = part;
            this.skin = skin;
            this.position = position;
            this.velocity = velocity;
            this.bodyYaw = bodyYaw;
            this.yaw = yaw;
            this.pitchSpeed = pitchSpeed;
            this.rollSpeed = rollSpeed;
            this.lastPhysicsTick = lastPhysicsTick;
            this.halfHeight = name.contains("leg") || name.contains("hind") || name.contains("foot") ? 0.28F
                    : name.contains("head") ? 0.22F : 0.20F;
            this.yawSpeed = pitchSpeed;
            this.pitch = 0.0F;
            this.roll = 0.0F;
        }

        private ClientLevel level() { return level; }
        private UUID ownerUuid() { return ownerUuid; }
        private ModelPart part() { return part; }
        private ResourceLocation skin() { return skin; }
        private Vec3 position() { return position; }
        private void setPosition(Vec3 position) { this.position = position; }
        private Vec3 velocity() { return velocity; }
        private void setVelocity(Vec3 velocity) { this.velocity = velocity; }
        private float bodyYaw() { return bodyYaw; }
        private float halfHeight() { return halfHeight; }
        private float yaw() { return yaw; }
        private void setYaw(float yaw) { this.yaw = yaw; }
        private float pitch() { return pitch; }
        private void setPitch(float pitch) { this.pitch = pitch; }
        private float roll() { return roll; }
        private void setRoll(float roll) { this.roll = roll; }
        private float yawSpeed() { return yawSpeed; }
        private void setYawSpeed(float yawSpeed) { this.yawSpeed = yawSpeed; }
        private float pitchSpeed() { return pitchSpeed; }
        private void setPitchSpeed(float pitchSpeed) { this.pitchSpeed = pitchSpeed; }
        private float rollSpeed() { return rollSpeed; }
        private void setRollSpeed(float rollSpeed) { this.rollSpeed = rollSpeed; }
        private long lastPhysicsTick() { return lastPhysicsTick; }
        private void setLastPhysicsTick(long tick) { this.lastPhysicsTick = tick; }
        private boolean isResting() { return resting; }
        private void setResting(boolean resting) { this.resting = resting; }
    }
}
