package com.sorrowmist.useless.stretcher.event;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.blockentity.OmniversalMyriadBlockEntity;
import com.sorrowmist.useless.stretcher.content.mold.MoldRecipeIndex;
import com.sorrowmist.useless.stretcher.network.Network;
import com.sorrowmist.useless.stretcher.util.RateLimitedLogger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

/** Jobs belong to blocks. This transient round-robin queue never holds players or their inventories. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class MyriadWorkQueue {
    private static final Map<MinecraftServer, ArrayDeque<OmniversalMyriadBlockEntity>> QUEUES = new WeakHashMap<>();
    private static final long MIN_SLICE_NANOS = 1_000_000L;
    private static final long MAX_SLICE_NANOS = 8_000_000L;

    private MyriadWorkQueue() { }

    public static void enqueue(OmniversalMyriadBlockEntity block) {
        if (!(block.getLevel() instanceof ServerLevel level)) return;
        var queue = QUEUES.computeIfAbsent(level.getServer(), ignored -> new ArrayDeque<>());
        if (!queue.contains(block)) queue.addLast(block);
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        var queue = QUEUES.get(event.getServer());
        long started = System.nanoTime();
        boolean jobs = queue != null && !queue.isEmpty();
        MoldRecipeIndex index;
        try {
            index = MoldRecipeIndex.get(event.getServer().overworld());
        } catch (RuntimeException exception) {
            RateLimitedLogger.warn(LogUtils.getLogger(), "Cannot read recipe catalog for myriad fetch", exception);
            if (jobs) {
                for (var block : queue) {
                    block.cancelFetch();
                    block.setFetchFailed();
                    Network.replyToViewers(block);
                }
                QUEUES.remove(event.getServer());
            }
            return;
        }
        // The latest base mod builds its shared recipe directory asynchronously. Wait for the
        // published snapshot instead of synchronously rebuilding it on the server tick thread.
        if (!index.catalogReady()) return;
        if (!jobs && (index.ready() || index.failed())) return;
        long slice = currentSlice(event.getServer());
        long deadline = started + slice;
        long warmDeadline = jobs ? deadline - slice / 2 : deadline;
        try {
            while (!index.ready() && !index.failed() && System.nanoTime() < warmDeadline) index.advance();
        } catch (RuntimeException exception) {
            RateLimitedLogger.warn(LogUtils.getLogger(), "Cannot prepare myriad recipe index; fetches will report an error", exception);
        }
        if (!jobs || System.nanoTime() >= deadline) return;
        var notify = new java.util.LinkedHashSet<OmniversalMyriadBlockEntity>();
        int steps = 0;
        do {
            var block = queue.removeFirst();
            if (block.isRemoved() || !(block.getLevel() instanceof ServerLevel level)
                    || !level.hasChunkAt(block.getBlockPos())
                    || level.getBlockEntity(block.getBlockPos()) != block) {
                block.cancelFetch();
                continue;
            }
            boolean finished;
            try {
                finished = block.stepFetch();
            } catch (RuntimeException exception) {
                RateLimitedLogger.warn(LogUtils.getLogger(), "Myriad fetch failed at {}", block.getBlockPos(), exception);
                block.cancelFetch();
                block.setFetchFailed();
                finished = true;
            }
            if (!finished) queue.addLast(block);
            if (finished || event.getServer().getTickCount() % 10 == 0) notify.add(block);
        } while (!queue.isEmpty() && ++steps < 8192 && System.nanoTime() < deadline);
        notify.forEach(Network::replyToViewers);
        if (queue.isEmpty()) QUEUES.remove(event.getServer());
    }

    private static long currentSlice(MinecraftServer server) {
        return sliceBudget((long) (server.getAverageTickTime() * 1_000_000D), 50_000_000L);
    }

    /** Use a quarter of measured headroom, capped globally; never a per-block allowance. */
    public static long sliceBudget(long average, long target) {
        long headroom = Math.max(0L, Math.max(0L, target) - Math.max(0L, average));
        return Math.max(MIN_SLICE_NANOS, Math.min(MAX_SLICE_NANOS, headroom / 4));
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        MoldRecipeIndex.removeServer(event.getServer());
        var queue = QUEUES.remove(event.getServer());
        if (queue != null) queue.forEach(OmniversalMyriadBlockEntity::cancelFetch);
    }
}
