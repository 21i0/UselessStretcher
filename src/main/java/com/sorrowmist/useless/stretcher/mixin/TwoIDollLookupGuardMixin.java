package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.entity.TwoILifecycleGuard;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityLookup.class)
public abstract class TwoIDollLookupGuardMixin {
    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void protectLiveDoll(EntityAccess entity, CallbackInfo ci) {
        if (TwoILifecycleGuard.blocks(entity)) ci.cancel();
    }
}
