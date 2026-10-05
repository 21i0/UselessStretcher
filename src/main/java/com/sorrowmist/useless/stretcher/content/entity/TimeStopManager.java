package com.sorrowmist.useless.stretcher.content.entity;

import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.network.Network;

import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-owned one-shot time stop state. The pause is scoped to the actor's dimension. */
public final class TimeStopManager {
    public static final int DURATION_TICKS = 240;
    private static final Map<MinecraftServer, State> ACTIVE = new WeakHashMap<>();

    private TimeStopManager() { }

    public static boolean start(ServerPlayer player) {
        if (player == null || player.getServer() == null || !StretcherConfig.serverTimeStop()) return false;
        MinecraftServer server = player.getServer();
        if (remaining(server) > 0) return false;
        ACTIVE.put(server, new State(player.serverLevel().dimension().location(), player.getUUID(), DURATION_TICKS));
        player.serverLevel().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.0F);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            online.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "msg.useless_stretcher.time_stop.start"), true);
        }
        broadcast(server, DURATION_TICKS);
        return true;
    }

    public static void tick(MinecraftServer server) {
        State state = ACTIVE.get(server);
        if (state == null) return;
        int remaining = state.remaining() - 1;
        if (remaining == 0) {
            ACTIVE.remove(server);
            broadcast(server, 0);
            ServerLevel level = null;
            for (ServerLevel candidate : server.getAllLevels()) {
                if (candidate.dimension().location().equals(state.dimension())) {
                    level = candidate;
                    break;
                }
            }
            if (level != null) level.playSound(null, level.getSharedSpawnPos(),
                    net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.0F);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "msg.useless_stretcher.time_stop.end"), true);
            }
        } else {
            ACTIVE.put(server, new State(state.dimension(), state.actor(), remaining));
            if (remaining % 20 == 0) broadcast(server, remaining);
        }
    }

    private static void broadcast(MinecraftServer server, int remaining) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) Network.sendTimeStopState(player, remaining);
    }

    /**
     * ServerLevel.tick is intentionally skipped while the stop is active.  Run
     * only staff holders once from the server tick phase so their inventory,
     * movement and input remain responsive without ticking them a second time
     * from the network connection callback.
     */
    public static void tickExemptPlayers(MinecraftServer server) {
        if (server == null || remaining(server) <= 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!mayPlayerTickDuringPause(player) || player.isRemoved()) continue;
            ServerLevel level = player.serverLevel();
            if (level.getServer() != server) continue;
            level.tickNonPassenger(player);
        }
    }

    public static boolean isPaused(ServerLevel level) {
        State state = level == null ? null : ACTIVE.get(level.getServer());
        return state != null && state.remaining() > 0
                && state.dimension().equals(level.dimension().location());
    }

    public static int remaining(MinecraftServer server) {
        State state = ACTIVE.get(server);
        return state == null ? 0 : state.remaining();
    }

    /** Any player currently carrying the staff remains responsive in the paused dimension. */
    public static boolean mayPlayerTickDuringPause(ServerPlayer player) {
        if (player == null || player.getServer() == null) return false;
        State state = ACTIVE.get(player.getServer());
        return state != null && state.remaining() > 0
                && state.dimension().equals(player.serverLevel().dimension().location())
                && (player.getMainHandItem().is(ModItems.WONDROUS_STAFF.get())
                || player.getOffhandItem().is(ModItems.WONDROUS_STAFF.get()));
    }

    public static boolean mayEntityTick(Entity entity) {
        return entity instanceof ServerPlayer player && mayPlayerTickDuringPause(player);
    }

    private record State(ResourceLocation dimension, UUID actor, int remaining) { }
}
