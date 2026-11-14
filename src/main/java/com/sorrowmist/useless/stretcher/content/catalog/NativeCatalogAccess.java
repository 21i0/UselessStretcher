package com.sorrowmist.useless.stretcher.content.catalog;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Cached private ABI for the byte-identical catalog in base 2.4.6 and 2.4.6.2. */
final class NativeCatalogAccess {
    private static final Class<?> CATALOG = AlloyFurnaceRecipeCatalog.class;
    private static final Class<?> SNAPSHOT = nested("Snapshot");
    private static final Class<?> COLLECTED = nested("CollectedRecipe");
    private static final Class<?> FINGERPRINTED = nested("FingerprintedRecipe");
    private static final Class<?> SOURCE_KEY = nested("SourceRecipeKey");
    private static final Constructor<?> NEW_SNAPSHOT = constructor(SNAPSHOT, List.class, Map.class, Map.class,
            Map.class, Map.class, long.class, Map.class, Map.class, Set.class);
    private static final Constructor<?> NEW_COLLECTED = constructor(COLLECTED, AdvancedAlloyFurnaceRecipe.class, String.class);
    private static final Constructor<?> NEW_SOURCE_KEY = constructor(SOURCE_KEY, String.class, ResourceLocation.class);
    private static final Method FINGERPRINT = method(CATALOG, "fingerprint", COLLECTED, Level.class);
    private static final Method IDENTITY = method(FINGERPRINTED, "identity");
    private static final Method GENERATION = method(CATALOG, "currentGenerationLocked", Object.class);
    private static final Method SNAPSHOT_GENERATION = method(SNAPSHOT, "generation");
    @SuppressWarnings("unchecked")
    static final Map<Object, Object> CACHE = (Map<Object, Object>) field("CACHE");
    private static final AtomicInteger COUNT = (AtomicInteger) field("CURRENT_RECIPE_COUNT");

    private NativeCatalogAccess() {}

    static long generation(Object manager) {
        synchronized (CACHE) { return (Long) invoke(GENERATION, manager); }
    }

    static Object ready(Object manager) {
        synchronized (CACHE) {
            Object snapshot = CACHE.get(manager);
            return snapshot != null && (Long) invoke(SNAPSHOT_GENERATION, snapshot, true) == generation(manager)
                    ? snapshot : null;
        }
    }

    static boolean publish(Object manager, long generation, Object snapshot, int count) {
        synchronized (CACHE) {
            if (generation(manager) != generation) return false;
            CACHE.put(manager, snapshot);
            COUNT.set(count);
            return true;
        }
    }

    static AlloyFurnaceRecipeIdentity fingerprint(AdvancedAlloyFurnaceRecipe recipe, String source, Level level) {
        Object collected = construct(NEW_COLLECTED, recipe, source);
        Object fingerprinted = invoke(FINGERPRINT, collected, level);
        return fingerprinted == null ? null : (AlloyFurnaceRecipeIdentity) invoke(IDENTITY, fingerprinted, true);
    }

    static Object sourceKey(String source, ResourceLocation id) { return construct(NEW_SOURCE_KEY, source, id); }

    /** All input collections are exclusively owned by a completed builder and never mutated again. */
    static Object snapshot(List<AlloyFurnaceRecipeCatalog.Entry> entries,
                           Map<AlloyFurnaceRecipeIdentity, AlloyFurnaceRecipeCatalog.Entry> unique,
                           Map<ResourceLocation, List<AlloyFurnaceRecipeCatalog.Entry>> byId,
                           Map<String, List<AlloyFurnaceRecipeCatalog.Entry>> bySource,
                           Map<Object, List<AlloyFurnaceRecipeCatalog.Entry>> bySourceAndId, long generation) {
        return construct(NEW_SNAPSHOT, Collections.unmodifiableList(entries), Collections.unmodifiableMap(unique),
                Collections.unmodifiableMap(byId), Collections.unmodifiableMap(bySource),
                Collections.unmodifiableMap(bySourceAndId), generation, new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(), ConcurrentHashMap.newKeySet());
    }

    static Object empty(long generation) {
        return snapshot(List.of(), Map.of(), Map.of(), Map.of(), Map.of(), generation);
    }

    private static Class<?> nested(String name) {
        try { return Class.forName(CATALOG.getName() + "$" + name); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Unsupported native catalog ABI: " + name, error); }
    }

    private static Constructor<?> constructor(Class<?> type, Class<?>... arguments) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(arguments);
            if (!constructor.trySetAccessible()) throw new IllegalAccessException(type.getName());
            return constructor;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Unsupported native catalog constructor", error); }
    }

    private static Method method(Class<?> type, String name, Class<?>... arguments) {
        try {
            Method method = type.getDeclaredMethod(name, arguments);
            if (!method.trySetAccessible()) throw new IllegalAccessException(name);
            return method;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Unsupported native catalog method: " + name, error); }
    }

    private static Object field(String name) {
        try {
            Field field = CATALOG.getDeclaredField(name);
            if (!field.trySetAccessible()) throw new IllegalAccessException(name);
            return field.get(null);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Unsupported native catalog field: " + name, error); }
    }

    private static Object construct(Constructor<?> constructor, Object... arguments) {
        try { return constructor.newInstance(arguments); }
        catch (ReflectiveOperationException error) { throw failure(error); }
    }

    private static Object invoke(Method method, Object... arguments) {
        try { return method.invoke(null, arguments); }
        catch (ReflectiveOperationException error) { throw failure(error); }
    }

    // Separate arity avoids confusing a record receiver with the static method's argument list.
    private static Object invoke(Method method, Object receiver, boolean instance) {
        try { return method.invoke(receiver); }
        catch (ReflectiveOperationException error) { throw failure(error); }
    }

    private static RuntimeException failure(ReflectiveOperationException error) {
        Throwable cause = error instanceof InvocationTargetException target ? target.getCause() : error;
        if (cause instanceof RuntimeException runtime) return runtime;
        if (cause instanceof Error fatal) throw fatal;
        return new IllegalStateException("Native catalog invocation failed", cause);
    }
}
