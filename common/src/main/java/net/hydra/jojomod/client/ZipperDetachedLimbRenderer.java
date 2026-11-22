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
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Captures dismembered meshes once, then renders them at fixed world positions. */
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
        double groundY = findGroundY(entity);

        for (int i = 0; i < parts.length && i < names.length; i++) {
            ModelPart part = parts[i];
            String name = names[i];
            if (part == null || name == null || !isPhysicalPart(name) || entityLimbs.containsKey(name)) continue;

            double side = name.contains("left") ? -1.0D : name.contains("right") ? 1.0D : 0.0D;
            double sideSpacing = isLeg(name) ? 0.22D : 0.30D;
            double forwardSpacing = isHead(name) ? -0.30D : isArm(name) ? -0.18D : 0.18D;
            Vec3 groundOffset = right.scale(side * sideSpacing).add(forward.scale(forwardSpacing));
            float startHeight = Math.max(0.18F, entity.getBbHeight() * (isHead(name) ? 0.88F : isArm(name) ? 0.60F : 0.28F));
            Vec3 origin = entity.position().add(right.scale(side * 0.10D)).add(forward.scale(forwardSpacing * 0.25D));
            Vec3 start = origin.add(0.0D, startHeight, 0.0D);
            Vec3 end = new Vec3(entity.getX() + groundOffset.x, groundY + 0.025D,
                    entity.getZ() + groundOffset.z);
            int seed = entity.getId() * 37 + name.hashCode();
            ModelPart detachedCopy = copyPart(part);
            entityLimbs.put(name, new DroppedLimb(level, entity.getUUID(), entity.getId(), name,
                    detachedCopy, skin, start, end, entity.getYRot(), Math.floorMod(seed, 360), level.getGameTime()));
        }
    }

    /** Called by the common LevelRenderer mixin after terrain and entities have rendered. */
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
                Vec3 toCamera = limb.end().subtract(cameraPos);
                if (toCamera.lengthSqr() > 96.0D * 96.0D) continue;

                float fallProgress = Mth.clamp((gameTime - limb.startedAt() + partialTick) / 8.0F, 0.0F, 1.0F);
                Vec3 position = limb.start().lerp(limb.end(), fallProgress);
                int packedLight = getLight(level, position.add(0.0D, 0.05D, 0.0D));
                renderLimb(limb, position, cameraPos, fallProgress,
                        poseStack, buffers, packedLight);
            }
            if (entityEntry.getValue().isEmpty()) entityIterator.remove();
        }
    }

    private static void renderLimb(DroppedLimb limb, Vec3 position, Vec3 cameraPos, float fallProgress,
                                   PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        ModelPart part = limb.part();
        float oldX = part.x, oldY = part.y, oldZ = part.z;
        float oldXRot = part.xRot, oldYRot = part.yRot, oldZRot = part.zRot;
        boolean oldVisible = part.visible;
        poseStack.pushPose();
        try {
            Vec3 relative = position.subtract(cameraPos);
            poseStack.translate(relative.x, relative.y, relative.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(limb.bodyYaw()));
            poseStack.mulPose(Axis.YP.rotationDegrees(limb.randomYaw()));
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F * fallProgress));
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

    private static double findGroundY(LivingEntity entity) {
        BlockPos feet = entity.blockPosition();
        for (int depth = 0; depth <= 8; depth++) {
            BlockPos supportPos = feet.below(depth);
            VoxelShape shape = entity.level().getBlockState(supportPos)
                    .getCollisionShape(entity.level(), supportPos);
            if (!shape.isEmpty()) return supportPos.getY() + shape.max(Direction.Axis.Y);
        }
        return entity.getY();
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

    private record DroppedLimb(ClientLevel level, UUID ownerUuid, int ownerId, String name,
                               ModelPart part, ResourceLocation skin, Vec3 start, Vec3 end,
                               float bodyYaw, float randomYaw, long startedAt) { }
}
