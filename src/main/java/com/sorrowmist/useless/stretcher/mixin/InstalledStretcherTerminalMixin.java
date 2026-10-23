package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.ae.InstalledStretcherPatterns;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity$2")
public abstract class InstalledStretcherTerminalMixin {
    @Inject(method = "isItemValid", at = @At("HEAD"), cancellable = true)
    private void uselessStretcher$accept(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (InstalledStretcherPatterns.isStretcher(stack)) cir.setReturnValue(true);
    }
}
