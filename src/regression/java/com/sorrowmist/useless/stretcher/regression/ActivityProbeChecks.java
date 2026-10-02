package com.sorrowmist.useless.stretcher.regression;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.content.acceleration.AccelerationExecutionBudget;
import com.sorrowmist.useless.stretcher.content.acceleration.MachineActivityProbe;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Target-local observation checks; never packaged in production jars. */
public final class ActivityProbeChecks {
    private ActivityProbeChecks() {
    }

    public static void run(ServerLevel level) throws Exception {
        boundedSnapshots();
        liveProbe(level);
        LogUtils.getLogger().info("REGRESSION activity: bounded exact snapshots, failing capabilities, bypass and cleanup passed");
    }

    private static void boundedSnapshots() throws Exception {
        Method sample = MachineActivityProbe.class.getDeclaredMethod("sampleCapabilities",
                IEnergyStorage.class, IItemHandler.class, IFluidHandler.class);
        sample.setAccessible(true);
        MachineActivityProbe probe = new MachineActivityProbe();
        ItemStackHandler items = new ItemStackHandler(64);
        items.setStackInSlot(63, new ItemStack(Items.IRON_INGOT, 1));
        check((boolean) sample.invoke(probe, null, items, null), "first complete inventory observation wakes target");
        check(!(boolean) sample.invoke(probe, null, items, null), "unchanged inventory is idle");
        items.getStackInSlot(63).setCount(2);
        check((boolean) sample.invoke(probe, null, items, null), "last slot count mutation is detected without sampling gaps");
        items.getStackInSlot(63).set(DataComponents.CUSTOM_NAME, Component.literal("changed"));
        check((boolean) sample.invoke(probe, null, items, null), "in-place component mutation wakes target");
        check(!(boolean) sample.invoke(probe, null, items, null), "component snapshot is reused when unchanged");

        OversizedHandler oversized = new OversizedHandler();
        check((boolean) sample.invoke(probe, null, oversized, null), "oversized virtual inventory stays awake");
        check(oversized.slotReads == 0, "oversized virtual inventory never enumerates slots");
        check(field(MachineActivityProbe.class, "unknownCapabilities").getBoolean(probe),
                "unknown inventory stays awake between capability samples");
        check(((ItemStack[]) field(MachineActivityProbe.class, "items").get(probe)).length == 0,
                "oversized inventory releases old snapshots");
        check((boolean) sample.invoke(probe, null, items, null), "bounded inventory is observed again after oversized replacement");
        check(!field(MachineActivityProbe.class, "unknownCapabilities").getBoolean(probe), "bounded replacement clears unknown state");
        check((boolean) sample.invoke(probe, null, null, null), "unobservable machine stays awake");
        check(field(MachineActivityProbe.class, "unknownCapabilities").getBoolean(probe),
                "missing public observation capabilities cannot prove that a machine is idle");

        FluidTank tank = new FluidTank(1000);
        tank.setFluid(new FluidStack(Fluids.WATER, 100));
        check((boolean) sample.invoke(probe, null, null, tank), "first fluid state is recorded");
        check(!(boolean) sample.invoke(probe, null, null, tank), "unchanged fluid state is idle");
        tank.getFluid().setAmount(200);
        check((boolean) sample.invoke(probe, null, null, tank), "in-place fluid mutation is detected");
        probe.reset();
        check(((ItemStack[]) field(MachineActivityProbe.class, "items").get(probe)).length == 0
                        && ((FluidStack[]) field(MachineActivityProbe.class, "fluids").get(probe)).length == 0,
                "reset releases item and fluid snapshots");
    }

    private static void liveProbe(ServerLevel level) throws Exception {
        BlockPos pos = new BlockPos(10, 150, 10);
        var original = level.getBlockState(pos);
        level.setBlockAndUpdate(pos, Blocks.HOPPER.defaultBlockState());
        ProbeHopper hopper = new ProbeHopper(pos);
        level.setBlockEntity(hopper);
        level.invalidateCapabilities(pos);
        field(BlockEntity.class, "uselessStretcher$lastChangedTick").setLong(hopper, -1L);
        MachineActivityProbe probe = new MachineActivityProbe();
        try {
            check(probe.isWorking(level, pos, hopper.getBlockState()), "new target gets initial wake grace");
            hopper.failReads = true;
            check(probe.isWorking(level, pos, hopper.getBlockState()), "throwing optional capability keeps machine awake");
            int reads = hopper.slotReads;
            check(reads > 0, "throwing item capability was exercised");
            for (int i = 0; i < 50; i++) {
                check(probe.isWorking(level, pos, hopper.getBlockState()), "failure cooldown cannot put target to sleep");
            }
            check(hopper.slotReads == reads, "failed capability is not repeatedly retried during cooldown");
            check(field(MachineActivityProbe.class, "failureUntil").getLong(probe) > level.getGameTime(),
                    "optional capability failure has a bounded retry cooldown");

            WondrousStaffAccelerationEntity normal = new WondrousStaffAccelerationEntity(level, pos, 1);
            hopper.setCooldown(100);
            AccelerationExecutionBudget.beginTick(level.getServer());
            normal.tick();
            check(hopper.slotReads == reads, "ordinary acceleration performs no optional inventory probe");
            check(!normal.isIdleThrottled(), "ordinary acceleration never idle-throttles");
            normal.setPermanent();
            normal.setIdleThrottleDisabled(true);
            hopper.setCooldown(100);
            AccelerationExecutionBudget.beginTick(level.getServer());
            normal.tick();
            check(hopper.slotReads == reads, "no-sleep permanent acceleration performs no inventory probe");
            normal.discard();
            check(field(WondrousStaffAccelerationEntity.class, "observedTarget").get(normal) == null,
                    "discarded marker releases target reference");

            hopper.failReads = false;
            ProbeHopper replacement = new ProbeHopper(pos);
            level.setBlockEntity(replacement);
            level.invalidateCapabilities(pos);
            check(probe.isWorking(level, pos, replacement.getBlockState()), "replacement target starts awake");
            check(field(MachineActivityProbe.class, "failureUntil").getLong(probe) == Long.MIN_VALUE,
                    "target replacement clears old provider failure cooldown");
            probe.reset();
            check(field(MachineActivityProbe.class, "target").get(probe) == null, "probe reset releases machine reference");
        } finally {
            hopper.failReads = false;
            level.setBlockAndUpdate(pos, original);
            AccelerationExecutionBudget.removeServer(level.getServer());
        }
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class ProbeHopper extends HopperBlockEntity {
        private boolean failReads;
        private int slotReads;

        private ProbeHopper(BlockPos pos) {
            super(pos, Blocks.HOPPER.defaultBlockState());
        }

        @Override
        public int getContainerSize() {
            slotReads++;
            if (failReads) throw new IllegalStateException("Broken external inventory");
            return super.getContainerSize();
        }
    }

    private static final class OversizedHandler implements IItemHandler {
        private int slotReads;

        @Override
        public int getSlots() { return Integer.MAX_VALUE; }

        @Override
        public ItemStack getStackInSlot(int slot) {
            slotReads++;
            throw new AssertionError("Virtual storage slots must not be enumerated");
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }

        @Override
        public int getSlotLimit(int slot) { return 64; }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) { return true; }
    }
}
