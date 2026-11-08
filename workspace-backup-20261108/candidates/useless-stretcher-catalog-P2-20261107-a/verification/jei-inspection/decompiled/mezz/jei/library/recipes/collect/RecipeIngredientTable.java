/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mezz.jei.api.ingredients.IIngredientType
 *  mezz.jei.api.recipe.RecipeType
 *  mezz.jei.library.recipes.collect.IngredientToRecipesMap
 *  org.jetbrains.annotations.UnmodifiableView
 */
package mezz.jei.library.recipes.collect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.library.recipes.collect.IngredientToRecipesMap;
import org.jetbrains.annotations.UnmodifiableView;

public class RecipeIngredientTable {
    private final Map<RecipeType<?>, IngredientToRecipesMap<?>> map = new HashMap();

    public <V> void addExact(V recipe, RecipeType<V> recipeType, IIngredientType<?> ingredientType, Object uid) {
        IngredientToRecipesMap ingredientToRecipesMap = this.map.computeIfAbsent(recipeType, k -> new IngredientToRecipesMap());
        ingredientToRecipesMap.addExact(recipe, ingredientType, uid);
    }

    public <V> @UnmodifiableView List<V> get(RecipeType<V> recipeType, IIngredientType<?> ingredientType, Object uid) {
        IngredientToRecipesMap<?> ingredientToRecipesMap = this.map.get(recipeType);
        if (ingredientToRecipesMap == null) {
            return List.of();
        }
        return ingredientToRecipesMap.get(ingredientType, uid);
    }

    public void compact() {
        this.map.values().forEach(IngredientToRecipesMap::compact);
    }
}
