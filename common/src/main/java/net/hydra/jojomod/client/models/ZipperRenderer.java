package net.hydra.jojomod.client.models;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.hydra.jojomod.Roundabout;
import net.hydra.jojomod.entity.objects.ZipperEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class ZipperRenderer extends EntityRenderer<ZipperEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Roundabout.MOD_ID,
            "textures/entity/zipper_animated.png");

    private static final int SEGMENTS = 16;
    private static final int DIMENSION_FRAME_COUNT = 29;

    private static final int DIMENSION_FRAME_HEIGHT = 16;
    private static final int DIMENSION_CONTENT_HEIGHT = 16;
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
    public boolean shouldRender(ZipperEntity entity, Frustum frustum,
                                double cameraX, double cameraY, double cameraZ) {
        boolean renderWallZippersIndividually = entity.getFace().getAxis() != Direction.Axis.Y
                && entity.getVehicle() == null;
        if ((!renderWallZippersIndividually && entity.getMergedRenderLeader() != entity)
                || entity.getState() == ZipperEntity.STATE_HIDING
                || entity.getState() == ZipperEntity.STATE_TUNNEL
                || entity.isHiddenFromLocalPlayer()) {
            return false;
        }
        if (renderWallZippersIndividually) {
            return super.shouldRender(entity, frustum, cameraX, cameraY, cameraZ);
        }
        if (super.shouldRender(entity, frustum, cameraX, cameraY, cameraZ)) return true;

        // The group is rendered by one leader, but the leader may be outside the view
        // while another connected zipper is still visible (especially around long walls).
        // Build the group's bounds and range from every member before culling the whole path.
        AABB groupBounds = entity.getBoundingBox();
        boolean anyMemberInRange = entity.shouldRender(cameraX, cameraY, cameraZ);
        for (ZipperEntity member : entity.getConnectedZippers()) {
            groupBounds = groupBounds.minmax(member.getBoundingBox());
            anyMemberInRange |= member.shouldRender(cameraX, cameraY, cameraZ);
        }
        return anyMemberInRange && frustum.isVisible(groupBounds.inflate(2.0D));
    }

    @Override
    public void render(ZipperEntity entity, float yaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        boolean renderWallZippersIndividually = entity.getFace().getAxis() != Direction.Axis.Y
                && entity.getVehicle() == null;
        if (!renderWallZippersIndividually && entity.getMergedRenderLeader() != entity) {
            return;
        }
        int state = entity.getState();
        if (state == ZipperEntity.STATE_HIDING || state == ZipperEntity.STATE_TUNNEL
                || entity.isHiddenFromLocalPlayer()) {
            return;
        }
        float rawAge = entity.tickCount - entity.getStateStart() + partialTicks;
        if (state == ZipperEntity.STATE_MINING && rawAge < 0.0F) {
            return;
        }
        float age = Math.max(0.0F, rawAge);
        float open;
        float baseMaxHalf = 0.28F;
        switch (state) {
            case ZipperEntity.STATE_TUNNEL -> {
                open = 1.0F;
                baseMaxHalf = 0.40F;
            }
            case ZipperEntity.STATE_NEUTRAL -> open = 0.0F;
            case ZipperEntity.STATE_OPENING -> open = Mth.clamp(age / ZipperEntity.OPEN_ANIM_TICKS, 0.0F, 1.0F);

            case ZipperEntity.STATE_CLOSING -> open = 1.0F;
            case ZipperEntity.STATE_STORAGE -> open = 0.0F;
            case ZipperEntity.STATE_LATCH_ARMING, ZipperEntity.STATE_LATCH_READY,
                    ZipperEntity.STATE_LATCH_RIDING, ZipperEntity.STATE_LATCH_RETRACTING -> open = 0.0F;
            default -> open = Mth.clamp(age / ZipperEntity.OPEN_ANIM_TICKS, 0.0F, 1.0F);
        }
        open = open * open * (3.0F - 2.0F * open);

        Direction face = entity.getFace();
        boolean flat = face.getAxis() == Direction.Axis.Y && entity.getVehicle() == null;
        // Surface zippers occupy one block. The previous two-block wall sprite
        // overlapped its neighbors, making a removed middle segment look intact.
        float baseLength = (flat || entity.isMini() || renderWallZippersIndividually) ? 1.0F : 2.0F;
        // Imported art is 16x48: retain its 1:3 width-to-length ratio on surfaces.
        float baseHalfWidth = baseLength / 6.0F;
        ZipperEntity.MergeRenderLayout layout = renderWallZippersIndividually
                ? new ZipperEntity.MergeRenderLayout(0.0F, 0.0F, baseHalfWidth * 2.0F, baseLength, 0.0F)
                : entity.getMergeRenderLayout(baseHalfWidth, baseLength, true);
        float length = layout.length();
        float widthScale = layout.width() / (baseHalfWidth * 2.0F);
        float lengthScale = length / baseLength;
        Entity vehicle = entity.getVehicle();
        
        poseStack.pushPose();
        if (flat) {
            poseStack.mulPose(Axis.XP.rotationDegrees(face == Direction.UP ? -90.0F : 90.0F));
        } else if (vehicle instanceof LivingEntity mount) {
            poseStack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTicks, mount.yBodyRotO, mount.yBodyRot)));
            poseStack.translate(0.0F, 0.0F, mount.getBbWidth() * 0.5F + 0.02F);
        } else {
            float angle = (float) Math.toDegrees(Math.atan2(face.getStepX(), face.getStepZ()));
            poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        }
        int dimensionFrame = ((int) ((entity.tickCount + partialTicks) / 2.0F)) % DIMENSION_FRAME_COUNT;
        if (flat) {
            List<ZipperEntity.ZipperPathCell> path = entity.getMergedFloorPath();
            if (path.size() > 1) {
                renderFloorPath(path, open, state, state == ZipperEntity.STATE_CLOSING ? age : -1.0F,
                        entity.tickCount, partialTicks, light, poseStack, buffer);
                poseStack.popPose();
                super.render(entity, yaw, partialTicks, poseStack, buffer, light);
                return;
            }
        }
        poseStack.mulPose(Axis.ZP.rotationDegrees(layout.rotationDegrees()));
        poseStack.translate(layout.centerX(), layout.centerY() - length / 2.0F, 0.0F);

        SpriteFrame spriteFrame = getSpriteFrame(entity, state, age, open, partialTicks);
        if (spriteFrame != null) {
            if (state == ZipperEntity.STATE_TUNNEL) {
                ZipperSpriteAtlas.Sprite dimension = ZipperSpriteAtlas.DIMENSION_LOOP;
                int frame = ZipperSpriteAtlas.loopFrame(entity.tickCount, partialTicks, dimension, 1);
                spriteFrame = new SpriteFrame(ZipperSpriteAtlas.UNZIP_OPEN,
                        ZipperSpriteAtlas.UNZIP_OPEN.frameCount() - 1);
                ZipperSpriteAtlas.drawRegion(ZipperSpriteAtlas.buffer(buffer, dimension), poseStack.last(),
                        dimension, frame, 4.0F, 12.0F, -layout.width() / 4.0F, 0.02F,
                        layout.width() / 4.0F, length - 0.02F, 0.02F, light);
                VertexConsumer zipperConsumer = ZipperSpriteAtlas.buffer(buffer, spriteFrame.sprite());
                ZipperSpriteAtlas.draw(zipperConsumer, poseStack.last(), spriteFrame.sprite(), spriteFrame.frame(),
                        -layout.width() / 2.0F, 0.0F, layout.width() / 2.0F, length, 0.03F, light);
                poseStack.popPose();
                super.render(entity, yaw, partialTicks, poseStack, buffer, light);
                return;
            }
            VertexConsumer spriteConsumer = ZipperSpriteAtlas.buffer(buffer, spriteFrame.sprite());
            ZipperSpriteAtlas.draw(spriteConsumer, poseStack.last(), spriteFrame.sprite(), spriteFrame.frame(),
                    -layout.width() / 2.0F, 0.0F, layout.width() / 2.0F, length,
                    0.025F, light);
            poseStack.popPose();
            super.render(entity, yaw, partialTicks, poseStack, buffer, light);
            return;
        }

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();

        float dimensionZ = 0.005F;
        float teethZ = 0.02F;
        float sliderZ = 0.03F;
        float maxHalf = baseMaxHalf * widthScale;
        float toothWidth = TOOTH_WIDTH * widthScale;
        float segment = length / SEGMENTS;
        for (int i = 0; i < SEGMENTS; i++) {
            float y0 = i * segment;
            float y1 = y0 + segment;
            float lens = (float) Math.pow(Math.sin(Math.PI * (i + 0.5D) / SEGMENTS), 0.8D);
            float segmentOpen = state == ZipperEntity.STATE_CLOSING
                    ? closingSegmentOpen(age, SEGMENTS - 1 - i, SEGMENTS) : open;
            float gap = maxHalf * segmentOpen * lens;

            if (gap > 0.001F) {
                quad(consumer, matrix, normal, -gap, y0, gap, y1, dimensionZ,
                        0, dimensionFrame, light);
            }
            quad(consumer, matrix, normal, -gap - toothWidth, y0, -gap, y1, teethZ, 2, dimensionFrame, light);
            quad(consumer, matrix, normal, gap, y0, gap + toothWidth, y1, teethZ, 2, dimensionFrame, light);
        }
        float closeProgress = state == ZipperEntity.STATE_CLOSING
                ? Mth.clamp(age / ZipperEntity.CLOSE_ANIM_TICKS, 0.0F, 1.0F) : 0.0F;
        float sliderY = length - 0.22F * lengthScale
                - (length - 0.44F * lengthScale) * closeProgress;
        quad(consumer, matrix, normal, -0.07F * widthScale, sliderY, 0.07F * widthScale,
                sliderY + 0.14F * lengthScale,
                sliderZ, 3, dimensionFrame, light);
        quad(consumer, matrix, normal, -0.04F * widthScale, sliderY - 0.12F * lengthScale,
                0.04F * widthScale, sliderY,
                sliderZ, 3, dimensionFrame, light);

        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffer, light);
    }

    private static SpriteFrame getSpriteFrame(ZipperEntity entity, int state, float age,
                                               float open, float partialTicks) {
        if (state == ZipperEntity.STATE_NEUTRAL
                || state == ZipperEntity.STATE_LATCH_ARMING
                || state == ZipperEntity.STATE_LATCH_READY
                || state == ZipperEntity.STATE_LATCH_RIDING
                || state == ZipperEntity.STATE_LATCH_RETRACTING) {
            return new SpriteFrame(ZipperSpriteAtlas.UNZIP_OPEN, 0);
        }
        if (state == ZipperEntity.STATE_OPENING) {
            return new SpriteFrame(ZipperSpriteAtlas.UNZIP_OPEN,
                    ZipperSpriteAtlas.frameAtProgress(open, ZipperSpriteAtlas.UNZIP_OPEN));
        }
        if (state == ZipperEntity.STATE_TUNNEL) {
            return new SpriteFrame(ZipperSpriteAtlas.DIMENSION_LOOP,
                    ZipperSpriteAtlas.loopFrame(entity.tickCount, partialTicks,
                            ZipperSpriteAtlas.DIMENSION_LOOP, 2));
        }
        if (state == ZipperEntity.STATE_CLOSING) {
            float progress = Mth.clamp(age / ZipperEntity.CLOSE_ANIM_TICKS, 0.0F, 1.0F);
            return new SpriteFrame(ZipperSpriteAtlas.UNZIP_CLOSE,
                    ZipperSpriteAtlas.frameAtProgress(progress, ZipperSpriteAtlas.UNZIP_CLOSE));
        }
        if (state == ZipperEntity.STATE_MINING) {
            if (age < ZipperEntity.OPEN_ANIM_TICKS) {
                float progress = Mth.clamp(age / ZipperEntity.OPEN_ANIM_TICKS, 0.0F, 1.0F);
                return new SpriteFrame(ZipperSpriteAtlas.STORAGE_OPEN,
                        ZipperSpriteAtlas.frameAtProgress(progress, ZipperSpriteAtlas.STORAGE_OPEN));
            }
            return new SpriteFrame(ZipperSpriteAtlas.STORAGE_IDLE, 0);
        }
        return null;
    }

    private static void renderFloorPath(List<ZipperEntity.ZipperPathCell> path, float open, int state,
                                        float closeAge, int tick, float partialTicks, int light,
                                        PoseStack poseStack, MultiBufferSource buffers) {
        List<PathEdge> edges = new ArrayList<>();
        Set<ZipperEntity.ZipperPathCell> cells = new HashSet<>(path);
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (ZipperEntity.ZipperPathCell cell : cells) {
            for (int[] direction : directions) {
                ZipperEntity.ZipperPathCell neighbor = new ZipperEntity.ZipperPathCell(
                        cell.u() + direction[0], cell.v() + direction[1]);
                if (cells.contains(neighbor) && comparePathCells(cell, neighbor) < 0) {
                    edges.add(new PathEdge(cell, neighbor));
                }
            }
        }
        // Close outward from the zipper that was activated, so branches and loops retract in waves.
        edges.sort(Comparator
                .comparingDouble((PathEdge edge) -> edge.distanceFromOrigin())
                .thenComparing(edge -> edge.first(), ZipperRenderer::comparePathCells)
                .thenComparing(edge -> edge.second(), ZipperRenderer::comparePathCells));

        for (int index = 0; index < edges.size(); index++) {
            PathEdge edge = edges.get(index);
            float edgeOpen = closeAge < 0.0F ? open : closingSegmentOpen(closeAge, index, edges.size());
            float dx = edge.second().u() - edge.first().u();
            float dy = edge.second().v() - edge.first().v();
            float angle = (float) Math.toDegrees(Math.atan2(-dx, dy));
            ZipperSpriteAtlas.Sprite sprite;
            int spriteFrame;
            if (closeAge >= 0.0F) {
                sprite = ZipperSpriteAtlas.UNZIP_CLOSE;
                spriteFrame = ZipperSpriteAtlas.frameAtProgress(1.0F - edgeOpen, sprite);
            } else if (state == ZipperEntity.STATE_TUNNEL) {
                sprite = ZipperSpriteAtlas.DIMENSION_LOOP;
                spriteFrame = ZipperSpriteAtlas.loopFrame(tick + index, partialTicks, sprite, 2);
            } else {
                sprite = ZipperSpriteAtlas.UNZIP_OPEN;
                spriteFrame = ZipperSpriteAtlas.frameAtProgress(edgeOpen, sprite);
            }
            poseStack.pushPose();
            poseStack.translate((edge.first().u() + edge.second().u()) * 0.5F,
                    (edge.first().v() + edge.second().v()) * 0.5F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
            if (closeAge < 0.0F && state == ZipperEntity.STATE_TUNNEL) {
                ZipperSpriteAtlas.Sprite dimension = ZipperSpriteAtlas.DIMENSION_LOOP;
                int dimensionFrame = ZipperSpriteAtlas.loopFrame(tick + index, partialTicks, dimension, 1);
                sprite = ZipperSpriteAtlas.UNZIP_OPEN;
                spriteFrame = sprite.frameCount() - 1;
                ZipperSpriteAtlas.drawRegion(ZipperSpriteAtlas.buffer(buffers, dimension), poseStack.last(),
                        dimension, dimensionFrame, 4.0F, 12.0F,
                        -1.0F / 12.0F, -0.48F, 1.0F / 12.0F, 0.48F, 0.02F, light);
                VertexConsumer zipperConsumer = ZipperSpriteAtlas.buffer(buffers, sprite);
                ZipperSpriteAtlas.draw(zipperConsumer, poseStack.last(), sprite, spriteFrame,
                        -1.0F / 6.0F, -0.5F, 1.0F / 6.0F, 0.5F, 0.03F, light);
                poseStack.popPose();
                continue;
            }
            VertexConsumer consumer = ZipperSpriteAtlas.buffer(buffers, sprite);
            ZipperSpriteAtlas.draw(consumer, poseStack.last(), sprite, spriteFrame,
                    -1.0F / 6.0F, -0.5F, 1.0F / 6.0F, 0.5F, 0.025F, light);
            poseStack.popPose();
        }
    }

    private static float closingSegmentOpen(float age, int index, int count) {
        if (count <= 0) {
            return 0.0F;
        }
        float slot = age / ZipperEntity.CLOSE_ANIM_TICKS * count - index;
        float amount = 1.0F - Mth.clamp(slot, 0.0F, 1.0F);
        return amount * amount * (3.0F - 2.0F * amount);
    }

    private record PathEdge(ZipperEntity.ZipperPathCell first, ZipperEntity.ZipperPathCell second) {
        private double distanceFromOrigin() {
            double midpointU = (first.u() + second.u()) * 0.5D;
            double midpointV = (first.v() + second.v()) * 0.5D;
            return midpointU * midpointU + midpointV * midpointV;
        }
    }

    private record SpriteFrame(ZipperSpriteAtlas.Sprite sprite, int frame) {}

    private static int comparePathCells(ZipperEntity.ZipperPathCell first,
                                        ZipperEntity.ZipperPathCell second) {
        int uComparison = Integer.compare(first.u(), second.u());
        return uComparison != 0 ? uComparison : Integer.compare(first.v(), second.v());
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
