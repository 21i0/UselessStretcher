package com.sorrowmist.useless.stretcher.content.mold;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.menu.OmniversalMyriadMenu;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Rebuilds and persists the mold list only when a world first opens it or its recipe snapshot changes. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class MoldCatalogService {
    private static final Map<MinecraftServer, ServerState> STATES = new WeakHashMap<>();

    private MoldCatalogService() { }

    public static void request(ServerPlayer player, BlockPos pos) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerState state = STATES.computeIfAbsent(server, ignored -> new ServerState());
        state.requests.put(player.getUUID(), new Request(pos.immutable()));
        state.sendCached(server);
        Network.sendMoldCatalogStatus(player, pos, "checking", "");
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        ServerState state = STATES.get(event.getServer());
        if (state == null) return;
        try {
            state.removeClosedRequests(event.getServer());
            state.tryStart(event.getServer());
            if (state.builder == null) return;
            boolean done = state.builder.advance();
            state.progress(event.getServer(), "building", state.builder.progress(), done);
            if (!done) return;

            var catalog = state.builder.result();
            MoldCatalogSavedData.get(event.getServer()).replace(state.fingerprint, catalog);
            state.builder = null;
            state.publish(event.getServer(), true);
        } catch (RuntimeException exception) {
            LogUtils.getLogger().error("Could not prepare the Omniversal Myriad mold catalog", exception);
            state.builder = null;
            state.fingerprint = "";
            state.fail(event.getServer());
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        STATES.remove(event.getServer());
    }

    private static final class ServerState {
        private final Map<UUID, Request> requests = new LinkedHashMap<>();
        private List<?> recipeSnapshot;
        private String fingerprint;
        private MoldCatalog.Builder builder;
        private long nextStatusTick;
        private String lastStatus = "";
        private String lastProgress = "";

        private void tryStart(MinecraftServer server) {
            if (requests.isEmpty() && builder == null) return;
            var level = server.overworld();
            RecipeCatalogAccess.Snapshot snapshot = RecipeCatalogAccess.read(level);
            if (!snapshot.ready()) {
                builder = null;
                RecipeCatalogAccess.prewarmAsync(level);
                sendCached(server);
                progress(server, "waiting", "", false);
                return;
            }

            // Snapshot identity is world-local. Client-side invalidations must not trigger
            // another full fingerprint pass over the unchanged server catalog.
            if (snapshot.entries() != recipeSnapshot || fingerprint == null || fingerprint.isEmpty()) {
                builder = null;
                recipeSnapshot = snapshot.entries();
                fingerprint = MoldCatalog.fingerprint(snapshot.entries());
            }
            if (builder != null) return;

            MoldCatalogSavedData saved = MoldCatalogSavedData.get(server);
            if (saved.matches(fingerprint)) {
                publish(server, true);
                return;
            }
            builder = MoldCatalog.start(level);
            progress(server, "building", builder.progress(), true);
        }

        private void sendCached(MinecraftServer server) {
            if (MoldCatalogSavedData.get(server).hasEntries()) publish(server, false);
        }

        private void publish(MinecraftServer server, boolean complete) {
            var catalog = MoldCatalogSavedData.get(server).moldsBySource();
            for (var request : requests.entrySet()) {
                if (!complete && request.getValue().cachedSent) continue;
                ServerPlayer player = server.getPlayerList().getPlayer(request.getKey());
                BlockPos pos = request.getValue().pos;
                if (!viewing(player, pos)) continue;
                Network.sendMoldCatalog(player, pos, catalog);
                request.getValue().cachedSent = true;
            }
            // Cached entries are provisional until the current recipe fingerprint is checked.
            if (complete) requests.clear();
        }

        private void progress(MinecraftServer server, String status, String progress, boolean force) {
            if (!force && server.getTickCount() < nextStatusTick) return;
            if (!force && status.equals(lastStatus) && progress.equals(lastProgress)) return;
            nextStatusTick = server.getTickCount() + 5L;
            lastStatus = status;
            lastProgress = progress;
            for (var request : requests.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(request.getKey());
                if (viewing(player, request.getValue().pos)) {
                    Network.sendMoldCatalogStatus(player, request.getValue().pos, status, progress);
                }
            }
        }

        private void removeClosedRequests(MinecraftServer server) {
            requests.entrySet().removeIf(request -> !viewing(
                    server.getPlayerList().getPlayer(request.getKey()), request.getValue().pos));
        }

        private void fail(MinecraftServer server) {
            for (var request : requests.entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(request.getKey());
                if (player != null) Network.sendMoldCatalogStatus(player, request.getValue().pos, "failed", "");
            }
            requests.clear();
        }
    }

    private static boolean viewing(ServerPlayer player, BlockPos pos) {
        return player != null && player.containerMenu instanceof OmniversalMyriadMenu menu
                && menu.getPos().equals(pos) && player.distanceToSqr(pos.getCenter()) <= 64.0D;
    }

    private static final class Request {
        private final BlockPos pos;
        private boolean cachedSent;

        private Request(BlockPos pos) {
            this.pos = pos;
        }
    }
}
