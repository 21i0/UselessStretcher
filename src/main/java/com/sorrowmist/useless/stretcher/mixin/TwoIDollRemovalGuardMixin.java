package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** setRemoved is final, so guard direct removal tools before any level callbacks run. */
@Mixin(Entity.class)
public abstract class TwoIDollRemovalGuardMixin {
    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true)
    private void uselessStretcher$guardDollRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object)this instanceof TwoIDollEntity doll && doll.blocksDestructiveRemoval(reason)) ci.cancel();
    }

    @Inject(method = "setPosRaw", at = @At("HEAD"), cancellable = true)
    private void uselessStretcher$guardDollPosition(double x, double y, double z, CallbackInfo ci) {
        if ((Object)this instanceof TwoIDollEntity doll && doll.blocksInvalidPosition(x, y, z)) ci.cancel();
    }
}
