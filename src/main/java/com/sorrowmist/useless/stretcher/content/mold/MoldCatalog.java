package com.sorrowmist.useless.stretcher.content.mold;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;

/** Incremental UI catalog; repeated EIO/JDTE molds are inspected once per recipe snapshot/source. */
public final class MoldCatalog {
    private static final Map<Object, Builder> CACHE = new WeakHashMap<>();
    private MoldCatalog() { }

    public record MoldEntry(String sourceId, ResourceLocation id, ItemStack mold) {
        public String displayName() { return mold.getHoverName().getString(); }
    }

    public static Builder start(Level level) {
        if (level == null) return new Builder(List.of());
        var recipes = AlloyFurnaceRecipeCatalog.entries(level);
        Builder cached = CACHE.get(level.getRecipeManager());
        if (cached == null || cached.recipes != recipes) {
            cached = new Builder(recipes);
            CACHE.put(level.getRecipeManager(), cached);
        }
        return cached;
    }

    public static final class Builder {
        private final List<AlloyFurnaceRecipeCatalog.Entry> recipes;
        private final Map<String, LinkedHashMap<ResourceLocation, MoldEntry>> byMod = new TreeMap<>();
        private final Map<String, Set<Ingredient>> inspected = new HashMap<>();
        private Map<String, List<MoldEntry>> result;
        private int cursor;

        private Builder(List<AlloyFurnaceRecipeCatalog.Entry> recipes) { this.recipes = recipes; }

        public boolean advance() {
            if (result != null) return true;
            long deadline = System.nanoTime() + 1_000_000L;
            int steps = 0;
            while (cursor < recipes.size() && steps++ < 512) {
                var entry = recipes.get(cursor++);
                if (entry.recipe() != null) {
                    var seen = inspected.computeIfAbsent(entry.sourceId(), ignored -> new HashSet<>());
                    var molds = byMod.computeIfAbsent(entry.sourceId(), ignored -> new LinkedHashMap<>());
                    for (Ingredient ingredient : entry.recipe().molds()) {
                        if (ingredient == null || !seen.add(ingredient)) continue;
                        ItemStack[] displayed;
                        try {
                            displayed = ingredient.getItems();
                        } catch (RuntimeException exception) {
                            com.mojang.logging.LogUtils.getLogger().warn(
                                    "Cannot enumerate myriad mold for {}", entry.recipe().id(), exception);
                            continue;
                        }
                        for (ItemStack stack : displayed) {
                            if (stack == null || stack.isEmpty()) continue;
                            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                            if (molds.containsKey(id)) continue;
                            ItemStack display = stack.getItem() instanceof SpawnEggItem
                                    ? new ItemStack(stack.getItem()) : stack.copyWithCount(1);
                            molds.put(id, new MoldEntry(entry.sourceId(), id, display));
                        }
                    }
                }
                if (System.nanoTime() >= deadline) break;
            }
            if (cursor < recipes.size()) return false;
            Map<String, List<MoldEntry>> completed = new TreeMap<>();
            byMod.forEach((source, entries) -> {
                if (!entries.isEmpty()) completed.put(source, List.copyOf(entries.values()));
            });
            result = java.util.Collections.unmodifiableMap(completed);
            inspected.clear();
            byMod.clear();
            return true;
        }

        public Map<String, List<MoldEntry>> result() { return result == null ? Map.of() : result; }
        public boolean done() { return result != null; }
        public String progress() { return cursor + "/" + recipes.size(); }
    }
}
