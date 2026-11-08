package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import com.sorrowmist.useless.stretcher.content.ae.AssemblyStretcherInventory;
import com.sorrowmist.useless.stretcher.content.ae.InstalledStretcherPatterns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.IntSupplier;
import java.util.function.Predicate;

@Mixin(MePatternAssemblyBlockEntity.class)
public abstract class InstalledStretcherAssemblyMixin {
    @Redirect(method = "<init>", at = @At(value = "NEW",
            target = "com/sorrowmist/useless/content/blockentities/RecoverableItemStackHandler"),
            require = 1, allow = 1)
    private RecoverableItemStackHandler uselessStretcher$lockInstalledBindings(
            IntSupplier activeSlots, Predicate<ItemStack> validator, Runnable changed) {
        return new AssemblyStretcherInventory(activeSlots, validator, changed, (BlockEntity) (Object) this);
    }

    @Inject(method = "isAcceptedPattern", at = @At("HEAD"), cancellable = true)
    private static void uselessStretcher$accept(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (InstalledStretcherPatterns.isStretcher(stack)) cir.setReturnValue(true);
    }
}
