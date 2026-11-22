package net.hydra.jojomod.mixin.access.models;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.List;

@Mixin(ModelPart.class)
public interface AccessModelPart {
    @Accessor("cubes")
    List<ModelPart.Cube> roundabout$getCubes();

    @Accessor("children")
    Map<String, ModelPart> roundabout$getChildren();
}
