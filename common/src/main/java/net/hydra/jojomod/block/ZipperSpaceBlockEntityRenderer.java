package net.hydra.jojomod.block;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** The opened cell is empty; the split debris entities provide the visible block fragments. */
public class ZipperSpaceBlockEntityRenderer implements BlockEntityRenderer<ZipperSpaceBlockEntity> {

    public ZipperSpaceBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ZipperSpaceBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        // Do not paint an intact fake block over the opening: everyone sees the detached halves.
    }
}
