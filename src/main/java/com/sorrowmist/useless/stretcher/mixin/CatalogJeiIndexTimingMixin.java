package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.client.CatalogJeiPublicationTiming;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.library.recipes.collect.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Times the existing role/UID index append, without replacing suppliers or index contents. */
@Pseudo
@Mixin(targets = "mezz.jei.library.recipes.RecipeManagerInternal", remap = false)
public abstract class CatalogJeiIndexTimingMixin {
    @Redirect(method = "addRecipe", at = @At(value = "INVOKE", target =
            "Lmezz/jei/library/recipes/collect/RecipeMap;addRecipe(Lmezz/jei/api/recipe/RecipeType;Ljava/lang/Object;Lmezz/jei/api/ingredients/IIngredientSupplier;)V"), require = 1)
    private <T> void stretcher$timeIndex(RecipeMap map, RecipeType<T> type, T recipe, IIngredientSupplier supplier) {
        CatalogJeiPublicationTiming.index(map, type, recipe, supplier);
    }
}
