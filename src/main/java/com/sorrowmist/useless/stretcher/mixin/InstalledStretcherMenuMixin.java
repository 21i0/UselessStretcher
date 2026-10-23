package com.sorrowmist.useless.stretcher.mixin;

import appeng.api.crafting.PatternDetailsHelper;
import com.sorrowmist.useless.content.menus.AdvancedAlloyFurnaceMenu;
import com.sorrowmist.useless.stretcher.content.ae.InstalledStretcherPatterns;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Shift-click follows the same pattern-slot route as manually placing the item. */
@Mixin(AdvancedAlloyFurnaceMenu.class)
public abstract class InstalledStretcherMenuMixin {
    @Redirect(method = "tryMoveToMachine", at = @At(value = "INVOKE",
            target = "Lappeng/api/crafting/PatternDetailsHelper;isEncodedPattern(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean uselessStretcher$route(ItemStack stack) {
        return InstalledStretcherPatterns.isStretcher(stack) || PatternDetailsHelper.isEncodedPattern(stack);
    }
}
