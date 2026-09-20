package com.sorrowmist.useless.stretcher.content.mold;

import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.stretcher.content.ae.AeMaterialContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the full set of omniversal patterns that a given mold can drive. */
public final class PatternFetcher {
    /** Hard safety cap for one omniversal block, including all mold groups. */
    public static final int MAX_PATTERNS = 4096;

    private PatternFetcher() {
    }

    /** Fetches patterns while preferring AE-available materials. */
    public static List<ItemStack> fetchForMold(Level level, BlockPos pos, BlockPos aeNodePos, String moldId) {
        if (level == null || moldId == null || moldId.isBlank()) return List.of();
        return fetchForMolds(level, pos, aeNodePos, List.of(moldId), MAX_PATTERNS)
                .getOrDefault(ResourceLocation.tryParse(moldId), List.of());
    }

    /**
     * Fetches several mold groups using one recipe catalog pass and one AE material snapshot.
     * The limit is global, so a bulk request can never encode more than the store can hold.
     */
    public static Map<ResourceLocation, List<ItemStack>> fetchForMolds(
            Level level, BlockPos pos, BlockPos aeNodePos, Collection<String> moldIds, int limit) {
        if (level == null || moldIds == null || moldIds.isEmpty() || limit <= 0) return Map.of();

        LinkedHashMap<ResourceLocation, List<ItemStack>> result = new LinkedHashMap<>();
        List<ResourceLocation> requested = new ArrayList<>();
        for (String moldId : moldIds) {
            ResourceLocation id = ResourceLocation.tryParse(moldId);
            if (id != null && !requested.contains(id)) requested.add(id);
        }
        if (requested.isEmpty()) return Map.of();

        AeMaterialContext context = AeMaterialContext.fromBoundNode(level, aeNodePos);
        if (context == null) {
            context = AeMaterialContext.fromNearbyGrid(level, pos, 8);
        }
        AeMaterialContext.push(context);
        try {
            List<AlloyFurnaceRecipeCatalog.Entry> catalog = AlloyFurnaceRecipeCatalog.entries(level);
            int remaining = Math.min(MAX_PATTERNS, limit);
            for (ResourceLocation id : requested) {
                if (remaining <= 0) break;
                List<ItemStack> fetched = fetch(catalog, level, id, remaining);
                result.put(id, fetched);
                remaining -= fetched.size();
            }
            return result;
        } finally {
            AeMaterialContext.pop();
        }
    }

    private static List<ItemStack> fetch(List<AlloyFurnaceRecipeCatalog.Entry> catalog,
                                         Level level, ResourceLocation id, int limit) {
        List<ItemStack> result = new ArrayList<>();
        for (AlloyFurnaceRecipeCatalog.Entry entry : catalog) {
            if (result.size() >= limit) break;
            AdvancedAlloyFurnaceRecipe recipe = entry.recipe();
            if (recipe == null) continue;

            boolean matches = false;
            for (Ingredient required : recipe.molds()) {
                if (required != null && !required.isEmpty() && MoldMatch.matches(required, id)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) continue;

            ItemStack processing = OmniversalPatternEncoding.createProcessingPattern(recipe);
            if (processing.isEmpty()) continue;
            ItemStack omniversal = OmniversalPatternEncoding.encode(processing, entry, level);
            if (!omniversal.isEmpty()) result.add(omniversal);
        }
        return result;
    }
}
