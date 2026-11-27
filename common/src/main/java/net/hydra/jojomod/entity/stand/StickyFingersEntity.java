package net.hydra.jojomod.entity.stand;

import net.hydra.jojomod.entity.projectile.StickyFingersFistEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

public class StickyFingersEntity extends FollowingStandEntity {
    private static final float MAX_ZIPPER_EXTENSION_BLOCKS = 14.0F;
    private float zipperExtensionLength;
    private float oldZipperExtensionLength;

    public float getZipperExtensionLength(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F),
                this.oldZipperExtensionLength, this.zipperExtensionLength);
    }

    @Override
    public void tick() {
        super.tick();
        this.oldZipperExtensionLength = this.zipperExtensionLength;

        float targetLength = 0.0F;
        if (this.level().isClientSide() && this.getUser() != null) {
            StickyFingersFistEntity fist = this.level().getEntitiesOfClass(
                    StickyFingersFistEntity.class,
                    this.getBoundingBox().inflate(16.0D),
                    entity -> entity.getOwner() == this.getUser())
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (fist != null) {
                // Use the projectile's real travel distance, not the offset stand position.
                targetLength = Mth.clamp(fist.getFlightDistance() - 0.35F,
                        0.0F, MAX_ZIPPER_EXTENSION_BLOCKS);
            }
        }

        // Ease toward the moving fist and ease back to zero after a hit or a miss.
        float easing = targetLength > this.zipperExtensionLength ? 0.72F : 0.35F;
        this.zipperExtensionLength = Mth.lerp(easing, this.zipperExtensionLength, targetLength);
        if (Math.abs(this.zipperExtensionLength - targetLength) < 0.02F) {
            this.zipperExtensionLength = targetLength;
        }
    }

    public StickyFingersEntity(EntityType<? extends Mob> entityType, Level world) {
        super(entityType, world);
    }
    public static final byte
            PART_5 = 1,
            MANGA_SKIN = 2,
            BLACK_AND_WHITE = 3,
            SO_RETRO_SKIN = 4;


    public final AnimationState finalPunch = new AnimationState();
    public final AnimationState finalPunch2 = new AnimationState();
    public final AnimationState finalPunch3 = new AnimationState();
    public final AnimationState finalPunchWindup = new AnimationState();
    public final AnimationState zipperFistPrepare = new AnimationState();
    public final AnimationState zipperFistIdle = new AnimationState();
    public final AnimationState zipperFistImpact = new AnimationState();
    public final AnimationState zipperPoleCollapse = new AnimationState();
    public final AnimationState guillotinePunch = new AnimationState();
    public final AnimationState guillotineChop = new AnimationState();
    public final AnimationState guillotineFull = new AnimationState();
    public final AnimationState deathByDisassembly = new AnimationState();
    public final AnimationState hideFists = new AnimationState();
    public final AnimationState impale = new AnimationState();
    public final AnimationState impale2 = new AnimationState();
    public final AnimationState blockGrabAnimation = new AnimationState();
    public final AnimationState blockThrowAnimation = new AnimationState();
    public final AnimationState itemGrabAnimation = new AnimationState();
    public final AnimationState itemThrowAnimation = new AnimationState();
    public final AnimationState blockRetractAnimation = new AnimationState();
    public final AnimationState itemRetractAnimation = new AnimationState();
    public final AnimationState entityGrabAnimation = new AnimationState();
    public final AnimationState bloodSplashWindup = new AnimationState();
    public final AnimationState bloodSplashThrow = new AnimationState();

    public static final byte
            FINAL_1 = 82,
            FINAL_2 = 83,
            IMPALE_2 = 84,
            ZIPPER_FIST_PREPARE = 88,
            ZIPPER_FIST_IDLE = 89,
            ZIPPER_FIST_IMPACT = 90,
            ZIPPER_POLE_COLLAPSE = 100,
            GUILLOTINE_PUNCH = 101,
            GUILLOTINE_CHOP = 102,
            GUILLOTINE_FULL = 103,
            DEATH_BY_DISASSEMBLY = 104,
            BLOOD_SPLASH_WINDUP = 50,
            BLOOD_SPLASH_THROW = 51;
    @Override
    public void setupAnimationStates() {
        super.setupAnimationStates();
        byte animation = getAnimation();
        if (animation != BARRAGE) {
            this.hideFists.startIfStopped(this.tickCount);
        } else {
            this.hideFists.stop();
        }
        if (animation == IMPALE) {
            this.impale.startIfStopped(this.tickCount);
        } else {
            this.impale.stop();
        }
        if (animation == IMPALE_2) {
            this.impale2.startIfStopped(this.tickCount);
        } else {
            this.impale2.stop();
        }
        if (animation == BLOOD_SPLASH_WINDUP) {
            this.bloodSplashWindup.startIfStopped(this.tickCount);
        } else {
            this.bloodSplashWindup.stop();
        }
        if (animation == BLOOD_SPLASH_THROW) {
            this.bloodSplashThrow.startIfStopped(this.tickCount);
        } else {
            this.bloodSplashThrow.stop();
        }

        if (this.getAnimation() == ITEM_GRAB) {
            this.itemGrabAnimation.startIfStopped(this.tickCount);
        } else {
            this.itemGrabAnimation.stop();
        }
        if (this.getAnimation() == ENTITY_GRAB) {
            this.entityGrabAnimation.startIfStopped(this.tickCount);
        } else {
            this.entityGrabAnimation.stop();
        }
        if (this.getAnimation() == ITEM_THROW) {
            this.itemThrowAnimation.startIfStopped(this.tickCount);
        } else {
            this.itemThrowAnimation.stop();
        }
        if (this.getAnimation() == BLOCK_GRAB) {
            this.blockGrabAnimation.startIfStopped(this.tickCount);
        } else {
            this.blockGrabAnimation.stop();
        }
        if (this.getAnimation() == BLOCK_THROW) {
            this.blockThrowAnimation.startIfStopped(this.tickCount);
        } else {
            this.blockThrowAnimation.stop();
        }
        if (this.getAnimation() == BLOCK_RETRACT) {
            this.blockRetractAnimation.startIfStopped(this.tickCount);
        } else {
            this.blockRetractAnimation.stop();
        }

        if (this.getAnimation() == ITEM_RETRACT) {
            this.itemRetractAnimation.startIfStopped(this.tickCount);
        } else {
            this.itemRetractAnimation.stop();
        }
        if (this.getAnimation() == FINAL_ATTACK_WINDUP) {
            this.finalPunchWindup.startIfStopped(this.tickCount);
        } else {
            this.finalPunchWindup.stop();
        }
        if (this.getAnimation() == FINAL_ATTACK) {
            this.finalPunch.startIfStopped(this.tickCount);
        } else {
            this.finalPunch.stop();
        }
        if (this.getAnimation() == FINAL_1) {
            this.finalPunch2.startIfStopped(this.tickCount);
        } else {
            this.finalPunch2.stop();
        }
        if (this.getAnimation() == FINAL_2) {
            this.finalPunch3.startIfStopped(this.tickCount);
        } else {
            this.finalPunch3.stop();
        }
        if (animation == ZIPPER_FIST_PREPARE) {
            this.zipperFistPrepare.startIfStopped(this.tickCount);
        } else {
            this.zipperFistPrepare.stop();
        }
        if (animation == ZIPPER_FIST_IDLE) {
            this.zipperFistIdle.startIfStopped(this.tickCount);
        } else {
            this.zipperFistIdle.stop();
        }
        if (animation == ZIPPER_FIST_IMPACT) {
            this.zipperFistImpact.startIfStopped(this.tickCount);
        } else {
            this.zipperFistImpact.stop();
        }
        if (animation == ZIPPER_POLE_COLLAPSE) {
            this.zipperPoleCollapse.startIfStopped(this.tickCount);
        } else {
            this.zipperPoleCollapse.stop();
        }
        if (animation == GUILLOTINE_PUNCH) {
            this.guillotinePunch.startIfStopped(this.tickCount);
        } else {
            this.guillotinePunch.stop();
        }
        if (animation == GUILLOTINE_CHOP) {
            this.guillotineChop.startIfStopped(this.tickCount);
        } else {
            this.guillotineChop.stop();
        }
        if (animation == GUILLOTINE_FULL) {
            this.guillotineFull.startIfStopped(this.tickCount);
        } else {
            this.guillotineFull.stop();
        }
        if (animation == DEATH_BY_DISASSEMBLY) {
            this.deathByDisassembly.startIfStopped(this.tickCount);
        } else {
            this.deathByDisassembly.stop();
        }
    }

}
