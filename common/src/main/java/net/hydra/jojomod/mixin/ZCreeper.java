package net.hydra.jojomod.mixin;

import net.hydra.jojomod.stand.powers.PowersStickyFingers;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Creeper.class)
public abstract class ZCreeper {
    @Inject(method = "explodeCreeper()V", at = @At("HEAD"), cancellable = true, require = 0)
    private void roundabout$stopExplosionWithoutLimbs(CallbackInfo ci) {
        Creeper creeper = (Creeper) (Object) this;
        if (PowersStickyFingers.hasNoLimbs(creeper)) {
            creeper.setSwellDir(-1);
            ci.cancel();
        }
    }
}
