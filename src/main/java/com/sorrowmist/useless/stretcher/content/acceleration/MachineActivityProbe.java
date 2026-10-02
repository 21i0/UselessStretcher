package com.sorrowmist.useless.stretcher.content.acceleration;

import appeng.api.networking.IInWorldGridNodeHost;
import com.sorrowmist.useless.stretcher.content.entity.ChangedTickAccessor;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** Bounded, target-owned observation. Unknown activity must never put a machine to sleep. */
public final class MachineActivityProbe {
    private static final int CHANGED_WINDOW_TICKS = 120;
    private static final int SAMPLE_INTERVAL_TICKS = 5;
    private static final int FAILURE_RETRY_TICKS = 200;
    private static final int MAX_ITEM_SLOTS = 64;
    private static final int MAX_FLUID_TANKS = 16;
    private static final ItemStack[] NO_ITEMS = new ItemStack[0];
    private static final FluidStack[] NO_FLUIDS = new FluidStack[0];

    private BlockEntity target;
    private BlockState lastState;
    private long lastEnergy = Long.MIN_VALUE;
    private long nextSampleTick = Long.MIN_VALUE;
    private long failureUntil = Long.MIN_VALUE;
    private ItemStack[] items = NO_ITEMS;
    private FluidStack[] fluids = NO_FLUIDS;
    private boolean unknownCapabilities;

    public boolean isWorking(ServerLevel level, BlockPos pos, BlockState state) {
        BlockEntity current = level.getBlockEntity(pos);
        if (current != target) {
            reset();
            target = current;
        }
        if (current == null) return true;
        long now = level.getGameTime();
        if (now < failureUntil) return true;
        try {
            if (current instanceof ChangedTickAccessor accessor) {
                long changed = accessor.uselessStretcher$getLastChangedTick();
                if (changed >= 0L && now >= changed && now - changed <= CHANGED_WINDOW_TICKS) return true;
            }
            if (state != lastState) {
                lastState = state;
                return true;
            }
            if (now < nextSampleTick) return unknownCapabilities;
            nextSampleTick = now + SAMPLE_INTERVAL_TICKS;
            if (current instanceof IInWorldGridNodeHost host
                    && WondrousStaffAcceleration.isAeDeviceWorking(host)) return true;
            IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
            long stored = energy == null ? Long.MIN_VALUE : energy.getEnergyStored();
            if (stored != lastEnergy) {
                lastEnergy = stored;
                return true;
            }
            return sampleCapabilities(energy,
                    level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null),
                    level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null));
        } catch (RuntimeException | LinkageError ignored) {
            // Observation is optional. A broken external capability must not break acceleration,
            // and must not be retried for every target tick while the machine is still present.
            failureUntil = now + FAILURE_RETRY_TICKS;
            unknownCapabilities = true;
            items = NO_ITEMS;
            fluids = NO_FLUIDS;
            return true;
        }
    }

    private boolean sampleCapabilities(IEnergyStorage energy, IItemHandler itemHandler, IFluidHandler fluidHandler) {
        int slots = itemHandler == null ? 0 : itemHandler.getSlots();
        int tanks = fluidHandler == null ? 0 : fluidHandler.getTanks();
        unknownCapabilities = (energy == null && itemHandler == null && fluidHandler == null)
                || slots < 0 || slots > MAX_ITEM_SLOTS || tanks < 0 || tanks > MAX_FLUID_TANKS;
        if (unknownCapabilities) {
            items = NO_ITEMS;
            fluids = NO_FLUIDS;
            return true;
        }

        long stored = energy == null ? Long.MIN_VALUE : energy.getEnergyStored();
        boolean changed = stored != lastEnergy;
        lastEnergy = stored;
        if (items.length != slots) {
            items = new ItemStack[slots];
            changed = true;
        }
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = itemHandler.getStackInSlot(slot);
            ItemStack previous = items[slot];
            if (previous == null || previous.getCount() != stack.getCount()
                    || !ItemStack.isSameItemSameComponents(previous, stack)) {
                items[slot] = stack.copy();
                changed = true;
            }
        }
        if (fluids.length != tanks) {
            fluids = new FluidStack[tanks];
            changed = true;
        }
        for (int tank = 0; tank < tanks; tank++) {
            FluidStack stack = fluidHandler.getFluidInTank(tank);
            FluidStack previous = fluids[tank];
            if (previous == null || previous.getAmount() != stack.getAmount()
                    || previous.getFluid() != stack.getFluid()
                    || !previous.getComponentsPatch().equals(stack.getComponentsPatch())) {
                fluids[tank] = stack.copy();
                changed = true;
            }
        }
        return changed;
    }

    public void reset() {
        target = null;
        lastState = null;
        lastEnergy = Long.MIN_VALUE;
        nextSampleTick = Long.MIN_VALUE;
        failureUntil = Long.MIN_VALUE;
        items = NO_ITEMS;
        fluids = NO_FLUIDS;
        unknownCapabilities = false;
    }
}
