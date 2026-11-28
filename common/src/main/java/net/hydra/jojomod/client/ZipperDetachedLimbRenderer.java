package net.hydra.jojomod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.hydra.jojomod.mixin.access.models.AccessModelPart;
import net.hydra.jojomod.event.ModParticles;
import net.hydra.jojomod.stand.powers.PowersStickyFingers;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ZipperDetachedLimbRenderer {
    private static final ResourceLocation ZIPPER_TEXTURE =
            new ResourceLocation("roundabout", "textures/entity/sticky_fingers/zipper_body_overlay_high.png");
    private static final Map<Integer, Map<String, DroppedLimb>> DROPPED_LIMBS = new HashMap<>();
    private static final Map<Integer, Integer> CONSUMED_LIMBS = new HashMap<>();
    private static final Map<Integer, ZipAnimation> ZIP_ANIMATIONS = new HashMap<>();
    private static final Map<Integer, Map<Integer, GraftedLimb>> GRAFTED_LIMBS = new HashMap<>();

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
            int limbMask = partMaskForName(normalize(name));
            int stillMissing = PowersStickyFingers.getClientDismembermentMask(entity);
            if ((stillMissing & limbMask) == 0) continue;
            int consumedMask = CONSUMED_LIMBS.getOrDefault(entity.getId(), 0);
            consumedMask &= stillMissing;
            if (consumedMask == 0) CONSUMED_LIMBS.remove(entity.getId());
            else CONSUMED_LIMBS.put(entity.getId(), consumedMask);
            if ((consumedMask & limbMask) != 0) continue;

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
        if (level == null) return;
        capturePlayerLimbs(level);
        Vec3 cameraPos = camera.getPosition();
        long gameTime = level.getGameTime();
        renderGraftZipEffects(level, gameTime);
        renderGrafts(poseStack, cameraPos, buffers, level);
        renderAnimations(level, gameTime);
        if (DROPPED_LIMBS.isEmpty()) return;
        var entityIterator = DROPPED_LIMBS.entrySet().iterator();
        while (entityIterator.hasNext()) {
            Map.Entry<Integer, Map<String, DroppedLimb>> entityEntry = entityIterator.next();
            Entity target = level.getEntity(entityEntry.getKey());
            if (!(target instanceof LivingEntity living) || !living.getUUID().equals(entityEntry.getValue().values()
                    .stream().findFirst().map(DroppedLimb::ownerUuid).orElse(null))
                    || PowersStickyFingers.getClientDismembermentMask(living) == 0) {
                entityIterator.remove();
                if (target != null) CONSUMED_LIMBS.remove(target.getId());
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

    /** First-person rendering can skip the player's body renderer, so capture missing limbs here too. */
    private static void capturePlayerLimbs(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        for (AbstractClientPlayer player : level.players()) {
            if (PowersStickyFingers.getClientDismembermentMask(player) == 0) continue;
            var renderer = minecraft.getEntityRenderDispatcher().getRenderer(player);
            if (!(renderer instanceof PlayerRenderer playerRenderer)) continue;
            PlayerModel<AbstractClientPlayer> model = playerRenderer.getModel();
            ModelPart[] parts = {model.head, model.hat, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg};
            String[] names = {"head", "hat", "leftArm", "rightArm", "leftLeg", "rightLeg"};
            capture(player, parts, names, player.getSkinTextureLocation());
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
        CONSUMED_LIMBS.remove(entityId);
    }

    /** Returns the closest visible arm/leg model piece under the player's crosshair. */
    public static DroppedLimbHit rayPick(LivingEntity player, double reach, int compatibleMask) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(reach));
        double closest = Double.MAX_VALUE;
        DroppedLimbHit result = null;
        for (Map<String, DroppedLimb> limbs : DROPPED_LIMBS.values()) {
            for (DroppedLimb limb : limbs.values()) {
                int mask = partMaskForName(normalize(limb.name()));
                // The user can reclaim their own missing limb or graft a compatible limb from another entity.
                if (limb.level() != player.level() || (mask & compatibleMask) == 0
                        || (mask & (PowersStickyFingers.ZIPPER_PART_LEFT_ARM
                        | PowersStickyFingers.ZIPPER_PART_RIGHT_ARM | PowersStickyFingers.ZIPPER_PART_LEFT_LEG
                        | PowersStickyFingers.ZIPPER_PART_RIGHT_LEG)) == 0) continue;
                // Generous interaction hitbox around the rendered model; it affects targeting only.
                double halfHeight = Math.max(0.42D, limb.halfHeight() + 0.16D);
                AABB bounds = new AABB(limb.position().x - 0.48D, limb.position().y - halfHeight,
                        limb.position().z - 0.48D, limb.position().x + 0.48D,
                        limb.position().y + halfHeight, limb.position().z + 0.48D);
                var intersection = bounds.clip(start, end);
                if (intersection.isEmpty()) continue;
                double distance = start.distanceToSqr(intersection.get());
                if (distance < closest) {
                    closest = distance;
                    result = new DroppedLimbHit(limb.ownerId(),
                            Integer.lowestOneBit(mask & compatibleMask), intersection.get());
                }
            }
        }
        return result;
    }

    public record DroppedLimbHit(int ownerId, int partMask, Vec3 hitPosition) { }

    public static void attachGraftedLimb(int recipientId, int donorId, int donorPart, int recipientPart) {
        int validParts = PowersStickyFingers.ZIPPER_PART_LEFT_ARM | PowersStickyFingers.ZIPPER_PART_RIGHT_ARM
                | PowersStickyFingers.ZIPPER_PART_LEFT_LEG | PowersStickyFingers.ZIPPER_PART_RIGHT_LEG;
        if (Integer.bitCount(recipientPart) != 1 || (recipientPart & validParts) == 0) return;
        Map<String, DroppedLimb> donorLimbs = DROPPED_LIMBS.get(donorId);
        if (donorLimbs == null) return;
        DroppedLimb source = donorLimbs.values().stream()
                .filter(limb -> (partMaskForName(normalize(limb.name())) & donorPart) != 0)
                .findFirst().orElse(null);
        if (source == null) return;
        donorLimbs.remove(source.name());
        if (donorLimbs.isEmpty()) DROPPED_LIMBS.remove(donorId);
        CONSUMED_LIMBS.merge(donorId, donorPart, (oldMask, newMask) -> oldMask | newMask);
        GRAFTED_LIMBS.computeIfAbsent(recipientId, ignored -> new HashMap<>())
                .put(recipientPart, new GraftedLimb(source, source.level().getGameTime()));
        playZipperAnimation(recipientId, recipientPart);
    }

    public static void clearGrafts(int entityId) {
        GRAFTED_LIMBS.remove(entityId);
        ZIP_ANIMATIONS.remove(entityId);
    }

    public static void playZipperAnimation(int entityId, int partMask) {
        if (partMask != 0) ZIP_ANIMATIONS.put(entityId, new ZipAnimation(partMask, Long.MIN_VALUE));
    }

    private static void renderGraftZipEffects(ClientLevel level, long gameTime) {
        if (gameTime % 6L != 0L) return;
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !(living instanceof net.hydra.jojomod.access.IStickyFingersDismemberment state)) continue;
            int mask = state.roundabout$getStickyFingersGraftMask();
            double yaw = Math.toRadians(living.getYRot());
            Vec3 left = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
            for (int bit : new int[]{2, 4, 8, 16}) {
                if ((mask & bit) == 0) continue;
                boolean isLeft = bit == 2 || bit == 8;
                boolean arm = bit == 2 || bit == 4;
                Vec3 pos = living.position().add(left.scale((isLeft ? 1.0D : -1.0D) * (arm ? 0.27D : 0.12D)))
                        .add(0.0D, living.getBbHeight() * (arm ? 0.70D : 0.38D), 0.0D);
                level.addParticle(ModParticles.ZIPPER_SPARK, pos.x, pos.y, pos.z, 0.0D, 0.012D, 0.0D);
            }
        }
    }

    private static void renderGrafts(PoseStack poseStack, Vec3 cameraPos, MultiBufferSource buffers, ClientLevel level) {
        var graftIterator = GRAFTED_LIMBS.entrySet().iterator();
        while (graftIterator.hasNext()) {
            Map.Entry<Integer, Map<Integer, GraftedLimb>> entry = graftIterator.next();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living) || entry.getValue().isEmpty()) {
                graftIterator.remove();
                continue;
            }
            Vec3 modelLeft = new Vec3(-Math.cos(Math.toRadians(living.getYRot())), 0.0D,
                    -Math.sin(Math.toRadians(living.getYRot())));
            var partIterator = entry.getValue().entrySet().iterator();
            while (partIterator.hasNext()) {
                Map.Entry<Integer, GraftedLimb> graft = partIterator.next();
                int bit = graft.getKey();
                if (living instanceof net.hydra.jojomod.access.IStickyFingersDismemberment state
                        && (state.roundabout$getStickyFingersGraftMask() & bit) == 0) {
                    partIterator.remove();
                    continue;
                }
                DroppedLimb source = graft.getValue().source();
                if (source.level() != level) {
                    partIterator.remove();
                    continue;
                }
                boolean left = bit == PowersStickyFingers.ZIPPER_PART_LEFT_ARM
                        || bit == PowersStickyFingers.ZIPPER_PART_LEFT_LEG;
                boolean arm = bit == PowersStickyFingers.ZIPPER_PART_LEFT_ARM
                        || bit == PowersStickyFingers.ZIPPER_PART_RIGHT_ARM;
                Vec3 anchor = living.position().add(modelLeft.scale((left ? 1.0D : -1.0D)
                        * (arm ? 0.30D : 0.13D)))
                        .add(0.0D, living.getBbHeight() * (arm ? 0.69D : 0.40D), 0.0D);
                Vec3 relative = anchor.subtract(cameraPos);
                ModelPart part = source.part();
                float x = part.x, y = part.y, z = part.z;
                float xr = part.xRot, yr = part.yRot, zr = part.zRot;
                boolean visible = part.visible;
                poseStack.pushPose();
                try {
                    poseStack.translate(relative.x, relative.y, relative.z);
                    poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - living.getYRot()));
                    part.setPos(0.0F, 0.0F, 0.0F);
                    part.xRot = part.yRot = part.zRot = 0.0F;
                    part.visible = true;
                    int light = getLight(level, anchor);
                    part.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(source.skin())),
                            light, OverlayTexture.NO_OVERLAY);
                    part.render(poseStack, buffers.getBuffer(RenderType.entityTranslucent(ZIPPER_TEXTURE)),
                            light, OverlayTexture.NO_OVERLAY, 1.0F, 0.82F, 0.22F, 0.92F);
                } finally {
                    poseStack.popPose();
                    part.setPos(x, y, z);
                    part.xRot = xr;
                    part.yRot = yr;
                    part.zRot = zr;
                    part.visible = visible;
                }
            }
            if (entry.getValue().isEmpty()) graftIterator.remove();
        }
    }

    private static void renderAnimations(ClientLevel level, long gameTime) {
        var iterator = ZIP_ANIMATIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, ZipAnimation> entry = iterator.next();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living)) { iterator.remove(); continue; }
            ZipAnimation animation = entry.getValue();
            if (animation.lastParticleTick() == gameTime) continue;
            // Store the start tick on first render, then emit a short zipper-tooth trail for six ticks.
            long start = animation.startTick() == Long.MIN_VALUE ? gameTime : animation.startTick();
            int age = (int) (gameTime - start);
            if (age >= 7) { iterator.remove(); continue; }
            animation = new ZipAnimation(animation.mask(), start, gameTime);
            entry.setValue(animation);
            int mask = animation.mask();
            for (int bit = 1; bit <= 16; bit <<= 1) {
                if ((mask & bit) == 0) continue;
                double side = (bit == PowersStickyFingers.ZIPPER_PART_LEFT_ARM
                        || bit == PowersStickyFingers.ZIPPER_PART_LEFT_LEG) ? 1.0D : -1.0D;
                double height = bit == PowersStickyFingers.ZIPPER_PART_HEAD ? living.getBbHeight() * 0.82D
                        : (bit & (PowersStickyFingers.ZIPPER_PART_LEFT_ARM
                        | PowersStickyFingers.ZIPPER_PART_RIGHT_ARM)) != 0 ? living.getBbHeight() * 0.62D
                        : living.getBbHeight() * 0.30D;
                double progress = age / 7.0D;
                double yaw = Math.toRadians(living.getYRot());
                Vec3 modelLeft = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
                Vec3 pos = living.position().add(modelLeft.scale(side * 0.25D * (1.0D - progress)))
                        .add(0.0D, height, 0.0D);
                level.addParticle(ModParticles.ZIPPER_SPARK, pos.x, pos.y, pos.z,
                        0.0D, (age % 2 == 0 ? 0.025D : -0.01D), 0.0D);
            }
        }
    }
    private record GraftedLimb(DroppedLimb source, long startedAt) { }
    private record ZipAnimation(int mask, long startTick, long lastParticleTick) {
        private ZipAnimation(int mask, long startTick) { this(mask, startTick, Long.MIN_VALUE); }
    }

    public static void consume(int entityId, int partMask) {
        Map<String, DroppedLimb> limbs = DROPPED_LIMBS.get(entityId);
        if (limbs != null) {
            limbs.keySet().removeIf(name -> (partMaskForName(normalize(name)) & partMask) != 0);
            if (limbs.isEmpty()) DROPPED_LIMBS.remove(entityId);
        }
        CONSUMED_LIMBS.merge(entityId, partMask, (oldMask, newMask) -> oldMask | newMask);
    }

    private static int partMaskForName(String part) {
        if (isHead(part)) return PowersStickyFingers.ZIPPER_PART_HEAD;
        boolean left = part.contains("left");
        boolean right = part.contains("right");
        if (isArm(part)) {
            if (left) return PowersStickyFingers.ZIPPER_PART_LEFT_ARM;
            if (right) return PowersStickyFingers.ZIPPER_PART_RIGHT_ARM;
            return PowersStickyFingers.ZIPPER_PART_LEFT_ARM | PowersStickyFingers.ZIPPER_PART_RIGHT_ARM;
        }
        if (isLeg(part)) {
            if (left) return PowersStickyFingers.ZIPPER_PART_LEFT_LEG;
            if (right) return PowersStickyFingers.ZIPPER_PART_RIGHT_LEG;
            return PowersStickyFingers.ZIPPER_PART_LEFT_LEG | PowersStickyFingers.ZIPPER_PART_RIGHT_LEG;
        }
        return 0;
    }

    private static ModelPart copyPart(ModelPart source) {
        AccessModelPart access = (AccessModelPart) (Object) source;
        Map<String, ModelPart> children = new HashMap<>();
        access.roundabout$getChildren().forEach((name, child) -> children.put(name, copyPart(child)));
        ModelPart copy = new ModelPart(new ArrayList<>(access.roundabout$getCubes()), children);
        copy.setPos(source.x, source.y, source.z);
        copy.xRot = source.xRot; copy.yRot = source.yRot; copy.zRot = source.zRot;
        copy.visible = true; copy.skipDraw = source.skipDraw;
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
        private int ownerId() { return ownerId; }
        private String name() { return name; }
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
