package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.entity.StaffPhaseManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Match spectator section visibility when the camera is inside solid terrain. */
@Mixin(LevelRenderer.class)
public abstract class StaffPhaseVisibilityMixin {
    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/LevelRenderer;setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V"), index = 3)
    private boolean phaseVisibility(boolean spectator) {
        return spectator || StaffPhaseManager.state(Minecraft.getInstance().player).phase();
    }
}
