package net.hydra.jojomod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.hydra.jojomod.event.powers.StandUser;
import net.hydra.jojomod.stand.powers.PowersStickyFingers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Applies a transparent zipper pattern directly to the entity's animated model and UVs. */
public final class ZipperBodyOverlayRenderer {
    private static final ResourceLocation ZIPPER_OVERLAY_LOW = texture("zipper_body_overlay_low.png");
    private static final ResourceLocation ZIPPER_OVERLAY_MID = texture("zipper_body_overlay_mid.png");
    private static final ResourceLocation ZIPPER_OVERLAY_HIGH = texture("zipper_body_overlay_high.png");

    private ZipperBodyOverlayRenderer() {}

    private static ResourceLocation texture(String name) {
        return new ResourceLocation("roundabout", "textures/entity/sticky_fingers/" + name);
    }

    public static void render(EntityModel<?> model, LivingEntity target, PoseStack poseStack,
                              MultiBufferSource buffers, int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.player instanceof StandUser user)
                || !(user.roundabout$getStandPowers() instanceof PowersStickyFingers powers)
                || (!powers.areZipMarksEnabled() && !PowersStickyFingers.isClientForcedTag(target))
                || minecraft.player.distanceToSqr(target) > 32.0D * 32.0D) return;

        int level = PowersStickyFingers.getClientTagLevel(target);
        if (level <= 0) return;

        ResourceLocation texture = level >= 8 ? ZIPPER_OVERLAY_HIGH
                : level >= 4 ? ZIPPER_OVERLAY_MID : ZIPPER_OVERLAY_LOW;
        poseStack.pushPose();
        model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.entityTranslucent(texture)),
                packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 0.94F);
        poseStack.popPose();
    }
}
