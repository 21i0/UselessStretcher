/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mezz.jei.api.gui.IRecipeLayoutDrawable
 *  mezz.jei.api.gui.builder.IIngredientAcceptor
 *  mezz.jei.api.recipe.IFocus
 *  mezz.jei.api.recipe.IFocusFactory
 *  mezz.jei.api.recipe.IFocusGroup
 *  mezz.jei.api.recipe.IRecipeManager
 *  mezz.jei.api.recipe.RecipeType
 *  mezz.jei.api.recipe.category.IRecipeCategory
 *  mezz.jei.api.runtime.IIngredientManager
 *  mezz.jei.common.Internal
 *  mezz.jei.common.config.IClientConfig
 *  mezz.jei.common.config.IClientConfigs
 *  mezz.jei.common.config.RecipeSorterStage
 *  mezz.jei.common.recipes.IRecipeVisibility
 *  mezz.jei.common.transfer.RecipeTransferService
 *  mezz.jei.common.util.MathUtil
 *  mezz.jei.gui.bookmarks.BookmarkFactory
 *  mezz.jei.gui.bookmarks.BookmarkList
 *  mezz.jei.gui.bookmarks.IBookmark
 *  mezz.jei.gui.bookmarks.IngredientBookmark
 *  mezz.jei.gui.bookmarks.RecipeBookmark
 *  mezz.jei.gui.overlay.bookmarks.history.LookupHistory
 *  mezz.jei.gui.recipes.IRecipeGuiLogic
 *  mezz.jei.gui.recipes.IRecipeLayoutWithButtons
 *  mezz.jei.gui.recipes.IRecipeLogicStateListener
 *  mezz.jei.gui.recipes.NavigationHistory
 *  mezz.jei.gui.recipes.layouts.IRecipeLayoutList
 *  mezz.jei.gui.recipes.lookups.IFocusedRecipes
 *  mezz.jei.gui.recipes.lookups.ILookupState
 *  mezz.jei.gui.recipes.lookups.IngredientLookupState
 *  mezz.jei.gui.recipes.lookups.SingleCategoryLookupState
 *  net.minecraft.client.renderer.Rect2i
 *  net.minecraft.world.inventory.AbstractContainerMenu
 *  org.jetbrains.annotations.Nullable
 *  org.jetbrains.annotations.Unmodifiable
 */
package mezz.jei.gui.recipes;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.Internal;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IClientConfigs;
import mezz.jei.common.config.RecipeSorterStage;
import mezz.jei.common.recipes.IRecipeVisibility;
import mezz.jei.common.transfer.RecipeTransferService;
import mezz.jei.common.util.MathUtil;
import mezz.jei.gui.bookmarks.BookmarkFactory;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.overlay.bookmarks.history.LookupHistory;
import mezz.jei.gui.recipes.IRecipeGuiLogic;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.IRecipeLogicStateListener;
import mezz.jei.gui.recipes.NavigationHistory;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.gui.recipes.layouts.IRecipeLayoutList;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.ILookupState;
import mezz.jei.gui.recipes.lookups.IngredientLookupState;
import mezz.jei.gui.recipes.lookups.SingleCategoryLookupState;
import mezz.jei.gui.recipes.lookups.StaticFocusedRecipes;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

public class RecipeGuiLogic
implements IRecipeGuiLogic {
    private final IRecipeManager recipeManager;
    private final IIngredientManager ingredientManager;
    private final RecipeTransferService recipeTransferService;
    private final IRecipeLogicStateListener stateListener;
    private boolean initialState = true;
    private ILookupState state;
    private final NavigationHistory<ILookupState> stateHistory = new NavigationHistory();
    private final LookupHistory lookupHistory;
    private final IFocusFactory focusFactory;
    private final BookmarkFactory bookmarkFactory;
    @Nullable
    private IRecipeCategory<?> cachedRecipeCategory;
    @Nullable
    private IRecipeLayoutList cachedRecipeLayoutsWithButtons;
    private int cachedContainerId = -1;
    private Set<RecipeSorterStage> cachedSorterStages = Set.of();

    public RecipeGuiLogic(IRecipeManager recipeManager, IIngredientManager ingredientManager, LookupHistory lookupHistory, RecipeTransferService recipeTransferService, IRecipeLogicStateListener stateListener, IFocusFactory focusFactory, BookmarkFactory bookmarkFactory) {
        this.recipeManager = recipeManager;
        this.ingredientManager = ingredientManager;
        this.lookupHistory = lookupHistory;
        this.recipeTransferService = recipeTransferService;
        this.stateListener = stateListener;
        List recipeCategories = recipeManager.createRecipeCategoryLookup().get().toList();
        this.state = IngredientLookupState.create((IRecipeManager)recipeManager, (IFocusGroup)focusFactory.getEmptyFocusGroup(), recipeCategories, (RecipeTransferService)recipeTransferService);
        this.focusFactory = focusFactory;
        this.bookmarkFactory = bookmarkFactory;
    }

    public void tick() {
        if (this.cachedRecipeLayoutsWithButtons != null) {
            this.cachedRecipeLayoutsWithButtons.tick();
        }
    }

    public boolean showFocus(IFocusGroup focuses) {
        List allFocuses = focuses.getAllFocuses();
        List recipeCategories = this.recipeManager.createRecipeCategoryLookup().limitFocus((Collection)allFocuses).get().toList();
        ILookupState state = IngredientLookupState.create((IRecipeManager)this.recipeManager, (IFocusGroup)focuses, recipeCategories, (RecipeTransferService)this.recipeTransferService);
        for (IFocus focus : allFocuses) {
            IngredientBookmark ingredientBookmark = this.bookmarkFactory.create(focus.getTypedValue());
            this.lookupHistory.add((IBookmark)ingredientBookmark);
        }
        return this.setState(state, true);
    }

    public boolean showRecipes(IFocusedRecipes<?> focusedRecipes, IFocusGroup focuses) {
        if ((focusedRecipes = this.filterVisibleRecipes(focusedRecipes, focuses)).getRecipes().isEmpty()) {
            return false;
        }
        RecipeBookmark<?, ?> recipeBookmark = RecipeGuiLogic.createRecipeBookmark(this.recipeManager, this.ingredientManager, this.recipeTransferService, focusedRecipes, focuses);
        if (recipeBookmark != null) {
            this.lookupHistory.add(recipeBookmark);
        } else {
            for (IFocus focus : focuses.getAllFocuses()) {
                IngredientBookmark ingredientBookmark = this.bookmarkFactory.create(focus.getTypedValue());
                this.lookupHistory.add((IBookmark)ingredientBookmark);
            }
        }
        SingleCategoryLookupState state = new SingleCategoryLookupState(focusedRecipes, focuses);
        return this.setState((ILookupState)state, true);
    }

    private <T> IFocusedRecipes<T> filterVisibleRecipes(IFocusedRecipes<T> focusedRecipes, IFocusGroup focuses) {
        IRecipeManager iRecipeManager = this.recipeManager;
        if (!(iRecipeManager instanceof IRecipeVisibility)) {
            return focusedRecipes;
        }
        IRecipeVisibility recipeVisibility = (IRecipeVisibility)iRecipeManager;
        IRecipeCategory recipeCategory = focusedRecipes.getRecipeCategory();
        List<Object> visibleRecipes = focusedRecipes.getRecipes().stream().filter(recipe -> recipeVisibility.isRecipeVisible(recipeCategory, recipe, focuses)).toList();
        return new StaticFocusedRecipes<Object>(recipeCategory, visibleRecipes);
    }

    @Nullable
    private static <T> RecipeBookmark<T, ?> createRecipeBookmark(IRecipeManager recipeManager, IIngredientManager ingredientManager, RecipeTransferService recipeTransferService, IFocusedRecipes<T> focusedRecipes, IFocusGroup focusGroup) {
        IRecipeCategory recipeCategory = focusedRecipes.getRecipeCategory();
        List recipes = focusedRecipes.getRecipes();
        if (recipes.size() != 1) {
            return null;
        }
        Object recipe = recipes.getFirst();
        return recipeManager.createRecipeLayoutDrawable(recipeCategory, recipe, focusGroup).map(drawable -> RecipeBookmark.create((IRecipeLayoutDrawable)drawable, (IIngredientManager)ingredientManager, (RecipeTransferService)recipeTransferService)).orElse(null);
    }

    public boolean back() {
        return this.stateHistory.goBack((Object)this.state).map(previousState -> this.setState((ILookupState)previousState, false)).orElse(false);
    }

    public boolean forward() {
        return this.stateHistory.goForward((Object)this.state).map(nextState -> this.setState((ILookupState)nextState, false)).orElse(false);
    }

    public void clearHistory() {
        this.stateHistory.clear();
    }

    private boolean setState(ILookupState state, boolean saveHistory) {
        List recipeCategories = state.getRecipeCategories();
        if (recipeCategories.isEmpty()) {
            return false;
        }
        if (saveHistory && !this.initialState) {
            this.stateHistory.record((Object)this.state);
        }
        this.state = state;
        this.initialState = false;
        this.cachedRecipeCategory = null;
        this.cachedRecipeLayoutsWithButtons = null;
        this.cachedContainerId = -1;
        this.stateListener.onStateChange();
        return true;
    }

    public boolean showAllRecipes() {
        IRecipeCategory<?> recipeCategory = this.getSelectedRecipeCategory();
        List recipeCategories = this.recipeManager.createRecipeCategoryLookup().get().toList();
        ILookupState state = IngredientLookupState.create((IRecipeManager)this.recipeManager, (IFocusGroup)this.focusFactory.getEmptyFocusGroup(), recipeCategories, (RecipeTransferService)this.recipeTransferService);
        state.moveToRecipeCategory(recipeCategory);
        this.setState(state, true);
        return true;
    }

    public boolean showCategories(List<RecipeType<?>> recipeTypes) {
        List recipeCategories = this.recipeManager.createRecipeCategoryLookup().limitTypes(recipeTypes).get().toList();
        ILookupState state = IngredientLookupState.create((IRecipeManager)this.recipeManager, (IFocusGroup)this.focusFactory.getEmptyFocusGroup(), recipeCategories, (RecipeTransferService)this.recipeTransferService);
        if (state.getRecipeCategories().isEmpty()) {
            return false;
        }
        this.setState(state, true);
        return true;
    }

    public Stream<Consumer<IIngredientAcceptor<?>>> getRecipeCatalystGroups() {
        IRecipeCategory<?> category = this.getSelectedRecipeCategory();
        RecipeType recipeType = category.getRecipeType();
        return this.recipeManager.createRecipeCatalystLookup(recipeType).getGroups();
    }

    public IRecipeCategory<?> getSelectedRecipeCategory() {
        return this.state.getFocusedRecipes().getRecipeCategory();
    }

    public @Unmodifiable List<IRecipeCategory<?>> getRecipeCategories() {
        return this.state.getRecipeCategories();
    }

    public List<IRecipeLayoutWithButtons<?>> getVisibleRecipeLayoutsWithButtons(int availableHeight, int minRecipePadding, @Nullable AbstractContainerMenu container, BookmarkList bookmarkList, RecipesGui recipesGui) {
        IRecipeCategory<?> recipeCategory = this.getSelectedRecipeCategory();
        IClientConfigs jeiClientConfigs = Internal.getClientConfigs();
        IClientConfig clientConfig = jeiClientConfigs.getClientConfig();
        Set recipeSorterStages = RecipeSorterStage.getEnabled((IClientConfig)clientConfig);
        int containerId = -1;
        if (container != null) {
            containerId = container.containerId;
        }
        if (!recipeSorterStages.equals(this.cachedSorterStages) || this.cachedRecipeLayoutsWithButtons == null || this.cachedRecipeCategory != recipeCategory || this.cachedContainerId != containerId) {
            IFocusedRecipes focusedRecipes = this.state.getFocusedRecipes();
            this.cachedRecipeLayoutsWithButtons = IRecipeLayoutList.create((Set)recipeSorterStages, (AbstractContainerMenu)container, (IFocusedRecipes)focusedRecipes, (IFocusGroup)this.state.getFocuses(), (BookmarkList)bookmarkList, (IRecipeManager)this.recipeManager, (RecipeTransferService)this.recipeTransferService, (RecipesGui)recipesGui);
            this.cachedRecipeCategory = recipeCategory;
            this.cachedSorterStages = Set.copyOf(recipeSorterStages);
            this.cachedContainerId = containerId;
        }
        int recipeHeight = this.cachedRecipeLayoutsWithButtons.findFirst().map(IRecipeLayoutWithButtons::getRecipeLayout).map(IRecipeLayoutDrawable::getRectWithBorder).map(Rect2i::getHeight).orElseGet(() -> recipeCategory.getHeight());
        int recipesPerPage = Math.max(1, 1 + (availableHeight - recipeHeight) / (recipeHeight + minRecipePadding));
        this.state.setRecipesPerPage(recipesPerPage);
        return this.state.getVisible(this.cachedRecipeLayoutsWithButtons);
    }

    public int getRecipesPerPage() {
        return this.state.getRecipesPerPage();
    }

    public boolean nextRecipeCategory() {
        if (this.state.nextRecipeCategory()) {
            this.stateListener.onStateChange();
            return true;
        }
        return false;
    }

    public void setRecipeCategory(IRecipeCategory<?> category) {
        if (this.state.moveToRecipeCategory(category)) {
            this.stateListener.onStateChange();
        }
    }

    public boolean hasMultiplePages() {
        List recipes = this.state.getFocusedRecipes().getRecipes();
        return recipes.size() > this.state.getRecipesPerPage();
    }

    public boolean previousRecipeCategory() {
        if (this.state.previousRecipeCategory()) {
            this.stateListener.onStateChange();
            return true;
        }
        return false;
    }

    public void goToFirstPage() {
        this.state.goToFirstPage();
        this.stateListener.onStateChange();
    }

    public boolean nextPage() {
        if (this.state.nextPage()) {
            this.stateListener.onStateChange();
            return true;
        }
        return false;
    }

    public boolean previousPage() {
        if (this.state.previousPage()) {
            this.stateListener.onStateChange();
            return true;
        }
        return false;
    }

    public String getPageString() {
        int pageIndex = MathUtil.divideCeil((int)(this.state.getRecipeIndex() + 1), (int)this.state.getRecipesPerPage());
        return pageIndex + "/" + this.state.pageCount();
    }

    public boolean hasMultipleCategories() {
        return this.state.getRecipeCategories().size() > 1;
    }

    public boolean hasAllCategories() {
        long categoryCount = this.recipeManager.createRecipeCategoryLookup().get().count();
        return (long)this.state.getRecipeCategories().size() == categoryCount;
    }
}
