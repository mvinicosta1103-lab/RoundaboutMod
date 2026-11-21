package net.hydra.jojomod.client.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.hydra.jojomod.entity.objects.ZipperDebrisEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**Draws the flying debris as a small spinning block.*/
public class ZipperDebrisRenderer extends EntityRenderer<ZipperDebrisEntity> {

    public ZipperDebrisRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ZipperDebrisEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(ZipperDebrisEntity entity, float yaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        BlockState state = entity.getBlockState();
        if (state.isAir()) {
            return;
        }
        float spin = (entity.tickCount + partialTicks) * 18.0F;
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.2D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        poseStack.mulPose(Axis.XP.rotationDegrees(spin * 0.6F));
        poseStack.scale(0.4F, 0.4F, 0.4F);
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, poseStack, buffer, light,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffer, light);
    }
}
