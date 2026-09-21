package com.sorrowmist.useless.stretcher.content.item;

import appeng.api.stacks.AEItemKey;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Call-local only; no mining cache is stored on the staff or player. */
public final class StaffMiningContext {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private StaffMiningContext() { }

    public static void run(Runnable action) {
        int previous = DEPTH.get();
        DEPTH.set(previous + 1);
        try { action.run(); }
        finally {
            if (previous == 0) DEPTH.remove();
            else DEPTH.set(previous);
        }
    }

    public static boolean active() { return DEPTH.get() > 0; }

    /** Same stable output order and stack limits as the upstream merger, with indexed lookup. */
    public static List<ItemStack> mergeDrops(List<ItemStack> drops) {
        Map<AEItemKey, ItemStack> partial = new HashMap<>();
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            AEItemKey key = AEItemKey.of(drop);
            ItemStack target = partial.get(key);
            if (target != null) {
                int moved = Math.min(drop.getCount(), target.getMaxStackSize() - target.getCount());
                target.grow(moved);
                drop.shrink(moved);
                if (target.getCount() >= target.getMaxStackSize()) partial.remove(key);
            }
            if (!drop.isEmpty()) {
                ItemStack copy = drop.copy();
                merged.add(copy);
                if (copy.getCount() < copy.getMaxStackSize()) partial.put(key, copy);
            }
        }
        return merged;
    }
}
