package com.sorrowmist.useless.stretcher.content.mold;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.LinkedHashSet;
import java.util.Set;

/** Mold identity must never consult the AE input-material preference or recurse into our mixin. */
public final class MoldMatch {
    private MoldMatch() { }

    public static Set<ResourceLocation> matchingIds(Ingredient required, Set<ResourceLocation> selected) {
        if (required == null || selected.isEmpty() || required.isEmpty()) return Set.of();
        Set<ResourceLocation> matches = new LinkedHashSet<>();
        ItemStack[] displayed;
        try {
            displayed = required.getItems();
        } catch (RuntimeException ignored) {
            displayed = new ItemStack[0];
        }
        if (!required.isCustom()) {
            for (ItemStack stack : displayed) {
                if (stack == null || stack.isEmpty()) continue;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (selected.contains(id)) matches.add(id);
            }
            return matches;
        }
        // Preserve the existing component-sensitive representative fallback (e.g. bee eggs).
        for (ItemStack stack : displayed) {
            if (stack == null || stack.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (selected.contains(id)) matches.add(id);
            break;
        }
        // Non-enumerable custom predicates still get their full test, once per cached ingredient.
        for (ResourceLocation id : selected) {
            if (matches.contains(id) || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            Item item = BuiltInRegistries.ITEM.get(id);
            try {
                if (required.test(new ItemStack(item))) matches.add(id);
            } catch (RuntimeException ignored) {
                // Some third-party predicates reject unrelated stack types.
            }
        }
        return matches;
    }

    public static boolean matches(Ingredient required, ResourceLocation id) {
        return id != null && !matchingIds(required, Set.of(id)).isEmpty();
    }
}
