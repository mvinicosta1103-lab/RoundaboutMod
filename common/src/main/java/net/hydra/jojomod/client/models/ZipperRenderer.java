package net.hydra.jojomod.client.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.hydra.jojomod.Roundabout;
import net.hydra.jojomod.entity.objects.ZipperEntity;
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

/**Draws Sticky Fingers' zipper procedurally (no model file): a row of golden teeth and a dark
 * opening in between that widens as the zipper unzips. TEMPORARY look until a real model exists.
 *
 * Texture layout (zipper.png, 32x16): 4 cells of 8x16 = [opening | (unused) | teeth | slider].
 * Only the golden teeth, the dark opening and the slider are drawn, there is no cloth.*/
public class ZipperRenderer extends EntityRenderer<ZipperEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Roundabout.MOD_ID,
            "textures/entity/zipper.png");

    private static final int SEGMENTS = 16;
    private static final float TOOTH_WIDTH = 0.06F;

    public ZipperRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ZipperEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(ZipperEntity entity, float yaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        int state = entity.getState();
        if (state == ZipperEntity.STATE_HIDING) {
            return;
        }
        float age = Math.max(0.0F, entity.tickCount - entity.getStateStart() + partialTicks);
        float open;
        float maxHalf = 0.28F;
        switch (state) {
            case ZipperEntity.STATE_TUNNEL -> {
                open = 1.0F;
                maxHalf = 0.40F;
            }
            case ZipperEntity.STATE_CLOSING -> open = 1.0F - Mth.clamp(age / ZipperEntity.CLOSE_ANIM_TICKS, 0.0F, 1.0F);
            default -> open = Mth.clamp(age / ZipperEntity.OPEN_ANIM_TICKS, 0.0F, 1.0F);
        }
        open = open * open * (3.0F - 2.0F * open);

        Direction face = entity.getFace();
        boolean flat = face.getAxis() == Direction.Axis.Y;
        float length = flat ? 1.0F : 2.0F;
        
        poseStack.pushPose();
        if (flat) {
            poseStack.mulPose(Axis.XP.rotationDegrees(face == Direction.UP ? -90.0F : 90.0F));
            poseStack.translate(0.0F, -length / 2.0F, 0.0F);
        } else {
            float angle = (float) Math.toDegrees(Math.atan2(face.getStepX(), face.getStepZ()));
            poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        }

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        float z = 0.005F;
        float segment = length / SEGMENTS;

        for (int i = 0; i < SEGMENTS; i++) {
            float y0 = i * segment;
            float y1 = y0 + segment;
            float lens = (float) Math.pow(Math.sin(Math.PI * (i + 0.5D) / SEGMENTS), 0.8D);
            float gap = maxHalf * open * lens;

            //opening
            if (gap > 0.001F) {
                quad(consumer, matrix, normal, -gap, y0, gap, y1, z, 0, light);
            }
            //teeth
            quad(consumer, matrix, normal, -gap - TOOTH_WIDTH, y0, -gap, y1, z, 2, light);
            quad(consumer, matrix, normal, gap, y0, gap + TOOTH_WIDTH, y1, z, 2, light);
        }
        //slider at the top of the opening and its pull tab
        float sliderY = length - 0.22F;
        quad(consumer, matrix, normal, -0.07F, sliderY, 0.07F, sliderY + 0.14F, z + 0.003F, 3, light);
        quad(consumer, matrix, normal, -0.04F, sliderY - 0.12F, 0.04F, sliderY, z + 0.003F, 3, light);

        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffer, light);
    }

    /**One quad facing local +Z using one of the 4 cells of the texture.*/
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
