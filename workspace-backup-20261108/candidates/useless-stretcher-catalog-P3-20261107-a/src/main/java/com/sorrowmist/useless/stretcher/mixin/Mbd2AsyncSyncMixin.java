package com.sorrowmist.useless.stretcher.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.sorrowmist.useless.stretcher.compat.Mbd2MainThreadSyncCompat;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** LDLib2 2.2.44 dispatches live MBD2/AE2 node serialization from this worker loop. */
@Pseudo
@Mixin(targets = "com.lowdragmc.lowdraglib2.async.AsyncThreadData", remap = false)
public abstract class Mbd2AsyncSyncMixin {
    @Shadow @Final public ServerLevel serverLevel;

    @WrapOperation(method = "searchingTask", at = @At(value = "INVOKE",
            target = "Lcom/lowdragmc/lowdraglib2/async/IAsyncLogic;asyncTick(J)V", remap = false), remap = false)
    private void stretcher$syncMachinesOnServer(@Coerce Object holder, long period, Operation<Void> original) {
        if (!Mbd2MainThreadSyncCompat.defer(serverLevel, holder, () -> original.call(holder, period))) {
            original.call(holder, period);
        }
    }
}
