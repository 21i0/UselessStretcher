package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.stretcher.content.mold.MoldMatch;
import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
 * Useless Mod 2.3.5 moved mold-hub matching into {@code MoldMatcher.PreparedMolds}, whose
 * item-based slot pre-filter drops the omniversal myriad block (it matches by NBT, not by item).
 * Restore the unlimited-mold semantics: requirements covered by the myriad are always satisfied,
 * and the remaining requirements are bipartite-matched against the ordinary mold slots.
 */
@Mixin(targets = "com.sorrowmist.useless.content.recipe.MoldMatcher$PreparedMolds")
public abstract class MoldMatcherPreparedMoldsMixin {

    /** PreparedMolds is reused by the upstream hub; retain the expensive ingredient checks. */
    @org.spongepowered.asm.mixin.Unique
    private final Map<Ingredient, List<ResourceLocation>> uselessStretcher$myriadMatchCache = new HashMap<>();

    @org.spongepowered.asm.mixin.Unique
    private Set<ResourceLocation> uselessStretcher$cachedMyriadMolds = Set.of();

    @Shadow
    @Final
    private Map<Integer, ItemStack> available;

    @Inject(method = "matches(Ljava/util/List;)Z", at = @At("HEAD"), cancellable = true)
    private void uselessStretcher$handleMyriad(List<Ingredient> requirements,
                                               CallbackInfoReturnable<Boolean> cir) {
        List<Ingredient> normalized = new ArrayList<>();
        for (Ingredient requirement : requirements) {
            if (requirement != null && !requirement.isEmpty()) normalized.add(requirement);
        }
        if (normalized.isEmpty()) {
            cir.setReturnValue(true);
            return;
        }

        List<ItemStack> normalSlots = new ArrayList<>();
        Set<ResourceLocation> myriadMolds = new LinkedHashSet<>();
        boolean hasMyriad = false;
        for (ItemStack stack : available.values()) {
            if (stack == null || stack.isEmpty()) continue;
            if (stack.getItem() == ModItems.OMNIVERSAL_MYRIAD.get()) {
                hasMyriad = true;
                myriadMolds.addAll(MyriadMoldData.readEnabledMolds(stack));
            } else {
                normalSlots.add(stack);
            }
        }
        if (!hasMyriad) return; // let the original matcher run
        if (!myriadMolds.equals(uselessStretcher$cachedMyriadMolds)) {
            uselessStretcher$myriadMatchCache.clear();
            uselessStretcher$cachedMyriadMolds = Set.copyOf(myriadMolds);
        }

        List<Ingredient> normalNeeded = new ArrayList<>();
        for (Ingredient requirement : normalized) {
            if (uselessStretcher$matchingMyriad(requirement, myriadMolds).isEmpty()) {
                normalNeeded.add(requirement);
            }
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

    @org.spongepowered.asm.mixin.Unique
    private List<ResourceLocation> uselessStretcher$matchingMyriad(
            Ingredient requirement, Set<ResourceLocation> myriadMolds) {
        return uselessStretcher$myriadMatchCache.computeIfAbsent(requirement, ignored -> {
            List<ResourceLocation> matches = new ArrayList<>();
            for (ResourceLocation id : myriadMolds) {
                if (MoldMatch.matches(requirement, id)) matches.add(id);
            }
            return List.copyOf(matches);
        });
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
