package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The omniversal pattern assembly uses the three-argument
 * {@code RecoverableItemStackHandler} constructor, which passes the inlined
 * {@code MAX_SLOTS} constant (540) straight through to the backing handler.
 * Bump that constant and the matching capacity guard up to 4096.
 */
@Mixin(RecoverableItemStackHandler.class)
public abstract class RecoverableItemStackHandlerMixin {

    @ModifyConstant(
            method = "<init>(Ljava/util/function/IntSupplier;Ljava/util/function/Predicate;Ljava/lang/Runnable;)V",
            constant = @Constant(intValue = 540))
    private static int uselessStretcher$expandAssemblyCapacity(int original) {
        return 4096;
    }

    @ModifyConstant(method = "validateCapacity(I)I", constant = @Constant(intValue = 540))
    private static int uselessStretcher$expandCapacityGuard(int original) {
        return 4096;
    }
}
