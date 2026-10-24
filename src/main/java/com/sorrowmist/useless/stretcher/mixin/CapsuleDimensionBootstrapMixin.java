package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.capsule.CapsuleDimensions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.Executor;

@Mixin(MinecraftServer.class)
public abstract class CapsuleDimensionBootstrapMixin implements CapsuleDimensions.ServerAccess {
    @Shadow @Final protected LevelStorageSource.LevelStorageAccess storageSource;
    @Shadow @Final private Executor executor;
    @Unique private ChunkProgressListener stretcher$capsuleListener;

    @Inject(method = "createLevels", at = @At("RETURN"))
    private void stretcher$mirrorDimensions(ChunkProgressListener listener, CallbackInfo ci) {
        stretcher$capsuleListener = listener;
        CapsuleDimensions.bootstrap((MinecraftServer)(Object)this, executor, storageSource, listener);
    }

    @Override public void stretcher$ensureCapsuleDimensions() {
        if (stretcher$capsuleListener != null)
            CapsuleDimensions.bootstrap((MinecraftServer)(Object)this, executor, storageSource, stretcher$capsuleListener);
    }
}
