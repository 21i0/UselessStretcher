package com.sorrowmist.useless.stretcher.regression;

import appeng.blockentity.misc.InscriberBlockEntity;
import appeng.api.config.Actionable;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.mojang.logging.LogUtils;
import com.mojang.authlib.GameProfile;
import com.sorrowmist.useless.stretcher.content.acceleration.AccelerationExecutionBudget;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.UUID;

final class RealAeMachineChecks {
    static void run(ServerLevel level) throws Exception {
        for (boolean range : new boolean[]{false, true}) {
            inscriber(level, range);
            crystalScience(level, range);
            lightningTech(level, range);
        }
    }

    private static void inscriber(ServerLevel level, boolean range) {
        BlockPos pos = new BlockPos(38, 150, 38);
        level.getChunkAt(pos);
        for (Direction side : Direction.values()) level.setBlockAndUpdate(pos.relative(side), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, AEBlocks.INSCRIBER.block().defaultBlockState());
        var machine = (InscriberBlockEntity) level.getBlockEntity(pos);
        try (var driver = new TestDriver(level, pos, range, 16)) {
            machine.onReady();
            check(machine.getMainNode().getNode() != null, "isolated inscriber has a local AE node");
            check(machine.getMainNode().getNode().getConnections().isEmpty(), "no external ME network connections");
            var energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, Direction.UP);
            check(energy != null, "inscriber exposes FE input");
            var inventory = machine.getInternalInventory();
            inventory.setItemDirect(0, new ItemStack(AEItems.LOGIC_PROCESSOR_PRESS));
            inventory.setItemDirect(2, new ItemStack(Items.GOLD_INGOT));
            check(machine.getTask() != null, "real inscriber recipe resolved");
            for (int batch = 0; batch < 100 && inventory.getStackInSlot(3).isEmpty(); batch++) {
                check(energy.receiveEnergy(100000, false) >= 0, "FE supplied through real capability");
                driver.tick();
            }
            check(inventory.getStackInSlot(3).is(AEItems.LOGIC_PROCESSOR_PRINT.asItem()),
                    "isolated FE-powered inscriber completes a real recipe through target acceleration");
            check(inventory.getStackInSlot(2).isEmpty() && inventory.getStackInSlot(0).getCount() == 1,
                    "recipe consumes input once and retains its press");
            LogUtils.getLogger().info("REGRESSION real AE machine: FE-only inscriber produced logic print via {} in {} batches",
                    driver.route(), driver.batches);
        } finally {
            level.removeBlock(pos, false);
        }
    }

    private static void lightningTech(ServerLevel level, boolean range) throws Exception {
        if (!ModList.get().isLoaded("ae2lt")) return;
        BlockPos pos = new BlockPos(44, 150, 44);
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2lt:lightning_simulation_room"));
        check(block != Blocks.AIR, "real Lightning Tech simulation chamber is registered");
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        var machine = (appeng.blockentity.grid.AENetworkedBlockEntity) level.getBlockEntity(pos);
        IStorageProvider provider = null;
        appeng.api.networking.storage.IStorageService storage = null;
        try (var driver = new TestDriver(level, pos, range, 1024)) {
            machine.onReady();
            check(machine.getMainNode().getNode().getConnections().isEmpty(),
                    "Lightning Tech test has no external network connections");
            var inventory = (IItemHandlerModifiable) machine.getClass().getMethod("getInventory").invoke(machine);
            var energy = (IEnergyStorage) machine.getClass().getMethod("getEnergyStorage").invoke(machine);
            var lightning = (AEKey) Class.forName("com.moakiee.ae2lt.me.key.LightningKey")
                    .getField("HIGH_VOLTAGE").get(null);
            long[] remainingLightning = {1};
            // Supply one real lightning key through AE's public storage service, not a fake recipe.
            MEStorage fixture = new MEStorage() {
                @Override public Component getDescription() { return Component.literal("Regression lightning storage"); }
                @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
                    if (!key.equals(lightning)) return 0;
                    long extracted = Math.min(amount, remainingLightning[0]);
                    if (mode == Actionable.MODULATE) remainingLightning[0] -= extracted;
                    return extracted;
                }
                @Override public void getAvailableStacks(KeyCounter out) { out.add(lightning, remainingLightning[0]); }
            };
            provider = mounts -> mounts.mount(fixture, 0);
            storage = machine.getMainNode().getGrid().getStorageService();
            storage.addGlobalStorageProvider(provider);
            inventory.setStackInSlot(0, new ItemStack(BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse("ae2lt:overload_alloy_blank"))));
            inventory.setStackInSlot(1, new ItemStack(BuiltInRegistries.ITEM.get(
                    ResourceLocation.parse("ae2lt:overload_crystal_dust")), 4));
            check(energy.receiveEnergy(20000, false) == 20000, "Lightning Tech accepts recipe FE");
            long initialGameTime = level.getGameTime();
            for (int batch = 0; batch < 100 && inventory.getStackInSlot(4).isEmpty(); batch++) {
                driver.tick();
            }
            check(inventory.getStackInSlot(4).is(BuiltInRegistries.ITEM.get(
                            ResourceLocation.parse("ae2lt:overload_alloy"))) && inventory.getStackInSlot(4).getCount() == 1,
                    "Lightning Tech completes a real lightning simulation recipe");
            check(inventory.getStackInSlot(0).isEmpty() && inventory.getStackInSlot(1).isEmpty()
                            && remainingLightning[0] == 0 && energy.getEnergyStored() == 0,
                    "Lightning Tech consumes each input, lightning and FE exactly once");
            check(level.getGameTime() == initialGameTime, "target acceleration never alters world time");
            LogUtils.getLogger().info("REGRESSION real AE addon: Lightning Tech produced overload alloy via {} in {} batches; inputs/FE/lightning conserved",
                    driver.route(), driver.batches);
        } finally {
            if (storage != null && provider != null) storage.removeGlobalStorageProvider(provider);
            level.removeBlock(pos, false);
        }
    }

    private static void crystalScience(ServerLevel level, boolean range) throws Exception {
        if (!ModList.get().isLoaded("ae2cs")) return;
        BlockPos pos = new BlockPos(40, 150, 40);
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2cs:crystal_pulverizer"));
        check(block != Blocks.AIR, "real Crystal Science pulverizer is registered");
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        var machine = level.getBlockEntity(pos);
        try (var driver = new TestDriver(level, pos, range, 1024)) {
            ((appeng.blockentity.AEBaseBlockEntity) machine).onReady();
            var input = (InternalInventory) machine.getClass().getMethod("getInputInv").invoke(machine);
            var output = (InternalInventory) machine.getClass().getMethod("getOutputInv").invoke(machine);
            input.setItemDirect(0, new ItemStack(AEItems.CERTUS_QUARTZ_CRYSTAL, 32));
            var power = (IAEPowerStorage) machine;
            for (int batch = 0; batch < 100 && output.getStackInSlot(0).isEmpty(); batch++) {
                power.injectAEPower(power.getAEMaxPower(), Actionable.MODULATE);
                driver.tick();
            }
            check(output.getStackInSlot(0).is(AEItems.CERTUS_QUARTZ_DUST.asItem()),
                    "Crystal Science production advances via its ordinary ticker without an ME network");
            check(input.getStackInSlot(0).getCount() + output.getStackInSlot(0).getCount() == 32,
                    "Crystal Science recipe conserves inputs and outputs");
            LogUtils.getLogger().info("REGRESSION real AE addon: Crystal Science produced {} quartz dust via {} in {} batches",
                    output.getStackInSlot(0).getCount(), driver.route(), driver.batches);
        } finally {
            level.removeBlock(pos, false);
        }
    }

    private static final class TestDriver implements AutoCloseable {
        private final ServerLevel level;
        private final BlockPos pos;
        private final int speed;
        private final RangeAccelerationSavedData ranges;
        private final UUID rangeId;
        private final UUID owner = UUID.randomUUID();
        private int batches;

        private TestDriver(ServerLevel level, BlockPos pos, boolean range, int speed) {
            this.level = level;
            this.pos = pos;
            this.speed = speed;
            if (range) {
                ranges = new RangeAccelerationSavedData();
                ItemStack staff = new ItemStack(ModItems.WONDROUS_STAFF.get());
                staff.set(StretcherComponents.WONDROUS_STAFF_SPEED.get(), speed);
                staff.set(StretcherComponents.RANGE_SIZE_X.get(), 1);
                staff.set(StretcherComponents.RANGE_SIZE_Y.get(), 1);
                staff.set(StretcherComponents.RANGE_SIZE_Z.get(), 1);
                staff.set(StretcherComponents.RANGE_IDLE_THROTTLE_DISABLED.get(), true);
                var player = FakePlayerFactory.get(level, new GameProfile(owner, "RealAeRange"));
                var placed = ranges.place(level, player, pos, staff);
                check(placed.status() == RangeAccelerationSavedData.PlacementStatus.CREATED, "real machine range placed");
                rangeId = placed.id();
            } else {
                ranges = null;
                rangeId = null;
            }
        }

        private void tick() {
            AccelerationExecutionBudget.beginTick(level.getServer());
            if (ranges == null) WondrousStaffAcceleration.tickTarget(level, pos, speed, this);
            else ranges.tick(level.getServer());
            batches++;
        }

        private String route() { return ranges == null ? "single-target" : "range placement"; }

        @Override public void close() {
            if (ranges != null) {
                check(ranges.reclaim(level.getServer(), owner, rangeId) != null, "real machine range reclaimed");
                check(level.getEntity(rangeId) == null || level.getEntity(rangeId).isRemoved(), "range marker removed");
            }
            AccelerationExecutionBudget.removeServer(level.getServer());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
