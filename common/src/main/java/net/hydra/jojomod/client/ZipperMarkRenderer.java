package net.hydra.jojomod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.hydra.jojomod.event.powers.StandUser;
import net.hydra.jojomod.stand.powers.PowersStickyFingers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Renders the temporary zipper-level placeholder only for a Sticky Fingers user. */
public final class ZipperMarkRenderer {
    private static final ResourceLocation ZIPPER_ICON = new ResourceLocation(
            "roundabout", "textures/gui/icons/sticky_fingers/zipper_mark.png");
    private static final ResourceLocation ZIPPER_ICON_GOLD = new ResourceLocation(
            "roundabout", "textures/gui/icons/sticky_fingers/zipper_mark_gold.png");
    private static final ResourceLocation ZIPPER_ICON_RED = new ResourceLocation(
            "roundabout", "textures/gui/icons/sticky_fingers/zipper_mark_red.png");

    private ZipperMarkRenderer() {}

    public static void render(LivingEntity target, PoseStack poseStack, MultiBufferSource buffers) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.player instanceof StandUser user)
                || !(user.roundabout$getStandPowers() instanceof PowersStickyFingers powers)
                || !powers.areZipMarksEnabled()
                || minecraft.player.distanceToSqr(target) > 32.0D * 32.0D) return;

        int level = PowersStickyFingers.getClientTagLevel(target);
        if (level <= 0) return;

        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        poseStack.pushPose();
        poseStack.translate(0.0D, target.getBbHeight() + 0.28D, 0.0D);
        poseStack.mulPose(dispatcher.cameraOrientation());
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));

        ResourceLocation icon = level >= 8 ? ZIPPER_ICON_RED : level >= 4 ? ZIPPER_ICON_GOLD : ZIPPER_ICON;
        VertexConsumer vertex = buffers.getBuffer(RenderType.entityTranslucent(icon));
        float spacing = 0.115F;
        float firstX = -((level - 1) * spacing) / 2.0F;
        for (int i = 0; i < level; i++) {
            Matrix4f matrix = poseStack.last().pose();
            Matrix3f normal = poseStack.last().normal();
            float centerX = firstX + i * spacing;
            float halfWidth = 0.052F;
            float halfHeight = 0.10F;
            vertex.vertex(matrix, centerX - halfWidth, -halfHeight, 0.0F).color(255, 255, 255, 255).uv(0, 1)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, 0, -1).endVertex();
            vertex.vertex(matrix, centerX + halfWidth, -halfHeight, 0.0F).color(255, 255, 255, 255).uv(1, 1)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, 0, -1).endVertex();
            vertex.vertex(matrix, centerX + halfWidth, halfHeight, 0.0F).color(255, 255, 255, 255).uv(1, 0)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, 0, -1).endVertex();
            vertex.vertex(matrix, centerX - halfWidth, halfHeight, 0.0F).color(255, 255, 255, 255).uv(0, 0)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal, 0, 0, -1).endVertex();
        }

        poseStack.pushPose();
        poseStack.translate(0.0D, -0.17D, 0.0D);
        poseStack.scale(-0.025F, -0.025F, 0.025F);
        Font font = minecraft.font;
        String text = Integer.toString(level);
        font.drawInBatch(text, -font.width(text) / 2.0F, 5.0F, 0xFFFFD54A, true,
                poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 15728880);
        poseStack.popPose();
        poseStack.popPose();
    }
}
