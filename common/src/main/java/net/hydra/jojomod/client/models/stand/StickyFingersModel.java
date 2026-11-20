// Sticky Fingers model, generated from stickyfingers.bbmodel
// Exported for Minecraft version 1.17+ for Yarn
// Paste this class into your mod and generate all required imports
package net.hydra.jojomod.client.models.stand;

import net.hydra.jojomod.client.models.stand.animations.KingCrimsonAnimations;
import net.hydra.jojomod.client.models.stand.animations.StandAnimations;
import net.hydra.jojomod.client.models.stand.animations.StarPlatinumAnimations;
import net.hydra.jojomod.client.models.stand.animations.TheWorldAnimations;
import net.hydra.jojomod.entity.stand.StickyFingersEntity;
import net.hydra.jojomod.entity.stand.TheWorldEntity;
import net.hydra.jojomod.event.powers.StandPowers;
import net.hydra.jojomod.stand.powers.PowersStickyFingers;
import net.hydra.jojomod.stand.powers.PowersTheWorld;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;


public class StickyFingersModel<T extends StickyFingersEntity> extends StandModel<T> {
    public StickyFingersModel(ModelPart root) {
        this.stand = root.getChild("stand");
        this.head = stand.getChild("stand2").getChild("head");
        this.body = stand.getChild("stand2").getChild("body");
        this.leftHand = stand.getChild("stand2").getChild("body").getChild("body2")
                .getChild("torso").getChild("upper_chest").getChild("left_arm").getChild("lower_left_arm");
        this.rightHand = stand.getChild("stand2").getChild("body").getChild("body2")
                .getChild("torso").getChild("upper_chest").getChild("right_arm").getChild("lower_right_arm");
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition stand = partdefinition.addOrReplaceChild("stand", CubeListBuilder.create(), PartPose.offset(0.0F, 7.0F, 0.0F));
        PartDefinition stand2 = stand.addOrReplaceChild("stand2", CubeListBuilder.create(), PartPose.offset(0.0F, 17.0F, 0.0F));
        PartDefinition head = stand2.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0.0F, -24.15F, 0.0F));
        PartDefinition head2 = head.addOrReplaceChild("head2", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -7.85F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)).texOffs(0, 16).addBox(-4.0F, -7.85F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.3F)).texOffs(0, 32).addBox(-4.0F, -7.85F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.45F)), PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition body = stand2.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, -24.0F, 0.0F));
        PartDefinition body2 = body.addOrReplaceChild("body2", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition torso = body2.addOrReplaceChild("torso", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition upper_chest = torso.addOrReplaceChild("upper_chest", CubeListBuilder.create(), PartPose.offset(0.0F, 6.0F, 0.0F));
        PartDefinition upper_chest_only = upper_chest.addOrReplaceChild("upper_chest_only", CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -6.0F, -2.0F, 8.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).texOffs(32, 10).addBox(-4.0F, -6.0F, -2.0F, 8.0F, 6.0F, 4.0F, new CubeDeformation(0.23F)), PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition right_arm = upper_chest.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-4.0F, -5.25F, 0.0F));
        PartDefinition upper_right_arm = right_arm.addOrReplaceChild("upper_right_arm", CubeListBuilder.create().texOffs(16, 48).addBox(-4.0F, -0.85F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.01F)).texOffs(48, 40).addBox(-4.0F, -0.85F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(0.0F, 0.1F, 0.0F));
        PartDefinition rzip1 = upper_right_arm.addOrReplaceChild("rzip1", CubeListBuilder.create().texOffs(64, 40).addBox(-2.0F, -6.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(-0.25F)), PartPose.offset(-2.0F, 5.0F, 0.0F));
        PartDefinition rzip3 = upper_right_arm.addOrReplaceChild("rzip3", CubeListBuilder.create().texOffs(64, 40).addBox(-2.0F, -6.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(-0.25F)), PartPose.offset(-2.0F, 5.0F, 0.0F));
        PartDefinition lower_right_arm = right_arm.addOrReplaceChild("lower_right_arm", CubeListBuilder.create().texOffs(48, 50).addBox(-2.0F, -0.25F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.022F)).texOffs(16, 58).addBox(-2.0F, -0.25F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.11F)), PartPose.offset(-2.0F, 5.5F, 0.0F));
        PartDefinition cube_r1 = lower_right_arm.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(48, 70).addBox(0.1F, -4.25F, -1.5F, 1.0F, 4.0F, 3.0F, new CubeDeformation(0.11F)), PartPose.offsetAndRotation(-2.25F, 4.5F, 0.0F, 0.0F, 0.0F, -0.0873F));
        PartDefinition rzip2 = lower_right_arm.addOrReplaceChild("rzip2", CubeListBuilder.create(), PartPose.offset(0.0F, -0.4F, 0.0F));
        PartDefinition cube_r2 = rzip2.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(64, 40).addBox(-2.0F, -6.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(-0.25F)), PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0.0F, 3.1416F, 0.0F));
        PartDefinition rzip4 = lower_right_arm.addOrReplaceChild("rzip4", CubeListBuilder.create(), PartPose.offset(0.0F, -0.4F, 0.0F));
        PartDefinition cube_r3 = rzip4.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(64, 40).addBox(-2.0F, -6.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(-0.25F)), PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, 0.0F, 3.1416F, 0.0F));
        PartDefinition left_arm = upper_chest.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(4.0F, -5.25F, 0.0F));
        PartDefinition upper_left_arm = left_arm.addOrReplaceChild("upper_left_arm", CubeListBuilder.create().texOffs(32, 51).addBox(0.0F, -0.75F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.01F)).texOffs(56, 0).addBox(0.0F, -0.75F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition lzip1 = upper_left_arm.addOrReplaceChild("lzip1", CubeListBuilder.create().texOffs(64, 40).mirror().addBox(-2.0F, -6.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(-0.25F)).mirror(false), PartPose.offset(2.0F, 5.1F, 0.0F));
        PartDefinition lzip3 = upper_left_arm.addOrReplaceChild("lzip3", CubeListBuilder.create().texOffs(64, 40).mirror().addBox(-2.0F, -6.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(-0.25F)).mirror(false), PartPose.offset(2.0F, 5.1F, 0.0F));
        PartDefinition lower_left_arm = left_arm.addOrReplaceChild("lower_left_arm", CubeListBuilder.create().texOffs(56, 10).addBox(-2.0F, -0.25F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.022F)).texOffs(0, 59).addBox(-2.0F, -0.25F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.11F)), PartPose.offset(2.0F, 5.5F, 0.0F));
        PartDefinition cube_r4 = lower_left_arm.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(56, 70).addBox(-0.85F, -4.25F, -1.5F, 1.0F, 4.0F, 3.0F, new CubeDeformation(0.11F)), PartPose.offsetAndRotation(2.0F, 4.5F, 0.0F, 0.0F, 0.0F, 0.0873F));
        PartDefinition lzip2 = lower_left_arm.addOrReplaceChild("lzip2", CubeListBuilder.create(), PartPose.offset(0.0F, 5.6F, 0.0F));
        PartDefinition cube_r5 = lzip2.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(64, 40).mirror().addBox(-2.0F, -6.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(-0.25F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 3.1416F, 0.0F));
        PartDefinition lzip4 = lower_left_arm.addOrReplaceChild("lzip4", CubeListBuilder.create(), PartPose.offset(0.0F, 5.6F, 0.0F));
        PartDefinition cube_r6 = lzip4.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(64, 40).mirror().addBox(-2.0F, -6.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(-0.25F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 3.1416F, 0.0F));
        PartDefinition lower_chest = torso.addOrReplaceChild("lower_chest", CubeListBuilder.create(), PartPose.offset(0.0F, 6.0F, 0.0F));
        PartDefinition lower_torso = lower_chest.addOrReplaceChild("lower_torso", CubeListBuilder.create().texOffs(32, 30).addBox(-4.0F, -6.0F, -2.0F, 8.0F, 6.0F, 4.0F, new CubeDeformation(0.22F)).texOffs(32, 20).addBox(-4.0F, -6.0F, -2.0F, 8.0F, 6.0F, 4.0F, new CubeDeformation(0.03F)), PartPose.offset(0.0F, 6.0F, 0.0F));
        PartDefinition cube_r7 = lower_torso.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(32, 71).addBox(-2.0F, -2.0F, 1.0F, 4.0F, 5.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.5F, -3.5F, -0.1745F, 0.0F, 0.0F));
        PartDefinition legs = body2.addOrReplaceChild("legs", CubeListBuilder.create(), PartPose.offset(0.0F, 12.0F, 0.0F));
        PartDefinition right_leg = legs.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-2.0F, -1.0F, 0.0F));
        PartDefinition upper_right_leg = right_leg.addOrReplaceChild("upper_right_leg", CubeListBuilder.create().texOffs(64, 50).addBox(-2.0F, 1.0F, -1.9999F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.25F)).texOffs(16, 68).addBox(-2.0F, 1.0F, -1.9998F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition lower_right_leg = right_leg.addOrReplaceChild("lower_right_leg", CubeListBuilder.create().texOffs(32, 40).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.02F)).texOffs(48, 60).addBox(-2.0F, -0.0001F, -1.9998F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.201F)).texOffs(40, 71).addBox(-1.565F, -1.675F, -2.2F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.2F)), PartPose.offset(0.0F, 7.0F, 0.0F));
        PartDefinition left_leg = legs.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(2.0F, -1.0F, 0.0F));
        PartDefinition upper_left_leg = left_leg.addOrReplaceChild("upper_left_leg", CubeListBuilder.create().texOffs(64, 68).addBox(-2.0F, 1.0F, -1.9998F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.251F)).texOffs(64, 59).addBox(-2.0F, 1.0F, -1.9998F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition lower_left_leg = left_leg.addOrReplaceChild("lower_left_leg", CubeListBuilder.create().texOffs(0, 48).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.01F)).texOffs(32, 61).addBox(-2.0F, 0.0F, -1.9999F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.202F)).texOffs(40, 71).addBox(-1.435F, -1.675F, -2.2F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.2F)), PartPose.offset(0.0F, 7.0F, 0.0F));
        PartDefinition BAM = stand2.addOrReplaceChild("BAM", CubeListBuilder.create(), PartPose.offset(0.0F, -17.0F, -4.0F));
        PartDefinition RightArmBAM = BAM.addOrReplaceChild("RightArmBAM", CubeListBuilder.create(), PartPose.offset(-12.0F, -8.0F, 1.0F));
        PartDefinition cube_r8 = RightArmBAM.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(56, 20).mirror().addBox(-10.75F, -1.25F, -5.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-2.25F, 7.25F, -1.0F, -1.5708F, 0.0F, 1.5708F));
        PartDefinition cube_r9 = RightArmBAM.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(56, 30).addBox(-10.75F, -2.25F, -5.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(-2.25F, 7.25F, -2.0F, -1.5708F, 0.0F, 1.5708F));
        PartDefinition cube_r10 = RightArmBAM.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(0, 69).addBox(-10.75F, -4.25F, -5.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.25F, 7.25F, -1.0F, -1.5708F, 0.0F, 1.5708F));
        PartDefinition RightArmBAM2 = BAM.addOrReplaceChild("RightArmBAM2", CubeListBuilder.create(), PartPose.offset(-14.5F, -2.75F, 0.0F));
        PartDefinition cube_r11 = RightArmBAM2.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(56, 20).mirror().addBox(-5.5F, -1.25F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.25F, 3.0F, 0.0F, -1.5708F, 0.0F, 1.5708F));
        PartDefinition cube_r12 = RightArmBAM2.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(56, 30).addBox(-5.5F, -2.25F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(0.25F, 3.0F, -1.0F, -1.5708F, 0.0F, 1.5708F));
        PartDefinition cube_r13 = RightArmBAM2.addOrReplaceChild("cube_r13", CubeListBuilder.create().texOffs(0, 69).addBox(-5.5F, -4.25F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.25F, 3.0F, 0.0F, -1.5708F, 0.0F, 1.5708F));
        PartDefinition RightArmBAM3 = BAM.addOrReplaceChild("RightArmBAM3", CubeListBuilder.create(), PartPose.offset(-13.625F, 0.75F, 0.0F));
        PartDefinition cube_r14 = RightArmBAM3.addOrReplaceChild("cube_r14", CubeListBuilder.create().texOffs(56, 20).mirror().addBox(0.25F, -1.25F, -4.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-0.625F, -0.5F, 0.0F, -1.5708F, 0.0F, 1.5708F));
        PartDefinition cube_r15 = RightArmBAM3.addOrReplaceChild("cube_r15", CubeListBuilder.create().texOffs(56, 30).addBox(0.25F, -2.25F, -4.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(-0.625F, -0.5F, -1.0F, -1.5708F, 0.0F, 1.5708F));
        PartDefinition cube_r16 = RightArmBAM3.addOrReplaceChild("cube_r16", CubeListBuilder.create().texOffs(0, 69).addBox(0.25F, -4.25F, -4.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.625F, -0.5F, 0.0F, -1.5708F, 0.0F, 1.5708F));
        PartDefinition LeftArmBAM = BAM.addOrReplaceChild("LeftArmBAM", CubeListBuilder.create(), PartPose.offset(12.0F, -9.0F, 0.0F));
        PartDefinition cube_r17 = LeftArmBAM.addOrReplaceChild("cube_r17", CubeListBuilder.create().texOffs(56, 20).addBox(-3.25F, -1.25F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.75F, -1.75F, 0.0F, -1.5708F, 0.0F, -1.5708F));
        PartDefinition cube_r18 = LeftArmBAM.addOrReplaceChild("cube_r18", CubeListBuilder.create().texOffs(56, 30).mirror().addBox(-3.25F, -2.25F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.3F)).mirror(false), PartPose.offsetAndRotation(-0.75F, -1.75F, -1.0F, -1.5708F, 0.0F, -1.5708F));
        PartDefinition cube_r19 = LeftArmBAM.addOrReplaceChild("cube_r19", CubeListBuilder.create().texOffs(0, 69).mirror().addBox(-3.25F, -4.25F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-0.75F, -1.75F, 0.0F, -1.5708F, 0.0F, -1.5708F));
        PartDefinition LeftArmBAM4 = BAM.addOrReplaceChild("LeftArmBAM4", CubeListBuilder.create(), PartPose.offset(14.5F, -2.75F, 0.0F));
        PartDefinition cube_r20 = LeftArmBAM4.addOrReplaceChild("cube_r20", CubeListBuilder.create().texOffs(56, 20).addBox(1.5F, -1.25F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.25F, 3.0F, 0.0F, -1.5708F, 0.0F, -1.5708F));
        PartDefinition cube_r21 = LeftArmBAM4.addOrReplaceChild("cube_r21", CubeListBuilder.create().texOffs(56, 30).mirror().addBox(1.5F, -2.25F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.3F)).mirror(false), PartPose.offsetAndRotation(-0.25F, 3.0F, -1.0F, -1.5708F, 0.0F, -1.5708F));
        PartDefinition cube_r22 = LeftArmBAM4.addOrReplaceChild("cube_r22", CubeListBuilder.create().texOffs(0, 69).mirror().addBox(1.5F, -4.25F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-0.25F, 3.0F, 0.0F, -1.5708F, 0.0F, -1.5708F));
        PartDefinition LeftArmBAM3 = BAM.addOrReplaceChild("LeftArmBAM3", CubeListBuilder.create(), PartPose.offset(13.625F, 0.75F, 0.0F));
        PartDefinition cube_r23 = LeftArmBAM3.addOrReplaceChild("cube_r23", CubeListBuilder.create().texOffs(56, 20).addBox(-4.25F, -1.25F, -4.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.625F, -0.5F, 0.0F, -1.5708F, 0.0F, -1.5708F));
        PartDefinition cube_r24 = LeftArmBAM3.addOrReplaceChild("cube_r24", CubeListBuilder.create().texOffs(56, 30).mirror().addBox(-4.25F, -2.25F, -4.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.3F)).mirror(false), PartPose.offsetAndRotation(0.625F, -0.5F, -1.0F, -1.5708F, 0.0F, -1.5708F));
        PartDefinition cube_r25 = LeftArmBAM3.addOrReplaceChild("cube_r25", CubeListBuilder.create().texOffs(0, 69).mirror().addBox(-4.25F, -4.25F, -4.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.625F, -0.5F, 0.0F, -1.5708F, 0.0F, -1.5708F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }


    StandPowers Power = new PowersStickyFingers(null);

    @Override
    public void setupAnim(T pEntity, float pLimbSwing, float pLimbSwingAmount, float pAgeInTicks, float pNetHeadYaw, float pHeadPitch) {
        super.setupAnim(pEntity, pLimbSwing, pLimbSwingAmount, pAgeInTicks, pNetHeadYaw, pHeadPitch);
        defaultModifiers(pEntity);
        defaultAnimations(pEntity, pAgeInTicks, 1/((float) Power.getBarrageWindup() /20));
        this.animate(pEntity.hideFists, StandAnimations.HIDE_FISTS, pAgeInTicks, 1f);
        this.animate(pEntity.impale, StandAnimations.IMPALE, pAgeInTicks, 1.04f);
        this.animate(pEntity.finalPunchWindup, KingCrimsonAnimations.Chop_Start, pAgeInTicks, 0.8f);
        this.animate(pEntity.finalPunch, StarPlatinumAnimations.FINAL_PUNCH, pAgeInTicks, 1.4f);
        this.animate(pEntity.finalPunch2, KingCrimsonAnimations.Chop_Attack, pAgeInTicks, 1.0f);
        this.animate(pEntity.finalPunch3, KingCrimsonAnimations.Chop_Charged, pAgeInTicks, 1.0f);
        this.animate(pEntity.impale2, StarPlatinumAnimations.Impale2, pAgeInTicks, 1.04f);

        this.animate(pEntity.bloodSplashWindup, KingCrimsonAnimations.Chop_Start, pAgeInTicks, 1.5f);
        this.animate(pEntity.bloodSplashThrow, KingCrimsonAnimations.Chop_Attack, pAgeInTicks, 1.6f);

        this.animate(pEntity.entityGrabAnimation, StandAnimations.GRAB_BLOCK, pAgeInTicks, 3f);
        this.animate(pEntity.blockGrabAnimation, StandAnimations.GRAB_BLOCK, pAgeInTicks, 1f);
        this.animate(pEntity.blockThrowAnimation, StandAnimations.THROW_BLOCK, pAgeInTicks, 1.7f);
        this.animate(pEntity.itemGrabAnimation, StandAnimations.GRAB_ITEM, pAgeInTicks, 1f);
        this.animate(pEntity.itemThrowAnimation, StandAnimations.THROW_ITEM, pAgeInTicks, 1.25f);
        this.animate(pEntity.blockRetractAnimation, StandAnimations.RETRACT_BLOCK, pAgeInTicks, 1.25f);
        this.animate(pEntity.itemRetractAnimation, StandAnimations.RETRACT_ITEM, pAgeInTicks, 1.25f);
    }

    @Override
    public ModelPart root() {
        return stand;
    }
}