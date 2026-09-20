package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.blockentities.multiblock.OmniversalMoldHubBlockEntity;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.stretcher.content.mold.MoldMatch;
import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Treats a configured {@code omniversal_myriad} block as an unlimited source of every enabled
 * mold. Requirements covered by the block are therefore always satisfiable no matter how often
 * they repeat, while the remaining requirements are matched against the other mold slots with
 * the same optimal bipartite matching the vanilla mold hub already uses.
 */
@Mixin(OmniversalMoldHubBlockEntity.class)
public abstract class MoldHubMultiMoldMixin {

    @Inject(method = "matchesMolds", at = @At("HEAD"), cancellable = true)
    private static void uselessStretcher$handleMyriad(List<Ingredient> requirements,
                                                      List<ItemStack> available,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (requirements == null || available == null) return;

        List<Ingredient> normalized = new ArrayList<>();
        for (Ingredient requirement : requirements) {
            if (requirement != null && !requirement.isEmpty()) normalized.add(requirement);
        }
        if (normalized.isEmpty()) {
            cir.setReturnValue(true);
            return;
        }

        boolean hasMyriad = false;
        for (ItemStack stack : available) {
            if (stack != null && stack.getItem() == ModItems.OMNIVERSAL_MYRIAD.get()) {
                hasMyriad = true;
                break;
            }
        }
        if (!hasMyriad) return; // let the original matcher run

        List<ItemStack> normalSlots = new ArrayList<>();
        Set<ResourceLocation> myriadMolds = new LinkedHashSet<>();
        for (ItemStack stack : available) {
            if (stack == null || stack.isEmpty()) continue;
            if (stack.getItem() == ModItems.OMNIVERSAL_MYRIAD.get()) {
                myriadMolds.addAll(MyriadMoldData.readEnabledMolds(stack));
            } else {
                normalSlots.add(stack);
            }
        }

        // Requirements the myriad block cannot cover must be satisfied by the ordinary slots.
        List<Ingredient> normalNeeded = new ArrayList<>();
        Map<Ingredient, Boolean> myriadMatchCache = new HashMap<>();
        for (Ingredient requirement : normalized) {
            boolean covered = myriadMatchCache.computeIfAbsent(requirement, ignored -> {
                for (ResourceLocation id : myriadMolds) {
                    if (MoldMatch.matches(requirement, id)) return true;
                }
                return false;
            });
            if (!covered) normalNeeded.add(requirement);
        }

        if (normalNeeded.isEmpty()) {
            cir.setReturnValue(true);
            return;
        }
        if (normalNeeded.size() > normalSlots.size()) {
            cir.setReturnValue(false);
            return;
        }

        int[] requirementForSlot = new int[normalSlots.size()];
        Arrays.fill(requirementForSlot, -1);
        Map<Ingredient, List<Integer>> matchingSlots = new HashMap<>();
        for (Ingredient requirement : normalNeeded) {
            matchingSlots.computeIfAbsent(requirement, ignored -> {
                List<Integer> slots = new ArrayList<>();
                for (int slot = 0; slot < normalSlots.size(); slot++) {
                    if (AdapterUtils.matchesMold(requirement, normalSlots.get(slot))) slots.add(slot);
                }
                return slots;
            });
        }
        for (int requirement = 0; requirement < normalNeeded.size(); requirement++) {
            if (!augment(normalNeeded, matchingSlots, requirement, requirementForSlot,
                    new boolean[normalSlots.size()])) {
                cir.setReturnValue(false);
                return;
            }
        }
        cir.setReturnValue(true);
    }

    private static boolean augment(List<Ingredient> requirements, Map<Ingredient, List<Integer>> matchingSlots,
                                   int requirement, int[] requirementForSlot, boolean[] visited) {
        for (int slot : matchingSlots.getOrDefault(requirements.get(requirement), List.of())) {
            if (visited[slot]) continue;
            visited[slot] = true;
            int previous = requirementForSlot[slot];
            if (previous < 0 || augment(requirements, matchingSlots, previous, requirementForSlot, visited)) {
                requirementForSlot[slot] = requirement;
                return true;
            }
        }
        return false;
    }
}
