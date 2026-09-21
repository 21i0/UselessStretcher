package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An unconfigured wildcard is not a mold; do not scan the entire converted catalog for it. */
@Mixin(AlloyFurnaceRecipeCatalog.class)
public abstract class EmptyMyriadMoldMixin {
    @Inject(method = "isKnownMold(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void uselessStretcher$skipEmpty(Level level, ItemStack stack,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (stack != null && stack.is(ModItems.OMNIVERSAL_MYRIAD.get())) {
            cir.setReturnValue(MyriadMoldData.hasEnabledMolds(stack));
        }
    }

    @Inject(method = "isKnownMold(Lnet/minecraft/world/item/ItemStack;Ljava/lang/Iterable;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void uselessStretcher$skipEmptyInRecipes(ItemStack stack,
            Iterable<AdvancedAlloyFurnaceRecipe> recipes, CallbackInfoReturnable<Boolean> cir) {
        if (stack != null && stack.is(ModItems.OMNIVERSAL_MYRIAD.get())) {
            cir.setReturnValue(MyriadMoldData.hasEnabledMolds(stack));
        }
    }
}
