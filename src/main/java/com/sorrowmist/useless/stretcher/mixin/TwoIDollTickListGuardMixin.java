package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.entity.TwoILifecycleGuard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityTickList.class)
public abstract class TwoIDollTickListGuardMixin {
    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void protectLiveDoll(Entity entity, CallbackInfo ci) {
        if (TwoILifecycleGuard.blocks(entity)) ci.cancel();
    }
}
