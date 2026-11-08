package com.sorrowmist.useless.stretcher.client;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog.Entry;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.library.recipes.collect.RecipeMap;

import java.util.List;
import java.util.function.LongSupplier;

/** Owner-thread, bounded diagnostics. Never expands ingredients or changes native JEI data. */
public final class CatalogJeiPublicationTiming {
    private static final ThreadLocal<Call> CURRENT = new ThreadLocal<>();
    private static final long SLOW_NANOS = 16_000_000L;
    private static final int MAX_SLOW_REPORTS = 8;
    private final LongSupplier clock;
    private long nativeCalls, nativeNanos, maximumNativeNanos;
    private long buildCalls, buildNanos, maximumBuildNanos;
    private long indexCalls, indexNanos, maximumIndexNanos;
    private long guardNanos, maximumGuardNanos, slowCalls;
    private String maximumNative = "none", maximumBuild = "none", maximumIndex = "none";
    private String maximumGuard = "none";
    private int slowReports;

    public CatalogJeiPublicationTiming() { this(System::nanoTime); }
    CatalogJeiPublicationTiming(LongSupplier clock) { this.clock = clock; }

    public void guard(String position, long elapsed) {
        guardNanos += elapsed;
        if (elapsed > maximumGuardNanos) { maximumGuardNanos = elapsed; maximumGuard = position; }
    }

    public Call begin(String operation, List<Entry> entries) { return new Call(this, operation, entries); }

    /** The redirect calls this wrapper once, including when native setRecipe throws. */
    public static <T> void build(IRecipeCategory<T> category, IRecipeLayoutBuilder builder,
                                 T recipe, IFocusGroup focuses) {
        Call call = current(recipe);
        if (call == null) { category.setRecipe(builder, recipe, focuses); return; }
        long start = call.owner.clock.getAsLong();
        try { category.setRecipe(builder, recipe, focuses); }
        finally {
            long elapsed = call.owner.clock.getAsLong() - start;
            call.buildNanos += elapsed;
            var owner = call.owner;
            owner.buildCalls++;
            owner.buildNanos += elapsed;
            if (elapsed > owner.maximumBuildNanos) {
                owner.maximumBuildNanos = elapsed;
                owner.maximumBuild = describe((Entry) recipe);
            }
        }
    }

    /** Times UID/recipe-map indexing without replacing JEI's implementation or suppliers. */
    public static <T> void index(RecipeMap map, RecipeType<T> type, T recipe, IIngredientSupplier supplier) {
        Call call = current(recipe);
        if (call == null) { map.addRecipe(type, recipe, supplier); return; }
        long start = call.owner.clock.getAsLong();
        try { map.addRecipe(type, recipe, supplier); }
        finally {
            long elapsed = call.owner.clock.getAsLong() - start;
            call.indexNanos += elapsed;
            var owner = call.owner;
            owner.indexCalls++;
            owner.indexNanos += elapsed;
            if (elapsed > owner.maximumIndexNanos) {
                owner.maximumIndexNanos = elapsed;
                owner.maximumIndex = describe((Entry) recipe);
            }
        }
    }

    private static Call current(Object recipe) {
        Call call = CURRENT.get();
        if (call != null && recipe instanceof Entry)
            for (Entry entry : call.entries) if (entry == recipe) return call;
        return null;
    }

    /** Detached identifiers and list sizes only; no getItems, registry lookup or recipe toString. */
    private static String describe(Entry entry) {
        if (entry == null) return "none";
        var identity = entry.identity();
        String text = "id=" + (identity == null ? "none" : identity.recipeId())
                + ",fingerprint=" + (identity == null ? "none" : identity.fingerprint())
                + ",source=" + entry.sourceId();
        var recipe = entry.recipe();
        if (recipe != null) text += ",inputs=" + recipe.inputs().size()
                + ",fluids=" + recipe.inputFluids().size() + ",outputs=" + recipe.outputs().size()
                + ",molds=" + recipe.molds().size();
        return text;
    }

    public Snapshot snapshot() {
        return new Snapshot(nativeCalls, nativeNanos, maximumNativeNanos, maximumNative,
                buildCalls, buildNanos, maximumBuildNanos, maximumBuild,
                indexCalls, indexNanos, maximumIndexNanos, maximumIndex,
                guardNanos, maximumGuardNanos, maximumGuard, slowCalls, slowReports);
    }

    public void logSummary(long epoch) {
        LogUtils.getLogger().info(
                "Stretcher JEI publication stages: epoch={}, native_calls={}, native_ms={}, max_native_ms={}, slowest_native=[{}], category_calls={}, category_ms={}, max_category_ms={}, slowest_category=[{}], index_calls={}, index_ms={}, max_index_ms={}, slowest_index=[{}], guard_ms={}, max_guard_ms={}, slowest_guard={}, slow_calls={}, slow_reports={}",
                epoch, nativeCalls, nativeNanos / 1_000_000.0, maximumNativeNanos / 1_000_000.0, maximumNative,
                buildCalls, buildNanos / 1_000_000.0, maximumBuildNanos / 1_000_000.0, maximumBuild,
                indexCalls, indexNanos / 1_000_000.0, maximumIndexNanos / 1_000_000.0, maximumIndex,
                guardNanos / 1_000_000.0, maximumGuardNanos / 1_000_000.0, maximumGuard, slowCalls, slowReports);
    }

    public record Snapshot(long nativeCalls, long nativeNanos, long maximumNativeNanos, String maximumNative,
                           long buildCalls, long buildNanos, long maximumBuildNanos, String maximumBuild,
                           long indexCalls, long indexNanos, long maximumIndexNanos, String maximumIndex,
                           long guardNanos, long maximumGuardNanos, String maximumGuard,
                           long slowCalls, int slowReports) {}

    public static final class Call implements AutoCloseable {
        private final CatalogJeiPublicationTiming owner;
        private final String operation;
        private final List<Entry> entries;
        private final Call previous;
        private final long start;
        private long buildNanos, indexNanos;
        private boolean closed;
        private Call(CatalogJeiPublicationTiming owner, String operation, List<Entry> entries) {
            this.owner = owner; this.operation = operation; this.entries = entries;
            previous = CURRENT.get(); CURRENT.set(this);
            start = owner.clock.getAsLong();
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            long elapsed = owner.clock.getAsLong() - start;
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
            owner.nativeCalls++;
            owner.nativeNanos += elapsed;
            if (elapsed > owner.maximumNativeNanos) {
                owner.maximumNativeNanos = elapsed;
                owner.maximumNative = operation + ",batch=" + entries.size()
                        + ",first=[" + (entries.isEmpty() ? "none" : describe(entries.getFirst()))
                        + "],last=[" + (entries.isEmpty() ? "none" : describe(entries.getLast())) + "]";
            }
            if (elapsed < SLOW_NANOS) return;
            owner.slowCalls++;
            // Bound I/O even when every recipe is slow. The final summary still counts all calls.
            if (owner.slowReports >= MAX_SLOW_REPORTS) return;
            owner.slowReports++;
            LogUtils.getLogger().warn(
                    "Stretcher JEI slow native call: operation={}, batch={}, elapsed_ms={}, category_ms={}, index_ms={}, native_other_ms={}, first=[{}], last=[{}], thread={}",
                    operation, entries.size(), elapsed / 1_000_000.0, buildNanos / 1_000_000.0,
                    indexNanos / 1_000_000.0, Math.max(0, elapsed - buildNanos - indexNanos) / 1_000_000.0,
                    entries.isEmpty() ? "none" : describe(entries.getFirst()),
                    entries.isEmpty() ? "none" : describe(entries.getLast()), Thread.currentThread().getName());
        }
    }
}
