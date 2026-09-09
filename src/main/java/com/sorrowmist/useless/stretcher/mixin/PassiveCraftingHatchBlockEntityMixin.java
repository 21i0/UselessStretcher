package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The passive crafting hatch's {@code MAX_PATTERN_SLOTS} is a compile-time
 * constant derived from {@code RecoverableItemStackHandler.MAX_SLOTS} (540),
 * so it is inlined into the backing handler, the four status arrays and every
 * slot bound check. Replace every inlined 540 with 4096.
 */
@Mixin(PassiveCraftingHatchBlockEntity.class)
public abstract class PassiveCraftingHatchBlockEntityMixin {

    @ModifyConstant(
            method = "<init>(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            constant = @Constant(intValue = 540))
    private static int uselessStretcher$expandCtor(int original) {
        return 4096;
    }

    @ModifyConstant(method = "activeSlotsForCoilTier(II)I", constant = @Constant(intValue = 540))
    private static int uselessStretcher$expandActiveSlots(int original) {
        return 4096;
    }

    @ModifyConstant(method = "loadDeferredTasks()V", constant = @Constant(intValue = 540))
    private static int uselessStretcher$expandDeferred(int original) {
        return 4096;
    }

    @ModifyConstant(method = "resetIdleStates()V", constant = @Constant(intValue = 540))
    private static int uselessStretcher$expandReset(int original) {
        return 4096;
    }

    @ModifyConstant(
            method = "setIdleState(ILcom/sorrowmist/useless/content/blockentities/multiblock/PassiveCraftingHatchBlockEntity$SlotState;Ljava/lang/String;)V",
            constant = @Constant(intValue = 540))
    private static int uselessStretcher$expandSetIdle(int original) {
        return 4096;
    }

    @ModifyConstant(method = "getSlotStatusSnapshot(II)Ljava/util/List;", constant = @Constant(intValue = 540))
    private static int uselessStretcher$expandSnapshot(int original) {
        return 4096;
    }

    @ModifyConstant(method = "flushStatusUpdates()V", constant = @Constant(intValue = 27))
    private static int uselessStretcher$statusPageSize(int original) {
        return 90;
    }
}
