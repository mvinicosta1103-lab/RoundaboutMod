package net.hydra.jojomod.client.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.hydra.jojomod.Roundabout;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Frame access for the 16x48 Sticky Fingers zipper sprites supplied as PNG/GIF sequences. */
public final class ZipperSpriteAtlas {
    public static final Sprite UNZIP_OPEN = sprite("zipper_unzip_open", 6);
    public static final Sprite UNZIP_CLOSE = sprite("zipper_unzip_close", 7);
    public static final Sprite DIMENSION_LOOP = sprite("zipper_dimension_loop", 61);
    public static final Sprite STORAGE_OPEN = sprite("zipper_storage_open", 6);
    public static final Sprite STORAGE_CLOSE = sprite("zipper_storage_close", 7);
    public static final Sprite STORAGE_IDLE = sprite("zipper_storage_idle", 1);

    private static final int FRAME_HEIGHT = 48;

    private ZipperSpriteAtlas() {}

    private static Sprite sprite(String name, int frames) {
        return new Sprite(new ResourceLocation(Roundabout.MOD_ID,
                "textures/entity/sticky_fingers/" + name + ".png"), frames);
    }

    public static VertexConsumer buffer(MultiBufferSource buffers, Sprite sprite) {
        return buffers.getBuffer(RenderType.entityCutoutNoCull(sprite.texture()));
    }

    public static int frameAtProgress(float progress, Sprite sprite) {
        return Math.max(0, Math.min(sprite.frameCount() - 1,
                Math.round(Math.max(0.0F, Math.min(1.0F, progress)) * (sprite.frameCount() - 1))));
    }

    public static int loopFrame(long tick, float partialTick, Sprite sprite, int ticksPerFrame) {
        return (int) Math.floor((tick + partialTick) / Math.max(1, ticksPerFrame)) % sprite.frameCount();
    }

    public static void draw(VertexConsumer consumer, PoseStack.Pose pose, Sprite sprite, int frame,
                            float x0, float y0, float x1, float y1, float z, int light) {
        drawRegion(consumer, pose, sprite, frame, 0.5F, 15.5F,
                x0, y0, x1, y1, z, light);
    }

    /** Draws a horizontal pixel slice of a frame; useful for showing only the portal's inner fill. */
    public static void drawRegion(VertexConsumer consumer, PoseStack.Pose pose, Sprite sprite, int frame,
                                  float sourceX0, float sourceX1, float x0, float y0,
                                  float x1, float y1, float z, int light) {
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        float textureHeight = FRAME_HEIGHT * sprite.frameCount();
        float vTop = (frame * FRAME_HEIGHT + 0.5F) / textureHeight;
        float vBottom = ((frame + 1) * FRAME_HEIGHT - 0.5F) / textureHeight;
        float u0 = sourceX0 / 16.0F;
        float u1 = sourceX1 / 16.0F;
        vertex(consumer, matrix, normal, x0, y0, z, u0, vBottom, light);
        vertex(consumer, matrix, normal, x1, y0, z, u1, vBottom, light);
        vertex(consumer, matrix, normal, x1, y1, z, u1, vTop, light);
        vertex(consumer, matrix, normal, x0, y1, z, u0, vTop, light);
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

    public record Sprite(ResourceLocation texture, int frameCount) {}
}
