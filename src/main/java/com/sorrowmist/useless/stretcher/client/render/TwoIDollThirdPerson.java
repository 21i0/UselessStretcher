package com.sorrowmist.useless.stretcher.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

/** A relaxed supporting arm with an upright doll anchored to its actual palm. */
public final class TwoIDollThirdPerson {
    private TwoIDollThirdPerson() { }

    public static void poseArm(PlayerModel<?> model, HumanoidArm side, float age) {
        ModelPart arm = side == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        arm.xRot = -0.85F;
        arm.yRot = side == HumanoidArm.RIGHT ? -0.08F : 0.08F;
        arm.zRot = 0;
        (side == HumanoidArm.RIGHT ? model.rightSleeve : model.leftSleeve).copyFrom(arm);
    }

    public static void transformToPalm(ModelPart arm, HumanoidArm side, boolean slim, PoseStack pose) {
        arm.translateAndRotate(pose);
        int sign = side == HumanoidArm.RIGHT ? -1 : 1;
        pose.translate(sign * (slim ? 0.5F : 1.0F) / 16, 10F / 16, -2.2F / 16);
        // Retain the palm position, then undo the arm rotation so the doll's head stays up.
        pose.mulPose(new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot).conjugate());
        pose.scale(0.80F, -0.80F, -0.80F);
    }

    public static void render(ItemStack stack, PlayerModel<?> model, HumanoidArm side, boolean slim,
                              PoseStack pose, MultiBufferSource buffers, int light, TwoIDollCarryMotion.Pose motion) {
        render(stack, model, side, slim, pose, buffers, light, motion, TwoIDollWeather.Pose.CLEAR);
    }

    public static void render(ItemStack stack, PlayerModel<?> model, HumanoidArm side, boolean slim,
                              PoseStack pose, MultiBufferSource buffers, int light, TwoIDollCarryMotion.Pose motion,
                              TwoIDollWeather.Pose weather) {
        pose.pushPose();
        transformToPalm(side == HumanoidArm.RIGHT ? model.rightArm : model.leftArm, side, slim, pose);
        TwoIDollItemRenderer.get().renderCarried(stack, pose, buffers, light, OverlayTexture.NO_OVERLAY, motion, weather);
        pose.popPose();
    }
}
