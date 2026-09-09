package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.client.gui.PatternAssemblyScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The pattern icon renderer treats every slot with index below
 * {@code SLOTS_PER_PAGE} as a pattern slot; raise it to the enlarged page size.
 */
@Mixin(PatternAssemblyScreen.class)
public abstract class PatternAssemblyScreenMixin {

    @ModifyConstant(method = "renderSlot(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V",
            constant = @Constant(intValue = 27))
    private static int uselessStretcher$renderSlotPageSize(int original) {
        return 90;
    }
}
