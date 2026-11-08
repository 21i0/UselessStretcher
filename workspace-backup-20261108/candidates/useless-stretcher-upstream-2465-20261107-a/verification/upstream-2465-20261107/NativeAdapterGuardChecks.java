package com.sorrowmist.useless.stretcher.content.catalog;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sorrowmist.useless.api.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Real official guards plus world-free owner-side cursor state; run old-base mode in its own JVM. */
public final class NativeAdapterGuardChecks {
    private static final JsonArray CONDITIONS = new JsonArray();
    private static int passed, failed;

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !(args[0].equals("new") || args[0].equals("legacy")))
            throw new IllegalArgumentException("Expected new|legacy result.json");
        if (args[0].equals("new")) modern(); else legacy();
        JsonObject report = new JsonObject();
        report.addProperty("status", failed == 0 ? "PASS" : "FAIL");
        report.addProperty("mode", args[0]);
        report.addProperty("checksFound", passed + failed);
        report.addProperty("checksStarted", passed + failed);
        report.addProperty("checksPassed", passed);
        report.addProperty("checksFailed", failed);
        report.addProperty("gameLaunched", false);
        report.addProperty("scope", "Real official AdapterGuards; generated/conversion production state methods without a world. Legacy mode must use an actual old-base classpath. No Minecraft process or full 184-test run.");
        report.add("conditions", CONDITIONS);
        Path result = Path.of(args[1]);
        Files.createDirectories(result.toAbsolutePath().getParent());
        Files.writeString(result, new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8);
        System.out.println("NATIVE ADAPTER GUARD CHECKS " + args[0] + " " + passed + "/" + (passed + failed));
        if (failed != 0) System.exit(1);
    }

    private static void modern() {
        check("official_guard_class_is_present_and_bridge_detects_it", () -> {
            Class.forName("com.sorrowmist.useless.content.recipe.AdapterGuards", false, NativeAdapterGuardChecks.class.getClassLoader());
            require(NativeAdapterGuards.available(), "new base did not enable guard bridge");
        });
        check("structural_runtime_disables_only_the_failing_action", () -> {
            clear(); Object adapter = new Object(); AtomicInteger calls = new AtomicInteger();
            require(NativeAdapterGuards.call(adapter, "getGeneratedRecipes", () -> { calls.incrementAndGet(); throw new IllegalStateException("bad structure"); }, -1) == -1, "fallback missing");
            require(NativeAdapterGuards.call(adapter, "getGeneratedRecipes", () -> calls.incrementAndGet(), -1) == -1 && calls.get() == 1, "disabled callback retried");
            require(NativeAdapterGuards.isDisabled(adapter, "getGeneratedRecipes"), "structural action not disabled");
            require(NativeAdapterGuards.call(adapter, "getRecipeClass", () -> 7, -1) == 7, "unrelated action disabled");
        });
        check("structural_linkage_records_the_real_source_mod_and_same_class_session_key", () -> {
            clear(); Object adapter = new Object(); NativeAdapterGuards.rememberSource(adapter, "ae2lt");
            NativeAdapterGuards.call(adapter, "getAllRecipes", () -> { throw new NoSuchMethodError("optional API moved"); }, null);
            require(NativeAdapterGuards.isDisabled(new Object(), "getAllRecipes"), "official class/action session key not shared");
            require(com.sorrowmist.useless.content.recipe.AdapterGuards.disabledMods().contains("ae2lt"), "source attribution lost");
        });
        check("per_item_runtime_skips_one_and_allows_the_next_item", () -> {
            clear(); Object adapter = new Object();
            require(NativeAdapterGuards.callEach(adapter, "convertAll", () -> { throw new IllegalArgumentException("one bad holder"); }, -1) == -1, "per-item fallback missing");
            require(!NativeAdapterGuards.isDisabled(adapter, "convertAll"), "runtime wrongly disabled next item");
            require(NativeAdapterGuards.callEach(adapter, "convertAll", () -> 9, -1) == 9, "next holder was not attempted");
        });
        check("per_item_linkage_disables_conversion_but_not_recipe_class_action", () -> {
            clear(); Object adapter = new Object(); AtomicInteger calls = new AtomicInteger();
            NativeAdapterGuards.callEach(adapter, "convertAll", () -> { calls.incrementAndGet(); throw new NoClassDefFoundError("optional/recipe/Type"); }, null);
            NativeAdapterGuards.callEach(adapter, "convertAll", () -> calls.incrementAndGet(), -1);
            require(calls.get() == 1 && NativeAdapterGuards.isDisabled(adapter, "convertAll"), "linkage action not fenced");
            require(NativeAdapterGuards.call(adapter, "getRecipeClass", () -> String.class, null) == String.class, "unrelated action fenced");
        });
        check("scope_guard_runtime_and_linkage_fallback_without_session_disable", () -> {
            clear(); Object adapter = new Object();
            require(NativeAdapterGuards.guard("scope", () -> { throw new IllegalStateException("transient"); }, -1) == -1, "runtime scope fallback missing");
            require(NativeAdapterGuards.guard("scope", () -> { throw new NoSuchMethodError("temporary linkage"); }, -1) == -1, "linkage scope fallback missing");
            require(NativeAdapterGuards.guard("scope", () -> 11, -1) == 11, "scope permanently disabled");
            require(!NativeAdapterGuards.isDisabled(adapter, "scope"), "scope polluted adapter state");
        });
        check("lazy_snapshot_fallback_is_allocated_only_after_guard_failure", () -> {
            clear(); AtomicInteger fallback = new AtomicInteger(); Object expected = new Object();
            require(NativeAdapterGuards.guardLazy("alloy-furnace recipe catalog build", () -> expected, () -> { fallback.incrementAndGet(); return new Object(); }) == expected, "ready identity changed");
            require(fallback.get() == 0, "ready hot path allocated fallback");
            Object empty = new Object();
            require(NativeAdapterGuards.guardLazy("alloy-furnace recipe catalog build", () -> { throw new NoSuchMethodError("fingerprint linkage"); }, () -> { fallback.incrementAndGet(); return empty; }) == empty && fallback.get() == 1, "empty fallback not used once");
        });
        check("official_null_adapter_fallback_does_not_execute_work", () -> {
            clear(); AtomicInteger calls = new AtomicInteger();
            require(NativeAdapterGuards.call(null, "getAllRecipes", calls::incrementAndGet, -1) == -1 && calls.get() == 0, "null adapter executed");
        });
        check("unrelated_errors_are_not_swallowed", () -> {
            clear(); AssertionError expected = new AssertionError("not a linkage error");
            expectSame(expected, () -> NativeAdapterGuards.callEach(new Object(), "convertAll", () -> { throw expected; }, null));
        });
        check("generated_late_runtime_discards_all_adapter_outputs_not_previous_source", () -> generatedRollback(new IllegalStateException("late generated helper")));
        check("generated_late_linkage_discards_all_and_another_adapter_continues", () -> generatedRollback(new NoSuchMethodError("late generated API")));
        check("conversion_late_runtime_discards_whole_holder_then_same_adapter_continues", () -> conversionRollback(new IllegalArgumentException("late variant"), false));
        check("conversion_late_linkage_discards_holder_and_fences_later_same_adapter", () -> conversionRollback(new NoClassDefFoundError("late/variant/Type"), true));
        check("callbacks_remain_on_the_calling_owner_thread", () -> {
            clear(); Thread owner = Thread.currentThread();
            require(NativeAdapterGuards.call(new Object(), "thread", Thread::currentThread, null) == owner, "callback moved threads");
            require(NativeAdapterGuards.callEach(new Object(), "thread", Thread::currentThread, null) == owner, "item callback moved threads");
        });
    }

    private static void legacy() {
        check("real_old_base_has_no_adapter_guards_class", () -> {
            try { Class.forName("com.sorrowmist.useless.content.recipe.AdapterGuards", false, NativeAdapterGuardChecks.class.getClassLoader()); throw new AssertionError("legacy classpath still contains new base"); }
            catch (ClassNotFoundException expected) { }
            require(!NativeAdapterGuards.available(), "old base falsely enabled new bridge");
        });
        check("legacy_call_executes_work_even_with_null_adapter", () -> {
            AtomicInteger calls = new AtomicInteger();
            require(NativeAdapterGuards.call(null, "getAllRecipes", calls::incrementAndGet, -1) == 1, "legacy work was intercepted");
        });
        check("legacy_structural_runtime_retains_original_exception", () -> {
            IllegalStateException expected = new IllegalStateException("legacy runtime");
            expectSame(expected, () -> NativeAdapterGuards.call(new Object(), "getGeneratedRecipes", () -> { throw expected; }, null));
        });
        check("legacy_per_item_linkage_retains_original_exception", () -> {
            NoSuchMethodError expected = new NoSuchMethodError("legacy linkage");
            expectSame(expected, () -> NativeAdapterGuards.callEach(new Object(), "convertAll", () -> { throw expected; }, null));
            require(NativeAdapterGuards.callEach(new Object(), "convertAll", () -> 5, -1) == 5, "legacy next work was fenced");
        });
        check("legacy_scope_guard_and_lazy_guard_do_not_change_old_catch_contract", () -> {
            RuntimeException expected = new RuntimeException("legacy scope"); AtomicInteger fallbacks = new AtomicInteger();
            expectSame(expected, () -> NativeAdapterGuards.guard("scope", () -> { throw expected; }, null));
            expectSame(expected, () -> NativeAdapterGuards.guardLazy("scope", () -> { throw expected; }, () -> fallbacks.incrementAndGet()));
            require(fallbacks.get() == 0, "legacy fallback hid error");
            require(!NativeAdapterGuards.isDisabled(new Object(), "scope"), "legacy state fenced");
        });
        check("legacy_source_registration_never_links_the_missing_class", () -> NativeAdapterGuards.rememberSource(new Object(), "ae2lt"));
    }

    private static void generatedRollback(Throwable failure) throws Exception {
        clear(); IRecipeAdapter<?> adapter = adapter(false); Object build = build(adapter);
        List<Object> collected = collected(build); Object prior = collected("previous");
        collected.add(prior); collected.add(collected("generated-early-1")); collected.add(collected("generated-early-2"));
        set(build, "generatedStart", 1);
        GeneratedRecipeSlices.Cursor cursor = allocate(GeneratedRecipeSlices.Cursor.class);
        set(cursor, "owner", Thread.currentThread()); setEnum(cursor, "phase", "CACHED_BASE");
        set(cursor, "cached", new Iterator<RecipeHolder<?>>() {
            public boolean hasNext() { raise(failure); return false; }
            public RecipeHolder<?> next() { throw new AssertionError("not reached"); }
        });
        set(build, "generatedSlice", cursor);
        invoke(build, "adapterStep");
        require(collected.size() == 1 && collected.getFirst() == prior, "late generated failure retained partial adapter outputs or removed another source");
        require(get(build, "generatedSlice") == null && (int) get(build, "generatedStart") == -1, "failed generation cursor retained");
        require(NativeAdapterGuards.isDisabled(adapter, "getGeneratedRecipes"), "generation failure not recorded in official guard");
        require(!NativeAdapterGuards.isDisabled(adapter, "convertAll"), "generation wrongly disabled conversion action");
        continueOther(build);
        require(collected.size() == 2 && collected.getFirst() == prior, "other adapter did not continue in order");
    }

    private static void conversionRollback(Throwable failure, boolean disables) throws Exception {
        clear(); IRecipeAdapter<?> adapter = adapter(false); Object build = build(adapter); AtomicInteger steps = new AtomicInteger();
        set(build, "conversionBuffer", new ArrayList<AdvancedAlloyFurnaceRecipe>());
        set(build, "conversionSlice", (SlicedRecipeConversions.Cursor) output -> {
            output.accept(null);
            if (steps.incrementAndGet() == 2) raise(failure);
            return false;
        });
        invoke(build, "conversionStep");
        require(((List<?>) get(build, "conversionBuffer")).size() == 1, "first variant was not buffered");
        invoke(build, "conversionStep");
        require(get(build, "conversionBuffer") == null && get(build, "conversionSlice") == null && !((Iterator<?>) get(build, "outputs")).hasNext(), "partial holder conversion leaked");
        require(NativeAdapterGuards.isDisabled(adapter, "convertAll") == disables, "wrong per-item disable semantics");
        AtomicInteger next = new AtomicInteger();
        set(build, "conversionBuffer", new ArrayList<AdvancedAlloyFurnaceRecipe>());
        set(build, "conversionSlice", (SlicedRecipeConversions.Cursor) output -> { next.incrementAndGet(); output.accept(null); return true; });
        invoke(build, "conversionStep");
        require(next.get() == (disables ? 0 : 1), "next holder invoked with wrong policy");
        require(((Iterator<?>) get(build, "outputs")).hasNext() != disables, "next holder output policy wrong");
        if (disables) continueOther(build);
    }

    private static void continueOther(Object build) throws Exception {
        activate(build, adapter(true), "other");
        set(build, "conversionBuffer", new ArrayList<AdvancedAlloyFurnaceRecipe>());
        set(build, "conversionSlice", (SlicedRecipeConversions.Cursor) output -> { output.accept(null); return true; });
        invoke(build, "conversionStep"); invoke(build, "adapterStep");
    }

    private static Object build(IRecipeAdapter<?> adapter) throws Exception {
        Object build = allocate(Class.forName(BudgetedRecipeCatalog.class.getName() + "$Build"));
        set(build, "adapterNanos", new LinkedHashMap<>()); set(build, "collected", new ArrayList<>());
        set(build, "outputs", Collections.emptyIterator()); set(build, "generated", Collections.emptyIterator());
        set(build, "adapters", Collections.emptyIterator()); set(build, "generatedStart", -1);
        activate(build, adapter, "test");
        return build;
    }
    private static void activate(Object build, IRecipeAdapter<?> adapter, String source) throws Exception {
        Class<?> type = Class.forName(BudgetedRecipeCatalog.class.getName() + "$Adapter");
        Constructor<?> constructor = type.getDeclaredConstructor(IRecipeAdapter.class, String.class); constructor.setAccessible(true);
        set(build, "active", constructor.newInstance(adapter, source));
    }
    @SuppressWarnings("unchecked") private static List<Object> collected(Object build) throws Exception { return (List<Object>) get(build, "collected"); }
    private static Object collected(String source) throws Exception {
        Class<?> type = Class.forName(BudgetedRecipeCatalog.class.getName() + "$Collected");
        Constructor<?> constructor = type.getDeclaredConstructor(AdvancedAlloyFurnaceRecipe.class, String.class); constructor.setAccessible(true);
        return constructor.newInstance(null, source);
    }
    public interface OtherMarker { }
    private static IRecipeAdapter<?> adapter(boolean other) {
        Class<?>[] types = other ? new Class<?>[]{IRecipeAdapter.class, OtherMarker.class} : new Class<?>[]{IRecipeAdapter.class};
        return (IRecipeAdapter<?>) Proxy.newProxyInstance(NativeAdapterGuardChecks.class.getClassLoader(), types,
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "guard-fixture";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "getRecipeClass" -> Object.class;
                    default -> null;
                });
    }
    @SuppressWarnings("unchecked") private static <T> T allocate(Class<T> type) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        return (T) ((Unsafe) unsafeField.get(null)).allocateInstance(type);
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static void setEnum(Object target, String name, String constant) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, Enum.valueOf((Class) field.getType(), constant));
    }
    private static Object get(Object target, String name) throws Exception { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
    private static void set(Object target, String name, Object value) throws Exception { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value); }
    private static void invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name); method.setAccessible(true);
        try { method.invoke(target); } catch (InvocationTargetException e) { Throwable cause = e.getCause(); if (cause instanceof Error error) throw error; if (cause instanceof RuntimeException runtime) throw runtime; throw e; }
    }
    private static void clear() { com.sorrowmist.useless.content.recipe.AdapterGuards.clearFailures(); }
    private static void raise(Throwable error) { if (error instanceof RuntimeException runtime) throw runtime; if (error instanceof Error fatal) throw fatal; throw new AssertionError(error); }
    private static void expectSame(Throwable expected, Throwing action) throws Exception {
        try { action.run(); } catch (Throwable actual) { require(actual == expected, "exception identity changed: " + actual); return; }
        throw new AssertionError("expected exception was swallowed");
    }
    private static void check(String name, Throwing action) {
        JsonObject condition = new JsonObject(); condition.addProperty("name", name);
        try { action.run(); passed++; condition.addProperty("status", "PASS"); System.out.println("PASS " + name); }
        catch (Throwable error) { failed++; condition.addProperty("status", "FAIL"); condition.addProperty("failure", error.toString()); error.printStackTrace(System.out); }
        CONDITIONS.add(condition);
    }
    private static void require(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
    @FunctionalInterface private interface Throwing { void run() throws Exception; }
}
