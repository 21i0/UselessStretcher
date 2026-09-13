package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.blocks.multiblock.MePatternAssemblyBlock;
import com.sorrowmist.useless.stretcher.content.ae.PatternAssemblyDataBridge;
import com.sorrowmist.useless.stretcher.content.ae.PatternAssemblyReferenceAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;

/** Keeps the assembly's 4096-slot inventory out of ItemEntity synchronization packets. */
@Mixin(MePatternAssemblyBlock.class)
public abstract class MePatternAssemblyBlockMixin {
    @Inject(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/storage/loot/LootParams$Builder;)Ljava/util/List;",
            at = @At("RETURN"))
    private void uselessStretcher$externalizeDrops(BlockState state, LootParams.Builder params,
                                                   CallbackInfoReturnable<List<ItemStack>> cir) {
        ServerLevel level = params.getLevel();
        UUID reference = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof PatternAssemblyReferenceAccessor accessor
                ? accessor.uselessStretcher$getPatternAssemblyReference() : null;
        for (ItemStack drop : cir.getReturnValue()) {
            UUID written = PatternAssemblyDataBridge.externalize(level, drop, reference);
            if (written != null) reference = written;
        }
    }

    @Inject(method = "setPlacedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"))
    private void uselessStretcher$migrateLegacyPlacedStack(Level level, BlockPos pos, BlockState state,
                                                           @Nullable LivingEntity placer, ItemStack stack,
                                                           CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel) {
            PatternAssemblyDataBridge.externalize(serverLevel, stack);
        }
    }

    @Inject(method = "setPlacedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("TAIL"))
    private void uselessStretcher$restoreExternalData(Level level, BlockPos pos, BlockState state,
                                                      @Nullable LivingEntity placer, ItemStack stack,
                                                      CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof MePatternAssemblyBlockEntity assembly) {
            var claim = PatternAssemblyDataBridge.claim(serverLevel, stack);
            if (claim != null) {
                assembly.restoreItemData(claim.data(), level.registryAccess());
                ((PatternAssemblyReferenceAccessor) (Object) assembly).uselessStretcher$setPatternAssemblyReference(
                        claim.reference());
            }
        }
    }
}
