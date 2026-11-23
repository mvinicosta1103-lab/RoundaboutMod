package net.hydra.jojomod.mixin.access;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface AccessEntity {
    @Accessor("maxUpStep")
    float roundabout$getMaxUpStep();

    @Accessor("maxUpStep")
    void roundabout$setMaxUpStep(float maxUpStep);
}
