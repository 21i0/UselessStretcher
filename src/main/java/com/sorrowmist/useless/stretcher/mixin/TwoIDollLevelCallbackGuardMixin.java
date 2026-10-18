package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.entity.TwoILifecycleGuard;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class TwoIDollLevelCallbackGuardMixin {
    @Inject(method = {"onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", "onTickingEnd(Lnet/minecraft/world/entity/Entity;)V"},
            at = @At("HEAD"), cancellable = true)
    private void protectLiveDoll(Entity entity, CallbackInfo ci) {
        if (TwoILifecycleGuard.blocks(entity)) ci.cancel();
    }
}
