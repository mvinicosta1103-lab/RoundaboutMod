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
    // Each of the four atlas cells is a Minecraft-sized 16x16 pixel tile.
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
        if (entity.getMergedRenderLeader() != entity
                || entity.getState() == ZipperEntity.STATE_HIDING
                || entity.isHiddenFromLocalPlayer()) {
            return false;
        }
        if (super.shouldRender(entity, frustum, cameraX, cameraY, cameraZ)) {
            return true;
        }

        // The leader's own hitbox is tiny compared with the connected zipper path.
        // Re-test visibility against the bounds of the whole rendered group.
        if (!entity.shouldRender(cameraX, cameraY, cameraZ)) {
            return false;
        }
        AABB groupBounds = entity.getBoundingBox();
        for (ZipperEntity member : entity.getConnectedZippers()) {
            groupBounds = groupBounds.minmax(member.getBoundingBox());
        }
        return frustum.isVisible(groupBounds.inflate(2.0D));
    }

    @Override
    public void render(ZipperEntity entity, float yaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int light) {
        if (entity.getMergedRenderLeader() != entity) {
            return;
        }
        int state = entity.getState();
        if (state == ZipperEntity.STATE_HIDING || entity.isHiddenFromLocalPlayer()) {
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
            // Closing is animated segment by segment below, starting at the slider.
            case ZipperEntity.STATE_CLOSING -> open = 1.0F;
            case ZipperEntity.STATE_STORAGE -> open = 0.0F;
            default -> open = Mth.clamp(age / ZipperEntity.OPEN_ANIM_TICKS, 0.0F, 1.0F);
        }
        open = open * open * (3.0F - 2.0F * open);

        Direction face = entity.getFace();
        boolean flat = face.getAxis() == Direction.Axis.Y && entity.getVehicle() == null;
        float baseLength = (flat || entity.isMini()) ? 1.0F : 2.0F;
        float baseHalfWidth = 0.46F;
        ZipperEntity.MergeRenderLayout layout = entity.getMergeRenderLayout(baseHalfWidth, baseLength, true);
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
                VertexConsumer pathConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
                renderFloorPath(path, 0.40F, open,
                        state == ZipperEntity.STATE_CLOSING ? age : -1.0F, dimensionFrame, light,
                        poseStack, pathConsumer);
                poseStack.popPose();
                super.render(entity, yaw, partialTicks, poseStack, buffer, light);
                return;
            }
        }
        poseStack.mulPose(Axis.ZP.rotationDegrees(layout.rotationDegrees()));
        poseStack.translate(layout.centerX(), layout.centerY() - length / 2.0F, 0.0F);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        // Keep the dimension video behind the zipper teeth in the same cutout pass.
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

    private static void renderFloorPath(List<ZipperEntity.ZipperPathCell> path, float maxHalf,
                                        float open, float closeAge, int frame, int light, PoseStack poseStack,
                                        VertexConsumer consumer) {
        Set<ZipperEntity.ZipperPathCell> cells = new HashSet<>(path);
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        List<PathEdge> edges = new ArrayList<>();
        for (ZipperEntity.ZipperPathCell cell : path) {
            for (int[] direction : directions) {
                ZipperEntity.ZipperPathCell neighbor = new ZipperEntity.ZipperPathCell(
                        cell.u() + direction[0], cell.v() + direction[1]);
                if (!cells.contains(neighbor) || comparePathCells(cell, neighbor) >= 0) {
                    continue;
                }
                edges.add(new PathEdge(cell, neighbor));
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
            poseStack.pushPose();
            poseStack.translate((edge.first().u() + edge.second().u()) * 0.5F,
                    (edge.first().v() + edge.second().v()) * 0.5F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
            PoseStack.Pose pose = poseStack.last();
            Matrix4f matrix = pose.pose();
            Matrix3f normal = pose.normal();
            float gap = maxHalf * edgeOpen;
            if (gap > 0.001F) {
                quad(consumer, matrix, normal, -gap, -0.5F, gap, 0.5F,
                        0.005F, 0, frame, light);
            }
            quad(consumer, matrix, normal, -gap - TOOTH_WIDTH, -0.5F, -gap, 0.5F,
                    0.02F, 2, frame, light);
            quad(consumer, matrix, normal, gap, -0.5F, gap + TOOTH_WIDTH, 0.5F,
                    0.02F, 2, frame, light);
            poseStack.popPose();
        }

        for (ZipperEntity.ZipperPathCell cell : path) {
            ZipperEntity.ZipperPathCell onlyNeighbor = null;
            int degree = 0;
            for (int[] direction : directions) {
                ZipperEntity.ZipperPathCell neighbor = new ZipperEntity.ZipperPathCell(
                        cell.u() + direction[0], cell.v() + direction[1]);
                if (cells.contains(neighbor)) {
                    degree++;
                    onlyNeighbor = neighbor;
                }
            }
            if (degree != 1 || onlyNeighbor == null) {
                continue;
            }
            if (closeAge >= 0.0F) {
                int edgeIndex = -1;
                for (int i = 0; i < edges.size(); i++) {
                    PathEdge edge = edges.get(i);
                    if ((edge.first().equals(cell) && edge.second().equals(onlyNeighbor))
                            || (edge.second().equals(cell) && edge.first().equals(onlyNeighbor))) {
                        edgeIndex = i;
                        break;
                    }
                }
                if (edgeIndex < 0 || closingSegmentOpen(closeAge, edgeIndex, edges.size()) <= 0.01F) {
                    continue;
                }
            }
            float dx = cell.u() - onlyNeighbor.u();
            float dy = cell.v() - onlyNeighbor.v();
            float angle = (float) Math.toDegrees(Math.atan2(-dx, dy));
            poseStack.pushPose();
            poseStack.translate(cell.u(), cell.v(), 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
            PoseStack.Pose pose = poseStack.last();
            Matrix4f matrix = pose.pose();
            Matrix3f normal = pose.normal();
            quad(consumer, matrix, normal, -0.07F, -0.07F, 0.07F, 0.07F,
                    0.03F, 3, frame, light);
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
