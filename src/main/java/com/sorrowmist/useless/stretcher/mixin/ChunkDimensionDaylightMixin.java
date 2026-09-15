package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.dimension.world.NineChunkDimensions;
import com.sorrowmist.useless.stretcher.dimension.world.NineChunkOddDimensions;
import com.sorrowmist.useless.stretcher.dimension.world.QuadChunkDimensions;
import com.sorrowmist.useless.stretcher.dimension.world.QuadChunkOddDimensions;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives the four fixed-time chunk dimensions the same solar behavior as Useless Mod dimensions. */
@Mixin(Level.class)
public abstract class ChunkDimensionDaylightMixin {
    @Inject(method = "isDay", at = @At("HEAD"), cancellable = true)
    private void stretcher$chunkDimensionsAreDay(CallbackInfoReturnable<Boolean> cir) {
        if (stretcher$isChunkDimension()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isNight", at = @At("HEAD"), cancellable = true)
    private void stretcher$chunkDimensionsAreNotNight(CallbackInfoReturnable<Boolean> cir) {
        if (stretcher$isChunkDimension()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isRaining", at = @At("HEAD"), cancellable = true)
    private void stretcher$chunkDimensionsAreClear(CallbackInfoReturnable<Boolean> cir) {
        if (stretcher$isChunkDimension()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isThundering", at = @At("HEAD"), cancellable = true)
    private void stretcher$chunkDimensionsDoNotThunder(CallbackInfoReturnable<Boolean> cir) {
        if (stretcher$isChunkDimension()) {
            cir.setReturnValue(false);
        }
    }

    private boolean stretcher$isChunkDimension() {
        Level level = (Level) (Object) this;
        return QuadChunkDimensions.QUAD_CHUNK_KEY.equals(level.dimension())
                || NineChunkDimensions.NINE_CHUNK_KEY.equals(level.dimension())
                || QuadChunkOddDimensions.QUAD_CHUNK_ODD_KEY.equals(level.dimension())
                || NineChunkOddDimensions.NINE_CHUNK_ODD_KEY.equals(level.dimension());
    }
}
