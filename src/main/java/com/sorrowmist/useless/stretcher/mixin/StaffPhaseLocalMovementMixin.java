package com.sorrowmist.useless.stretcher.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sorrowmist.useless.stretcher.content.entity.StaffPhaseManager;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LocalPlayer.class)
public abstract class StaffPhaseLocalMovementMixin {
    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isAlwaysFlying()Z"))
    private boolean lockPhaseFlight(boolean alwaysFlying) {
        return alwaysFlying || StaffPhaseManager.state((LocalPlayer) (Object) this).phase();
    }
    @ModifyExpressionValue(method = "aiStep", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z", opcode = org.objectweb.asm.Opcodes.GETFIELD))
    private boolean keepFreeFlight(boolean flying) {
        return flying || StaffPhaseManager.state((LocalPlayer) (Object) this).phase();
    }
}
