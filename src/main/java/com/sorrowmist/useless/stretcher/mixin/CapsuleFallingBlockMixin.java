package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.capsule.CapsuleGravitySupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlock.class)
public abstract class CapsuleFallingBlockMixin {
    @Inject(method = "tick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void capsuleKeepNativeGravity(BlockState state, ServerLevel level, BlockPos pos,
                                          RandomSource random, CallbackInfo ci) {
        if (CapsuleGravitySupport.shouldKeepStatic(level, pos, state)) ci.cancel();
    }
}
