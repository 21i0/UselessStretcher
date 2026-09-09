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

        List<Ingredient> normalNeeded = new ArrayList<>();
        for (Ingredient requirement : normalized) {
            boolean covered = false;
            for (ResourceLocation id : myriadMolds) {
                if (MoldMatch.matches(requirement, id)) {
                    covered = true;
                    break;
                }
            }
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
        for (int requirement = 0; requirement < normalNeeded.size(); requirement++) {
            if (!augment(normalNeeded, normalSlots, requirement, requirementForSlot, new boolean[normalSlots.size()])) {
                cir.setReturnValue(false);
                return;
            }
        }
        cir.setReturnValue(true);
    }

    private static boolean augment(List<Ingredient> requirements, List<ItemStack> slots,
                                   int requirement, int[] requirementForSlot, boolean[] visited) {
        Ingredient needed = requirements.get(requirement);
        for (int slot = 0; slot < slots.size(); slot++) {
            if (visited[slot] || !AdapterUtils.matchesMold(needed, slots.get(slot))) continue;
            visited[slot] = true;
            int previous = requirementForSlot[slot];
            if (previous < 0 || augment(requirements, slots, previous, requirementForSlot, visited)) {
                requirementForSlot[slot] = requirement;
                return true;
            }
        }
        return false;
    }
}
