package net.hydra.jojomod.client.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.hydra.jojomod.Roundabout;
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
            "textures/entity/zipper.png");

    private static final double VIEW_DISTANCE_SQR = 7.0D * 7.0D;
    private static final int SEGMENTS = 12;
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
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !ZipperStorageEntity.isStandUser(player)
                || entity.distanceToSqr(player) > VIEW_DISTANCE_SQR) {
            return;
        }
        int light = LevelRenderer.getLightColor(entity.level(), entity.getFrontPos());
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
        poseStack.translate(0.0F, -LENGTH / 2.0F, 0.0F);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        float z = 0.005F;
        float segment = LENGTH / SEGMENTS;

        for (int i = 0; i < SEGMENTS; i++) {
            float y0 = i * segment;
            float y1 = y0 + segment;
            float lens = (float) Math.pow(Math.sin(Math.PI * (i + 0.5D) / SEGMENTS), 0.8D);
            float gap = MAX_HALF * open * lens;
            if (gap > 0.001F) {
                quad(consumer, matrix, normal, -gap, y0, gap, y1, z, 0, light);
            }
            quad(consumer, matrix, normal, -gap - TOOTH_WIDTH, y0, -gap, y1, z, 2, light);
            quad(consumer, matrix, normal, gap, y0, gap + TOOTH_WIDTH, y1, z, 2, light);
            float clothInner = gap + TOOTH_WIDTH;
            if (clothInner < HALF_WIDTH) {
                quad(consumer, matrix, normal, -HALF_WIDTH, y0, -clothInner, y1, z, 1, light);
                quad(consumer, matrix, normal, clothInner, y0, HALF_WIDTH, y1, z, 1, light);
            }
        }
        float sliderY = LENGTH - 0.2F;
        quad(consumer, matrix, normal, -0.06F, sliderY, 0.06F, sliderY + 0.12F, z + 0.003F, 3, light);
        quad(consumer, matrix, normal, -0.035F, sliderY - 0.1F, 0.035F, sliderY, z + 0.003F, 3, light);

        poseStack.popPose();
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                             float x0, float y0, float x1, float y1, float z, int cell, int light) {
        float u0 = cell * 0.25F;
        float u1 = u0 + 0.25F;
        vertex(consumer, matrix, normal, x0, y0, z, u0, 1.0F, light);
        vertex(consumer, matrix, normal, x1, y0, z, u1, 1.0F, light);
        vertex(consumer, matrix, normal, x1, y1, z, u1, 0.0F, light);
        vertex(consumer, matrix, normal, x0, y1, z, u0, 0.0F, light);
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
