package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.inventory.slot.PatternSlotItemHandler;
import com.sorrowmist.useless.stretcher.content.ae.InstalledStretcherPlacementGuard;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PatternSlotItemHandler.class)
public abstract class InstalledStretcherSlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void uselessStretcher$accept(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Boolean accepted = InstalledStretcherPlacementGuard.mayPlace((SlotItemHandler) (Object) this, stack);
        if (accepted != null) cir.setReturnValue(accepted);
    }
}
