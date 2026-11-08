package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.client.CatalogJeiPublication;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blocks explicit showRecipes and history paths before they build or display partial layouts. */
@Pseudo
@Mixin(targets = "mezz.jei.gui.recipes.RecipesGui", remap = false)
public abstract class CatalogJeiGuiMixin {
    @Inject(method = {"show", "showTypes", "showRecipes", "back", "forward", "open", "updateLayout"},
            at = @At("HEAD"), cancellable = true, require = 1)
    private void stretcher$pendingGui(CallbackInfo ci) { if (CatalogJeiPublication.blocked()) ci.cancel(); }
}
