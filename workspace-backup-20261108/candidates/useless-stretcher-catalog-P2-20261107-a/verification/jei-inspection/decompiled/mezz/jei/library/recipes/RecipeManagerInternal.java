/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ImmutableList
 *  com.google.common.collect.ImmutableListMultimap
 *  mezz.jei.api.gui.builder.IIngredientAcceptor
 *  mezz.jei.api.ingredients.IIngredientSupplier
 *  mezz.jei.api.ingredients.ITypedIngredient
 *  mezz.jei.api.ingredients.subtypes.UidContext
 *  mezz.jei.api.recipe.IFocus
 *  mezz.jei.api.recipe.IFocusGroup
 *  mezz.jei.api.recipe.RecipeIngredientRole
 *  mezz.jei.api.recipe.RecipeType
 *  mezz.jei.api.recipe.advanced.IRecipeManagerPlugin
 *  mezz.jei.api.recipe.category.IRecipeCategory
 *  mezz.jei.api.runtime.IIngredientManager
 *  mezz.jei.api.runtime.IIngredientVisibility
 *  mezz.jei.api.runtime.IIngredientVisibility$IListener
 *  mezz.jei.common.util.ErrorUtil
 *  mezz.jei.library.config.RecipeCategorySortingConfig
 *  mezz.jei.library.ingredients.RecipeIngredientSupplier
 *  mezz.jei.library.ingredients.RecipeIngredientSupplier$FocusLink
 *  mezz.jei.library.ingredients.SimpleIngredientAcceptor
 *  mezz.jei.library.recipes.InternalRecipeManagerPlugin
 *  mezz.jei.library.recipes.PluginManager
 *  mezz.jei.library.recipes.RecipeCatalystBuilder
 *  mezz.jei.library.recipes.collect.RecipeTypeDataMap
 *  mezz.jei.library.util.IngredientSupplierHelper
 *  mezz.jei.library.util.RecipeDebugUtil
 *  net.minecraft.resources.ResourceLocation
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 *  org.jetbrains.annotations.Nullable
 *  org.jetbrains.annotations.Unmodifiable
 */
package mezz.jei.library.recipes;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.advanced.IRecipeManagerPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.common.util.ErrorUtil;
import mezz.jei.library.config.RecipeCategorySortingConfig;
import mezz.jei.library.ingredients.RecipeIngredientSupplier;
import mezz.jei.library.ingredients.SimpleIngredientAcceptor;
import mezz.jei.library.recipes.InternalRecipeManagerPlugin;
import mezz.jei.library.recipes.PluginManager;
import mezz.jei.library.recipes.RecipeCatalystBuilder;
import mezz.jei.library.recipes.collect.RecipeMap;
import mezz.jei.library.recipes.collect.RecipeTypeData;
import mezz.jei.library.recipes.collect.RecipeTypeDataMap;
import mezz.jei.library.util.IngredientSupplierHelper;
import mezz.jei.library.util.RecipeDebugUtil;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

public class RecipeManagerInternal
implements IIngredientVisibility.IListener {
    private static final Logger LOGGER = LogManager.getLogger();
    private final @Unmodifiable List<IRecipeCategory<?>> recipeCategories;
    private final IIngredientManager ingredientManager;
    private final RecipeTypeDataMap recipeTypeDataMap;
    private final Comparator<IRecipeCategory<?>> recipeCategoryComparator;
    private final EnumMap<RecipeIngredientRole, RecipeMap> recipeMaps;
    private final PluginManager pluginManager;
    private final Set<RecipeType<?>> hiddenRecipeTypes = new HashSet();
    private final IIngredientVisibility ingredientVisibility;
    private final RecipeCategorySortingConfig recipeCategorySortingConfig;
    private final List<RecipeType<?>> recipeTypes;
    private final Runnable removeRecipeCategorySortingConfigChangeListener;
    @Nullable
    private @Unmodifiable List<IRecipeCategory<?>> recipeCategoriesVisibleCache = null;

    public RecipeManagerInternal(List<IRecipeCategory<?>> recipeCategories, ImmutableListMultimap<RecipeType<?>, Consumer<IIngredientAcceptor<?>>> recipeCatalysts, IIngredientManager ingredientManager, RecipeCategorySortingConfig recipeCategorySortingConfig, IIngredientVisibility ingredientVisibility) {
        ErrorUtil.checkNotEmpty(recipeCategories, (String)"recipeCategories");
        this.ingredientManager = ingredientManager;
        this.ingredientVisibility = ingredientVisibility;
        this.ingredientVisibility.registerListener((IIngredientVisibility.IListener)this);
        this.recipeCategorySortingConfig = recipeCategorySortingConfig;
        this.removeRecipeCategorySortingConfigChangeListener = this.recipeCategorySortingConfig.addChangeListener(this::onRecipeCategorySortingConfigChanged);
        this.recipeTypes = recipeCategories.stream().map(IRecipeCategory::getRecipeType).toList();
        Comparator recipeTypeComparator = recipeCategorySortingConfig.getComparator(this.recipeTypes);
        this.recipeMaps = new EnumMap(RecipeIngredientRole.class);
        for (RecipeIngredientRole role : RecipeIngredientRole.values()) {
            RecipeMap recipeMap = new RecipeMap(recipeTypeComparator, ingredientManager, role);
            this.recipeMaps.put(role, recipeMap);
        }
        this.recipeCategoryComparator = Comparator.comparing(IRecipeCategory::getRecipeType, recipeTypeComparator);
        this.recipeCategories = recipeCategories.stream().sorted(this.recipeCategoryComparator).toList();
        RecipeCatalystBuilder recipeCatalystBuilder = new RecipeCatalystBuilder(this.recipeMaps.get(RecipeIngredientRole.CATALYST));
        for (IRecipeCategory<?> recipeCategory : recipeCategories) {
            RecipeType recipeType = recipeCategory.getRecipeType();
            ImmutableList categoryCatalysts = recipeCatalysts.get((Object)recipeType);
            recipeCatalystBuilder.addCategoryCatalysts(recipeCategory, (List)categoryCatalysts, this::resolveRecipeCatalyst);
        }
        ImmutableListMultimap recipeCategoryCatalystsMap = recipeCatalystBuilder.buildRecipeCategoryCatalysts();
        this.recipeTypeDataMap = new RecipeTypeDataMap(recipeCategories, recipeCategoryCatalystsMap);
        InternalRecipeManagerPlugin internalRecipeManagerPlugin = new InternalRecipeManagerPlugin(ingredientManager, this.recipeTypeDataMap, this.recipeMaps);
        this.pluginManager = new PluginManager((IRecipeManagerPlugin)internalRecipeManagerPlugin);
    }

    public void addPlugins(List<IRecipeManagerPlugin> plugins) {
        this.pluginManager.addAll(plugins);
    }

    public <T> void addRecipes(RecipeType<T> recipeType, List<T> recipes) {
        LOGGER.debug("Adding recipes: {}", recipeType);
        RecipeTypeData recipeTypeData = this.recipeTypeDataMap.get(recipeType);
        IRecipeCategory recipeCategory = recipeTypeData.getRecipeCategory();
        Set hiddenRecipes = recipeTypeData.getHiddenRecipes();
        ArrayList<T> addedRecipes = new ArrayList<T>(recipes.size());
        for (T recipe : recipes) {
            RecipeIngredientSupplier ingredientSupplier = this.addRecipe(recipeCategory, recipe, hiddenRecipes);
            if (ingredientSupplier == null) continue;
            recipeTypeData.addFocusLinks(recipe, ingredientSupplier.getFocusLinks());
            addedRecipes.add(recipe);
        }
        if (!addedRecipes.isEmpty()) {
            recipeTypeData.addRecipes(addedRecipes);
            this.recipeCategoriesVisibleCache = null;
        }
    }

    @Nullable
    private <T> RecipeIngredientSupplier addRecipe(IRecipeCategory<T> recipeCategory, T recipe, Set<T> hiddenRecipes) {
        RecipeType recipeType = recipeCategory.getRecipeType();
        if (hiddenRecipes.contains(recipe)) {
            if (LOGGER.isDebugEnabled()) {
                String recipeInfo = RecipeDebugUtil.getDebugInfoFromRecipe(recipe, recipeCategory, (IIngredientManager)this.ingredientManager);
                LOGGER.debug("Recipe not added because it is hidden: {}", (Object)recipeInfo);
            }
            return null;
        }
        if (!recipeCategory.isHandled(recipe)) {
            if (LOGGER.isDebugEnabled()) {
                String recipeInfo = RecipeDebugUtil.getDebugInfoFromRecipe(recipe, recipeCategory, (IIngredientManager)this.ingredientManager);
                LOGGER.debug("Recipe not added because the recipe category cannot handle it: {}", (Object)recipeInfo);
            }
            return null;
        }
        RecipeIngredientSupplier ingredientSupplier = IngredientSupplierHelper.getIngredientSupplier(recipe, recipeCategory, (IIngredientManager)this.ingredientManager);
        try {
            for (RecipeMap recipeMap : this.recipeMaps.values()) {
                recipeMap.addRecipe(recipeType, recipe, (IIngredientSupplier)ingredientSupplier);
            }
            return ingredientSupplier;
        }
        catch (LinkageError | RuntimeException e) {
            String recipeInfo = RecipeDebugUtil.getDebugInfoFromRecipe(recipe, recipeCategory, (IIngredientManager)this.ingredientManager);
            LOGGER.error("Found a broken recipe, failed to addRecipe: {}\n", (Object)recipeInfo, (Object)e);
            return null;
        }
    }

    public boolean isCategoryHidden(IRecipeCategory<?> recipeCategory, IFocusGroup focuses) {
        RecipeType recipeType = recipeCategory.getRecipeType();
        if (this.isRecipeTypeHidden(recipeType)) {
            return true;
        }
        if (this.hasRecipeCatalysts(recipeType, true) && !this.hasRecipeCatalysts(recipeType, false)) {
            return true;
        }
        Stream visibleRecipes = this.getRecipesStream(recipeType, focuses, false);
        return visibleRecipes.findAny().isEmpty();
    }

    private boolean hasRecipeCatalysts(RecipeType<?> recipeType, boolean includeHidden) {
        return this.getRecipeCatalystGroups(recipeType, includeHidden).findAny().isPresent();
    }

    public Stream<IRecipeCategory<?>> getRecipeCategoriesForTypes(Collection<RecipeType<?>> recipeTypes, IFocusGroup focuses, boolean includeHidden) {
        List<IRecipeCategory<?>> recipeCategories = recipeTypes.stream().map(arg_0 -> ((RecipeTypeDataMap)this.recipeTypeDataMap).get(arg_0)).map(RecipeTypeData::getRecipeCategory).toList();
        return this.getRecipeCategoriesCached(recipeCategories, focuses, includeHidden);
    }

    public <T> IRecipeCategory<T> getRecipeCategory(RecipeType<T> recipeType) {
        RecipeTypeData value = this.recipeTypeDataMap.get(recipeType);
        return value.getRecipeCategory();
    }

    private Stream<IRecipeCategory<?>> getRecipeCategoriesCached(Collection<IRecipeCategory<?>> recipeCategories, IFocusGroup focuses, boolean includeHidden) {
        if (recipeCategories.isEmpty() && focuses.isEmpty() && !includeHidden) {
            if (this.recipeCategoriesVisibleCache == null) {
                this.recipeCategoriesVisibleCache = this.getRecipeCategoriesUncached(recipeCategories, focuses, includeHidden).toList();
            }
            return this.recipeCategoriesVisibleCache.stream();
        }
        return this.getRecipeCategoriesUncached(recipeCategories, focuses, includeHidden);
    }

    private Stream<IRecipeCategory<?>> getRecipeCategoriesUncached(Collection<IRecipeCategory<?>> recipeCategories, IFocusGroup focuses, boolean includeHidden) {
        Stream<Object> categoryStream;
        if (focuses.isEmpty()) {
            categoryStream = recipeCategories.isEmpty() ? this.recipeCategories.stream() : recipeCategories.stream().distinct();
        } else {
            categoryStream = this.pluginManager.getRecipeTypes(focuses).map(arg_0 -> ((RecipeTypeDataMap)this.recipeTypeDataMap).get(arg_0)).map(RecipeTypeData::getRecipeCategory);
            if (!recipeCategories.isEmpty()) {
                categoryStream = categoryStream.filter(recipeCategories::contains);
            }
        }
        if (!includeHidden) {
            categoryStream = categoryStream.filter(c -> !this.isCategoryHidden((IRecipeCategory<?>)c, focuses));
        }
        return categoryStream.sorted(this.recipeCategoryComparator);
    }

    public <T> Stream<T> getRecipesStream(RecipeType<T> recipeType, IFocusGroup focuses, boolean includeHidden) {
        if (!includeHidden && this.isRecipeTypeHidden(recipeType)) {
            return Stream.empty();
        }
        RecipeTypeData recipeTypeData = this.recipeTypeDataMap.get(recipeType);
        Stream<Object> recipes = this.pluginManager.getRecipes(recipeTypeData, focuses, includeHidden);
        if (!includeHidden) {
            recipes = recipes.filter(recipe -> this.isRecipeVisible(recipeTypeData, recipe, focuses));
        }
        return recipes;
    }

    public <T> boolean isRecipeVisible(IRecipeCategory<T> recipeCategory, T recipe, IFocusGroup focuses) {
        RecipeTypeData recipeTypeData = this.recipeTypeDataMap.get(recipeCategory.getRecipeType());
        if (recipeTypeData.getHiddenRecipes().contains(recipe)) {
            return false;
        }
        return this.isRecipeVisible(recipeTypeData, recipe, focuses);
    }

    private <T> boolean isRecipeVisible(RecipeTypeData<T> recipeTypeData, T recipe, IFocusGroup focuses) {
        List<RecipeIngredientSupplier.FocusLink> focusLinks = this.getFocusLinks(recipeTypeData, recipe);
        for (RecipeIngredientSupplier.FocusLink focusLink : focusLinks) {
            Set visibleIndexes = focusLink.getVisibleIngredientIndexes(focuses, this.ingredientManager, ingredient -> this.ingredientVisibility.isIngredientVisible(ingredient, UidContext.Recipe));
            if (visibleIndexes != null) continue;
            return false;
        }
        return true;
    }

    private <T> List<RecipeIngredientSupplier.FocusLink> getFocusLinks(RecipeTypeData<T> recipeTypeData, T recipe) {
        List<RecipeIngredientSupplier.FocusLink> focusLinks = recipeTypeData.getFocusLinks(recipe);
        if (focusLinks != null) {
            return focusLinks;
        }
        RecipeIngredientSupplier ingredientSupplier = IngredientSupplierHelper.getIngredientSupplier(recipe, recipeTypeData.getRecipeCategory(), (IIngredientManager)this.ingredientManager);
        return ingredientSupplier.getFocusLinks();
    }

    public <T> Stream<Consumer<IIngredientAcceptor<?>>> getRecipeCatalystGroups(RecipeType<T> recipeType, boolean includeHidden) {
        Stream<Consumer<IIngredientAcceptor<?>>> catalysts = this.recipeTypeDataMap.get(recipeType).getRecipeCategoryCatalysts().stream();
        if (!includeHidden) {
            catalysts = catalysts.filter(catalyst -> this.getRecipeCatalystIngredients((Consumer<IIngredientAcceptor<?>>)catalyst, false).findAny().isPresent());
        }
        return catalysts;
    }

    public Stream<ITypedIngredient<?>> getRecipeCatalystIngredients(Consumer<IIngredientAcceptor<?>> catalyst, boolean includeHidden) {
        Stream<ITypedIngredient<?>> ingredients = this.resolveRecipeCatalyst(catalyst);
        if (!includeHidden) {
            ingredients = ingredients.filter(this::isRecipeCatalystVisible);
        }
        return ingredients;
    }

    private Stream<ITypedIngredient<?>> resolveRecipeCatalyst(Consumer<IIngredientAcceptor<?>> catalyst) {
        SimpleIngredientAcceptor acceptor = new SimpleIngredientAcceptor(this.ingredientManager);
        catalyst.accept((IIngredientAcceptor<?>)acceptor);
        return acceptor.getAllIngredients().stream().map(arg_0 -> ((IIngredientManager)this.ingredientManager).normalizeTypedIngredient(arg_0));
    }

    private boolean isRecipeCatalystVisible(ITypedIngredient<?> catalyst) {
        return this.ingredientVisibility.isIngredientVisible(catalyst, UidContext.Recipe);
    }

    public <V> void onIngredientVisibilityChanged(ITypedIngredient<V> ingredient, boolean visible) {
        this.recipeCategoriesVisibleCache = null;
    }

    public <V> void onIngredientsVisibilityChanged(Collection<ITypedIngredient<V>> ingredients, Collection<UidContext> contexts, boolean visible) {
        if (contexts.contains(UidContext.Recipe)) {
            this.recipeCategoriesVisibleCache = null;
        }
    }

    private boolean isRecipeTypeHidden(RecipeType<?> recipeType) {
        return this.hiddenRecipeTypes.contains(recipeType) || !this.recipeCategorySortingConfig.isRecipeCategoryVisible(this.recipeTypes, recipeType);
    }

    private void onRecipeCategorySortingConfigChanged() {
        this.recipeCategoriesVisibleCache = null;
    }

    public void onRuntimeStopped() {
        this.removeRecipeCategorySortingConfigChangeListener.run();
    }

    public <T> void hideRecipes(RecipeType<T> recipeType, Collection<T> recipes) {
        RecipeTypeData recipeTypeData = this.recipeTypeDataMap.get(recipeType);
        Set hiddenRecipes = recipeTypeData.getHiddenRecipes();
        hiddenRecipes.addAll(recipes);
        this.recipeCategoriesVisibleCache = null;
    }

    public <T> void unhideRecipes(RecipeType<T> recipeType, Collection<T> recipes) {
        RecipeTypeData recipeTypeData = this.recipeTypeDataMap.get(recipeType);
        Set hiddenRecipes = recipeTypeData.getHiddenRecipes();
        hiddenRecipes.removeAll(recipes);
        this.recipeCategoriesVisibleCache = null;
    }

    public void hideRecipeCategory(RecipeType<?> recipeType) {
        this.hiddenRecipeTypes.add(recipeType);
        this.recipeCategoriesVisibleCache = null;
    }

    public void unhideRecipeCategory(RecipeType<?> recipeType) {
        this.recipeTypeDataMap.validate(recipeType);
        this.hiddenRecipeTypes.remove(recipeType);
        this.recipeCategoriesVisibleCache = null;
    }

    public <T> Optional<RecipeType<T>> getRecipeType(ResourceLocation recipeUid, Class<? extends T> recipeClass) {
        return this.recipeTypeDataMap.getType(recipeUid, recipeClass);
    }

    public Optional<RecipeType<?>> getRecipeType(ResourceLocation recipeUid) {
        return this.recipeTypeDataMap.getType(recipeUid);
    }

    public void compact() {
        this.recipeMaps.values().forEach(RecipeMap::compact);
    }

    public boolean isRecipeCatalyst(RecipeType<?> recipeType, IFocus<?> focus) {
        RecipeMap recipeMap = this.recipeMaps.get(focus.getRole());
        return recipeMap.isCatalystForRecipeCategory(recipeType, focus.getTypedValue());
    }
}
