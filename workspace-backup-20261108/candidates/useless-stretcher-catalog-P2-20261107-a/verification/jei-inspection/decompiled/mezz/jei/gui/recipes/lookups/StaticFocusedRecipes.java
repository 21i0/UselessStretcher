/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mezz.jei.api.recipe.category.IRecipeCategory
 *  mezz.jei.gui.recipes.lookups.IFocusedRecipes
 *  org.jetbrains.annotations.Unmodifiable
 */
package mezz.jei.gui.recipes.lookups;

import java.util.List;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import org.jetbrains.annotations.Unmodifiable;

public record StaticFocusedRecipes<T>(IRecipeCategory<T> recipeCategory, List<T> recipes) implements IFocusedRecipes<T>
{
    public IRecipeCategory<T> getRecipeCategory() {
        return this.recipeCategory;
    }

    public @Unmodifiable List<T> getRecipes() {
        return this.recipes;
    }
}
