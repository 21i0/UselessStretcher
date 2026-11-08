/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mezz.jei.api.gui.builder.IIngredientAcceptor
 *  mezz.jei.api.recipe.category.IRecipeCategory
 *  mezz.jei.library.ingredients.RecipeIngredientSupplier$FocusLink
 *  org.jetbrains.annotations.Nullable
 *  org.jetbrains.annotations.Unmodifiable
 *  org.jetbrains.annotations.UnmodifiableView
 */
package mezz.jei.library.recipes.collect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.library.ingredients.RecipeIngredientSupplier;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.UnmodifiableView;

public class RecipeTypeData<T> {
    private final IRecipeCategory<T> recipeCategory;
    private final List<Consumer<IIngredientAcceptor<?>>> recipeCategoryCatalysts;
    private final Map<T, List<RecipeIngredientSupplier.FocusLink>> focusLinks = new IdentityHashMap<T, List<RecipeIngredientSupplier.FocusLink>>();
    private final List<T> recipes = new ArrayList<T>();
    private final Set<T> hiddenRecipes = Collections.newSetFromMap(new IdentityHashMap());

    public RecipeTypeData(IRecipeCategory<T> recipeCategory, List<Consumer<IIngredientAcceptor<?>>> recipeCategoryCatalysts) {
        this.recipeCategory = recipeCategory;
        this.recipeCategoryCatalysts = List.copyOf(recipeCategoryCatalysts);
    }

    public IRecipeCategory<T> getRecipeCategory() {
        return this.recipeCategory;
    }

    public @Unmodifiable List<Consumer<IIngredientAcceptor<?>>> getRecipeCategoryCatalysts() {
        return this.recipeCategoryCatalysts;
    }

    public @UnmodifiableView List<T> getRecipes() {
        return Collections.unmodifiableList(this.recipes);
    }

    public void addRecipes(Collection<T> recipes) {
        this.recipes.addAll(recipes);
    }

    public void addFocusLinks(T recipe, List<RecipeIngredientSupplier.FocusLink> links) {
        this.focusLinks.put(recipe, List.copyOf(links));
    }

    @Nullable
    public List<RecipeIngredientSupplier.FocusLink> getFocusLinks(T recipe) {
        return this.focusLinks.get(recipe);
    }

    public Set<T> getHiddenRecipes() {
        return this.hiddenRecipes;
    }
}
