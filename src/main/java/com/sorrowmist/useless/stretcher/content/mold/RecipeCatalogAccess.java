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
    private static final Method ENTRIES_IF_READY = find("entriesIfReady", Level.class);
    private static final Method IS_READY = find("isReady", Level.class);
    private static final Method PREWARM_ASYNC = find("prewarmAsync", Level.class);

    private RecipeCatalogAccess() {
    }

    static Snapshot read(Level level) {
        if (level == null) return new Snapshot(List.of(), true);
        if (ENTRIES_IF_READY == null || IS_READY == null) {
            // Old base versions have no non-blocking API; preserve their established behavior.
            return new Snapshot(AlloyFurnaceRecipeCatalog.entries(level), true);
        }
        try {
            boolean ready = (boolean) IS_READY.invoke(null, level);
            @SuppressWarnings("unchecked")
            List<AlloyFurnaceRecipeCatalog.Entry> entries =
                    (List<AlloyFurnaceRecipeCatalog.Entry>) ENTRIES_IF_READY.invoke(null, level);
            return new Snapshot(entries, ready);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            // A mismatched optional base build should not make the storage block unusable.
            return new Snapshot(AlloyFurnaceRecipeCatalog.entries(level), true);
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

    private static Method find(String name, Class<?>... parameterTypes) {
        try {
            return AlloyFurnaceRecipeCatalog.class.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    record Snapshot(List<AlloyFurnaceRecipeCatalog.Entry> entries, boolean ready) {
    }
}
