package net.hydra.jojomod.mixin.access.models;

import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(QuadrupedModel.class)
public interface AccessQuadrupedModel {

    @Accessor("body")
    ModelPart roundabout$getBody();

    @Accessor("head")
    ModelPart roundabout$getHead();

    @Accessor("rightFrontLeg")
    ModelPart roundabout$getRightFrontLeg();

    @Accessor("leftFrontLeg")
    ModelPart roundabout$getLeftFrontLeg();

    @Accessor("rightHindLeg")
    ModelPart roundabout$getRightHindLeg();

    @Accessor("leftHindLeg")
    ModelPart roundabout$getLeftHindLeg();
}
