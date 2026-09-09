package com.sorrowmist.useless.stretcher.content.mold;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Enumerates every mold used by Useless Mod's converted alloy-furnace recipes, grouped by source
 * (mod) id. Derived from the shared recipe directory so multi-mold recipes (for example Create's
 * sequenced machines) are included alongside ordinary fixed molds.
 */
public final class MoldCatalog {
    private MoldCatalog() {
    }

    public record MoldEntry(String sourceId, ResourceLocation id, ItemStack mold) {
        public String displayName() {
            return mold.getHoverName().getString();
        }
    }

    public static Map<String, List<MoldEntry>> getAllMolds(Level level) {
        Map<String, LinkedHashMap<ResourceLocation, MoldEntry>> byMod = new TreeMap<>();
        if (level == null) return Map.of();

        for (AlloyFurnaceRecipeCatalog.Entry entry : AlloyFurnaceRecipeCatalog.entries(level)) {
            AdvancedAlloyFurnaceRecipe recipe = entry.recipe();
            if (recipe == null) continue;
            for (Ingredient mold : recipe.molds()) {
                if (mold == null || mold.isEmpty()) continue;
                for (ItemStack stack : mold.getItems()) {
                    if (stack.isEmpty()) continue;
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (id == null) continue;
                    // Spawn eggs (e.g. Productive Bees) encode the specific bee in a data
                    // component; show a generic "spawn egg" name instead of the first bee's name.
                    ItemStack display = stack.getItem() instanceof SpawnEggItem
                            ? new ItemStack(stack.getItem())
                            : stack.copyWithCount(1);
                    MoldEntry moldEntry = new MoldEntry(entry.sourceId(), id, display);
                    byMod.computeIfAbsent(entry.sourceId(), k -> new LinkedHashMap<>())
                            .putIfAbsent(id, moldEntry);
                }
            }
        }

        Map<String, List<MoldEntry>> result = new TreeMap<>();
        for (Map.Entry<String, LinkedHashMap<ResourceLocation, MoldEntry>> entry : byMod.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
        }
        return result;
    }
}
