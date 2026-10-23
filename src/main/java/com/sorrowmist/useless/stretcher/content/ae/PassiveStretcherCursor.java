package com.sorrowmist.useless.stretcher.content.ae;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnacePatternResolver;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PassivePatternInputTransaction;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One native executor per physical slot; fair, bounded selection from an arbitrarily large library. */
public final class PassiveStretcherCursor {
    private List<AEItemKey> source = List.of();
    private int next;
    private long catalog = Long.MIN_VALUE;
    private final Map<AEItemKey, OmniversalPatternDetails> decoded = new LinkedHashMap<>(64, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<AEItemKey, OmniversalPatternDetails> entry) {
            return size() > 64;
        }
    };

    public int position() { return next; }
    public void position(int value) { next = Math.max(0, value); }

    public OmniversalPatternDetails select(PassiveCraftingHatchBlockEntity hatch, int slot, ItemStack stack) {
        if (!(hatch.getLevel() instanceof ServerLevel level) || hatch.getController() == null) return null;
        List<AEItemKey> keys = InstalledStretcherPatterns.keys(level, stack, null);
        if (keys != source || catalog != AlloyFurnaceRecipeCatalog.generation()) {
            source = keys;
            catalog = AlloyFurnaceRecipeCatalog.generation();
            decoded.clear();
        }
        if (keys.isEmpty()) return null;
        var controller = hatch.getController();
        var grid = hatch.getAeGrid();
        OmniversalPatternDetails fallback = null;
        // Never decode or simulate the entire library in one passive cycle.
        for (int checked = 0; checked < Math.min(32, keys.size()); checked++) {
            int index = Math.floorMod(next++, keys.size());
            if (next < 0) next = 0;
            AEItemKey key = keys.get(index);
            OmniversalPatternDetails pattern;
            if (decoded.containsKey(key)) pattern = decoded.get(key);
            else {
                var details = AdvancedAlloyFurnacePatternResolver.decode(key.toStack(1), level);
                pattern = details instanceof OmniversalPatternDetails omni ? omni : null;
                decoded.put(key, pattern);
            }
            if (pattern == null) continue;
            if (fallback == null) fallback = pattern;
            if (!controller.isTaskRecipeAvailable(pattern.recipe())) continue;
            if (grid == null) return pattern;
            var storage = grid.getStorageService();
            var simulated = PassivePatternInputTransaction.extract(pattern, hatch.getSlotMultiplier(slot), level,
                    new PreviewStorage(storage.getInventory()), storage::getCachedInventory,
                    IActionSource.empty(), (ignored, amount) -> { });
            if (simulated.successful()) return pattern;
        }
        // Let the original executor report missing input/mold/offline instead of consuming anything here.
        return fallback;
    }

    /** A private reservation ledger: even the native transaction's commit only SIMULATEs real storage. */
    private static final class PreviewStorage implements MEStorage {
        private final MEStorage actual;
        private final Map<AEKey, Long> reserved = new HashMap<>();
        private PreviewStorage(MEStorage actual) { this.actual = actual; }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) { return 0; }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long used = reserved.getOrDefault(key, 0L);
            long request = amount > Long.MAX_VALUE - used ? Long.MAX_VALUE : amount + used;
            long found = Math.min(amount, Math.max(0L, actual.extract(key, request, Actionable.SIMULATE, source) - used));
            if (mode == Actionable.MODULATE && found > 0) reserved.merge(key, found, Long::sum);
            return found;
        }
        @Override public void getAvailableStacks(KeyCounter out) { actual.getAvailableStacks(out); }
        @Override public net.minecraft.network.chat.Component getDescription() { return actual.getDescription(); }
    }
}
