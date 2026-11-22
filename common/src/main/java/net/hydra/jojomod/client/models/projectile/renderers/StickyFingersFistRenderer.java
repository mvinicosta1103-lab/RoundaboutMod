package net.hydra.jojomod.client.models.projectile.renderers;

import net.hydra.jojomod.entity.projectile.StickyFingersFistEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;

/**
 * The fist projectile keeps its collision and latch behavior, but has no sprite.
 * Sticky Fingers' stand animation now supplies the visual for Zip Punch.
 */
public class StickyFingersFistRenderer extends EntityRenderer<StickyFingersFistEntity> {
    private static final ResourceLocation UNUSED_TEXTURE = new ResourceLocation(
            "minecraft", "textures/atlas/blocks.png");

    public StickyFingersFistRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(StickyFingersFistEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        // Deliberately invisible: the stand arm is animated for the attack.
    }

    @Override
    public ResourceLocation getTextureLocation(StickyFingersFistEntity entity) {
        return UNUSED_TEXTURE;
    }
}
