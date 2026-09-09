package com.sorrowmist.useless.stretcher.content.mold;

import com.sorrowmist.useless.content.recipe.AdapterUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Mold matching that understands data-component ingredients. Productive Bees encodes each bee in
 * a spawn egg's data component, so a bare {@code new ItemStack(item)} can never match its
 * {@code ComponentIngredient}. When plain matching fails, fall back to the ingredient's own
 * representative stack (which keeps the component) and compare item ids.
 */
public final class MoldMatch {
    private MoldMatch() {
    }

    public static boolean matches(Ingredient required, ResourceLocation moldId) {
        if (required == null || required.isEmpty() || moldId == null) return false;

        Item item = BuiltInRegistries.ITEM.get(moldId);
        if (item != null && AdapterUtils.matchesMold(required, new ItemStack(item))) {
            return true;
        }

        ItemStack representative;
        try {
            representative = AdapterUtils.itemRepresentative(required);
        } catch (RuntimeException ignored) {
            return false;
        }
        return representative != null && !representative.isEmpty()
                && moldId.equals(BuiltInRegistries.ITEM.getKey(representative.getItem()));
    }
}
