package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.compat.MekanismRadiationCompat;
import com.sorrowmist.useless.stretcher.compat.StaffRadiationProtection;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mekanism.common.lib.radiation.capability.RadiationEntity", remap = false)
public abstract class StaffRadiationEntityMixin {
    @Shadow @Final private LivingEntity entity;

    @Inject(method = "radiate", at = @At("HEAD"), cancellable = true)
    private void uselessStretcher$preventDose(double magnitude, CallbackInfo ci) {
        if (entity instanceof Player player && StaffRadiationProtection.enabled(player)) ci.cancel();
    }

    @Inject(method = "update", at = @At("HEAD"))
    private void uselessStretcher$clearBeforeEffects(CallbackInfo ci) {
        if (entity instanceof Player player && StaffRadiationProtection.enabled(player)) MekanismRadiationCompat.clear(entity);
    }
}
