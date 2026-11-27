package net.hydra.jojomod.client.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.hydra.jojomod.entity.objects.ZipperDebrisEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

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
        if (entity.isSplitPiece()) {
            if (entity.getSplitAxis() == Direction.Axis.X) poseStack.scale(0.5F, 1.0F, 1.0F);
            else poseStack.scale(1.0F, 1.0F, 0.5F);
        } else {
            poseStack.translate(0.0D, 0.2D, 0.0D);
            poseStack.mulPose(Axis.YP.rotationDegrees(spin));
            poseStack.mulPose(Axis.XP.rotationDegrees(spin * 0.6F));
            poseStack.scale(0.4F, 0.4F, 0.4F);
        }
        poseStack.translate(-0.5D, entity.isSplitPiece() ? 0.0D : -0.5D, -0.5D);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, poseStack, buffer, light,
                OverlayTexture.NO_OVERLAY);
        if (entity.isSplitPiece() && entity.isReturning()) {
            renderZipperHalf(entity, poseStack, buffer, light);
        }
        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffer, light);
    }

    private static void renderZipperHalf(ZipperDebrisEntity entity, PoseStack poseStack,
                                         MultiBufferSource buffers, int light) {
        ZipperSpriteAtlas.Sprite sprite = entity.isReturning()
                ? ZipperSpriteAtlas.UNZIP_CLOSE : ZipperSpriteAtlas.UNZIP_OPEN;
        int frame = entity.isReturning()
                ? ZipperSpriteAtlas.frameAtProgress(1.0F - entity.getReturnTicks() / 8.0F, sprite)
                : sprite.frameCount() - 1;
        float textureHeight = 48.0F * sprite.frameCount();
        float u0 = (entity.getHalf() * 8.0F + 0.5F) / 16.0F;
        float u1 = ((entity.getHalf() + 1) * 8.0F - 0.5F) / 16.0F;
        float vTop = (frame * 48.0F + 0.5F) / textureHeight;
        float vBottom = ((frame + 1) * 48.0F - 0.5F) / textureHeight;
        VertexConsumer consumer = ZipperSpriteAtlas.buffer(buffers, sprite);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        Direction face = entity.getZipperFace();
        switch (face) {
            case NORTH, SOUTH -> {
                float z = face == Direction.NORTH ? -0.001F : 1.001F;
                float nz = face.getStepZ();
                zipperVertex(consumer, matrix, normalMatrix, 0, 0, z, u0, vBottom, 0, 0, nz, light);
                zipperVertex(consumer, matrix, normalMatrix, 1, 0, z, u1, vBottom, 0, 0, nz, light);
                zipperVertex(consumer, matrix, normalMatrix, 1, 1, z, u1, vTop, 0, 0, nz, light);
                zipperVertex(consumer, matrix, normalMatrix, 0, 1, z, u0, vTop, 0, 0, nz, light);
            }
            case WEST, EAST -> {
                float x = face == Direction.WEST ? -0.001F : 1.001F;
                float nx = face.getStepX();
                zipperVertex(consumer, matrix, normalMatrix, x, 0, 0, u0, vBottom, nx, 0, 0, light);
                zipperVertex(consumer, matrix, normalMatrix, x, 0, 1, u1, vBottom, nx, 0, 0, light);
                zipperVertex(consumer, matrix, normalMatrix, x, 1, 1, u1, vTop, nx, 0, 0, light);
                zipperVertex(consumer, matrix, normalMatrix, x, 1, 0, u0, vTop, nx, 0, 0, light);
            }
            case DOWN, UP -> {
                float y = face == Direction.DOWN ? -0.001F : 1.001F;
                float ny = face.getStepY();
                zipperVertex(consumer, matrix, normalMatrix, 0, y, 0, u0, vBottom, 0, ny, 0, light);
                zipperVertex(consumer, matrix, normalMatrix, 1, y, 0, u1, vBottom, 0, ny, 0, light);
                zipperVertex(consumer, matrix, normalMatrix, 1, y, 1, u1, vTop, 0, ny, 0, light);
                zipperVertex(consumer, matrix, normalMatrix, 0, y, 1, u0, vTop, 0, ny, 0, light);
            }
        }
    }

    private static void zipperVertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normalMatrix,
                                     float x, float y, float z, float u, float v,
                                     float nx, float ny, float nz, int light) {
        consumer.vertex(matrix, x, y, z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normalMatrix, nx, ny, nz).endVertex();
    }
}
