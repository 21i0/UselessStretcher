package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.capsule.CapsuleGravitySupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class CapsuleGravityPlacementMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), require = 1)
    private void capsuleGravityBeforePlacement(BlockPos pos, BlockState state, int flags, int recursionLeft,
                                               CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerLevel level)
            CapsuleGravitySupport.beforeNativePlacement(level, pos, state);
    }
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN"), require = 1)
    private void capsuleGravityPlaced(BlockPos pos, BlockState requested, int flags, int recursionLeft,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && (Object) this instanceof ServerLevel level && CapsuleGravitySupport.eligible(level))
            CapsuleGravitySupport.afterSuccessfulPlacement(level, pos, level.getBlockState(pos));
    }
}
