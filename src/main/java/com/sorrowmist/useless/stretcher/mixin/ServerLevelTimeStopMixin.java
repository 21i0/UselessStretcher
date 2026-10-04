package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.entity.TimeStopManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/** EndingLibrary-style pause: keep chunk/entity bookkeeping alive, but tick only the exempt actor. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelTimeStopMixin {
    @Shadow @Final EntityTickList entityTickList;
    @Shadow @Final private ServerChunkCache chunkSource;
    @Shadow @Final private PersistentEntitySectionManager<Entity> entityManager;
    @Shadow private boolean handlingTick;

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"), cancellable = true)
    private void uselessStretcher$pause(BooleanSupplier hasTimeLeft, CallbackInfo callback) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!TimeStopManager.isPaused(level)) return;

        // Keep chunk loading and entity storage maintenance running. Do not run
        // block ticks, weather, scheduled ticks or block entities while stopped.
        chunkSource.tick(hasTimeLeft, true);
        handlingTick = false;
        entityTickList.forEach(entity -> {
            if (!entity.isRemoved() && TimeStopManager.mayEntityTick(entity)) {
                ((ServerLevel) (Object) this).tickNonPassenger(entity);
            }
        });
        entityManager.tick();
        callback.cancel();
    }
}
