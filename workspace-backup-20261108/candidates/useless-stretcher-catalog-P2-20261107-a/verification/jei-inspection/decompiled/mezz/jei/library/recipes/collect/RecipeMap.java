/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
 *  mezz.jei.api.ingredients.IIngredientHelper
 *  mezz.jei.api.ingredients.IIngredientSupplier
 *  mezz.jei.api.ingredients.IIngredientType
 *  mezz.jei.api.ingredients.ITypedIngredient
 *  mezz.jei.api.ingredients.subtypes.UidContext
 *  mezz.jei.api.recipe.RecipeIngredientRole
 *  mezz.jei.api.recipe.RecipeType
 *  mezz.jei.api.runtime.IIngredientManager
 *  mezz.jei.library.recipes.collect.IngredientUidIndex
 *  mezz.jei.library.recipes.collect.RecipeIngredientTable
 *  org.jetbrains.annotations.UnmodifiableView
 */
package mezz.jei.library.recipes.collect;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.library.recipes.collect.IngredientUidIndex;
import mezz.jei.library.recipes.collect.RecipeIngredientTable;
import org.jetbrains.annotations.UnmodifiableView;

public class RecipeMap {
    private final RecipeIngredientTable recipeTable = new RecipeIngredientTable();
    private final IngredientUidIndex<Set<RecipeType<?>>> ingredientUidToCategoryMap = new IngredientUidIndex();
    private final IngredientUidIndex<Set<RecipeType<?>>> categoryCatalystUidToRecipeCategoryMap = new IngredientUidIndex();
    private final Comparator<RecipeType<?>> recipeTypeComparator;
    private final IIngredientManager ingredientManager;
    private final RecipeIngredientRole role;

    public RecipeMap(Comparator<RecipeType<?>> recipeTypeComparator, IIngredientManager ingredientManager, RecipeIngredientRole role) {
        this.recipeTypeComparator = recipeTypeComparator;
        this.ingredientManager = ingredientManager;
        this.role = role;
    }

    public <T> Stream<RecipeType<?>> getRecipeTypes(ITypedIngredient<T> ingredient) {
        IIngredientType ingredientType = ingredient.getType();
        IIngredientHelper ingredientHelper = this.ingredientManager.getIngredientHelper(ingredientType);
        Object uid = ingredientHelper.getUid(ingredient, UidContext.Recipe);
        Set recipeTypes = (Set)this.ingredientUidToCategoryMap.get(ingredientType, uid).exact();
        Set catalystRecipeTypes = (Set)this.categoryCatalystUidToRecipeCategoryMap.get(ingredientType, uid).exact();
        return Stream.of(recipeTypes, catalystRecipeTypes).filter(Objects::nonNull).flatMap(Collection::stream).sorted(this.recipeTypeComparator);
    }

    public <T> void addCatalystForCategory(RecipeType<?> recipeType, ITypedIngredient<T> ingredient) {
        IIngredientType ingredientType = ingredient.getType();
        IIngredientHelper ingredientHelper = this.ingredientManager.getIngredientHelper(ingredientType);
        Object uid = ingredientHelper.getUid(ingredient, UidContext.Recipe);
        ((Set)this.categoryCatalystUidToRecipeCategoryMap.computeExactIfAbsent(ingredientType, uid, ObjectOpenHashSet::new)).add(recipeType);
    }

    public <T> @UnmodifiableView List<T> getRecipes(RecipeType<T> recipeType, ITypedIngredient<?> ingredient) {
        return this.getRecipes(recipeType, ingredient.getType(), ingredient);
    }

    public <T> boolean isCatalystForRecipeCategory(RecipeType<T> recipeType, ITypedIngredient<?> ingredient) {
        return this.isCatalystForRecipeCategory(recipeType, ingredient.getType(), ingredient);
    }

    private <T, I> List<T> getRecipes(RecipeType<T> recipeType, IIngredientType<I> ingredientType, ITypedIngredient<?> ingredient) {
        ITypedIngredient typedIngredient = ingredient.cast(ingredientType);
        if (typedIngredient == null) {
            return List.of();
        }
        IIngredientHelper ingredientHelper = this.ingredientManager.getIngredientHelper(ingredientType);
        Object uid = ingredientHelper.getUid(typedIngredient, UidContext.Recipe);
        return this.recipeTable.get(recipeType, ingredientType, uid);
    }

    private <T, I> boolean isCatalystForRecipeCategory(RecipeType<T> recipeType, IIngredientType<I> ingredientType, ITypedIngredient<?> ingredient) {
        ITypedIngredient typedIngredient = ingredient.cast(ingredientType);
        if (typedIngredient == null) {
            return false;
        }
        IIngredientHelper ingredientHelper = this.ingredientManager.getIngredientHelper(ingredientType);
        Object exactUid = ingredientHelper.getUid(typedIngredient, UidContext.Recipe);
        Set catalystTypes = (Set)this.categoryCatalystUidToRecipeCategoryMap.get(ingredientType, exactUid).exact();
        if (catalystTypes == null) {
            return false;
        }
        return catalystTypes.contains(recipeType);
    }

    public <T> void addRecipe(RecipeType<T> recipeType, T recipe, IIngredientSupplier ingredientSupplier) {
        IngredientUidIndex ingredientUids = new IngredientUidIndex();
        for (ITypedIngredient ingredient : ingredientSupplier.getIngredients(this.role)) {
            this.addRecipeIngredient(ingredient, (IngredientUidIndex<Boolean>)ingredientUids);
        }
        ingredientUids.forEach((ingredientType, uid, buckets) -> {
            if (buckets.exact() != null) {
                ((Set)this.ingredientUidToCategoryMap.computeExactIfAbsent(ingredientType, uid, () -> new ObjectOpenHashSet(2))).add(recipeType);
                this.recipeTable.addExact(recipe, recipeType, ingredientType, uid);
            }
        });
    }

    public void compact() {
        this.recipeTable.compact();
    }

    private <T> void addRecipeIngredient(ITypedIngredient<T> typedIngredient, IngredientUidIndex<Boolean> ingredientUids) {
        IIngredientType ingredientType = typedIngredient.getType();
        IIngredientHelper ingredientHelper = this.ingredientManager.getIngredientHelper(ingredientType);
        Object uid = ingredientHelper.getUid(typedIngredient, UidContext.Recipe);
        ingredientUids.computeExactIfAbsent(ingredientType, uid, () -> Boolean.TRUE);
    }
}
