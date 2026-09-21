package com.sorrowmist.useless.stretcher.content.mold;

import com.sorrowmist.useless.content.recipe.MoldMatcher;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Prepared once for an immutable hub snapshot, including a deliberately empty selection. */
public final class MyriadMoldMatcher {
    private final Set<ResourceLocation> enabled = new LinkedHashSet<>();
    private final Map<Ingredient, Boolean> covered = new HashMap<>();
    private final MoldMatcher.PreparedMolds ordinary;

    private MyriadMoldMatcher(Map<Integer, ItemStack> slots) {
        Map<Integer, ItemStack> normal = new LinkedHashMap<>();
        slots.forEach((slot, stack) -> {
            if (stack == null || stack.isEmpty()) return;
            if (stack.is(ModItems.OMNIVERSAL_MYRIAD.get())) enabled.addAll(MyriadMoldData.readEnabledMolds(stack));
            else normal.put(slot, stack);
        });
        ordinary = MoldMatcher.prepare(normal);
    }

    public static MyriadMoldMatcher prepare(Map<Integer, ItemStack> slots) {
        if (slots == null) return null;
        for (ItemStack stack : slots.values()) {
            if (stack != null && !stack.isEmpty() && stack.is(ModItems.OMNIVERSAL_MYRIAD.get())) {
                return new MyriadMoldMatcher(slots);
            }
        }
        return null;
    }

    public boolean matches(List<Ingredient> requirements) {
        if (requirements == null || requirements.isEmpty()) return true;
        List<Ingredient> remaining = new ArrayList<>();
        for (Ingredient requirement : requirements) {
            if (requirement == null || requirement.isEmpty()) continue;
            boolean matched = !enabled.isEmpty() && covered.computeIfAbsent(requirement,
                    key -> !MoldMatch.matchingIds(key, enabled).isEmpty());
            if (!matched) remaining.add(requirement);
        }
        return ordinary.matches(remaining);
    }
}
