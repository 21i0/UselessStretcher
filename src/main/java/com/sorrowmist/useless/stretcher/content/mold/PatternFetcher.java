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
import java.util.List;

/** Builds the full set of omniversal patterns that a given mold can drive. */
public final class PatternFetcher {
    private PatternFetcher() {
    }

    /** Fetches patterns while preferring AE-available materials. */
    public static List<ItemStack> fetchForMold(Level level, BlockPos pos, BlockPos aeNodePos, String moldId) {
        if (level == null || moldId == null || moldId.isBlank()) return List.of();
        AeMaterialContext context = AeMaterialContext.fromBoundNode(level, aeNodePos);
        if (context == null) {
            context = AeMaterialContext.fromNearbyGrid(level, pos, 8);
        }
        AeMaterialContext.push(context);
        try {
            return fetch(level, moldId);
        } finally {
            AeMaterialContext.pop();
        }
    }

    private static List<ItemStack> fetch(Level level, String moldId) {
        ResourceLocation id = ResourceLocation.tryParse(moldId);
        if (id == null) return List.of();

        List<ItemStack> result = new ArrayList<>();
        for (AlloyFurnaceRecipeCatalog.Entry entry : AlloyFurnaceRecipeCatalog.entries(level)) {
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
