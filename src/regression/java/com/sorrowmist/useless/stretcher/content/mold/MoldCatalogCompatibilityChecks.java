package com.sorrowmist.useless.stretcher.content.mold;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.List;
import java.util.Map;

/** Opt-in API fixtures cover both released 2.4.5.x and newer non-blocking wrappers. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class MoldCatalogCompatibilityChecks {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void start(ServerStartedEvent event) {
        if (!Boolean.getBoolean("useless_stretcher.regression")) return;
        try {
            var legacy = RecipeCatalogAccess.Reader.find(PrivateCatalog.class, Object.class);
            check(!legacy.read(null).ready(), "unpublished legacy snapshot is pending");
            PrivateCatalog.current = new PrivateSnapshot(List.of());
            var empty = legacy.read(null);
            check(empty.ready(), "an empty published catalog must still be ready");
            check(empty.entries() == PrivateCatalog.current.entries(), "preserve immutable snapshot identity");
            PrivateCatalog.current = null;
            check(!legacy.read(null).ready(), "invalidated legacy snapshot becomes pending");

            var modern = RecipeCatalogAccess.Reader.find(PublicCatalog.class, Object.class);
            check(!modern.read(null).ready(), "new public API waits until ready");
            PublicCatalog.ready = true;
            check(modern.read(null).ready(), "new public API accepts an empty completed catalog");
            boolean rejected = false;
            try {
                RecipeCatalogAccess.Reader.find(SynchronousOnlyCatalog.class, Object.class).read(null);
            } catch (NoSuchMethodException expected) {
                rejected = true;
            }
            check(rejected, "unsupported APIs fail explicitly instead of waiting forever or scanning synchronously");

            var level = event.getServer().overworld();
            var snapshot = RecipeCatalogAccess.read(level);
            if (snapshot.ready()) {
                check(RecipeCatalogAccess.read(level).entries() == snapshot.entries(),
                        "repeated live reads reuse the base snapshot");
                check(MoldCatalog.start(level) == MoldCatalog.start(level), "catalog builders are reused");
            }
            var id = ResourceLocation.parse("minecraft:furnace");
            var saved = new MoldCatalogSavedData();
            saved.replace("first", Map.of("minecraft", List.of(
                    new MoldCatalog.MoldEntry("minecraft", id, new ItemStack(Items.FURNACE)))));
            var restored = MoldCatalogSavedData.load(saved.save(new CompoundTag(), level.registryAccess()),
                    level.registryAccess());
            check(restored.matches("first"), "persisted fingerprint survives reload");
            check(restored.moldsBySource().get("minecraft").equals(List.of(id)), "persisted mold ids survive reload");
            check(!restored.matches("changed"), "recipe changes invalidate the persisted cache");
            restored.replace("empty", Map.of());
            check(restored.matches("empty"), "empty catalogs are cached instead of rebuilt every request");
            LogUtils.getLogger().info("REGRESSION: mold catalog compatibility and persistence checks passed");
        } catch (Throwable failure) {
            LogUtils.getLogger().error("REGRESSION FAILED: mold catalog compatibility", failure);
            event.getServer().halt(false);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static final class PublicCatalog {
        private static boolean ready;
        public static boolean isReady(Object ignored) { return ready; }
        public static List<AlloyFurnaceRecipeCatalog.Entry> entriesIfReady(Object ignored) { return List.of(); }
    }

    private static final class PrivateCatalog {
        private static PrivateSnapshot current;
        private static PrivateSnapshot snapshotIfReady(Object ignored) { return current; }
        public static List<?> entries(Object ignored) { throw new AssertionError("Blocking API must never run"); }
    }

    private record PrivateSnapshot(List<AlloyFurnaceRecipeCatalog.Entry> entries) { }

    public static final class SynchronousOnlyCatalog {
        public static List<?> entries(Object ignored) { throw new AssertionError("Blocking API must never run"); }
    }
}
