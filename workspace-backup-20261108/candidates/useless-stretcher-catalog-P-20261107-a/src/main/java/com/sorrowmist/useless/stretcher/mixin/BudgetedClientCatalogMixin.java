package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.client.ClientCatalogLifecycle;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the original body intact for other mods' injection targets; replaces its scheduling only. */
@Pseudo
@Mixin(targets = "com.sorrowmist.useless.init.ClientSetup", remap = false, priority = 1100)
public abstract class BudgetedClientCatalogMixin {
    @Inject(method = "onClientTick", at = @At("HEAD"), cancellable = true, require = 1)
    private static void stretcher$clientBarrier(ClientTickEvent.Post event, CallbackInfo ci) {
        ClientCatalogLifecycle.tick();
        ci.cancel();
    }
}
