package com.sorrowmist.useless.stretcher.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sorrowmist.useless.stretcher.client.WondrousStaffCloudTime;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces only vanilla's cloud-motion clock while a dimension-wide time effect is active. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererCloudMixin {
    @Unique
    private float uselessStretcher$cloudPartialTick;

    @Inject(
            method = "renderClouds(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FDDD)V",
            at = @At("HEAD"),
            require = 0)
    private void uselessStretcher$captureCloudPartialTick(PoseStack poseStack, Matrix4f frustumMatrix,
                                                           Matrix4f projectionMatrix, float partialTick,
                                                           double camX, double camY, double camZ,
                                                           CallbackInfo ci) {
        this.uselessStretcher$cloudPartialTick = partialTick;
    }

    @Redirect(
            method = "renderClouds(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FDDD)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;ticks:I",
                    ordinal = 1),
            require = 0)
    private int uselessStretcher$accelerateCloudClock(LevelRenderer renderer) {
        return WondrousStaffCloudTime.cloudTicks(renderer.getTicks(),
                this.uselessStretcher$cloudPartialTick);
    }
}
