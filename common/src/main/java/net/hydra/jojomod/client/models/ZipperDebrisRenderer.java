package net.hydra.jojomod.client.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.hydra.jojomod.entity.objects.ZipperDebrisEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**Draws the debris of Zipper Barrage: a small, spinning copy of the block that was mined.*/
public class ZipperDebrisRenderer extends EntityRenderer<ZipperDebrisEntity> {

    private final BlockRenderDispatcher dispatcher;

    public ZipperDebrisRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.15F;
        this.dispatcher = context.getBlockRenderDispatcher();
    }

    @Override
    public ResourceLocation getTextureLocation(ZipperDebrisEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(ZipperDebrisEntity entity, float yaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        BlockState state = entity.getBlockState();
        if (state.getRenderShape() != RenderShape.MODEL) {
            return;
        }
        float spin = (entity.tickCount + partialTicks) * 25.0F;
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.2F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        poseStack.mulPose(Axis.XP.rotationDegrees(spin * 0.7F));
        poseStack.scale(0.4F, 0.4F, 0.4F);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        this.dispatcher.renderSingleBlock(state, poseStack, buffer, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffer, light);
    }
}
