package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.compat.StaffRadiationProtection;
import mekanism.api.radiation.capability.IRadiationEntity;
import mekanism.common.lib.radiation.RadiationManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mekanism.common.lib.radiation.capability.DefaultRadiationEntity", remap = false)
public abstract class StaffRadiationEntityMixin {
    @Inject(method = "update", at = @At("HEAD"), require = 1)
    private void uselessStretcher$clearBeforeEffects(LivingEntity entity, CallbackInfo ci) {
        if (entity instanceof Player player && StaffRadiationProtection.enabled(player)) {
            ((IRadiationEntity) this).set(RadiationManager.BASELINE);
        }
    }
}
