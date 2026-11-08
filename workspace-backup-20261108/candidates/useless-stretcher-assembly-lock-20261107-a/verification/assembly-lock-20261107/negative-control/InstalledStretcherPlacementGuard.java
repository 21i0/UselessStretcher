package com.sorrowmist.useless.stretcher.content.ae;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;
/** Test-only negative control, equivalent to the previous offered-stretcher-only hook. */
public final class InstalledStretcherPlacementGuard {
    private InstalledStretcherPlacementGuard() {}
    public static Boolean mayPlace(SlotItemHandler slot, ItemStack offered) {
        if (InstalledStretcherPatterns.isStretcher(offered)) {
            return slot.getItemHandler().isItemValid(slot.getSlotIndex(), offered);
        }
        return null;
    }
}
