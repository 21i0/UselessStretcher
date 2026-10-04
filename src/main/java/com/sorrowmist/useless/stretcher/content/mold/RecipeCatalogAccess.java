package com.sorrowmist.useless.stretcher.content.mold;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Reads the shared recipe catalog without forcing the synchronous build introduced by older
 * Useless Mod versions. Reflection keeps the addon loadable with older supported base jars.
 */
final class RecipeCatalogAccess {
    private static final Reader READER = Reader.find(AlloyFurnaceRecipeCatalog.class, Level.class);
    private static final Method PREWARM_ASYNC = find("prewarmAsync", Level.class);
    private static final Method GENERATION = find("generation");

    private RecipeCatalogAccess() {
    }

    static Snapshot read(Level level) {
        if (level == null) return new Snapshot(List.of(), true);
        try {
            return READER.read(level);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            // Report an incompatible API as an error, not an infinitely pending build.
            throw new IllegalStateException("Cannot read the base recipe catalog without rebuilding it", exception);
        }
    }

    static void prewarmAsync(Level level) {
        if (level == null || PREWARM_ASYNC == null) return;
        try {
            PREWARM_ASYNC.invoke(null, level);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // The normal server lifecycle will retry the base catalog build.
        }
    }

    static long generation() {
        if (GENERATION == null) return 0L;
        try {
            return ((Number) GENERATION.invoke(null)).longValue();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return 0L;
        }
    }

    private static Method find(String name, Class<?>... parameterTypes) {
        try {
            return AlloyFurnaceRecipeCatalog.class.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    /** 2.4.5.x retains the non-blocking snapshot internally but omits its public wrappers. */
    static final class Reader {
        private final Method ready;
        private final Method snapshot;
        private final Method entries;

        private Reader(Method ready, Method snapshot, Method entries) {
            this.ready = ready;
            this.snapshot = snapshot;
            this.entries = entries;
        }

        static Reader find(Class<?> catalogClass, Class<?> levelClass) {
            try {
                return new Reader(catalogClass.getMethod("isReady", levelClass), null,
                        catalogClass.getMethod("entriesIfReady", levelClass));
            } catch (NoSuchMethodException ignored) {
                try {
                    Method snapshot = catalogClass.getDeclaredMethod("snapshotIfReady", levelClass);
                    Method entries = snapshot.getReturnType().getDeclaredMethod("entries");
                    if (snapshot.trySetAccessible() && entries.trySetAccessible()) {
                        return new Reader(null, snapshot, entries);
                    }
                } catch (ReflectiveOperationException | RuntimeException ignoredPrivateApi) {
                    // No synchronous entries(Level) fallback: it can freeze a large modpack.
                }
                return new Reader(null, null, null);
            }
        }

        @SuppressWarnings("unchecked")
        Snapshot read(Object level) throws ReflectiveOperationException {
            if (entries == null) throw new NoSuchMethodException("No non-blocking recipe catalog API");
            if (snapshot != null) {
                Object value = snapshot.invoke(null, level);
                return value == null ? new Snapshot(List.of(), false)
                        : new Snapshot((List<AlloyFurnaceRecipeCatalog.Entry>) entries.invoke(value), true);
            }
            if (!(boolean) ready.invoke(null, level)) return new Snapshot(List.of(), false);
            var values = (List<AlloyFurnaceRecipeCatalog.Entry>) entries.invoke(null, level);
            return (boolean) ready.invoke(null, level) ? new Snapshot(values, true)
                    : new Snapshot(List.of(), false);
        }
    }

    record Snapshot(List<AlloyFurnaceRecipeCatalog.Entry> entries, boolean ready) {
    }
}
