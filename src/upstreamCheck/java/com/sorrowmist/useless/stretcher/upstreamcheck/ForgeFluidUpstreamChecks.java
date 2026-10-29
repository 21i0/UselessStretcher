package com.sorrowmist.useless.stretcher.upstreamcheck;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.compat.AE2Compat;
import com.sorrowmist.useless.compat.forge1201.component.ComponentAccess;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.stretcher.content.capsule.ForgeMeteoriteUpstreamChecks;
import com.sorrowmist.useless.stretcher.content.item.StaffFluidCollector;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.network.Network;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Real Forge 47 / Useless Mod / AE2 15 check; excluded from every release jar. */
@Mod.EventBusSubscriber(modid = "useless_stretcher")
public final class ForgeFluidUpstreamChecks {
    private static final BlockPos NODE = new BlockPos(72, 150, 72), CENTER = new BlockPos(80, 150, 72);
    private static final BlockPos[] NODES = {NODE, NODE.east(4), NODE.south(4)};
    private static final WirelessAccessPointBlockEntity[] nodes = new WirelessAccessPointBlockEntity[NODES.length];
    private static WirelessAccessPointBlockEntity node;
    private static ServerPlayer player;
    private static boolean done, fluidsDone;
    private static int ticks;
    private static final FluidStore store = new FluidStore();

    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if (!Boolean.getBoolean("stretcher.upstreamCheck")) return;
        try {
            var level = event.getServer().overworld();
            ForgeMeteoriteUpstreamChecks.run(level);
            ForgeInstalledUpstreamChecks.start(level);
            for (int x = 4; x <= 5; x++) level.setChunkForced(x, 4, true);
            for (int i = 0; i < NODES.length; i++) {
                level.setBlockAndUpdate(NODES[i], AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
                nodes[i] = (WirelessAccessPointBlockEntity) level.getBlockEntity(NODES[i]);
                for (Direction side : Direction.values())
                    level.setBlockAndUpdate(NODES[i].relative(side), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            }
            node = nodes[0];
            player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("d928ae83-e218-49a2-b510-48938606a321"), "FluidCheck"));
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(CENTER.getX() + .5, CENTER.getY(), CENTER.getZ() + .5);
        } catch (Throwable failure) { finish(event.getServer(), failure); }
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("stretcher.upstreamCheck") || done || event.phase != TickEvent.Phase.END) return;
        try {
            check(++ticks < 1900, "upstream integration checks timed out");
            if (ticks < 60) return;
            if (!fluidsDone) { fluidChecks(event.getServer().overworld()); fluidsDone = true; }
            if (ForgeInstalledUpstreamChecks.tick(event.getServer().overworld())) finish(event.getServer(), null);
        } catch (Throwable failure) { finish(event.getServer(), failure); }
    }

    private static void fluidChecks(ServerLevel level) {
        check(Arrays.stream(nodes).allMatch(n -> n.getMainNode().isOnline()), "real wireless access points online");
        var storages = Arrays.stream(nodes).map(n -> n.getMainNode().getGrid().getStorageService()).toList();
        check(storages.stream().distinct().count() == NODES.length, "exception checks use isolated real AE grids");
        appeng.api.storage.IStorageProvider provider = mounts -> mounts.mount(store, 0);
        storages.forEach(storage -> storage.addGlobalStorageProvider(provider));
        try {
            var staff = reset(level);
            check(AE2Compat.resolveLinkedGrid(staff, player) != null, "actual upstream linked-grid resolver");
            water(level, CENTER); collect();
            check(store.total() == 1000 && level.isEmptyBlock(CENTER), "one full source stores exactly 1000 mB");
            for (int mode = 0; mode < 4; mode++) {
                staff = reset(level); water(level, CENTER);
                switch (mode) {
                    case 0 -> ComponentAccess.remove(staff, UComponents.WIRELESS_LINK_TARGET.get());
                    case 1 -> ComponentAccess.set(staff, UComponents.AEStoragePriorityComponent.get(), false);
                    case 2 -> ComponentAccess.set(staff, UComponents.WIRELESS_LINK_TARGET.get(), GlobalPos.of(level.dimension(), CENTER.above(12)));
                    case 3 -> store.capacity = 999;
                }
                collect(); check(store.total() == 0 && StaffFluidCollector.isFullSource(level.getBlockState(CENTER)), "unbound/disabled/offline/full preserves source " + mode);
            }
            staff = reset(level); StaffFluidCollector.configure(staff, true, 2, 64);
            water(level, CENTER); level.setBlock(CENTER.east(2), Blocks.LAVA.defaultBlockState(), 2);
            level.setBlock(CENTER.north(), Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, 1), 2);
            level.setBlock(CENTER.south(), Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, 8), 2);
            level.setBlock(CENTER.west(), Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true), 2);
            collect();
            check(store.total() == 2000 && level.isEmptyBlock(CENTER.east(2)), "water and lava sources both collected");
            check(!level.isEmptyBlock(CENTER.north()) && !level.isEmptyBlock(CENTER.south())
                    && level.getBlockState(CENTER.west()).getValue(BlockStateProperties.WATERLOGGED), "flowing, falling and waterlogged blocks untouched");
            reset(level); water(level, CENTER);
            Consumer<StaffFluidCollector.DrainPermissionCheck> denied = event -> event.setCanceled(true);
            MinecraftForge.EVENT_BUS.addListener(denied);
            try { collect(); check(store.total() == 0 && StaffFluidCollector.isFullSource(level.getBlockState(CENTER)), "Forge claim cancellation preserves source"); }
            finally { MinecraftForge.EVENT_BUS.unregister(denied); }
            staff = reset(level); water(level, CENTER); store.executionLeft = 400;
            collect(); check(store.total() == 400 && pending(staff) == 600 && level.isEmptyBlock(CENTER), "partial insert journals exactly 600 mB");
            collect(); check(store.total() == 400 && pending(staff) == 600, "blocked pending does not duplicate");
            var restored = ItemStack.of(staff.save(new CompoundTag()));
            check(pending(restored) == 600 && StaffFluidCollector.enabled(restored), "Forge NBT bridge persists pending fluid and switches");
            var wire = new FriendlyByteBuf(Unpooled.buffer());
            try {
                wire.writeItem(restored); restored = wire.readItem();
                check(pending(restored) == 600, "Forge item packet retains pending fluid");
                var packet = new Network.StaffFluidSettingsPayload(true, 16, 64, true);
                Network.StaffFluidSettingsPayload.STREAM_CODEC.encode(wire, packet);
                check(packet.equals(Network.StaffFluidSettingsPayload.STREAM_CODEC.decode(wire)), "fluid settings packet roundtrip");
            } finally { wire.release(); }
            player.setItemInHand(InteractionHand.MAIN_HAND, restored); StaffFluidCollector.clear(); store.executionLeft = Long.MAX_VALUE;
            collect(); collect(); check(store.total() == 1000 && pending(restored) == 0, "restored pending flushes once without redraining");
            staff = reset(level); StaffFluidCollector.configure(staff, true, 1, 1); water(level, CENTER);
            for (int i = 0; i < 19; i++) collect();
            check(store.total() == 0, "one bucket/sec waits for full credit"); collect(); check(store.total() == 1000, "one bucket/sec fires after 20 ticks");
            staff = reset(level); player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, staff); water(level, CENTER); collect();
            check(store.total() == 1000, "offhand collection works");
            staff = reset(level); StaffFluidCollector.configure(staff, true, -100, 999); water(level, CENTER);
            check(StaffFluidCollector.radius(staff) == 1 && StaffFluidCollector.rate(staff) == 64, "server clamps radius/rate");
            check(StaffFluidCollector.tickPlayer(player, 0, Long.MAX_VALUE) == 0 && store.total() == 0, "zero scan budget");
            check(StaffFluidCollector.tickPlayer(player, 512, 0) == 0 && store.total() == 0, "expired time budget");
            // AE2 15 leaves a failed network on its thread-local recursion stack.
            // Inject each exception into a separate grid, never the healthy baseline.
            staff = reset(level); water(level, CENTER); store.executionLeft = 400; store.throwAfterInsert = true;
            ComponentAccess.set(staff, UComponents.WIRELESS_LINK_TARGET.get(), GlobalPos.of(level.dimension(), NODES[1]));
            collect(); assertQuarantined(level, staff, 1000, 400);
            staff = reset(level); water(level, CENTER); store.executionLeft = 400;
            collect(); check(pending(staff) == 600, "pending remainder seeded for failed retry");
            ComponentAccess.set(staff, UComponents.WIRELESS_LINK_TARGET.get(), GlobalPos.of(level.dimension(), NODES[2]));
            store.executionLeft = 200; store.throwAfterInsert = true;
            collect(); assertQuarantined(level, staff, 600, 600);
            LogUtils.getLogger().info("FORGE FLUID COLLECTION PASSED: real AE grid, sources, offline/full, Forge claims, partial journal, unknown-commit quarantine on initial/retry insert, item NBT/packet, rate, offhand and bounded scanning");
        } finally {
            storages.forEach(storage -> storage.removeGlobalStorageProvider(provider));
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            StaffFluidCollector.clear();
        }
    }

    private static void assertQuarantined(ServerLevel level, ItemStack staff, int attempted, long committed) {
        var pending = ComponentAccess.get(staff, StretcherComponents.STAFF_FLUID_PENDING.get());
        check(pending != null && pending.uncertain() && pending.amount() == attempted && store.total() == committed,
                "partial commit then exception quarantines attempted amount");
        check(level.isEmptyBlock(CENTER), "quarantined source was already removed");
        var restored = ItemStack.of(staff.save(new CompoundTag()));
        var wire = new FriendlyByteBuf(Unpooled.buffer());
        try {
            wire.writeItem(restored); restored = wire.readItem();
            check(ComponentAccess.get(restored, StretcherComponents.STAFF_FLUID_PENDING.get()).uncertain(), "unknown result survives item NBT/packet");
            StaffFluidCollector.PendingFluid.STREAM_CODEC.encode(wire, pending);
            check(pending.equals(StaffFluidCollector.PendingFluid.STREAM_CODEC.decode(wire)), "quarantine stream codec roundtrip");
            var legacy = (CompoundTag) StaffFluidCollector.PendingFluid.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, pending).result().orElseThrow();
            legacy.remove("uncertain");
            check(!StaffFluidCollector.PendingFluid.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, legacy).result().orElseThrow().uncertain(),
                    "old pending records without uncertainty marker still decode");
        } finally { wire.release(); }
        player.setItemInHand(InteractionHand.MAIN_HAND, restored);
        StaffFluidCollector.configure(restored, false, 1, 64); StaffFluidCollector.configure(restored, true, 1, 64);
        StaffFluidCollector.clear(); store.throwAfterInsert = false; store.executionLeft = Long.MAX_VALUE;
        ComponentAccess.set(restored, UComponents.WIRELESS_LINK_TARGET.get(), GlobalPos.of(level.dimension(), NODE));
        // Prove this fresh binding is writable; otherwise AE recursion suppression
        // after the injected exception could make a broken auto-retry appear safe.
        check(AE2Compat.resolveLinkedGrid(restored, player).grid().getStorageService().getInventory().insert(
                AEFluidKey.of(Fluids.LAVA), 1, Actionable.MODULATE, IActionSource.empty()) == 1, "healthy rebound grid accepts a probe");
        int calls = store.modulateCalls;
        water(level, CENTER); collect(); collect();
        check(store.total() == committed + 1 && store.modulateCalls == calls && pending(restored) == attempted
                        && ComponentAccess.get(restored, StretcherComponents.STAFF_FLUID_PENDING.get()).uncertain()
                        && StaffFluidCollector.isFullSource(level.getBlockState(CENTER)),
                "restored unknown commit never replays or drains another source, even after rebind, cache reset and toggling");
    }

    private static ItemStack reset(ServerLevel level) {
        StaffFluidCollector.clear(); store.reset();
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        for (var pos : BlockPos.betweenClosed(CENTER.offset(-2, -2, -2), CENTER.offset(2, 2, 2))) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        var staff = new ItemStack(ModItems.WONDROUS_STAFF.get());
        ComponentAccess.set(staff, UComponents.AEStoragePriorityComponent.get(), true);
        ComponentAccess.set(staff, UComponents.WIRELESS_LINK_TARGET.get(), GlobalPos.of(level.dimension(), NODE));
        StaffFluidCollector.configure(staff, true, 1, 64);
        player.setItemInHand(InteractionHand.MAIN_HAND, staff); return staff;
    }
    private static void collect() { StaffFluidCollector.tickPlayer(player, 512, Long.MAX_VALUE); }
    private static int pending(ItemStack staff) {
        var value = ComponentAccess.get(staff, StretcherComponents.STAFF_FLUID_PENDING.get()); return value == null ? 0 : value.amount();
    }
    private static void water(ServerLevel level, BlockPos pos) { level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2); }
    private static void finish(net.minecraft.server.MinecraftServer server, Throwable failure) {
        done = true;
        if (failure == null) LogUtils.getLogger().info("FORGE UPSTREAM CHECKS PASSED");
        else LogUtils.getLogger().error("FORGE UPSTREAM CHECKS FAILED", failure);
        server.halt(false);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static final class FluidStore implements MEStorage {
        final Map<AEKey, Long> amounts = new HashMap<>();
        long capacity, executionLeft;
        int modulateCalls;
        boolean throwAfterInsert;
        void reset() { amounts.clear(); capacity = Long.MAX_VALUE; executionLeft = Long.MAX_VALUE; modulateCalls = 0; throwAfterInsert = false; }
        long total() { return amounts.values().stream().mapToLong(Long::longValue).sum(); }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (!(key instanceof AEFluidKey)) return 0;
            long accepted = Math.min(amount, capacity - total());
            if (mode == Actionable.MODULATE) {
                modulateCalls++;
                accepted = Math.min(accepted, executionLeft); executionLeft -= accepted; amounts.merge(key, accepted, Long::sum);
                if (throwAfterInsert) throw new IllegalStateException("Intentional check: external storage threw after committing " + accepted + " mB");
            }
            return accepted;
        }
        @Override public void getAvailableStacks(KeyCounter counter) { amounts.forEach(counter::add); }
        @Override public Component getDescription() { return Component.literal("Fluid insertion check"); }
    }
}
