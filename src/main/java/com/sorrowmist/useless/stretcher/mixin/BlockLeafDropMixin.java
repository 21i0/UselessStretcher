package com.sorrowmist.useless.stretcher.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Block.class)
public abstract class BlockLeafDropMixin {
    @Inject(method="dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",at=@At("TAIL"))
    private static void uselessStretcher$leafReward(BlockState state,Level level,BlockPos pos,BlockEntity block,Entity breaker,ItemStack tool,CallbackInfo ci) {
        com.sorrowmist.useless.stretcher.event.WondrousStaffLeafDropHandler.onBlockDestroyed(level,breaker,state);
    }
}
