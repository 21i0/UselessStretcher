package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.client.CatalogJeiPublicationTiming;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Explicit overloads retain the native helper's exception handling in both verified JEI versions. */
@Pseudo
@Mixin(targets = "mezz.jei.library.util.IngredientSupplierHelper", remap = false)
public abstract class CatalogJeiBuildTimingMixin {
    @Redirect(method = {
            "getIngredientSupplier(Ljava/lang/Object;Lmezz/jei/api/recipe/category/IRecipeCategory;Lmezz/jei/api/runtime/IIngredientManager;)Lmezz/jei/library/ingredients/RecipeIngredientSupplier;",
            "getIngredientSupplier(Ljava/lang/Object;Lmezz/jei/api/recipe/category/IRecipeCategory;Lmezz/jei/api/runtime/IIngredientManager;Ljava/util/Set;)Lmezz/jei/library/ingredients/RecipeIngredientSupplier;"
    }, at = @At(value = "INVOKE", target =
            "Lmezz/jei/api/recipe/category/IRecipeCategory;setRecipe(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;Ljava/lang/Object;Lmezz/jei/api/recipe/IFocusGroup;)V"), require = 1, allow = 1)
    private static <T> void stretcher$timeCategory(IRecipeCategory<T> category, IRecipeLayoutBuilder builder,
                                                 T recipe, IFocusGroup focuses) {
        CatalogJeiPublicationTiming.build(category, builder, recipe, focuses);
    }
}
