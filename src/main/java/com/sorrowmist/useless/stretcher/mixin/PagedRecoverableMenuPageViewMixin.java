package com.sorrowmist.useless.stretcher.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The page view maps a visible slot to a backing slot via
 * {@code page * SLOTS_PER_PAGE + slot}. Grow it to match the 90-slot page.
 */
@Mixin(targets = "com.sorrowmist.useless.content.menus.PagedRecoverableMenu$PageView")
public abstract class PagedRecoverableMenuPageViewMixin {

    @ModifyConstant(
            method = "<init>(Lcom/sorrowmist/useless/content/blockentities/RecoverableItemStackHandler;Ljava/util/function/IntSupplier;Z)V",
            constant = @Constant(intValue = 27))
    private static int uselessStretcher$clientBufferSize(int original) {
        return 90;
    }

    @ModifyConstant(method = "actual(I)I", constant = @Constant(intValue = 27))
    private static int uselessStretcher$actualMapping(int original) {
        return 90;
    }

    @ModifyConstant(method = "getSlots()I", constant = @Constant(intValue = 27))
    private static int uselessStretcher$viewSize(int original) {
        return 90;
    }
}
