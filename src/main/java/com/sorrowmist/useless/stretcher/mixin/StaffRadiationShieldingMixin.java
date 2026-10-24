package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.compat.StaffRadiationProtection;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mekanism.common.lib.radiation.RadiationManager", remap = false)
public abstract class StaffRadiationShieldingMixin {
    @Inject(method = "getRadiationResistance", at = @At("HEAD"), cancellable = true, require = 1)
    private void uselessStretcher$shield(LivingEntity entity, CallbackInfoReturnable<Double> cir) {
        if (entity instanceof Player player && StaffRadiationProtection.enabled(player)) cir.setReturnValue(1D);
    }
}
