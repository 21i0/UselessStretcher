package com.sorrowmist.useless.stretcher.content.ae;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A thread-local snapshot of the AE network used while writing omniversal patterns. When a recipe
 * input is a tag, {@link #pickPreferred} picks a tag member that the network already has, falling
 * back to a member it can craft, and finally to Useless Mod's own representative.
 */
public final class AeMaterialContext {
    private static final ThreadLocal<AeMaterialContext> CURRENT = new ThreadLocal<>();

    private final KeyCounter available;
    private final ICraftingService crafting;

    private AeMaterialContext(KeyCounter available, ICraftingService crafting) {
        this.available = available;
        this.crafting = crafting;
    }

    public static AeMaterialContext fromNearbyGrid(Level level, BlockPos center, int radius) {
        if (level == null || center == null) return null;
        IGrid grid = findGrid(level, center, radius);
        if (grid == null) return null;
        return new AeMaterialContext(
                grid.getStorageService().getCachedInventory(), grid.getCraftingService());
    }

    /** Resolves the grid of a specific (previously bound) AE node position. */
    public static AeMaterialContext fromBoundNode(Level level, BlockPos nodePos) {
        if (level == null || nodePos == null || !level.isLoaded(nodePos)) return null;
        BlockEntity blockEntity = level.getBlockEntity(nodePos);
        if (!(blockEntity instanceof IInWorldGridNodeHost host)) return null;
        for (Direction direction : Direction.values()) {
            IGridNode node = host.getGridNode(direction);
            if (node != null && node.getGrid() != null) {
                IGrid grid = node.getGrid();
                return new AeMaterialContext(
                        grid.getStorageService().getCachedInventory(), grid.getCraftingService());
            }
        }
        return null;
    }

    public static void push(AeMaterialContext context) {
        CURRENT.set(context);
    }

    public static void pop() {
        CURRENT.remove();
    }

    public static AeMaterialContext current() {
        return CURRENT.get();
    }

    public ItemStack pickPreferred(Ingredient ingredient, ItemStack fallback) {
        if (ingredient == null) return fallback;

        ItemStack[] items;
        try {
            items = ingredient.getItems();
        } catch (RuntimeException exception) {
            return fallback;
        }

        // Prefer a member already present in the network.
        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) continue;
            AEItemKey key = AEItemKey.of(stack);
            if (key != null && available.get(key) > 0) return stack.copyWithCount(1);
        }
        // Then a member the network can craft.
        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) continue;
            AEItemKey key = AEItemKey.of(stack);
            if (key != null && crafting.isCraftable(key)) return stack.copyWithCount(1);
        }
        return fallback;
    }

    private static IGrid findGrid(Level level, BlockPos center, int radius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!level.isLoaded(cursor)) continue;
                    BlockEntity blockEntity = level.getBlockEntity(cursor);
                    if (!(blockEntity instanceof IInWorldGridNodeHost host)) continue;
                    for (Direction direction : Direction.values()) {
                        IGridNode node = host.getGridNode(direction);
                        if (node != null && node.getGrid() != null) return node.getGrid();
                    }
                }
            }
        }
        return null;
    }
}
