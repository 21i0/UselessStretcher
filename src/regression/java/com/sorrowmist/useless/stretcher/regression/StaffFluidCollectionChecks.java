package com.sorrowmist.useless.stretcher.regression;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.compat.AE2Compat;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.stretcher.content.item.StaffFluidCollector;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.network.Network;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Real powered wireless node and real ME storage service; never substitutes the upstream resolver. */
final class StaffFluidCollectionChecks {
    private static final BlockPos NODE = new BlockPos(72, 150, 72);
    private static final BlockPos CENTER = new BlockPos(80, 150, 80);
    private static final BlockPos PERSIST = CENTER.offset(12, 0, 0);
    private static WirelessAccessPointBlockEntity node;
    private static ServerPlayer player;
    private static int ticks;
    private static final FluidStore store = new FluidStore();

    static void start(ServerLevel level) {
        level.setChunkForced(4, 4, true);
        level.setChunkForced(5, 5, true);
        level.setChunkForced(5, 4, true);
        level.setChunkForced(4, 5, true);
        level.setBlockAndUpdate(NODE, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
        node = (WirelessAccessPointBlockEntity) level.getBlockEntity(NODE);
        // The access point connects only on its back; surround it with powered AE nodes.
        for (Direction side : Direction.values()) {
            var p = NODE.relative(side);
            level.setBlockAndUpdate(p, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        }
        player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("d928ae83-e218-49a2-b510-48938606a321"), "FluidCheck"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(CENTER.getX() + .5, CENTER.getY(), CENTER.getZ() + .5);
    }

    static boolean tick(ServerLevel level) throws Exception {
        if (++ticks < 60) return false;
        check(node.getMainNode().isOnline(), "real wireless access point is online with power and channel");
        var storage = node.getMainNode().getGrid().getStorageService();
        appeng.api.storage.IStorageProvider provider = mounts -> mounts.mount(store, 0);
        storage.addGlobalStorageProvider(provider);
        try {
            upstreamBinding(level);
            check(AE2Compat.resolveLinkedGrid(staff(), player) != null, "upstream resolver reaches actual bound ME grid");
            storage.removeGlobalStorageProvider(provider);
            var cell = appeng.api.storage.StorageCells.getCellInventory(new ItemStack(appeng.core.definitions.AEItems.FLUID_CELL_1K), null);
            check(cell != null, "real AE fluid storage cell resolves");
            appeng.api.storage.IStorageProvider cellProvider = mounts -> mounts.mount(cell, 0);
            storage.addGlobalStorageProvider(cellProvider);
            try {
                reset(level); water(level, CENTER); collect();
                check(cell.getAvailableStacks().get(AEFluidKey.of(Fluids.WATER)) == 1000 && level.isEmptyBlock(CENTER), "1000mB stored in real AE fluid cell via wireless binding");
                LogUtils.getLogger().info("STAFF FLUID REAL CELL PASSED: 1000mB in AE2 1k fluid cell through bound wireless grid");
            } finally { storage.removeGlobalStorageProvider(cellProvider); }
            storage.addGlobalStorageProvider(provider);
            persisted(level);
            sourceRules(level);
            blocked(level);
            permissions(level);
            partial(level);
            limits(level);
            crossDimension(level);
            persistenceSeed(level);
            LogUtils.getLogger().info("STAFF FLUID DETAILS PASSED: source-only water/lava, exact 1000mB, upstream bound grid, offline/full, claims, partial journal, radius/rate, offhand, codecs and bounded scanning");
        } finally {
            storage.removeGlobalStorageProvider(provider);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            StaffFluidCollector.clear();
            for (int x = 4; x <= 5; x++) for (int z = 4; z <= 5; z++) level.setChunkForced(x, z, false);
        }
        return true;
    }

    private static ItemStack staff() {
        var staff = new ItemStack(ModItems.WONDROUS_STAFF.get());
        staff.set(UComponents.AEStoragePriorityComponent.get(), true);
        staff.set(UComponents.WIRELESS_LINK_TARGET.get(), GlobalPos.of(player.level().dimension(), NODE));
        StaffFluidCollector.configure(staff, true, 1, 64);
        return staff;
    }
    private static ItemStack reset(ServerLevel level) {
        StaffFluidCollector.clear(); store.reset();
        player.setPos(CENTER.getX() + .5, CENTER.getY(), CENTER.getZ() + .5);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        for (var p : BlockPos.betweenClosed(CENTER.offset(-2, -2, -2), CENTER.offset(2, 2, 2)))
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        var staff = staff(); player.setItemInHand(InteractionHand.MAIN_HAND, staff);
        return staff;
    }
    private static int collect() { return StaffFluidCollector.tickPlayer(player, 512, Long.MAX_VALUE); }
    private static void water(ServerLevel level, BlockPos pos) { level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2); }
    private static void upstreamBinding(ServerLevel level) {
        var staff = reset(level); staff.remove(UComponents.WIRELESS_LINK_TARGET.get());
        staff.set(UComponents.AeNetworkConnectComponent.get(), true);
        player.setShiftKeyDown(true);
        try {
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(NODE), Direction.UP, NODE, false);
            var event = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, NODE, hit);
            NeoForge.EVENT_BUS.post(event);
            check(event.isCanceled() && GlobalPos.of(level.dimension(), NODE).equals(staff.get(UComponents.WIRELESS_LINK_TARGET.get())), "actual upstream sneak-right-click event binds this staff");
            LogUtils.getLogger().info("STAFF FLUID UPSTREAM BINDING PASSED: native sneak-right-click event writes shared wireless target");
        } finally { player.setShiftKeyDown(false); staff.set(UComponents.AeNetworkConnectComponent.get(), false); }
    }
    private static void sourceRules(ServerLevel level) {
        var staff = reset(level); StaffFluidCollector.configure(staff, true, 2, 64); water(level, CENTER);
        level.setBlock(CENTER.east(2), Blocks.LAVA.defaultBlockState(), 2);
        level.setBlock(CENTER.north(), Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, 1), 2);
        level.setBlock(CENTER.south(), Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, 8), 2);
        level.setBlock(CENTER.west(), Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true), 2);
        int drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(CENTER).inflate(4)).size();
        check(collect() <= 512, "bounded work count");
        check(store.amount(Fluids.WATER) == 1000 && store.amount(Fluids.LAVA) == 1000, "each full water/lava source yields exactly one bucket");
        check(level.isEmptyBlock(CENTER) && level.isEmptyBlock(CENTER.east(2)), "only successful sources removed");
        check(!level.isEmptyBlock(CENTER.north()) && !level.isEmptyBlock(CENTER.south()) && level.getBlockState(CENTER.west()).getValue(BlockStateProperties.WATERLOGGED), "flowing/falling/waterlogged unchanged");
        check(drops == level.getEntitiesOfClass(ItemEntity.class, new AABB(CENTER).inflate(4)).size(), "permission probe never runs mining/drop dispatcher");
        check(!StaffFluidCollector.isFullSource(Blocks.CAULDRON.defaultBlockState()), "containers are not sources");
    }
    private static void blocked(ServerLevel level) {
        for (int mode = 0; mode < 5; mode++) {
            var staff = reset(level); water(level, CENTER);
            switch (mode) {
                case 0 -> staff.remove(UComponents.WIRELESS_LINK_TARGET.get());
                case 1 -> staff.set(UComponents.AEStoragePriorityComponent.get(), false);
                case 2 -> staff.set(UComponents.WIRELESS_LINK_TARGET.get(), GlobalPos.of(level.dimension(), CENTER.above(12)));
                case 3 -> store.capacity = 999;
                case 4 -> store.capacity = 0;
            }
            collect(); check(StaffFluidCollector.isFullSource(level.getBlockState(CENTER)) && store.total() == 0, "unbound/priority/offline/full preserves source mode " + mode);
        }
        var staff = reset(level); water(level, CENTER);
        var unloaded = new BlockPos(30_000_000 - 2048, 150, 2000);
        check(!level.hasChunkAt(unloaded), "far bound node starts unloaded");
        staff.set(UComponents.WIRELESS_LINK_TARGET.get(), GlobalPos.of(level.dimension(), unloaded));
        collect(); check(!level.hasChunkAt(unloaded) && store.total() == 0, "bound node check never forces a chunk load");
    }
    private static void permissions(ServerLevel level) {
        reset(level); water(level, CENTER);
        Consumer<StaffFluidCollector.DrainPermissionCheck> denied = event -> event.setCanceled(true);
        NeoForge.EVENT_BUS.addListener(denied);
        try { collect(); check(store.total() == 0 && StaffFluidCollector.isFullSource(level.getBlockState(CENTER)), "claim cancellation retains full source"); }
        finally { NeoForge.EVENT_BUS.unregister(denied); }
        var staff = reset(level); water(level, CENTER);
        Consumer<StaffFluidCollector.DrainPermissionCheck> rebind = event -> staff.remove(UComponents.WIRELESS_LINK_TARGET.get());
        NeoForge.EVENT_BUS.addListener(rebind);
        try { collect(); check(store.total() == 0 && StaffFluidCollector.isFullSource(level.getBlockState(CENTER)), "binding change during permission event aborts collection"); }
        finally { NeoForge.EVENT_BUS.unregister(rebind); }
    }
    private static void partial(ServerLevel level) {
        var staff = reset(level); water(level, CENTER); store.executionLeft = 400;
        collect(); check(store.total() == 400 && pending(staff) == 600 && level.isEmptyBlock(CENTER), "partial execution journals exactly missing 600mB");
        collect(); check(store.total() == 400 && pending(staff) == 600, "blocked journal does not duplicate");
        var reloaded = ItemStack.parseOptional(level.registryAccess(), (CompoundTag) staff.save(level.registryAccess()));
        check(pending(reloaded) == 600 && StaffFluidCollector.enabled(reloaded), "item persistent codec retains settings and pending fluid");
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        ItemStack.STREAM_CODEC.encode(buf, reloaded);
        var synced = ItemStack.STREAM_CODEC.decode(buf); buf.release();
        check(pending(synced) == 600, "network item codec retains pending fluid");
        player.setItemInHand(InteractionHand.MAIN_HAND, reloaded); StaffFluidCollector.clear(); store.executionLeft = Long.MAX_VALUE;
        collect(); collect(); check(store.total() == 1000 && pending(reloaded) == 0, "reloaded journal flushes once without redraining");
        var packet = new Network.StaffFluidSettingsPayload(true, 16, 64, true);
        var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        Network.StaffFluidSettingsPayload.STREAM_CODEC.encode(wire, packet);
        check(packet.equals(Network.StaffFluidSettingsPayload.STREAM_CODEC.decode(wire)), "settings packet roundtrip"); wire.release();
    }
    private static void limits(ServerLevel level) {
        var staff = reset(level); water(level, CENTER.east(2)); collect();
        check(store.total() == 0, "outside radius untouched");
        StaffFluidCollector.configure(staff, true, 2, 64); collect(); check(store.total() == 1000, "expanded radius reaches source");
        staff = reset(level); StaffFluidCollector.configure(staff, true, 1, 1); water(level, CENTER);
        for (int i = 0; i < 19; i++) collect(); check(store.total() == 0, "1 bucket/sec cannot fire before 20 ticks");
        collect(); check(store.total() == 1000, "1 bucket/sec fires at 20 ticks");
        staff = reset(level); StaffFluidCollector.configure(staff, true, 1, 1); water(level, CENTER);
        for (int i = 0; i < 10; i++) collect();
        player.setPos(CENTER.getX() + 1.5, CENTER.getY(), CENTER.getZ() + .5);
        for (int i = 0; i < 10; i++) collect();
        check(store.total() == 1000, "moving across block centers retains fractional rate credit");
        staff = reset(level); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        player.setItemInHand(InteractionHand.OFF_HAND, staff); water(level, CENTER); collect(); check(store.total() == 1000, "offhand staff works");
        staff = reset(level); StaffFluidCollector.configure(staff, true, 1, 1);
        var off = staff(); player.setItemInHand(InteractionHand.OFF_HAND, off); water(level, CENTER);
        collect(); check(store.total() == 0, "dual wield uses main-hand rate without double draining");
        StaffFluidCollector.configure(staff, false, 1, 1); collect(); check(store.total() == 1000, "disabled main hand falls back to enabled offhand");
        staff = reset(level); StaffFluidCollector.configure(staff, true, -100, 999);
        check(StaffFluidCollector.radius(staff) == 1 && StaffFluidCollector.rate(staff) == 64, "server settings clamp out-of-range values");
        water(level, CENTER); check(StaffFluidCollector.tickPlayer(player, 0, Long.MAX_VALUE) == 0 && store.total() == 0, "zero budget scans nothing");
        check(StaffFluidCollector.tickPlayer(player, 512, 0) == 0 && store.total() == 0, "expired deadline scans nothing");
        var clock = level.getGameTime(); collect(); check(level.getGameTime() == clock, "collection never changes world clock");
        reset(level); water(level, CENTER); player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        collect(); check(store.total() == 0, "inventory-only staff does not passively drain");
    }
    private static void crossDimension(ServerLevel level) {
        reset(level);
        var previous = player;
        var other = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        check(other != null, "cross-dimension fixture has Nether"); other.getChunkAt(CENTER);
        player = FakePlayerFactory.get(other, new GameProfile(UUID.fromString("8069f224-f685-4fdd-8b7d-d9f58c5fa989"), "FluidNether"));
        try {
            player.setGameMode(GameType.SURVIVAL); player.setPos(CENTER.getX() + .5, CENTER.getY(), CENTER.getZ() + .5);
            var staff = staff(); staff.set(UComponents.WIRELESS_LINK_TARGET.get(), GlobalPos.of(level.dimension(), NODE));
            player.setItemInHand(InteractionHand.MAIN_HAND, staff);
            other.setBlock(CENTER, Blocks.LAVA.defaultBlockState(), 2);
            collect(); check(store.amount(Fluids.LAVA) == 1000 && other.isEmptyBlock(CENTER), "Nether source goes to bound overworld network");
            LogUtils.getLogger().info("STAFF FLUID CROSS DIMENSION PASSED: Nether lava inserted into bound overworld grid");
        } finally { player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); player = previous; }
    }
    private static Path fixture(ServerLevel level) { return level.getServer().getServerDirectory().resolve("fluid-pending-fixture.nbt"); }
    private static void persisted(ServerLevel level) throws Exception {
        if (!java.nio.file.Files.exists(fixture(level))) return;
        var data = NbtIo.readCompressed(fixture(level), NbtAccounter.unlimitedHeap());
        var staff = ItemStack.parseOptional(level.registryAccess(), data.getCompound("staff"));
        check(level.isEmptyBlock(PERSIST) && pending(staff) == 600, "real restart retains removed source and saved journal");
        reset(level); store.amounts.put(AEFluidKey.of(Fluids.WATER), data.getLong("stored"));
        player.setItemInHand(InteractionHand.MAIN_HAND, staff); collect(); collect();
        check(store.total() == 1000 && pending(staff) == 0, "real process restart inserts remaining fluid exactly once");
        LogUtils.getLogger().info("STAFF FLUID REAL RESTART PASSED: stored400 + persisted600 = 1000mB; removed source stays absent");
    }
    private static void persistenceSeed(ServerLevel level) throws Exception {
        var staff = reset(level); player.setPos(PERSIST.getX() + .5, PERSIST.getY(), PERSIST.getZ() + .5);
        water(level, PERSIST); store.executionLeft = 400; collect();
        check(store.total() == 400 && pending(staff) == 600 && level.isEmptyBlock(PERSIST), "restart fixture partially inserted");
        var data = new CompoundTag(); data.put("staff", staff.save(level.registryAccess())); data.putLong("stored", store.total());
        NbtIo.writeCompressed(data, fixture(level));
        LogUtils.getLogger().info("STAFF FLUID PERSISTENCE SEEDED: 600mB saved on staff for next process");
    }
    private static int pending(ItemStack staff) { var p = staff.get(StretcherComponents.STAFF_FLUID_PENDING.get()); return p == null ? 0 : p.amount(); }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static final class FluidStore implements MEStorage {
        final Map<AEKey, Long> amounts = new HashMap<>();
        long capacity, executionLeft;
        void reset() { amounts.clear(); capacity = Long.MAX_VALUE; executionLeft = Long.MAX_VALUE; }
        long total() { return amounts.values().stream().mapToLong(Long::longValue).sum(); }
        long amount(net.minecraft.world.level.material.Fluid fluid) { return amounts.getOrDefault(AEFluidKey.of(fluid), 0L); }
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (!(key instanceof AEFluidKey)) return 0;
            long accepted = Math.min(amount, capacity - total());
            if (mode == Actionable.MODULATE) { accepted = Math.min(accepted, executionLeft); executionLeft -= accepted; amounts.merge(key, accepted, Long::sum); }
            return accepted;
        }
        public void getAvailableStacks(KeyCounter counter) { amounts.forEach(counter::add); }
        public Component getDescription() { return Component.literal("fluid regression capacity fixture"); }
    }
}
