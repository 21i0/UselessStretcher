package com.sorrowmist.useless.stretcher.content.item;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.compat.AE2Compat;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.stretcher.content.entity.StaffPhaseManager;
import com.sorrowmist.useless.stretcher.content.entity.TimeStopManager;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

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
        return staff.getOrDefault(StretcherComponents.STAFF_FLUID_COLLECT.get(), false);
    }
    public static int radius(ItemStack staff) {
        return Math.clamp(staff.getOrDefault(StretcherComponents.STAFF_FLUID_RADIUS.get(), 4), 1, MAX_RADIUS);
    }
    public static int rate(ItemStack staff) {
        return Math.clamp(staff.getOrDefault(StretcherComponents.STAFF_FLUID_RATE.get(), 4), 1, MAX_RATE);
    }
    public static void configure(ItemStack staff, boolean enabled, int radius, int rate) {
        staff.set(StretcherComponents.STAFF_FLUID_COLLECT.get(), enabled);
        staff.set(StretcherComponents.STAFF_FLUID_RADIUS.get(), Math.clamp(radius, 1, MAX_RADIUS));
        staff.set(StretcherComponents.STAFF_FLUID_RATE.get(), Math.clamp(rate, 1, MAX_RATE));
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

    /** Public so integration checks can drive the same player path without a fake grid resolver. */
    public static int tickPlayer(ServerPlayer player, int scanLimit, long deadline) {
        Scan previous = SCANS.get(player.getUUID());
        if (previous != null && player.serverLevel().getGameTime() < previous.retryAt) return 0;
        try {
            return collect(player, scanLimit, deadline);
        } catch (RuntimeException failure) {
            // A broken external inventory must not take down the server or spam every tick.
            // Any journaled fluid remains on the staff for a later retry.
            SCANS.computeIfAbsent(player.getUUID(), ignored -> new Scan()).retryAt = player.serverLevel().getGameTime() + 200;
            LogUtils.getLogger().warn("Staff fluid collection paused for {} after an integration error", player.getUUID(), failure);
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
        // Exactly the upstream G-menu preference and wireless binding, including cross-dimension,
        // loaded-chunk and online-node checks. Never fall back to another nearby ME network.
        if (!staff.getOrDefault(UComponents.AEStoragePriorityComponent.get(), false)) {
            SCANS.remove(player.getUUID());
            return 0;
        }
        var access = AE2Compat.resolveLinkedGrid(staff, player);
        if (access == null) { SCANS.remove(player.getUUID()); return 0; }
        var binding = staff.get(UComponents.WIRELESS_LINK_TARGET.get());
        var storage = access.grid().getStorageService().getInventory();
        PendingFluid pending = staff.get(StretcherComponents.STAFF_FLUID_PENDING.get());
        if (pending != null) {
            long inserted = storage.insert(pending.key(), pending.amount(), Actionable.MODULATE, access.source());
            int left = pending.amount() - (int) Math.clamp(inserted, 0L, pending.amount());
            if (left > 0) {
                if (left != pending.amount()) staff.set(StretcherComponents.STAFF_FLUID_PENDING.get(), new PendingFluid(pending.key(), left));
                return 0;
            }
            staff.remove(StretcherComponents.STAFF_FLUID_PENDING.get());
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
                            NeoForge.EVENT_BUS.post(check);
                            if (!check.isCanceled() && activeStaff(player) == staff
                                    && java.util.Objects.equals(binding, staff.get(UComponents.WIRELESS_LINK_TARGET.get()))
                                    && staff.getOrDefault(UComponents.AEStoragePriorityComponent.get(), false)
                                    && level.getBlockState(pos) == state) {
                                // Establish immutable recovery data before removing the source.
                                // A partial real insert remains on this item across save/relogin.
                                staff.set(StretcherComponents.STAFF_FLUID_PENDING.get(), new PendingFluid(key, FluidType.BUCKET_VOLUME));
                                if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)) {
                                    staff.remove(StretcherComponents.STAFF_FLUID_PENDING.get());
                                    if (end) break;
                                    continue;
                                }
                                scan.credit -= 20;
                                long inserted = storage.insert(key, FluidType.BUCKET_VOLUME, Actionable.MODULATE, access.source());
                                int left = FluidType.BUCKET_VOLUME - (int) Math.clamp(inserted, 0L, FluidType.BUCKET_VOLUME);
                                if (left > 0) {
                                    staff.set(StretcherComponents.STAFF_FLUID_PENDING.get(), new PendingFluid(key, left));
                                    return checked;
                                }
                                staff.remove(StretcherComponents.STAFF_FLUID_PENDING.get());
                            }
                        }
                    }
                }
            }
            if (end) break;
        }
        return checked;
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

    /** Data components require immutable values with structural equality. */
    public record PendingFluid(AEFluidKey key, int amount) {
        public PendingFluid {
            java.util.Objects.requireNonNull(key);
            if (amount < 1 || amount > FluidType.BUCKET_VOLUME) throw new IllegalArgumentException("Invalid pending bucket amount");
        }
        public static final Codec<PendingFluid> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                AEFluidKey.CODEC.fieldOf("fluid").forGetter(PendingFluid::key),
                Codec.intRange(1, FluidType.BUCKET_VOLUME).fieldOf("amount").forGetter(PendingFluid::amount)
        ).apply(instance, PendingFluid::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, PendingFluid> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> { value.key().writeToPacket(buf); buf.writeVarInt(value.amount()); },
                buf -> new PendingFluid(AEFluidKey.fromPacket(buf), buf.readVarInt()));
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
