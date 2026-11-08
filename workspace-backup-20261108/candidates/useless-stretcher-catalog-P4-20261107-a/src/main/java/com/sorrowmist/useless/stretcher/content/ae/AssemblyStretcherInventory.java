package com.sorrowmist.useless.stretcher.content.ae;

import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.IntSupplier;
import java.util.function.Predicate;

/** Assembly slots own their installed binding. A different item must wait until the slot is emptied. */
public final class AssemblyStretcherInventory extends RecoverableItemStackHandler {
    private final BlockEntity owner;
    private boolean preparingSnapshot;

    public AssemblyStretcherInventory(IntSupplier activeSlots, Predicate<ItemStack> validator,
                                     Runnable changed, BlockEntity owner) {
        super(activeSlots, validator, changed);
        this.owner = owner;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack offered) {
        if (!super.isItemValid(slot, offered)) return false;
        ItemStack installed = super.getStackInSlot(slot);
        if (InstalledStretcherPatterns.isStretcher(installed)) return false;
        if (!InstalledStretcherPatterns.isStretcher(offered)) return true;
        if (MyriadMoldData.readPatternRef(offered) == null) return false;
        return !(owner.getLevel() instanceof ServerLevel level)
                || InstalledStretcherPatterns.canSnapshotForAssembly(level, offered);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack offered, boolean simulate) {
        if (offered.isEmpty()) return ItemStack.EMPTY;
        if (!InstalledStretcherPatterns.isStretcher(offered)) return super.insertItem(slot, offered, simulate);
        if (!isItemValid(slot, offered)) return offered;
        // Probe capacity before allocating a snapshot; unsuccessful and simulated insertions are read-only.
        ItemStack simulated = super.insertItem(slot, offered, true);
        if (simulate || simulated.getCount() == offered.getCount()) return simulated;
        ItemStack prepared = prepare(offered);
        if (prepared.isEmpty()) return offered;
        ItemStack remainder = super.insertItem(slot, prepared, false);
        // Uninserted items keep the caller's original library UUID, never the installed snapshot UUID.
        int inserted = prepared.getCount() - remainder.getCount();
        int left = offered.getCount() - inserted;
        return left <= 0 ? ItemStack.EMPTY : offered.copyWithCount(left);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack offered) {
        if (!(owner.getLevel() instanceof ServerLevel)) {
            // The server is authoritative; client mirrors must accept UUID changes from its slot updates.
            super.setStackInSlot(slot, offered.copy());
            return;
        }
        ItemStack installed = super.getStackInSlot(slot);
        if (!offered.isEmpty() && InstalledStretcherPatterns.isStretcher(installed)) return;
        if (!offered.isEmpty() && InstalledStretcherPatterns.isStretcher(offered)) {
            if (offered.getCount() != 1 || !isItemValid(slot, offered)) return;
            ItemStack prepared = prepare(offered);
            if (prepared.isEmpty()) return;
            super.setStackInSlot(slot, prepared.copyWithCount(1));
        } else {
            super.setStackInSlot(slot, offered);
        }
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        ItemStack installed = super.getStackInSlot(slot);
        if (!InstalledStretcherPatterns.isStretcher(installed)) return installed;
        // Migrate an old live link from the item already in this slot, never from a player's held item.
        if (!preparingSnapshot && !InstalledStretcherPatterns.isAssemblySnapshot(installed)
                && owner.getLevel() instanceof ServerLevel) {
            preparingSnapshot = true;
            try {
                ItemStack prepared = prepare(installed);
                if (!prepared.isEmpty()) {
                    super.setStackInSlot(slot, prepared);
                    installed = prepared;
                }
            } finally { preparingSnapshot = false; }
        }
        // Callers may inspect the stack but cannot mutate the installed UUID through a returned reference.
        return installed.copy();
    }

    @Override
    protected int getStackLimit(int slot, ItemStack stack) {
        return InstalledStretcherPatterns.isStretcher(stack) ? 1 : super.getStackLimit(slot, stack);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        if (!preparingSnapshot && owner.getLevel() instanceof ServerLevel) {
            preparingSnapshot = true;
            try {
                // One native change notification prevents recursive external-inventory saves per legacy slot.
                withChangeBatch(() -> {
                    for (int slot = 0; slot < getSlots(); slot++) {
                        ItemStack installed = super.getStackInSlot(slot);
                        if (InstalledStretcherPatterns.isStretcher(installed)
                                && !InstalledStretcherPatterns.isAssemblySnapshot(installed)) {
                            ItemStack prepared = prepare(installed);
                            if (!prepared.isEmpty()) super.setStackInSlot(slot, prepared);
                        }
                    }
                });
            } finally { preparingSnapshot = false; }
        }
        return super.serializeNBT(registries);
    }

    private ItemStack prepare(ItemStack stack) {
        if (owner.getLevel() instanceof ServerLevel level) {
            return InstalledStretcherPatterns.snapshotForAssembly(level, stack);
        }
        // Client menu synchronization and pre-level restoration do not create world-owned libraries.
        return stack.copy();
    }
}
