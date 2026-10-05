package com.sorrowmist.useless.stretcher.content.mold;

import appeng.api.stacks.AEItemKey;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** One cancellable query per player, with a shared world-library index and a global tick budget. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class PatternRepositorySearch {
    private static final Map<MinecraftServer, Map<UUID, Job>> JOBS = new WeakHashMap<>();

    public static void cancel(ServerPlayer player) {
        var jobs = JOBS.get(player.getServer());
        if (jobs != null) jobs.remove(player.getUUID());
    }

    public static void request(ServerPlayer player, UUID ref, MyriadPatternStore store,
                               Network.PatternPageRequestPayload request, boolean aeBound) {
        request(player, ref, store, request, aeBound, false);
    }

    public static void request(ServerPlayer player, UUID ref, MyriadPatternStore store,
                               Network.PatternPageRequestPayload request, boolean aeBound, boolean trim) {
        PatternSearchIndex index = store.searchIndex(ref);
        if (index == null) { reply(player, request, aeBound, List.of(), "", null); return; }
        var filter = PatternSearchIndex.Filter.of(request.query(), request.matchingItems(), request.matchingFluids(), request.mode());
        var cached = index.cached(filter);
        if (!trim && cached != null) { reply(player, request, aeBound, cached, "", index); return; }
        Job job = new Job(ref, store, index, index.search(filter), request, aeBound,
                trim ? store.stripByproducts(ref) : null, "trimmed:");
        JOBS.computeIfAbsent(player.getServer(), ignored -> new LinkedHashMap<>()).put(player.getUUID(), job);
        reply(player, request, aeBound, List.of(), trim ? "trim:0" : index.progress(), null);
    }

    public static void deduplicate(ServerPlayer player, UUID ref, MyriadPatternStore store,
                                   Network.PatternDeduplicatePayload payload) {
        cancel(player);
        var index = store.searchIndex(ref);
        if (index == null || !index.ready() || index.revision() != payload.revision()) {
            reply(player, payload.request(), false, List.of(), "changed", null);
            return;
        }
        var rewrite = store.removeDuplicates(ref, index, payload.keep());
        if (rewrite == null) { reply(player, payload.request(), false, List.of(), "changed", null); return; }
        JOBS.computeIfAbsent(player.getServer(), ignored -> new LinkedHashMap<>()).put(player.getUUID(),
                new Job(ref, store, index, null, payload.request(), false, rewrite, "deduplicated:"));
        reply(player, payload.request(), false, List.of(), "dedup:0", null);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var jobs = JOBS.get(event.getServer());
        if (jobs == null || jobs.isEmpty()) return;
        long deadline = System.nanoTime() + 2_000_000L;
        var iterator = jobs.entrySet().iterator();
        while (iterator.hasNext() && System.nanoTime() < deadline) {
            var entry = iterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            Job job = entry.getValue();
            if (!job.valid(player)) {
                iterator.remove();
                // A dropped job must never leave the client waiting forever; report failure so
                // the screen re-arms its buttons instead of staying pending until reopened.
                if (player != null) reply(player, job.request, job.aeBound, List.of(), "failed", null);
                continue;
            }
            try {
                if (job.store.searchIndex(job.ref) != job.index) {
                    reply(player, job.request, job.aeBound, List.of(), "changed", null);
                    iterator.remove();
                    continue;
                }
                if (job.rewrite != null) {
                    if (job.rewrite.advance(deadline)) {
                        reply(player, job.request, job.aeBound, List.of(), job.completion + job.rewrite.changed(), null);
                        iterator.remove();
                    } else if (event.getServer().getTickCount() % 10 == 0) {
                        reply(player, job.request, job.aeBound, List.of(), job.rewrite.progress(), null);
                    }
                    continue;
                }
                if (!job.index.ready()) job.index.advance(deadline);
                if (job.index.ready() && job.search.advance(deadline)) {
                    reply(player, job.request, job.aeBound, job.search.result(), "", job.index);
                    iterator.remove();
                } else if (event.getServer().getTickCount() % 10 == 0) {
                    reply(player, job.request, job.aeBound, List.of(),
                            job.index.ready() ? job.search.progress() : job.index.progress(), null);
                }
            } catch (RuntimeException exception) {
                LogUtils.getLogger().warn("Could not search a stretcher pattern library", exception);
                reply(player, job.request, job.aeBound, List.of(), "failed", null);
                iterator.remove();
            }
        }
    }

    private static void reply(ServerPlayer player, Network.PatternPageRequestPayload request, boolean bound,
                              List<AEItemKey> matches, String progress, PatternSearchIndex index) {
        int pageSize = Math.max(1, Math.min(Network.PatternPagePayload.MAX_PAGE, request.pageSize()));
        int pages = Math.max(1, (matches.size() + pageSize - 1) / pageSize);
        int page = Math.min(Math.max(0, request.page()), pages - 1);
        int from = page * pageSize;
        var slice = List.copyOf(matches.subList(from, Math.min(matches.size(), from + pageSize)));
        PacketDistributor.sendToPlayer(player, new Network.PatternPagePayload(request.pos(), page, pages, bound,
                request.item(), request.offhand(), slice, request.requestId(), progress,
                index == null ? new byte[0] : index.duplicateFlags(slice),
                index == null ? PatternSearchIndex.DuplicatePage.EMPTY : index.duplicateSelection(slice)));
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) { JOBS.remove(event.getServer()); }

    private record Job(UUID ref, MyriadPatternStore store, PatternSearchIndex index, PatternSearchIndex.Search search,
                       Network.PatternPageRequestPayload request, boolean aeBound, MyriadPatternStore.Rewrite rewrite,
                       String completion) {
        boolean valid(ServerPlayer player) {
            if (player == null || !request.item()) return false;
            if (MyriadPatternStore.get(player.serverLevel()) != store) return false;
            var held = player.getItemInHand(request.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            return held.is(ModItems.USELESS_STRETCHER.get()) && ref.equals(MyriadMoldData.readPatternRef(held));
        }
    }
}
