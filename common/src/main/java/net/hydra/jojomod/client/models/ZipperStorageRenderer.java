package net.hydra.jojomod.client.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.hydra.jojomod.entity.objects.ZipperEntity;
import net.hydra.jojomod.entity.objects.ZipperStorageEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class ZipperStorageRenderer extends EntityRenderer<ZipperStorageEntity> {

    private static final double VIEW_DISTANCE_SQR = 7.0D * 7.0D;
    private static final float LENGTH = 0.8F;

    public ZipperStorageRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ZipperStorageEntity entity) {
        return ZipperSpriteAtlas.STORAGE_IDLE.texture();
    }

    @Override
    public void render(ZipperStorageEntity entity, float yaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int unusedLight) {
        if (entity.isMobMode() || entity.getMergedRenderLeader() != entity) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !ZipperStorageEntity.isStickyFingersUser(player)
                || entity.distanceToSqr(player) > VIEW_DISTANCE_SQR) {
            return;
        }
        int light = LevelRenderer.getLightColor(entity.level(), entity.getFrontPos());
        // The supplied zipper frames are 16x48 pixels; keep their world-space ratio at 1:3.
        float baseHalfWidth = LENGTH / 6.0F;
        ZipperEntity.MergeRenderLayout layout = entity.getMergeRenderLayout(baseHalfWidth, LENGTH, false);
        float length = layout.length();
        float open = Mth.lerp(partialTicks, entity.getPrevOpenAnim(), entity.getOpenAnim());
        open = open * open * (3.0F - 2.0F * open);

        Direction face = entity.getFace();
        poseStack.pushPose();
        // Wall zippers are positioned at the block's lower edge, so center them vertically.
        // Floor and ceiling zippers already use a surface-level entity position; adding this
        // offset there makes the whole portal visibly float above the block.
        if (face.getAxis() != Direction.Axis.Y) {
            poseStack.translate(0.0D, ZipperStorageEntity.SIZE / 2.0D, 0.0D);
        }
        if (face.getAxis() == Direction.Axis.Y) {
            poseStack.mulPose(Axis.XP.rotationDegrees(face == Direction.UP ? -90.0F : 90.0F));
        } else {
            float angle = (float) Math.toDegrees(Math.atan2(face.getStepX(), face.getStepZ()));
            poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        }
        poseStack.mulPose(Axis.ZP.rotationDegrees(layout.rotationDegrees()));
        poseStack.translate(layout.centerX(), layout.centerY() - length / 2.0F, 0.0F);

        boolean closing = entity.getState() == ZipperEntity.STATE_CLOSING;
        ZipperSpriteAtlas.Sprite sprite;
        int frame;
        if (closing) {
            sprite = ZipperSpriteAtlas.STORAGE_CLOSE;
            frame = ZipperSpriteAtlas.frameAtProgress(1.0F - open, sprite);
        } else if (open > 0.98F) {
            sprite = ZipperSpriteAtlas.STORAGE_IDLE;
            frame = 0;
        } else {
            sprite = ZipperSpriteAtlas.STORAGE_OPEN;
            frame = ZipperSpriteAtlas.frameAtProgress(open, sprite);
        }
        if (open > 0.05F) {
            ZipperSpriteAtlas.Sprite dimension = ZipperSpriteAtlas.DIMENSION_LOOP;
            int dimensionFrame = ZipperSpriteAtlas.loopFrame(entity.tickCount, partialTicks, dimension, 1);
            ZipperSpriteAtlas.drawRegion(ZipperSpriteAtlas.buffer(buffer, dimension), poseStack.last(),
                    dimension, dimensionFrame, 4.0F, 12.0F, -layout.width() / 4.0F, 0.03F,
                    layout.width() / 4.0F, length - 0.03F, 0.02F, light);
        }
        VertexConsumer consumer = ZipperSpriteAtlas.buffer(buffer, sprite);
        ZipperSpriteAtlas.draw(consumer, poseStack.last(), sprite, frame,
                -layout.width() / 2.0F, 0.0F, layout.width() / 2.0F,
                length, 0.03F, light);

        poseStack.popPose();
    }
}
