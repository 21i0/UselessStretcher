package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.dimension.world.QuadChunkDimGen;
import com.sorrowmist.useless.world.dimension.UselessDimensionConfigManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 前置 UselessDimensionConfigManager.apply(ServerLevel) 只对 instanceof
 * AbstractPlasticPlatformGenerator 的生成器下发配置；本模组的四联/九联区块生成器是独立实现
 * （前置该抽象类的构造器为包私有，无法被本包继承），故在 apply 尾部把保存的配置也同步给
 * QuadChunkDimGen（NineChunkDimGen 继承自它，instanceof 一并命中）。apply 会从前置的
 * save()（GUI 提交）与 applyAll()/onLevelLoad()（服务器启动/维度加载）两条路径被调用，
 * 这里补一处即全覆盖。
 */
@Mixin(UselessDimensionConfigManager.class)
public abstract class UselessDimensionConfigManagerMixin {

    @Inject(method = "apply", at = @At("TAIL"), remap = false)
    private static void stm$applyToQuadChunk(ServerLevel level, CallbackInfo ci) {
        if (level != null && level.getChunkSource().getGenerator() instanceof QuadChunkDimGen gen) {
            gen.setConfiguration(UselessDimensionConfigManager.get(level.getServer(), level.dimension()));
        }
    }
}
