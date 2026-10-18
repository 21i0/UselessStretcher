package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.entity.TwoILifecycleGuard;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntitySection.class)
public abstract class TwoIDollSectionGuardMixin {
    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void protectLiveDoll(EntityAccess entity, CallbackInfoReturnable<Boolean> cir) {
        if (TwoILifecycleGuard.blocks(entity)) cir.setReturnValue(false);
    }
}
