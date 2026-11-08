package com.sorrowmist.useless.stretcher.compat;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.acceleration.AccelerationExecutionBudget;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Admits work through the machine's own batching contract, without replaying its IO ticker. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class CoalescedMachineAccelerationCompat {
    private static final String JDTE = "com.jdte.common.blockentities.CoalescedAcceleratedMachine";
    private static final String PBG = "com.ayoshiko.productivebeesgenesis.mek.IJdteCentrifugeFactory";
    private static final int MAX_QUEUED_TARGETS = 4096;
    // All access is on the owning server thread. Stopped servers are explicitly released.
    private static final Map<MinecraftServer, Queue> QUEUES = new WeakHashMap<>();
    private static final ClassValue<Contract> CONTRACTS = new ClassValue<>() {
        @Override protected Contract computeValue(Class<?> type) {
            Class<?> api = findInterface(type, JDTE);
            if (api != null) return contract(api, "accumulateAcceleratedTicks", "flushAcceleratedTicks");
            api = findInterface(type, PBG);
            return api == null ? Contract.NONE : contract(api,
                    "productivebeesgenesis$accumulateAcceleratedTicks",
                    "productivebeesgenesis$flushAcceleratedTicks");
        }
    };

    private CoalescedMachineAccelerationCompat() { }

    /**
     * null means there is no batching API and ordinary ticking is still appropriate. Once an API
     * call starts, its void result cannot identify a partial commit: never fall back or replay it.
     * Accepted ticks belong to the machine's bounded bank, not to our pending-tick counter.
     */
    public static Integer accumulate(BlockEntity target, int ticks) {
        Contract contract = CONTRACTS.get(target.getClass());
        if (contract == Contract.NONE) return null;
        if (ticks <= 0 || target.isRemoved() || !(target.getLevel() instanceof ServerLevel level)) return 0;
        MinecraftServer server = level.getServer();
        if (!server.isSameThread()) throw new IllegalStateException("Machine acceleration requires the server thread");
        Queue queue = QUEUES.computeIfAbsent(server, ignored -> new Queue());
        queue.begin(server.getTickCount());
        if (queue.failed.contains(target)) return ticks;
        boolean alreadyQueued = queue.pending.containsKey(target);
        if (!alreadyQueued && queue.pending.size() >= MAX_QUEUED_TARGETS) return 0;
        try {
            if (contract.accumulate == null) throw new IllegalStateException("Unsupported batching API signature");
            contract.accumulate.invoke(target, ticks);
            if (!alreadyQueued) {
                queue.pending.put(target, new Pending(level, contract));
                queue.order.addLast(target);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            fail(queue, target, failure);
        }
        return ticks;
    }

    /** Flushes each target at most once per real server tick, after single and range contributions. */
    public static void flush(MinecraftServer server) {
        Queue queue = QUEUES.get(server);
        if (queue == null) return;
        if (!server.isSameThread()) throw new IllegalStateException("Machine acceleration requires the server thread");
        queue.begin(server.getTickCount());
        int remaining = queue.order.size();
        while (remaining-- > 0 && !queue.order.isEmpty()) {
            BlockEntity target = queue.order.peekFirst();
            Pending pending = queue.pending.get(target);
            if (pending == null || queue.failed.contains(target) || !isCurrent(target, pending.level)) {
                queue.order.removeFirst();
                queue.pending.remove(target);
                continue;
            }
            if (queue.flushed.contains(target)) {
                // A reentrant/new contribution after a flush belongs to the next real tick.
                queue.order.addLast(queue.order.removeFirst());
                continue;
            }
            long deadline = AccelerationExecutionBudget.deadline(server, target);
            long started = System.nanoTime();
            if (started >= deadline) {
                long globalDeadline = AccelerationExecutionBudget.deadline(server);
                if (System.nanoTime() >= globalDeadline) break;
                // This target spent its own slice; another queued machine may still run.
                queue.order.addLast(queue.order.removeFirst());
                continue;
            }
            queue.order.removeFirst();
            queue.pending.remove(target);
            queue.flushed.add(target);
            try {
                pending.contract.flush.invoke(target);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                fail(queue, target, failure);
            } finally {
                AccelerationExecutionBudget.recordWork(server, target, started);
            }
        }
    }

    private static boolean isCurrent(BlockEntity target, ServerLevel level) {
        return !target.isRemoved() && target.getLevel() == level && level.hasChunkAt(target.getBlockPos())
                && level.getBlockEntity(target.getBlockPos()) == target;
    }

    private static void fail(Queue queue, BlockEntity target, Throwable failure) {
        Throwable cause = failure instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause() : failure;
        if (cause instanceof VirtualMachineError fatal) throw fatal;
        if (cause instanceof ThreadDeath fatal) throw fatal;
        queue.pending.remove(target);
        // Keep queue order/map consistent when accumulation fails on an already queued target.
        queue.order.removeIf(queued -> queued == target);
        if (queue.failed.add(target)) LogUtils.getLogger().warn(
                "Paused extra staff ticks for {} at {} after its batching callback failed; "
                        + "accepted work will not be replayed. Normal machine ticks remain enabled.",
                target.getClass().getName(), target.getBlockPos(), cause);
    }

    private static Contract contract(Class<?> api, String accumulate, String flush) {
        try {
            Method add = api.getMethod(accumulate, int.class);
            Method run = api.getMethod(flush);
            return add.getReturnType() == void.class && run.getReturnType() == void.class
                    ? new Contract(add, run) : new Contract(null, null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Recognized but incompatible contract: do not replay an expensive ordinary ticker.
            return new Contract(null, null);
        }
    }

    private static Class<?> findInterface(Class<?> type, String name) {
        if (type == null) return null;
        if (name.equals(type.getName())) return type;
        for (Class<?> api : type.getInterfaces()) {
            Class<?> found = findInterface(api, name);
            if (found != null) return found;
        }
        return findInterface(type.getSuperclass(), name);
    }

    public static void clear(MinecraftServer server) { QUEUES.remove(server); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTickStart(ServerTickEvent.Pre event) {
        // The shared budget has reset. Give older accepted work a turn before fresh
        // ordinary accelerators can spend the whole allowance again.
        flush(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTickEnd(ServerTickEvent.Post event) { flush(event.getServer()); }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { clear(event.getServer()); }

    private record Contract(Method accumulate, Method flush) {
        private static final Contract NONE = new Contract(null, null);
    }

    private record Pending(ServerLevel level, Contract contract) { }

    private static final class Queue {
        private final Map<BlockEntity, Pending> pending = new IdentityHashMap<>();
        private final ArrayDeque<BlockEntity> order = new ArrayDeque<>();
        private final WeakIdentitySet failed = new WeakIdentitySet();
        private final Set<BlockEntity> flushed = Collections.newSetFromMap(new IdentityHashMap<>());
        private int tick = Integer.MIN_VALUE;

        private void begin(int currentTick) {
            if (tick != currentTick) {
                tick = currentTick;
                flushed.clear();
            }
        }
    }

    /** A replacement BE must not inherit a pause even if a mod defines position-based equals. */
    private static final class WeakIdentitySet {
        private final ReferenceQueue<BlockEntity> collected = new ReferenceQueue<>();
        private final Set<IdentityReference> entries = new HashSet<>();

        boolean contains(BlockEntity target) {
            reap();
            return entries.contains(new IdentityReference(target, null));
        }

        boolean add(BlockEntity target) {
            reap();
            return entries.add(new IdentityReference(target, collected));
        }

        private void reap() {
            IdentityReference reference;
            while ((reference = (IdentityReference) collected.poll()) != null) entries.remove(reference);
        }
    }

    private static final class IdentityReference extends WeakReference<BlockEntity> {
        private final int hash;
        IdentityReference(BlockEntity target, ReferenceQueue<BlockEntity> queue) {
            super(target, queue);
            hash = System.identityHashCode(target);
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof IdentityReference reference
                    && get() != null && get() == reference.get();
        }
    }
}
