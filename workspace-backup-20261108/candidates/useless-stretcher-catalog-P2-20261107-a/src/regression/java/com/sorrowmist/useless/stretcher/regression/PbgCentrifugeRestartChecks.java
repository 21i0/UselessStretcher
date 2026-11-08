package com.sorrowmist.useless.stretcher.regression;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.compat.CoalescedMachineAccelerationCompat;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;
import java.util.WeakHashMap;

import static com.sorrowmist.useless.stretcher.regression.PbgCentrifugeLiveChecks.call;

/** Graceful two-process restart check. Its instrumented AE backend balance is world SavedData. */
final class PbgCentrifugeRestartChecks {
    private static final ResourceLocation FACTORY = ResourceLocation.parse(
            "productivebeesgenesis:absolute_extra_mek_centrifuge_factory");
    private static final ResourceLocation HONEY = ResourceLocation.parse("productivebees:honey");
    private static final Map<MinecraftServer, Run> RUNS = new WeakHashMap<>();
    private static final String DATA_ID = "useless_stretcher_regression_pbg_restart";

    private PbgCentrifugeRestartChecks() { }

    static boolean tick(ServerLevel level) throws Exception {
        Run run = RUNS.get(level.getServer());
        if (run == null) {
            run = new Run(level);
            RUNS.put(level.getServer(), run);
        }
        return run.tick(level);
    }

    private static final class Run {
        final Balance data;
        final boolean resume;
        final BlockEntity machine;
        final AENetworkedBlockEntity power;
        final RestartFluidStore backend;
        final IStorageProvider provider;
        IStorageService storage;
        int ticks;
        int stableTicks;
        int lastServerTick = Integer.MIN_VALUE;
        boolean done;

        Run(ServerLevel level) throws Exception {
            data = level.getServer().overworld().getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(Balance::new, Balance::load), DATA_ID);
            resume = data.phase >= 2;
            level.getChunkAt(data.machinePos);
            level.setChunkForced(data.machinePos.getX() >> 4, data.machinePos.getZ() >> 4, true);
            if (data.phase == 0) {
                check(level.isEmptyBlock(data.machinePos) && level.isEmptyBlock(data.powerPos),
                        "fresh restart fixture coordinates are empty; never replace existing world state");
                level.setBlockAndUpdate(data.machinePos, BuiltInRegistries.BLOCK.get(FACTORY).defaultBlockState());
                level.setBlockAndUpdate(data.powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                data.phase = 1;
                data.setDirty();
            }
            check(BuiltInRegistries.BLOCK.getKey(level.getBlockState(data.machinePos).getBlock()).equals(FACTORY),
                    "saved restart fixture still contains the original native factory");
            machine = level.getBlockEntity(data.machinePos);
            power = (AENetworkedBlockEntity) level.getBlockEntity(data.powerPos);
            check(machine != null && power != null, "saved factory and AE power block were loaded without replacement");
            Object holder = call(machine, "productivebeesgenesis$getAe2StateHolder");
            call(holder, "setAeItemOutputEnabled", false);
            call(holder, "setAeItemInputEnabled", false);
            call(holder, "setSmeltingCompatEnabled", false);
            // Keep persisted 550mB isolated until its restored external balance is mounted.
            call(holder, "setAeFluidOutputEnabled", false);
            backend = new RestartFluidStore(data);
            provider = mounts -> mounts.mount(backend, 0);
            if (resume) {
                check(data.inputInserted, "restart never reseeds the original recipe");
                assertConserved();
                if (data.phase == 2) check(data.amount == 250 && fluidAmount() == 550 && outputs() == 8,
                        "fresh process loads exactly saved 250mB external + 550mB local + 8 wax");
                LogUtils.getLogger().info("PBG PROCESS RESTART LOADED: phase={} backend={}mB capacity={}mB local={}mB wax={} input={}",
                        data.phase, data.amount, data.capacity, fluidAmount(), outputs(), inputs());
            }
        }

        boolean tick(ServerLevel level) throws Exception {
            if (done) return true;
            int now = level.getServer().getTickCount();
            if (now == lastServerTick) return false;
            lastServerTick = now;
            check(++ticks < 600, "PBG restart balance stage finishes within 600 real ticks");
            // Let AE2's scheduled world lifecycle initialize its own managed node exactly once.
            if (ticks == 20) connect();
            if (ticks < 40 || !node().isOnline()) return false;
            if (storage == null) {
                storage = node().getGrid().getStorageService();
                storage.addGlobalStorageProvider(provider);
                if (resume) {
                    data.capacity = 800;
                    data.phase = 3;
                    data.setDirty();
                }
                call(call(machine, "productivebeesgenesis$getAe2StateHolder"), "setAeFluidOutputEnabled", true);
            }
            if (!data.inputInserted) {
                check(!resume && inputs() == 0 && outputs() == 0 && data.amount == 0 && fluidAmount() == 0,
                        "recipe is seeded once into an empty original fixture");
                inventory("inputSlot", 0).setStack(new ItemStack(Items.HONEYCOMB, 8));
                data.inputInserted = true;
                data.setDirty();
            }
            IEnergyContainer energy = (IEnergyContainer) call(machine, "getEnergyContainer");
            energy.setEnergy(energy.getMaxEnergy());
            assertConserved();
            WondrousStaffAcceleration.tickTarget(level, data.machinePos, 1024, this);
            WondrousStaffAcceleration.tickTarget(level, data.machinePos, 512, this);
            CoalescedMachineAccelerationCompat.flush(level.getServer());
            CoalescedMachineAccelerationCompat.flush(level.getServer());
            assertConserved();
            if (!resume) {
                if (inputs() != 0 || outputs() != 8) return false;
                check(data.amount == 250 && fluidAmount() == 550,
                        "seed process settles native 800mB as saved 250 external + 550 local");
                // Disable only output transfer, not the stored contents. No transient queue is
                // serialized and the second process must use the original block/chunk data.
                call(call(machine, "productivebeesgenesis$getAe2StateHolder"), "setAeFluidOutputEnabled", false);
                storage.removeGlobalStorageProvider(provider);
                storage = null;
                machine.setChanged();
                data.phase = 2;
                data.setDirty();
                done = true;
                LogUtils.getLogger().info("PBG PROCESS RESTART SEED READY: machine={} backend=250mB capacity=250mB local=550mB wax=8; halt/save, then launch this same world again",
                        data.machinePos);
                return true;
            }
            if (inputs() == 0 && outputs() == 8 && data.amount == 800 && fluidAmount() == 0) stableTicks++;
            else stableTicks = 0;
            if (stableTicks < 40) return false;
            data.phase = 4;
            data.setDirty();
            machine.setChanged();
            storage.removeGlobalStorageProvider(provider);
            storage = null;
            call(call(machine, "productivebeesgenesis$getAe2StateHolder"), "setAeFluidOutputEnabled", false);
            done = true;
            LogUtils.getLogger().info("PBG PROCESS RESTART CHECKS PASSED: original factory reused, no recipe reseed; backend=800mB local=0mB wax=8 input=0, 40 real ticks without replay");
            return true;
        }

        private void connect() throws Exception {
            Object lifecycle = call(machine, "productivebeesgenesis$getAe2LifecycleHandler");
            call(lifecycle, "prepareForLoad", machine);
            call(lifecycle, "tryConnectNode", machine);
            check(node().getNode() != null && power.getMainNode().getNode() != null, "saved native AE nodes were recreated");
            if (node().getGrid() != power.getMainNode().getGrid())
                GridHelper.createConnection(node().getNode(), power.getMainNode().getNode());
        }

        private IManagedGridNode node() throws Exception {
            return (IManagedGridNode) call(machine, "productivebeesgenesis$getAe2GridNode");
        }

        private IInventorySlot inventory(String accessor, int lane) throws Exception {
            return (IInventorySlot) call(machine, accessor, lane);
        }

        private int count(String accessor, Item expected) throws Exception {
            int total = 0;
            int lanes = ((Number) call(machine, "processes")).intValue();
            for (int lane = 0; lane < lanes; lane++) {
                ItemStack stack = inventory(accessor, lane).getStack();
                check(stack.isEmpty() || stack.is(expected), "saved native inventory has the expected recipe item");
                total += stack.getCount();
            }
            return total;
        }

        private int inputs() throws Exception { return count("inputSlot", Items.HONEYCOMB); }

        private int outputs() throws Exception {
            Item wax = BuiltInRegistries.ITEM.get(ResourceLocation.parse("productivebees:wax"));
            return count("primaryOutputSlot", wax) + count("secondaryOutputSlot", wax) + count("tertiaryOutputSlot", wax);
        }

        private long fluidAmount() throws Exception {
            long total = 0;
            int count = ((Number) call(machine, "getFluidTankCount")).intValue();
            for (int index = 0; index < count; index++) {
                IExtendedFluidTank tank = (IExtendedFluidTank) call(machine, "getFluidTank", index);
                check(tank.isEmpty() || BuiltInRegistries.FLUID.getKey(tank.getFluid().getFluid()).equals(HONEY),
                        "saved tank contains only native recipe honey");
                total += tank.getFluidAmount();
            }
            return total;
        }

        private void assertConserved() throws Exception {
            int consumed = 8 - inputs();
            check(consumed >= 0 && outputs() == consumed, "restart keeps native honeycomb/wax item conservation");
            check(data.amount >= 0 && data.amount + fluidAmount() == consumed * 100L,
                    "restart keeps persisted external balance plus native tank fluid conservation");
        }
    }

    private static final class RestartFluidStore implements MEStorage {
        final Balance data;

        RestartFluidStore(Balance data) { this.data = data; }

        @Override public Component getDescription() { return Component.literal("Saved PBG restart fluid backend"); }

        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (!(key instanceof AEFluidKey fluid) || !BuiltInRegistries.FLUID.getKey(fluid.getFluid()).equals(HONEY)) return 0;
            long accepted = Math.min(amount, Math.max(0, data.capacity - data.amount));
            if (mode == Actionable.MODULATE && accepted > 0) {
                data.amount += accepted;
                data.setDirty();
            }
            return accepted;
        }

        @Override public void getAvailableStacks(KeyCounter out) {
            if (data.amount > 0) out.add(AEFluidKey.of(BuiltInRegistries.FLUID.get(HONEY)), data.amount);
        }
    }

    private static final class Balance extends SavedData {
        int phase;
        long amount;
        long capacity = 250;
        boolean inputInserted;
        BlockPos machinePos = new BlockPos(224, 180, 32);
        BlockPos powerPos = machinePos.east();

        static Balance load(CompoundTag tag, HolderLookup.Provider registries) {
            Balance balance = new Balance();
            balance.phase = tag.getInt("phase");
            balance.amount = tag.getLong("amount");
            balance.capacity = tag.getLong("capacity");
            balance.inputInserted = tag.getBoolean("input_inserted");
            balance.machinePos = BlockPos.of(tag.getLong("machine_pos"));
            balance.powerPos = BlockPos.of(tag.getLong("power_pos"));
            return balance;
        }

        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("phase", phase);
            tag.putLong("amount", amount);
            tag.putLong("capacity", capacity);
            tag.putBoolean("input_inserted", inputInserted);
            tag.putLong("machine_pos", machinePos.asLong());
            tag.putLong("power_pos", powerPos.asLong());
            return tag;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
