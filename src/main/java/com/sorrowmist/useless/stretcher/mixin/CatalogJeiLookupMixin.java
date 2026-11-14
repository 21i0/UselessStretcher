package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.client.CatalogJeiPublication;
import mezz.jei.api.recipe.RecipeType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.stream.Stream;

/** JEI has no atomic bulk transaction: keep this category unavailable until the final batch. */
@Pseudo
@Mixin(targets = "mezz.jei.library.recipes.RecipeLookup", remap = false)
public abstract class CatalogJeiLookupMixin {
    @Shadow @Final private RecipeType<?> recipeType;
    @Inject(method = "get", at = @At("HEAD"), cancellable = true, require = 1)
    private void stretcher$pendingLookup(CallbackInfoReturnable<Stream<?>> cir) {
        if (CatalogJeiPublication.blocked() && CatalogJeiPublication.isTargetRecipeType(recipeType))
            cir.setReturnValue(Stream.empty());
    }
}
