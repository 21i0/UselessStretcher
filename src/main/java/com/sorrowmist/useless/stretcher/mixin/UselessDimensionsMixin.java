package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.dimension.world.NineChunkDimensions;
import com.sorrowmist.useless.stretcher.dimension.world.NineChunkOddDimensions;
import com.sorrowmist.useless.stretcher.dimension.world.QuadChunkDimensions;
import com.sorrowmist.useless.stretcher.dimension.world.QuadChunkOddDimensions;
import com.sorrowmist.useless.world.dimension.UselessDimensions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让前置的 UselessDimensions.isUselessDimension() 把四联/九联区块维度及各自的奇数中心变体
 * 也识别为「无用之物维度」。
 * 前置的 DimensionConfigMenu.submit() / AbstractDimensionTeleporter.handleTeleport() /
 * EventHandler.onLevelLoad() 都靠这个判定来决定是否打开配置界面、是否下发生成配置，
 * 不改它则本维度的 GUI 配置流程不会生效。
 */
@Mixin(UselessDimensions.class)
public abstract class UselessDimensionsMixin {

    @Inject(method = "isUselessDimension", at = @At("RETURN"), cancellable = true, remap = false)
    private static void stm$includeChunkDimensions(ResourceKey<Level> dimension, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()
                && (QuadChunkDimensions.QUAD_CHUNK_KEY.equals(dimension)
                        || NineChunkDimensions.NINE_CHUNK_KEY.equals(dimension)
                        || QuadChunkOddDimensions.QUAD_CHUNK_ODD_KEY.equals(dimension)
                        || NineChunkOddDimensions.NINE_CHUNK_ODD_KEY.equals(dimension))) {
            cir.setReturnValue(true);
        }
    }
}
