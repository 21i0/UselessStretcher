/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mezz.jei.api.gui.builder.IRecipeLayoutBuilder
 *  mezz.jei.api.recipe.category.IRecipeCategory
 *  mezz.jei.api.runtime.IIngredientManager
 *  mezz.jei.common.util.ErrorUtil
 *  mezz.jei.library.focus.FocusGroup
 *  mezz.jei.library.gui.recipes.supplier.builder.IngredientSupplierBuilder
 *  mezz.jei.library.ingredients.RecipeIngredientSupplier
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package mezz.jei.library.util;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.util.ErrorUtil;
import mezz.jei.library.focus.FocusGroup;
import mezz.jei.library.gui.recipes.supplier.builder.IngredientSupplierBuilder;
import mezz.jei.library.ingredients.RecipeIngredientSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class IngredientSupplierHelper {
    private static final Logger LOGGER = LogManager.getLogger();

    private IngredientSupplierHelper() {
    }

    public static <T> RecipeIngredientSupplier getIngredientSupplier(T recipe, IRecipeCategory<T> recipeCategory, IIngredientManager ingredientManager) {
        IngredientSupplierBuilder builder = new IngredientSupplierBuilder(ingredientManager);
        if (!recipeCategory.isHandled(recipe)) {
            return builder.buildIngredientSupplier();
        }
        try {
            recipeCategory.setRecipe((IRecipeLayoutBuilder)builder, recipe, FocusGroup.EMPTY);
        }
        catch (LinkageError | RuntimeException e) {
            String recipeInfo = ErrorUtil.getRecipeInfo(recipeCategory, recipe);
            LOGGER.error("Found a broken recipe, failed to setRecipe with RecipeLayoutBuilder:\n{}", (Object)recipeInfo, (Object)e);
        }
        return builder.buildIngredientSupplier();
    }
}
