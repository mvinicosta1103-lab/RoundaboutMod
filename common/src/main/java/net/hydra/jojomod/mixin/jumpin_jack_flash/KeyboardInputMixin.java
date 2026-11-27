package net.hydra.jojomod.mixin.jumpin_jack_flash;

import net.hydra.jojomod.event.powers.jumpingJackFlash.ZeroGravityAccess;
import net.hydra.jojomod.event.powers.jumpingJackFlash.ZeroGravityState;
import net.hydra.jojomod.event.powers.jumpingJackFlash.ZeroGravitySystem;
import net.hydra.jojomod.stand.powers.PowersStickyFingers;
import net.hydra.jojomod.access.IStickyFingersDismemberment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {

    @Inject(method = "tick", at = @At("TAIL"))
    private void jjf$limitZeroGravityAirControl(
            boolean slowDown,
            float slowDownFactor,
            CallbackInfo ci
    ) {
        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null) {
            return;
        }

        if (player instanceof IStickyFingersDismemberment dismemberment
                && (dismemberment.roundabout$getStickyFingersDismemberment()
                & PowersStickyFingers.ZIPPER_PART_HEAD) != 0) {
            this.forwardImpulse = -this.forwardImpulse;
            this.leftImpulse = -this.leftImpulse;
        }

        if (!(player instanceof ZeroGravityAccess access)) {
            return;
        }

        ZeroGravityState state = access.jjf$getZeroGravityState();

        if (!state.isZeroGravity()) {
            return;
        }

        
        if (ZeroGravitySystem.isTouchingGround(player)) {
            return;
        }

        
        
        this.leftImpulse *= 0.08F;
        this.forwardImpulse *= 0.08F;
    }

}
