/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.base.Stopwatch
 *  mezz.jei.api.recipe.IFocus
 *  mezz.jei.api.recipe.IFocusGroup
 *  mezz.jei.api.recipe.RecipeType
 *  mezz.jei.api.recipe.advanced.IRecipeManagerPlugin
 *  mezz.jei.api.recipe.category.IRecipeCategory
 *  mezz.jei.common.util.TimeUtil
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package mezz.jei.library.recipes;

import com.google.common.base.Stopwatch;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.advanced.IRecipeManagerPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.util.TimeUtil;
import mezz.jei.library.recipes.collect.RecipeTypeData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PluginManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private List<IRecipeManagerPlugin> plugins = new ArrayList<IRecipeManagerPlugin>();

    public PluginManager(IRecipeManagerPlugin internalRecipeManagerPlugin) {
        this.plugins.add(internalRecipeManagerPlugin);
    }

    public <T> Stream<T> getRecipes(RecipeTypeData<T> recipeTypeData, IFocusGroup focusGroup, boolean includeHidden) {
        IRecipeCategory recipeCategory = recipeTypeData.getRecipeCategory();
        Stream<Object> recipes = this.plugins.stream().flatMap(p -> this.getPluginRecipeStream((IRecipeManagerPlugin)p, recipeCategory, focusGroup)).distinct();
        if (!includeHidden) {
            Set<T> hiddenRecipes = recipeTypeData.getHiddenRecipes();
            Predicate<Object> notHidden = ((Predicate<Object>)hiddenRecipes::contains).negate();
            recipes = recipes.filter(notHidden);
        }
        return recipes;
    }

    public Stream<RecipeType<?>> getRecipeTypes(IFocusGroup focusGroup) {
        return this.plugins.stream().flatMap(p -> this.getPluginRecipeTypeStream((IRecipeManagerPlugin)p, focusGroup)).distinct();
    }

    private Stream<RecipeType<?>> getPluginRecipeTypeStream(IRecipeManagerPlugin plugin, IFocusGroup focuses) {
        List allFocuses = focuses.getAllFocuses();
        return allFocuses.stream().flatMap(focus -> this.getRecipeTypes(plugin, (IFocus<?>)focus));
    }

    private <T> Stream<T> getPluginRecipeStream(IRecipeManagerPlugin plugin, IRecipeCategory<T> recipeCategory, IFocusGroup focuses) {
        if (!focuses.isEmpty()) {
            List allFocuses = focuses.getAllFocuses();
            return allFocuses.stream().flatMap(focus -> this.getRecipes(plugin, recipeCategory, (IFocus<?>)focus));
        }
        return this.getRecipes(plugin, recipeCategory);
    }

    private Stream<RecipeType<?>> getRecipeTypes(IRecipeManagerPlugin plugin, IFocus<?> focus) {
        return this.safeCallPlugin(plugin, () -> plugin.getRecipeTypes(focus).stream(), Stream.of(new RecipeType[0]));
    }

    private <T> Stream<T> getRecipes(IRecipeManagerPlugin plugin, IRecipeCategory<T> recipeCategory) {
        return this.safeCallPlugin(plugin, () -> plugin.getRecipes(recipeCategory).stream(), Stream.of(new Object[0]));
    }

    private <T> Stream<T> getRecipes(IRecipeManagerPlugin plugin, IRecipeCategory<T> recipeCategory, IFocus<?> focus) {
        return this.safeCallPlugin(plugin, () -> plugin.getRecipes(recipeCategory, focus).stream(), Stream.of(new Object[0]));
    }

    private <T> T safeCallPlugin(IRecipeManagerPlugin plugin, Supplier<T> supplier, T defaultValue) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            T result = supplier.get();
            stopwatch.stop();
            if (stopwatch.elapsed(TimeUnit.MILLISECONDS) > 10L) {
                LOGGER.warn("Recipe registry plugin is slow, took {}. {}", (Object)TimeUtil.toHumanString((Duration)stopwatch.elapsed()), (Object)plugin.getClass());
            }
            return result;
        }
        catch (LinkageError | RuntimeException e) {
            LOGGER.error("Recipe registry plugin crashed, it is being disabled: {}", (Object)plugin.getClass(), (Object)e);
            this.plugins = new ArrayList<IRecipeManagerPlugin>(this.plugins);
            this.plugins.remove(plugin);
            return defaultValue;
        }
    }

    public void addAll(List<IRecipeManagerPlugin> plugins) {
        this.plugins.addAll(plugins);
    }
}
