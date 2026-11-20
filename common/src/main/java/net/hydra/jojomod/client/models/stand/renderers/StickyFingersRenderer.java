package net.hydra.jojomod.client.models.stand.renderers;

import net.hydra.jojomod.client.models.layers.ModEntityRendererClient;
import net.hydra.jojomod.client.models.stand.StickyFingersModel;
import net.hydra.jojomod.entity.stand.StickyFingersEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class StickyFingersRenderer extends StickyFingersBaseRenderer<StickyFingersEntity> {

    public StickyFingersRenderer(EntityRendererProvider.Context context) {
        super(context, new StickyFingersModel<>(context.bakeLayer(ModEntityRendererClient.STICKY_FINGERS_LAYER)));
    }
}
