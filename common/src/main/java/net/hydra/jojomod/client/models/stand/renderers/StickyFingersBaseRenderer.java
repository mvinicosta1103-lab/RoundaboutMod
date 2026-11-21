package net.hydra.jojomod.client.models.stand.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.hydra.jojomod.Roundabout;
import net.hydra.jojomod.client.models.stand.StandModel;
import net.hydra.jojomod.entity.stand.StandEntity;
import net.hydra.jojomod.entity.stand.StickyFingersEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class StickyFingersBaseRenderer<M extends StandEntity> extends StandRenderer<StickyFingersEntity>{

    private static final ResourceLocation PART_5_SKIN = new ResourceLocation(Roundabout.MOD_ID,"textures/stand/sticky_fingers/part_5.png");
    private static final ResourceLocation MANGA_SKIN = new ResourceLocation(Roundabout.MOD_ID,"textures/stand/sticky_fingers/manga.png");
    private static final ResourceLocation BLACK_AND_WHITE_SKIN = new ResourceLocation(Roundabout.MOD_ID,"textures/stand/sticky_fingers/black_and_white.png");

    public StickyFingersBaseRenderer(EntityRendererProvider.Context context, StandModel root) {
        super(context, root,0f);
    }

    public static ResourceLocation getSkin(byte BT){
        if (BT == StickyFingersEntity.MANGA_SKIN) {
            return MANGA_SKIN;
        } else if (BT == StickyFingersEntity.BLACK_AND_WHITE) {
            return BLACK_AND_WHITE_SKIN;
        }
        return PART_5_SKIN;
    }

    @Override
    public ResourceLocation getTextureLocation(StickyFingersEntity entity) {
        return getSkin(entity.getSkin());
    }

    @Override
    public void render(StickyFingersEntity mobEntity, float f, float g, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i) {
        float factor = 0.5F + (mobEntity.getSizePercent()/2);
        if (mobEntity.isBaby()) {
            matrixStack.scale(0.5f*factor, 0.5f*factor, 0.5f*factor);
        } else {
            matrixStack.scale(0.87f * factor, 0.87f * factor, 0.87f * factor);
        }
        super.render(mobEntity, f, g, matrixStack, vertexConsumerProvider, i);
    }

    @Nullable
    @Override
    protected RenderType getRenderType(StickyFingersEntity entity, boolean showBody, boolean translucent, boolean showOutline) {
        return super.getRenderType(entity, showBody, true, showOutline);
    }
}