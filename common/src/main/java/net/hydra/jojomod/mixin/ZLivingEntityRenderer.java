package net.hydra.jojomod.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.hydra.jojomod.access.IEntityAndData;
import net.hydra.jojomod.access.ILivingEntityRenderer;
import net.hydra.jojomod.access.IPlayerEntity;
import net.hydra.jojomod.client.*;
import net.hydra.jojomod.client.models.layers.BigBubbleLayer;
import net.hydra.jojomod.client.models.layers.FrozenLayer;
import net.hydra.jojomod.client.models.stand.renderers.*;
import net.hydra.jojomod.entity.corpses.FallenMob;
import net.hydra.jojomod.entity.gold_experience.GELifeform;
import net.hydra.jojomod.entity.pathfinding.AnubisPossessorEntity;
import net.hydra.jojomod.entity.pathfinding.CommandDiscPossession;
import net.hydra.jojomod.entity.stand.StandEntity;
import net.hydra.jojomod.entity.visages.JojoNPCPlayer;
import net.hydra.jojomod.entity.visages.mobs.JosukePartEightNPC;
import net.hydra.jojomod.entity.visages.mobs.PlayerAlexNPC;
import net.hydra.jojomod.entity.visages.mobs.PlayerSteveNPC;
import net.hydra.jojomod.event.IVillagerAccess;
import net.hydra.jojomod.event.index.PlayerPosIndex;
import net.hydra.jojomod.event.index.PowerIndex;
import net.hydra.jojomod.event.powers.DiverDownDisguiseService;
import net.hydra.jojomod.event.powers.StandPowers;
import net.hydra.jojomod.event.powers.StandUser;
import net.hydra.jojomod.event.powers.TimeStop;
import net.hydra.jojomod.item.ModItems;
import net.hydra.jojomod.mixin.access.models.AccessQuadrupedModel;
import net.hydra.jojomod.mixin.access.models.AccessModelPart;
import net.hydra.jojomod.stand.powers.*;
import net.hydra.jojomod.util.MainUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Mixin(LivingEntityRenderer.class)
public abstract class ZLivingEntityRenderer<T extends LivingEntity, M extends EntityModel<T>> extends EntityRenderer<T> implements RenderLayerParent<T, M>, ILivingEntityRenderer {

    @Unique private ModelPart[] roundabout$zipperParts;
    @Unique private String[] roundabout$zipperPartNames;
    @Unique private boolean[] roundabout$zipperPartVisibility;

    @Shadow protected abstract boolean addLayer(RenderLayer<T, M> $$0);
    @Shadow protected M model;
    @Shadow public abstract void render(T $$0, float $$1, float $$2, PoseStack $$3, MultiBufferSource $$4, int $$5);
    @Shadow public M getModel() { return null; }
    @Shadow @Nullable protected abstract RenderType getRenderType(T $$0, boolean $$1, boolean $$2, boolean $$3);

    protected ZLivingEntityRenderer(EntityRendererProvider.Context $$0) {
        super($$0);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void roundabout$hideZipperedParts(T entity, float yaw, float partialTicks, PoseStack poseStack,
                                              MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        roundabout$restoreZipperParts();
        int mask = PowersStickyFingers.getClientDismembermentMask(entity);
        int legs = PowersStickyFingers.ZIPPER_PART_LEFT_LEG | PowersStickyFingers.ZIPPER_PART_RIGHT_LEG;
        if ((mask & legs) == legs) {
            if (entity instanceof Player) {
                entity.setPose(Pose.SWIMMING);
                entity.setSwimming(true);
            } else {
                // Most mob models ignore the swimming pose outside water; render their body pose ourselves.
                entity.setPose(Pose.STANDING);
                entity.setSwimming(false);
            }
        } else if (!entity.isInWater() && entity.getPose() == Pose.SWIMMING && !entity.isFallFlying()) {
            entity.setSwimming(false);
            entity.setPose(Pose.STANDING);
        }
        if (mask == 0) return;
        IdentityHashMap<ModelPart, String> affected = new IdentityHashMap<>();
        List<Map.Entry<String, ModelPart>> roots = roundabout$getModelPartRoots(this.model);
        for (Map.Entry<String, ModelPart> root : roots) {
            roundabout$collectDismemberedParts(root.getValue(), root.getKey(), mask, affected);
        }
        if (affected.isEmpty()) return;
        this.roundabout$zipperParts = affected.keySet().toArray(new ModelPart[0]);
        this.roundabout$zipperPartNames = new String[this.roundabout$zipperParts.length];
        this.roundabout$zipperPartVisibility = new boolean[this.roundabout$zipperParts.length];
        for (int i = 0; i < this.roundabout$zipperParts.length; i++) {
            ModelPart part = this.roundabout$zipperParts[i];
            this.roundabout$zipperPartNames[i] = affected.get(part);
            this.roundabout$zipperPartVisibility[i] = part.visible;
            part.visible = false;
        }
    }

    /** Collects named body parts from vanilla and modded entity models. */
    @Unique
    private static List<Map.Entry<String, ModelPart>> roundabout$getModelPartRoots(EntityModel<?> model) {
        List<Map.Entry<String, ModelPart>> roots = new ArrayList<>();
        if (model instanceof HumanoidModel<?> humanoid) {
            roots.add(new AbstractMap.SimpleImmutableEntry<>("head", humanoid.head));
            roots.add(new AbstractMap.SimpleImmutableEntry<>("hat", humanoid.hat));
            roots.add(new AbstractMap.SimpleImmutableEntry<>("rightArm", humanoid.rightArm));
            roots.add(new AbstractMap.SimpleImmutableEntry<>("leftArm", humanoid.leftArm));
            roots.add(new AbstractMap.SimpleImmutableEntry<>("rightLeg", humanoid.rightLeg));
            roots.add(new AbstractMap.SimpleImmutableEntry<>("leftLeg", humanoid.leftLeg));
        } else if (model instanceof net.minecraft.client.model.QuadrupedModel<?> quadruped) {
            AccessQuadrupedModel parts = (AccessQuadrupedModel) quadruped;
            roots.add(new AbstractMap.SimpleImmutableEntry<>("head", parts.roundabout$getHead()));
            roots.add(new AbstractMap.SimpleImmutableEntry<>("rightFrontLeg", parts.roundabout$getRightFrontLeg()));
            roots.add(new AbstractMap.SimpleImmutableEntry<>("leftFrontLeg", parts.roundabout$getLeftFrontLeg()));
            roots.add(new AbstractMap.SimpleImmutableEntry<>("rightHindLeg", parts.roundabout$getRightHindLeg()));
            roots.add(new AbstractMap.SimpleImmutableEntry<>("leftHindLeg", parts.roundabout$getLeftHindLeg()));
        }
        if (model instanceof HierarchicalModel<?> hierarchical) {
            roots.add(new AbstractMap.SimpleImmutableEntry<>("root", hierarchical.root()));
        }
        for (Class<?> type = model.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!ModelPart.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(model);
                    if (value instanceof ModelPart part) {
                        roots.add(new AbstractMap.SimpleImmutableEntry<>(field.getName(), part));
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Some third-party models restrict reflection; their hierarchical root is still handled.
                }
            }
        }
        return roots;
    }

    @Unique
    private static void roundabout$collectDismemberedParts(ModelPart part, String name, int mask,
                                                            IdentityHashMap<ModelPart, String> affected) {
        String normalized = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "");
        if (roundabout$matchesPart(normalized, mask)) affected.putIfAbsent(part, normalized);
        Map<String, ModelPart> children = ((AccessModelPart) (Object) part).roundabout$getChildren();
        for (Map.Entry<String, ModelPart> child : children.entrySet()) {
            roundabout$collectDismemberedParts(child.getValue(), child.getKey(), mask, affected);
        }
    }

    @Unique
    private static boolean roundabout$matchesPart(String name, int mask) {
        if ((mask & PowersStickyFingers.ZIPPER_PART_HEAD) != 0
                && (name.equals("head") || name.contains("headwear") || name.equals("hat") || name.equals("skull"))) {
            return true;
        }
        boolean left = name.startsWith("left") || name.contains("left");
        boolean right = name.startsWith("right") || name.contains("right");
        if (!left && !right) return false;
        boolean frontLimb = name.contains("frontleg") || name.contains("foreleg");
        boolean armLike = name.contains("arm") || name.contains("hand") || frontLimb
                || name.contains("wings") || name.contains("sleeve");
        boolean legLike = !frontLimb && (name.contains("leg") || name.contains("hind") || name.contains("foot")
                || name.contains("feet") || name.contains("paw") || name.contains("pants"));
        if (armLike && (mask & (left ? PowersStickyFingers.ZIPPER_PART_LEFT_ARM
                : PowersStickyFingers.ZIPPER_PART_RIGHT_ARM)) != 0) return true;
        return legLike && (mask & (left ? PowersStickyFingers.ZIPPER_PART_LEFT_LEG
                : PowersStickyFingers.ZIPPER_PART_RIGHT_LEG)) != 0;
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
    private void roundabout$restoreZipperedParts(T entity, float yaw, float partialTicks, PoseStack poseStack,
                                                 MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (this.roundabout$zipperParts != null) {
            ZipperDetachedLimbRenderer.capture(entity, this.roundabout$zipperParts, this.roundabout$zipperPartNames,
                    this.getTextureLocation(entity));
        } else {
            ZipperDetachedLimbRenderer.clear(entity.getId());
        }
        roundabout$restoreZipperParts();
    }

    @Unique
    private void roundabout$restoreZipperParts() {
        if (this.roundabout$zipperParts == null || this.roundabout$zipperPartVisibility == null) return;
        for (int i = 0; i < this.roundabout$zipperParts.length; i++) {
            this.roundabout$zipperParts[i].visible = this.roundabout$zipperPartVisibility[i];
        }
        this.roundabout$zipperParts = null;
        this.roundabout$zipperPartNames = null;
        this.roundabout$zipperPartVisibility = null;
    }

    @Unique
    private static int roundabout$PackRed(int $$0, int $$1) { return $$0 | $$1 << 16; }

    @Inject(method = "isBodyVisible", at = @At("HEAD"), cancellable = true)
    private void roundabout$forceBodyVisible(T entity, CallbackInfoReturnable<Boolean> cir) {
        if (PowersMetallica.hasAnyFadeActive(entity)) {
            cir.setReturnValue(true);
        }
    }



    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void roundabout$forceTranslucent(T entity, boolean bodyVisible, boolean translucent, boolean glowing, CallbackInfoReturnable<RenderType> cir) {
        if (PowersMetallica.hasAnyFadeActive(entity) || (ClientUtil.getThrowFadePercent(entity,ClientUtil.getDelta()) != 1
        && !entity.isInvisible())) {
            ResourceLocation texture = this.getTextureLocation(entity);
            cir.setReturnValue(RenderType.entityTranslucent(texture));
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "HEAD"))
    private void roundabout$applyInvisibilityFade(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {

        byte sam = ((StandUser)entity).roundabout$getStandAnimation();
        boolean stopTime = ((TimeStop) entity.level()).CanTimeStopEntity(entity);
        if (sam == StandPowers.MELT_DODGE_ANIM
        && !stopTime){
            float animTime = entity.tickCount + partialTicks;

            float xDistortion = Mth.sin(animTime * 0.31F) * 0.045F;
            float yDistortion = Mth.sin(animTime * 0.43F + 1.2F) * 0.025F;
            float zDistortion = Mth.sin(animTime * 0.37F + 2.4F) * 0.045F;

            poseStack.scale(
                    1.0F + xDistortion,
                    1.0F + yDistortion,
                    1.0F + zDistortion
            );
        } else if (sam == StandPowers.SWITCH_INTO_BODY
                && !stopTime){
            if (((StandUser)entity).roundabout$getStandPowers() instanceof PowersD4C pd4c){
                float ticksSince = ((pd4c.ticksSinceSwitch+ partialTicks)*0.1F)+0.01F;
                ticksSince = Math.min(ticksSince,1F);
                poseStack.scale(
                        ticksSince,
                        ticksSince,
                        ticksSince
                );
            }
        }


        if (PowersMetallica.hasAnyFadeActive(entity)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.cameraEntity != null) {
                double dist = entity.distanceTo(mc.cameraEntity);
                float alpha = PowersMetallica.getMetallicaInvisibilityAlpha(entity, dist, partialTicks);

                if (buffer instanceof MultiBufferSource.BufferSource bs) {
                    bs.endBatch();
                }
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            }
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "TAIL"))
    private void roundabout$renderTail(T entity, float $$1, float $$2, PoseStack matrixStack, MultiBufferSource buffer, int $$5, CallbackInfo ci) {
        if (PowersMetallica.hasAnyFadeActive(entity)) {
            if (buffer instanceof MultiBufferSource.BufferSource bs) {
                bs.endBatch();
            }
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
        ClientUtil.setThrowFadeToTheEther(1.0F);
        MetallicaClientRenderer.renderMetalMeterBar(entity, matrixStack, buffer);
        for (PowersPearlJam instance : PowersPearlJam.getInstances()){
            instance.renderEffectIcons(entity, matrixStack, buffer);
        }
        LocalPlayer player = Minecraft.getInstance().player;

        ZipperMarkRenderer.render(entity, matrixStack, buffer);


        if (player != null && ((StandUser)player).roundabout$getStandPowers() != null
                && ((StandUser)player).roundabout$getStandPowers().shouldRenderIconEntity(entity)) {
            ((StandUser)player).roundabout$getStandPowers().renderIconEntity(entity, matrixStack, buffer);
        }

        HallucinationIndicatorRenderer.render(entity, matrixStack, buffer);

        if (entity instanceof GELifeform lifeform && lifeform.isGrowing(entity)) {
            ItemStack sourceStack = lifeform.getSourceItem();
            if (!sourceStack.isEmpty()) {
                float itemScale = lifeform.getItemShrinkScale(entity, $$2);
                if (itemScale > 0.02F) {
                    float progress = lifeform.getGrowthProgress(entity, $$2);
                    matrixStack.pushPose();
                    float itemYOffset = (entity.getBbHeight() * 0.15F) + (progress * 0.24F);
                    matrixStack.translate(0.0D, itemYOffset, 0.0D);
                    matrixStack.scale(itemScale, itemScale, itemScale);
                    Minecraft.getInstance().getItemRenderer().renderStatic(
                            sourceStack,
                            ItemDisplayContext.GROUND,
                            $$5,
                            OverlayTexture.NO_OVERLAY,
                            matrixStack,
                            buffer,
                            entity.level(),
                            entity.getId()
                    );
                    matrixStack.popPose();
                }
            }
        }

        /*
        //DiverDownPointerRenderer.render(entity, matrixStack, buffer);
        // lyin eyes highlighter
        if (player != null && ((StandUser)player).roundabout$getStandPowers() instanceof PowersLyinEyes LE
                && LE.getVisionModeClient() && LE.isActive()) {
            float maxDetectionRange = ClientNetworking.getAppropriateConfig().lyinEyesSettings.maxDetectionRange;
            if (entity != player && entity.isAlive() && player.distanceToSqr(entity) <= (maxDetectionRange * maxDetectionRange)
                    && !(entity instanceof StandEntity)
                    && !(entity instanceof FallenMob)
                    && ((entity instanceof Mob) || (entity instanceof Player))) {
                // hostile mobs
                if (entity instanceof Enemy && !(entity instanceof NeutralMob)) {
                    LyinEyesHighlighterRenderer.render(entity, matrixStack, buffer, 255, 0, 0, 200, 0.7F);
                }
                // neutral mobs
                else if (entity instanceof NeutralMob || entity instanceof Player || ((StandUser) entity).roundabout$hasAStand()) {
                    LyinEyesHighlighterRenderer.render(entity, matrixStack, buffer, 255, 255, 0, 200, 0.7F);
                }
                // passive mobs
                else if (!(entity instanceof Enemy) && !(entity instanceof NeutralMob)) {
                    LyinEyesHighlighterRenderer.render(entity, matrixStack, buffer, 0, 255, 0, 200, 0.7F);
                }
            }
        }*/
    }

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at=@At("HEAD"), cancellable = true)
    private void roundabout$shouldShowName(T entity, CallbackInfoReturnable<Boolean> cir)
    {

        if (ClientUtil.shouldHideName(entity)){
            cir.setReturnValue(false);
            return;
        }

        if (entity instanceof IEntityAndData data && MainUtil.isUsingMetallica(entity)) {
            if (!ClientUtil.getInvisibilityVision()) {
                cir.setReturnValue(false);
                cir.cancel();
            }
        }
    }


    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "HEAD"), cancellable = true)
    private void roundabout$renderX(T entity, float $$1, float $$2, PoseStack $$3, MultiBufferSource $$4, int $$5, CallbackInfo ci) {
//        if (roundabout$isRenderingYellowLines)
//            return;

        if (entity != Minecraft.getInstance().player && PowersStickyFingers.isInZipperDimension(entity)) {
            ci.cancel();
            return;
        }

        if (entity instanceof StandEntity stand && stand.getUser() != null
                && PowersStickyFingers.isInZipperDimension(stand.getUser())) {
            ci.cancel();
            return;
        }

        StandUser SU = (StandUser) entity;
        if ( SU.roundabout$isPossessed()  ) {
            PathfinderMob poss = SU.roundabout$getPossessor();
            if (poss != null && poss.getTarget() != null) {
                float yRot = MainUtil.getLookAtEntityYaw(entity,poss.getTarget());
                entity.setYBodyRot(yRot);
            }
        }

        ClientUtil.savedPose = $$3.last().pose();
    }

    @Inject(method= "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Lnet/minecraft/client/model/EntityModel;F)V", at = @At(value = "RETURN"))
    private void roundabout$init(EntityRendererProvider.Context $$0, EntityModel $$1, float $$2, CallbackInfo ci) {
        this.addLayer(new BigBubbleLayer<>($$0, ((LivingEntityRenderer)(Object)this)));
        this.addLayer(new FrozenLayer<>($$0, ((LivingEntityRenderer)(Object)this)));
        this.addLayer(new ZipperBodyOverlayLayer<>((LivingEntityRenderer<T, M>) (Object) this));
    }

    @Nullable
    public RenderType roundabout$getRenderType(LivingEntity $$0, boolean $$1, boolean $$2, boolean $$3) {
//        if (roundabout$isRenderingYellowLines)
//        {
//            return RenderType.translucent();
//        }

        ResourceLocation $$4 = this.getTextureLocation((T) $$0);
        if ($$2) {
            return RenderType.itemEntityTranslucentCull($$4);
        } else if ($$1) {
            return this.model.renderType($$4);
        } else {
            return $$3 ? RenderType.outline($$4) : null;
        }
    }

    @Inject(method = "setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V", at = @At(value = "TAIL"), cancellable = true)
    private void roundabout$rotations(T $$0, PoseStack poseStack, float $$2, float $$3, float $$4, CallbackInfo ci) {
        if ($$0 instanceof Player P) {
            StandUser SU = (StandUser) P;
            if (SU.roundabout$getStandPowers() instanceof PowersAnubis PA) {
                float leftHand = $$0.getMainArm() == HumanoidArm.LEFT ? -1 : 1;

                float deltaTime = PA.getAttackTimeDuring()+$$4;
                float realTime = SU.roundabout$getWornStandAnimation().getAccumulatedTime()/1000F;
                switch (SU.roundabout$getStandAnimation()) {
                    case PowerIndex.SNEAK_MOVEMENT -> {
                        if (deltaTime < 16) {
                            poseStack.rotateAround(new Quaternionf().fromAxisAngleDeg(1,0,0,360 * ((deltaTime)/15F)), 0, P.getEyeHeight()*0.6F, 0 );
                        }
                    }
                    case PowersAnubis.POGO, PowersAnubis.STAB -> {
                        if (PA.getAttackTimeDuring() > 0) {
                            poseStack.translate(0, 0.5, 0.5);
                            float time = Math.min(1, (deltaTime) / (PA.getPogoDelay() - 2));
                            float end = -100 - P.getViewXRot(0F);
                            poseStack.rotateAround(new Quaternionf().fromAxisAngleDeg(1, 0, 0, time * end), 0, P.getEyeHeight() * 0.4F, 0);
                        }
                    }
                    case PowersAnubis.CLEAVE -> {
                        if (realTime < 1.125) {
                            poseStack.rotateAround(new Quaternionf().fromAxisAngleDeg(0,1,0,Mth.lerp(realTime/1.125F,0,leftHand*-60)),0,0,0);
                        } else if (realTime < 1.25) {
                            realTime -= 1.125F;
                            poseStack.rotateAround(new Quaternionf().fromAxisAngleDeg(0,1,0,Mth.lerp(realTime/0.125F,leftHand*-60,leftHand*60)),0,0,0);
                        } else if (realTime < 1.375F) {
                            realTime -= 1.25F;
                            poseStack.rotateAround(new Quaternionf().fromAxisAngleDeg(0,1,0,Mth.lerp(realTime/0.125F,leftHand*60,0)),0,0,0);
                        }
                    }
                    case PowersAnubis.SPIN -> {
                        if (realTime > 0.35F && realTime < 1.35F) {
                            realTime -= 0.35F;
                            poseStack.rotateAround(new Quaternionf().fromAxisAngleDeg(0,1,0,Mth.lerp(realTime,0,leftHand*720)),0,0,0);
                        }
                    }
                    case PowersAnubis.FLURRY -> {
                        if (realTime < 0.75F) {
                            poseStack.rotateAround(new Quaternionf().fromAxisAngleDeg(0,1,0,Mth.lerp(realTime/0.75F,0,leftHand*-20)),0,0,0);
                        } else if (realTime < 0.88F) {
                            realTime -= 0.75F;
                            poseStack.rotateAround(new Quaternionf().fromAxisAngleDeg(0,1,0,Mth.lerp(realTime/0.13F,0,leftHand*10)),0,0,0);
                        } else if (realTime < 1.58F) {
                            poseStack.rotateAround(new Quaternionf().fromAxisAngleDeg(0,1,0,leftHand*10),0,0,0);
                        }
                    }
                }
            } else if (SU.roundabout$getStandPowers() instanceof PowersTusk PT) {
               if (SU.roundabout$getStandAnimation() != PowersTusk.NONE) {
                    float scale = Math.min(1,PT.getAttackTime()/7.0F+$$4);

                    if (SU.roundabout$getStandAnimation() == PowersTusk.WARP) {
                        if (scale < 1) {
                            poseStack.scale(scale, scale, scale);
                            poseStack.rotateAround(new Quaternionf().fromAxisAngleDeg(0, 1, 0, scale * 360), 0, 0, 0);
                        }
                    }

                }
            }

            byte playerP = ((IPlayerEntity)$$0).roundabout$GetPos();

            /*Dodge makes you lean forward visually*/
            if (playerP == PlayerPosIndex.DODGE_FORWARD || playerP == PlayerPosIndex.DODGE_BACKWARD) {
                int dodgeTime = ((IPlayerEntity)$$0).roundabout$getDodgeTime();
                float $$5;
                if (dodgeTime > -1) {
                    if (dodgeTime > 5) {
                        $$5 = ((11 - ((float) dodgeTime + 1 + $$4 - 1.0F)) / 20.0F * 1.6F);
                    } else {
                        $$5 = ((float) dodgeTime + 1 + $$4 - 1.0F) / 20.0F * 1.6F;
                    }
                    $$5 = Mth.sqrt($$5);
                    if ($$5 > 1.0F) {
                        $$5 = 1.0F;
                    }

                    if (playerP == PlayerPosIndex.DODGE_FORWARD) {
                        $$5 *= -1;
                    }

                    poseStack.mulPose(Axis.XP.rotationDegrees($$5 * 45));
                }
            }else if (playerP == PlayerPosIndex.SKATE_TWIRL){
                poseStack.mulPose(Axis.YP.rotationDegrees(((float)$$0.tickCount + $$4) * -45.0F));
            }
        }
        if (!($$0 instanceof Player) && PowersStickyFingers.hasBothLegsRemoved($$0)
                && !$$0.isInWater() && !$$0.isPassenger() && !$$0.isFallFlying()) {
            // Lay land based mobs on their side to produce a visible crawling pose even for custom models.
            poseStack.translate(0.0D, 0.25D, 0.0D);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.translate(0.0D, -0.25D, 0.0D);
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at= @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
    private void roundabout$renderLivingEntity(T entity, float $$1, float $$2, PoseStack matrices, MultiBufferSource $$4, int $$5, CallbackInfo ci)
    {
        if (entity instanceof PlayerAlexNPC || entity instanceof PlayerSteveNPC)
        {
            Vector3f offset = ((JojoNPCPlayer) entity).getSizeOffset();
            matrices.scale(
                    1.0f+offset.x,
                    1.0f+offset.y,
                    1.0f+offset.z
            );
        }
        if (entity instanceof Player pl && pl.isSleeping()){
            ItemStack stack = ((IPlayerEntity) pl).roundabout$getMaskSlot();
            if (stack !=null && !stack.isEmpty() && stack.is(ModItems.JOSUKE_PART_EIGHT_MASK)){
                matrices.translate(0,-0.4,0);
            }
        } else if (entity instanceof JosukePartEightNPC jp && jp.isSleeping()){
            matrices.translate(0,-0.4,0);
        }
        if (((StandUser)entity).roundabout$getExplosionInflation() > 4) {

            float value = ((((StandUser)entity).roundabout$getExplosionInflation() - 4) /14.0f);

            float tween = 0.14f * (value * value * value);

            matrices.scale(1.0f + tween, 1.0f + tween, 1.0f + tween);

        }
    }


    @Inject(method = "getOverlayCoords", at = @At(value = "HEAD"), cancellable = true)
    private static void roundabout$GetOverlayCoords(LivingEntity $$0, float $$1, CallbackInfoReturnable<Integer> ci) {
        if (((StandUser)$$0).roundabout$getStoredDamageByte() > 0) {
            ci.setReturnValue(roundabout$PackRed(
                    ((StandUser)$$0).roundabout$getStoredDamageByte(),
                    10));
        } else if (ClientUtil.getFrozenLevel() > 1 && !ClientUtil.isHiddenIceEntity($$0)){
            int flevel = ClientUtil.getFrozenLevel();
            if (flevel == 2){
                ci.setReturnValue(roundabout$PackRed(
                        2,
                        10));
            } else {
                ci.setReturnValue(roundabout$PackRed(
                        6,
                        10));
            }
        }
        else if(ClientUtil.checkIfClientCanSeeMobsForWindVision()){
            ci.setReturnValue(roundabout$PackRed(
                    15,
                    10));
        }
    }

    // for shaking stuff
    @Inject(method = "isShaking", at = @At("HEAD"), cancellable = true)
    private void roundabout$shakeWhenCalledOut(T entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Villager villager && ((IVillagerAccess) villager).roundabout$getCalloutTicks() > 0) {
            cir.setReturnValue(true);
        }
    }

    // risky because it's brittle, but it honestly might just work
   @Redirect(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isPassenger()Z"))
    private boolean roundabout$allowWalkingAnimation(LivingEntity instance) {
       return instance.isPassenger()
               && !(instance.getVehicle() instanceof AnubisPossessorEntity)
               && !(instance.getVehicle() instanceof CommandDiscPossession);
   }

    // diver down disguise
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void roundabout$renderDiverDownDisguise(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if ((Object) this instanceof AbstractDisguiseRenderer) {
            return;
        }

        if (entity instanceof AbstractClientPlayer) {
            return;
        }

        StandUser su = (StandUser) entity;
        if (su.roundabout$isDisguised()) {
            GameProfile profile = su.roundabout$getDisguiseProfile();
            if (profile != null) {
                //disguises with skin
                if (DiverDownDisguiseRenderer.INSTANCE != null) {
                    DiverDownDisguiseRenderer.INSTANCE.renderDisguise(entity, profile, entityYaw, partialTicks, poseStack, buffer, packedLight);
                }
                //adds the nametag
                if (entity != Minecraft.getInstance().player && !entity.isInvisible() && !Minecraft.getInstance().options.hideGui) {
                    String disguiseName = profile.getName();
                    if (disguiseName != null && !disguiseName.isEmpty()) {
                        float targetY = (entity.isCrouching() ? 2.0F : 2.3F);
                        float yDiff = targetY - entity.getNameTagOffsetY();

                        poseStack.pushPose();
                        poseStack.translate(0.0D, yDiff, 0.0D);
                        this.renderNameTag(entity, Component.literal(disguiseName), poseStack, buffer, packedLight);
                        poseStack.popPose();
                    }
                }
                ci.cancel();
            }
        }
    }

    // gold experience mobs scale growing
    @Inject(method = "scale", at = @At("TAIL"))
    private void roundabout$scaleGELifeformGrowth(T entity, PoseStack poseStack, float partialTicks, CallbackInfo ci) {
        if (entity instanceof GELifeform lifeform && lifeform.isGrowing(entity)) {
            float scale = lifeform.getGrowthScale(entity, partialTicks);
            poseStack.scale(scale, scale, scale);
        }
    }
}
