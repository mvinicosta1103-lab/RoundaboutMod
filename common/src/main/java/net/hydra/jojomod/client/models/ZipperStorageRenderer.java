package net.hydra.jojomod.client.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.hydra.jojomod.Roundabout;
import net.hydra.jojomod.entity.objects.ZipperEntity;
import net.hydra.jojomod.entity.objects.ZipperStorageEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class ZipperStorageRenderer extends EntityRenderer<ZipperStorageEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Roundabout.MOD_ID,
            "textures/entity/zipper_animated.png");

    private static final double VIEW_DISTANCE_SQR = 7.0D * 7.0D;
    private static final int SEGMENTS = 12;
    private static final int DIMENSION_FRAME_COUNT = 29;
    private static final int DIMENSION_FRAME_HEIGHT = 66;
    private static final int DIMENSION_CONTENT_HEIGHT = 64;
    private static final float TOOTH_WIDTH = 0.04F;
    private static final float LENGTH = 0.8F;
    private static final float HALF_WIDTH = 0.42F;
    private static final float MAX_HALF = 0.22F;

    public ZipperStorageRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ZipperStorageEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(ZipperStorageEntity entity, float yaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int unusedLight) {
        if (entity.getMergedRenderLeader() != entity) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !ZipperStorageEntity.isStickyFingersUser(player)
                || entity.distanceToSqr(player) > VIEW_DISTANCE_SQR) {
            return;
        }
        int light = LevelRenderer.getLightColor(entity.level(), entity.getFrontPos());
        float baseHalfWidth = HALF_WIDTH + TOOTH_WIDTH;
        ZipperEntity.MergeRenderLayout layout = entity.getMergeRenderLayout(baseHalfWidth, LENGTH, false);
        float length = layout.length();
        float widthScale = layout.width() / (baseHalfWidth * 2.0F);
        float lengthScale = length / LENGTH;
        float open = Mth.lerp(partialTicks, entity.getPrevOpenAnim(), entity.getOpenAnim());
        open = open * open * (3.0F - 2.0F * open);

        Direction face = entity.getFace();
        poseStack.pushPose();
        poseStack.translate(0.0D, ZipperStorageEntity.SIZE / 2.0D, 0.0D);
        if (face.getAxis() == Direction.Axis.Y) {
            poseStack.mulPose(Axis.XP.rotationDegrees(face == Direction.UP ? -90.0F : 90.0F));
        } else {
            float angle = (float) Math.toDegrees(Math.atan2(face.getStepX(), face.getStepZ()));
            poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        }
        poseStack.mulPose(Axis.ZP.rotationDegrees(layout.rotationDegrees()));
        poseStack.translate(layout.centerX(), layout.centerY() - length / 2.0F, 0.0F);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        // Keep the dimension video behind the zipper teeth and storage border.
        float dimensionZ = 0.005F;
        float zipperZ = 0.02F;
        float sliderZ = 0.03F;
        float halfWidth = HALF_WIDTH * widthScale;
        float maxHalf = MAX_HALF * widthScale;
        float toothWidth = TOOTH_WIDTH * widthScale;
        float segment = length / SEGMENTS;
        int dimensionFrame = ((int) ((entity.tickCount + partialTicks) / 2.0F)) % DIMENSION_FRAME_COUNT;

        for (int i = 0; i < SEGMENTS; i++) {
            float y0 = i * segment;
            float y1 = y0 + segment;
            float lens = (float) Math.pow(Math.sin(Math.PI * (i + 0.5D) / SEGMENTS), 0.8D);
            float gap = maxHalf * open * lens;
            if (gap > 0.001F) {
                quad(consumer, matrix, normal, -gap, y0, gap, y1, dimensionZ,
                        0, dimensionFrame, light);
            }
            quad(consumer, matrix, normal, -gap - toothWidth, y0, -gap, y1, zipperZ, 2, dimensionFrame, light);
            quad(consumer, matrix, normal, gap, y0, gap + toothWidth, y1, zipperZ, 2, dimensionFrame, light);
            float clothInner = gap + toothWidth;
            if (clothInner < halfWidth) {
                quad(consumer, matrix, normal, -halfWidth, y0, -clothInner, y1, zipperZ, 1, dimensionFrame, light);
                quad(consumer, matrix, normal, clothInner, y0, halfWidth, y1, zipperZ, 1, dimensionFrame, light);
            }
        }
        float sliderY = length - 0.2F * lengthScale;
        quad(consumer, matrix, normal, -0.06F * widthScale, sliderY, 0.06F * widthScale,
                sliderY + 0.12F * lengthScale,
                sliderZ, 3, dimensionFrame, light);
        quad(consumer, matrix, normal, -0.035F * widthScale, sliderY - 0.1F * lengthScale,
                0.035F * widthScale, sliderY,
                sliderZ, 3, dimensionFrame, light);

        poseStack.popPose();
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                             float x0, float y0, float x1, float y1, float z,
                             int cell, int frame, int light) {
        float u0 = cell * 0.25F;
        float u1 = u0 + 0.25F;
        float frameTop = (frame * DIMENSION_FRAME_HEIGHT + 1.0F) /
                (DIMENSION_FRAME_COUNT * DIMENSION_FRAME_HEIGHT);
        float frameBottom = (frame * DIMENSION_FRAME_HEIGHT + 1.0F + DIMENSION_CONTENT_HEIGHT) /
                (DIMENSION_FRAME_COUNT * DIMENSION_FRAME_HEIGHT);
        vertex(consumer, matrix, normal, x0, y0, z, u0, frameBottom, light);
        vertex(consumer, matrix, normal, x1, y0, z, u1, frameBottom, light);
        vertex(consumer, matrix, normal, x1, y1, z, u1, frameTop, light);
        vertex(consumer, matrix, normal, x0, y1, z, u0, frameTop, light);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                               float x, float y, float z, float u, float v, int light) {
        consumer.vertex(matrix, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normal, 0.0F, 0.0F, 1.0F)
                .endVertex();
    }
}
