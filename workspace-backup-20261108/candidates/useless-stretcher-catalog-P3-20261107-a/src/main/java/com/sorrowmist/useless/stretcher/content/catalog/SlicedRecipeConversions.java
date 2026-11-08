package com.sorrowmist.useless.stretcher.content.catalog;

import com.sorrowmist.useless.api.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Cooperative native conversions: advance exactly once for each catalog owner step. */
public final class SlicedRecipeConversions {
    private static final String VAT_ADAPTER =
            "com.sorrowmist.useless.content.recipe.adapters.enderio.VatFermentingRecipeAdapter";

    private SlicedRecipeConversions() {}

    /** Null means the caller must use the unmodified adapter conversion. */
    public static Cursor tryCreate(IRecipeAdapter<?> adapter, RecipeHolder<?> holder, Level level) {
        if (adapter == null || !adapter.getClass().getName().equals(VAT_ADAPTER)) return null;
        return probe(adapter.getClass(), holder);
    }

    /** Reflection-only probe; no native helper, tag expansion, multiplier or conversion runs here. */
    private static Cursor probe(Class<?> adapterType, RecipeHolder<?> holder) {
        try {
            VatCursor cursor = new VatCursor(adapterType, holder);
            cursor.initialize();
            return cursor;
        } catch (RuntimeException | LinkageError unsupported) {
            LogUtils.getLogger().warn("Recipe conversion slicing unavailable for {}; native callback retained: {}",
                    adapterType.getName(), unsupported.toString());
            return null;
        }
    }

    public interface Cursor {
        /** One bounded native operation, at most one output. Returns true after the final operation. */
        boolean advance(Consumer<AdvancedAlloyFurnaceRecipe> output);
        default String phase() { return "STEP"; }
    }

    private enum Stage { VALIDATE, FIRST_TAG, FIRST_DISTINCT, SECOND_TAG, SECOND_DISTINCT, PAIR, REVERSE, OUTPUT, DONE }

    /**
     * Ender IO is optional, so resolve its native adapter ABI only for an actual Vat adapter.
     * Native private helpers retain all component matching, ID, multiplier and mold semantics.
     */
    private static final class VatCursor implements Cursor {
        final Thread owner = Thread.currentThread();
        final Class<?> adapter;
        final RecipeHolder<?> holder;
        final List<Object> pairs = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        Stage stage;
        Object recipe;
        List<ItemStack> first, second;
        DistinctStacks distinct;
        int a, b, outputIndex;
        boolean allSame = true;
        double firstMultiplier;
        long firstAmount;
        Method valid, addPair, exact, variantId, createRecipe;
        Method firstReagent, secondReagent, input, outputFluid;
        Method pairFirst, pairSecond, pairMultiplier, pairAmount, copyWithAmount;

        VatCursor(Class<?> adapter, RecipeHolder<?> holder) { this.adapter = adapter; this.holder = holder; }

        @Override public String phase() { return stage.name(); }

        @Override public boolean advance(Consumer<AdvancedAlloyFurnaceRecipe> output) {
            if (Thread.currentThread() != owner) throw new IllegalStateException("Conversion owner thread changed");
            if (output == null) throw new NullPointerException("output");
            switch (stage) {
                case VALIDATE -> stage = (boolean) call(valid, recipe) ? Stage.FIRST_TAG : Stage.DONE;
                case FIRST_TAG -> {
                    distinct = new DistinctStacks(Ingredient.of(tag(callOn(firstReagent, recipe))).getItems());
                    stage = Stage.FIRST_DISTINCT;
                }
                case FIRST_DISTINCT -> {
                    if (distinct.advance()) { first = distinct.values; distinct = null; stage = Stage.SECOND_TAG; }
                }
                case SECOND_TAG -> {
                    distinct = new DistinctStacks(Ingredient.of(tag(callOn(secondReagent, recipe))).getItems());
                    stage = Stage.SECOND_DISTINCT;
                }
                case SECOND_DISTINCT -> {
                    if (distinct.advance()) {
                        second = distinct.values; distinct = null;
                        stage = first.isEmpty() || second.isEmpty() ? Stage.DONE : Stage.PAIR;
                    }
                }
                case PAIR -> {
                    if (!add(first.get(a), second.get(b))) { stage = Stage.DONE; break; }
                    stage = Stage.REVERSE;
                }
                case REVERSE -> {
                    ItemStack left = first.get(a), right = second.get(b);
                    if (left.is(tag(callOn(secondReagent, recipe)))
                            && right.is(tag(callOn(firstReagent, recipe))) && !add(right, left)) {
                        stage = Stage.DONE;
                        break;
                    }
                    if (++b == second.size()) { b = 0; a++; }
                    stage = a == first.size() ? Stage.OUTPUT : Stage.PAIR;
                }
                case OUTPUT -> emit(output);
                case DONE -> { }
            }
            return stage == Stage.DONE;
        }

        private void initialize() {
            if (holder == null || holder.value() == null) { stage = Stage.DONE; return; }
            recipe = holder.value();
            Class<?> recipeType = recipe.getClass();
            valid = method(adapter, "valid", recipeType);
            addPair = method(adapter, "addPairResult", recipeType, ItemStack.class, ItemStack.class, List.class, Set.class);
            exact = method(adapter, "exact", ItemStack.class);
            variantId = method(adapter, "variantId", ResourceLocation.class, int.class, int.class);
            firstReagent = method(recipeType, "firstReagent");
            secondReagent = method(recipeType, "secondReagent");
            input = method(recipeType, "input");
            outputFluid = method(recipeType, "output");
            Class<?> fluidType = outputFluid.getReturnType();
            copyWithAmount = method(fluidType, "copyWithAmount", int.class);
            createRecipe = method(adapter, "createRecipe", ResourceLocation.class, recipeType,
                    CountedIngredient.class, CountedIngredient.class, input.getReturnType(), fluidType, ResourceLocation.class);
            Class<?> pairType;
            try { pairType = Class.forName(adapter.getName() + "$PairResult", false, adapter.getClassLoader()); }
            catch (ClassNotFoundException error) { throw new IllegalStateException("Native Vat pair ABI changed", error); }
            pairFirst = method(pairType, "first"); pairSecond = method(pairType, "second");
            pairMultiplier = method(pairType, "multiplier"); pairAmount = method(pairType, "outputAmount");
            stage = Stage.VALIDATE;
        }

        private boolean add(ItemStack left, ItemStack right) {
            int before = pairs.size();
            if (!(boolean) call(addPair, recipe, left, right, pairs, seen)) return false;
            if (pairs.size() > before) {
                Object pair = pairs.getLast();
                double multiplier = (double) callOn(pairMultiplier, pair);
                long amount = (long) callOn(pairAmount, pair);
                if (before == 0) { firstMultiplier = multiplier; firstAmount = amount; }
                else if (Double.compare(multiplier, firstMultiplier) != 0 || amount != firstAmount) allSame = false;
            }
            return true;
        }

        private void emit(Consumer<AdvancedAlloyFurnaceRecipe> sink) {
            if (pairs.isEmpty()) { stage = Stage.DONE; return; }
            Object pair = pairs.get(outputIndex);
            Ingredient left = allSame ? Ingredient.of(tag(callOn(firstReagent, recipe)))
                    : (Ingredient) call(exact, callOn(pairFirst, pair));
            Ingredient right = allSame ? Ingredient.of(tag(callOn(secondReagent, recipe)))
                    : (Ingredient) call(exact, callOn(pairSecond, pair));
            Object fluid = callOn(outputFluid, recipe);
            Object result = callOn(copyWithAmount, fluid, ((Long) callOn(pairAmount, pair)).intValue());
            ResourceLocation id = (ResourceLocation) call(variantId, holder.id(), 0, allSame ? 0 : outputIndex);
            sink.accept((AdvancedAlloyFurnaceRecipe) call(createRecipe, holder.id(), recipe,
                    new CountedIngredient(left, 1L), new CountedIngredient(right, 1L),
                    callOn(input, recipe), result, id));
            if (allSame || ++outputIndex == pairs.size()) stage = Stage.DONE;
        }
    }

    /** Same first-occurrence, count-insensitive component equality as native distinctStacks. */
    private static final class DistinctStacks {
        final ItemStack[] candidates;
        final List<ItemStack> values = new ArrayList<>();
        final Set<StackKey> seen = new LinkedHashSet<>();
        int position;
        DistinctStacks(ItemStack[] candidates) { this.candidates = candidates; }
        boolean advance() {
            if (position == candidates.length) return true;
            ItemStack candidate = candidates[position++];
            if (candidate != null && !candidate.isEmpty() && seen.add(new StackKey(candidate))) values.add(candidate.copy());
            return position == candidates.length;
        }
    }

    private record StackKey(ItemStack stack) {
        @Override public boolean equals(Object other) {
            return other instanceof StackKey key && ItemStack.isSameItemSameComponents(stack, key.stack);
        }
        @Override public int hashCode() { return ItemStack.hashItemAndComponents(stack); }
    }

    @SuppressWarnings("unchecked") private static <T> TagKey<T> tag(Object value) { return (TagKey<T>) value; }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        try {
            Method result = owner.getDeclaredMethod(name, parameters);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Native conversion ABI changed: " + name, error); }
    }

    private static Object call(Method method, Object... parameters) { return callOn(method, null, parameters); }

    private static Object callOn(Method method, Object receiver, Object... parameters) {
        try { return method.invoke(receiver, parameters); }
        catch (IllegalAccessException error) { throw new IllegalStateException("Cannot access native conversion helper", error); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            if (error.getCause() instanceof Error fatal) throw fatal;
            throw new IllegalStateException("Native conversion helper failed", error.getCause());
        }
    }
}
