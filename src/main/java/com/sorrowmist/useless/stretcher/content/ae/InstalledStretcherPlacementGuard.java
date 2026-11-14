package com.sorrowmist.useless.stretcher.content.ae;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Placement decisions that must run before the menu changes its carried stack. */
public final class InstalledStretcherPlacementGuard {
    private InstalledStretcherPlacementGuard() {}

    /** Null leaves the native slot's ordinary pattern acceptance unchanged. */
    public static Boolean mayPlace(SlotItemHandler slot, ItemStack offered) {
        if (!offered.isEmpty()
                && slot.getItemHandler() instanceof AssemblyStretcherInventory
                && InstalledStretcherPatterns.isStretcher(slot.getItem())) {
            return false;
        }
        if (InstalledStretcherPatterns.isStretcher(offered)) {
            return slot.getItemHandler().isItemValid(slot.getSlotIndex(), offered);
        }
        return null;
    }
}
