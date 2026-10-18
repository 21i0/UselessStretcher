package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reject before the ritual detaches leashes or writes anything into a jar. */
@Pseudo
@Mixin(targets = "com.hollingsworth.arsnouveau.common.ritual.RitualMobCapture", remap = false)
public abstract class TwoIArsRitualMixin {
    @Inject(method = "canJar", at = @At("HEAD"), cancellable = true)
    private void uselessStretcher$rejectDoll(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof TwoIDollEntity) cir.setReturnValue(false);
    }
}
