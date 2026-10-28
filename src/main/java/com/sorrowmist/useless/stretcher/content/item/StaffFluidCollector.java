package com.sorrowmist.useless.stretcher.content.item;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.MEStorage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sorrowmist.useless.compat.AE2Compat;
import com.sorrowmist.useless.compat.forge1201.component.ComponentAccess;
import com.sorrowmist.useless.compat.forge1201.network.StreamCodec;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.stretcher.compat.NativeMath;
import com.sorrowmist.useless.stretcher.content.entity.StaffPhaseManager;
import com.sorrowmist.useless.stretcher.content.entity.TimeStopManager;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.util.RateLimitedLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One bounded scan per carrier; never loads chunks or drains tanks/waterlogged solids. */
public final class StaffFluidCollector {
    public static final int MAX_RADIUS = 16;
    public static final int MAX_RATE = 64;
    private static final int PLAYER_SCAN_LIMIT = 512;
    private static final int SERVER_SCAN_LIMIT = 4096;
    private static final long SERVER_SCAN_NANOS = 2_000_000L;
    private static final Map<UUID, Scan> SCANS = new HashMap<>();
    private static int rotation;

    private StaffFluidCollector() { }

    public static boolean enabled(ItemStack staff) {
        return ComponentAccess.getOrDefault(staff, StretcherComponents.STAFF_FLUID_COLLECT.get(), false);
    }
    public static int radius(ItemStack staff) {
        return NativeMath.clamp(ComponentAccess.getOrDefault(staff, StretcherComponents.STAFF_FLUID_RADIUS.get(), 4), 1, MAX_RADIUS);
    }
    public static int rate(ItemStack staff) {
        return NativeMath.clamp(ComponentAccess.getOrDefault(staff, StretcherComponents.STAFF_FLUID_RATE.get(), 4), 1, MAX_RATE);
    }
    public static void configure(ItemStack staff, boolean enabled, int radius, int rate) {
        ComponentAccess.set(staff, StretcherComponents.STAFF_FLUID_COLLECT.get(), enabled);
        ComponentAccess.set(staff, StretcherComponents.STAFF_FLUID_RADIUS.get(), NativeMath.clamp(radius, 1, MAX_RADIUS));
        ComponentAccess.set(staff, StretcherComponents.STAFF_FLUID_RATE.get(), NativeMath.clamp(rate, 1, MAX_RATE));
    }

    public static void tick(MinecraftServer server) {
        var players = server.getPlayerList().getPlayers();
        SCANS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        if (players.isEmpty()) return;
        long deadline = System.nanoTime() + SERVER_SCAN_NANOS;
        int remaining = SERVER_SCAN_LIMIT;
        int first = Math.floorMod(rotation++, players.size());
        for (int n = 0; n < players.size() && remaining > 0 && System.nanoTime() < deadline; n++) {
            remaining -= tickPlayer(players.get((first + n) % players.size()),
                    Math.min(PLAYER_SCAN_LIMIT, remaining), deadline);
        }
    }

    /** Scan one carrier within the caller's budget; return the number of blocks inspected. */
    public static int tickPlayer(ServerPlayer player, int scanLimit, long deadline) {
        Scan previous = SCANS.get(player.getUUID());
        if (previous != null && player.serverLevel().getGameTime() < previous.retryAt) return 0;
        try {
            return collect(player, scanLimit, deadline);
        } catch (RuntimeException failure) {
            // A broken external inventory must not take down the server or spam every tick.
            // Ordinary integration failures may retry; a commit with an unknown result
            // stays quarantined on the staff and is never automatically replayed.
            SCANS.computeIfAbsent(player.getUUID(), ignored -> new Scan()).retryAt = player.serverLevel().getGameTime() + 200;
            var pending = ComponentAccess.get(activeStaff(player), StretcherComponents.STAFF_FLUID_PENDING.get());
            if (pending != null && pending.uncertain())
                RateLimitedLogger.warn(LogUtils.getLogger(),
                        "Staff fluid collection disabled for {}: {} mB commit result unknown; inspect staff before recovery",
                        player.getUUID(), pending.amount(), failure);
            else RateLimitedLogger.warn(LogUtils.getLogger(),
                    "Staff fluid collection paused for {} after an integration error", player.getUUID(), failure);
            return Math.max(0, scanLimit);
        }
    }

    private static int collect(ServerPlayer player, int scanLimit, long deadline) {
        ItemStack staff = activeStaff(player);
        if (staff.isEmpty() || !player.isAlive() || player.isSpectator() || !player.mayBuild()
                || StaffPhaseManager.blocksInteraction(player)
                || (TimeStopManager.isPaused(player.serverLevel()) && !TimeStopManager.mayPlayerTickDuringPause(player))) {
            SCANS.remove(player.getUUID());
            return 0;
        }
        // Use exactly the upstream G-menu preference and wireless binding, including
        // cross-dimension, loaded-chunk and online-node checks. Never use a nearby grid.
        if (!ComponentAccess.getOrDefault(staff, UComponents.AEStoragePriorityComponent.get(), false)) {
            SCANS.remove(player.getUUID());
            return 0;
        }
        var access = AE2Compat.resolveLinkedGrid(staff, player);
        if (access == null) { SCANS.remove(player.getUUID()); return 0; }
        var binding = ComponentAccess.get(staff, UComponents.WIRELESS_LINK_TARGET.get());
        var storage = access.grid().getStorageService().getInventory();
        PendingFluid pending = ComponentAccess.get(staff, StretcherComponents.STAFF_FLUID_PENDING.get());
        if (pending != null) {
            if (pending.uncertain() || insertPending(staff, pending, storage, access.source()) > 0) return 0;
        }
        Scan scan = SCANS.computeIfAbsent(player.getUUID(), ignored -> new Scan());
        int radius = radius(staff), rate = rate(staff);
        BlockPos center = player.blockPosition();
        if (scan.staff != staff || scan.level != player.serverLevel() || scan.radius != radius || !center.equals(scan.center)) {
            if (scan.staff != staff || scan.level != player.serverLevel()) scan.credit = 0;
            scan.cursor = 0;
            scan.nextSweep = 0;
            scan.center = center;
            scan.level = player.serverLevel();
            scan.radius = radius;
            scan.staff = staff;
        }
        // Rate is buckets per 20 game ticks. No backlog accumulates while blocked or idle.
        scan.credit = Math.min(scan.credit + rate, 19 + rate);
        if (scan.credit < 20 || player.serverLevel().getGameTime() < scan.nextSweep) return 0;
        int width = 2 * radius + 1, volume = width * width * width;
        var pos = new BlockPos.MutableBlockPos();
        Set<AEFluidKey> full = new HashSet<>();
        int checked = 0;
        while (checked < Math.min(PLAYER_SCAN_LIMIT, Math.max(0, scanLimit)) && scan.credit >= 20
                && System.nanoTime() < deadline) {
            int index = scan.cursor++;
            checked++;
            pos.set(center.getX() + offset(index % width), center.getY() + offset(index / (width * width)),
                    center.getZ() + offset((index / width) % width));
            boolean end = scan.cursor >= volume;
            if (end) { scan.cursor = 0; scan.nextSweep = player.serverLevel().getGameTime() + 20; }
            ServerLevel level = player.serverLevel();
            if (level.isInWorldBounds(pos) && level.getWorldBorder().isWithinBounds(pos) && level.hasChunkAt(pos)) {
                BlockState state = level.getBlockState(pos);
                if (isFullSource(state) && level.mayInteract(player, pos)) {
                    var fluid = new FluidStack(state.getFluidState().getType(), FluidType.BUCKET_VOLUME);
                    var key = AEFluidKey.of(fluid);
                    if (!full.contains(key)) {
                        if (storage.insert(key, FluidType.BUCKET_VOLUME, Actionable.SIMULATE, access.source()) < FluidType.BUCKET_VOLUME) full.add(key);
                        else {
                            // Claims see an ordinary cancellable player break check. The marker
                            // subclass prevents our upstream mining dispatcher from mining here.
                            var check = new DrainPermissionCheck(level, pos.immutable(), state, player);
                            MinecraftForge.EVENT_BUS.post(check);
                            if (!check.isCanceled() && activeStaff(player) == staff
                                    && java.util.Objects.equals(binding, ComponentAccess.get(staff, UComponents.WIRELESS_LINK_TARGET.get()))
                                    && ComponentAccess.getOrDefault(staff, UComponents.AEStoragePriorityComponent.get(), false)
                                    && level.getBlockState(pos) == state) {
                                // Persist recovery data before removing the source. A partial real
                                // insert remains on this item across save/relogin via the NBT bridge.
                                var drained = new PendingFluid(key, FluidType.BUCKET_VOLUME);
                                ComponentAccess.set(staff, StretcherComponents.STAFF_FLUID_PENDING.get(), drained);
                                if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)) {
                                    ComponentAccess.remove(staff, StretcherComponents.STAFF_FLUID_PENDING.get());
                                    if (end) break;
                                    continue;
                                }
                                scan.credit -= 20;
                                if (insertPending(staff, drained, storage, access.source()) > 0) return checked;
                            }
                        }
                    }
                }
            }
            if (end) break;
        }
        return checked;
    }

    private static int insertPending(ItemStack staff, PendingFluid pending, MEStorage storage, IActionSource source) {
        // An external inventory can commit some fluid and then throw. Persist an
        // in-flight marker before calling it; only a normal return authorizes replay
        // of a known remainder. This also survives save/relogin and scan-cache resets.
        ComponentAccess.set(staff, StretcherComponents.STAFF_FLUID_PENDING.get(),
                new PendingFluid(pending.key(), pending.amount(), true));
        long inserted = storage.insert(pending.key(), pending.amount(), Actionable.MODULATE, source);
        int left = pending.amount() - (int) NativeMath.clamp(inserted, 0L, pending.amount());
        if (left > 0) ComponentAccess.set(staff, StretcherComponents.STAFF_FLUID_PENDING.get(), new PendingFluid(pending.key(), left));
        else ComponentAccess.remove(staff, StretcherComponents.STAFF_FLUID_PENDING.get());
        return left;
    }

    public static boolean isFullSource(BlockState state) {
        return state.getBlock() instanceof LiquidBlock && state.getFluidState().isSource()
                && state.getValue(BlockStateProperties.LEVEL) == 0 && !state.hasBlockEntity();
    }

    private static int offset(int i) { return i == 0 ? 0 : (i % 2 == 1 ? -(i + 1) / 2 : i / 2); }
    private static ItemStack activeStaff(ServerPlayer player) {
        var main = player.getMainHandItem();
        if (main.is(ModItems.WONDROUS_STAFF.get()) && enabled(main)) return main;
        var off = player.getOffhandItem();
        return off.is(ModItems.WONDROUS_STAFF.get()) && enabled(off) ? off : ItemStack.EMPTY;
    }
    public static void clear() { SCANS.clear(); rotation = 0; }

    /** Immutable values stored by the Forge component/NBT bridge. AE2 15 uses NBT fluid keys. */
    public record PendingFluid(AEFluidKey key, int amount, boolean uncertain) {
        public PendingFluid(AEFluidKey key, int amount) { this(key, amount, false); }
        public PendingFluid {
            java.util.Objects.requireNonNull(key);
            if (amount < 1 || amount > FluidType.BUCKET_VOLUME) throw new IllegalArgumentException("Invalid pending bucket amount");
        }
        private static final Codec<AEFluidKey> FLUID_CODEC = CompoundTag.CODEC.comapFlatMap(tag -> {
            AEFluidKey key = AEFluidKey.fromTag(tag);
            return key == null ? DataResult.error(() -> "Invalid pending fluid key") : DataResult.success(key);
        }, AEFluidKey::toTag);
        public static final Codec<PendingFluid> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                FLUID_CODEC.fieldOf("fluid").forGetter(PendingFluid::key),
                Codec.intRange(1, FluidType.BUCKET_VOLUME).fieldOf("amount").forGetter(PendingFluid::amount),
                Codec.BOOL.optionalFieldOf("uncertain", false).forGetter(PendingFluid::uncertain)
        ).apply(instance, PendingFluid::new));
        public static final StreamCodec<FriendlyByteBuf, PendingFluid> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> { value.key().writeToPacket(buf); buf.writeVarInt(value.amount()); buf.writeBoolean(value.uncertain()); },
                buf -> new PendingFluid(AEFluidKey.fromPacket(buf), buf.readVarInt(), buf.readBoolean()));
    }

    public static final class DrainPermissionCheck extends BlockEvent.BreakEvent {
        public DrainPermissionCheck(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
            super(level, pos, state, player);
        }
    }
    private static final class Scan {
        ItemStack staff;
        ServerLevel level;
        BlockPos center;
        int radius, cursor, credit;
        long nextSweep, retryAt;
    }
}
