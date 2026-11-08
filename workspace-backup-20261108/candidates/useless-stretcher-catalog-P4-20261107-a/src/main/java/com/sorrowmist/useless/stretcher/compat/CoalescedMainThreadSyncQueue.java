package com.sorrowmist.useless.stretcher.compat;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Async producers request a fresh main-thread sync; they never inspect or snapshot managed fields. */
final class CoalescedMainThreadSyncQueue<K> {
    private static final long RETRY_TICKS = 20;
    private static final long ERROR_LOG_TICKS = 1_200;
    private final ConcurrentHashMap<IdentityKey<K>, Task<K>> pending = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Task<K>> ready = new ConcurrentLinkedQueue<>();

    boolean submit(K holder, Runnable sync) {
        return submit(holder, sync, sync);
    }

    boolean submit(K holder, Runnable sync, Runnable fullSync) {
        var key = new IdentityKey<>(Objects.requireNonNull(holder));
        var task = new Task<>(key, Objects.requireNonNull(sync), Objects.requireNonNull(fullSync));
        if (pending.putIfAbsent(key, task) != null) return false;
        ready.add(task);
        return true;
    }

    /** Called by the server thread only. Failed work stays pending and moves behind other holders. */
    int drain(long tick, int maxTasks, long budgetNanos, LongSupplier clock,
              Predicate<K> valid, BiConsumer<K, Throwable> reportFailure) {
        int scans = Math.min(Math.max(0, maxTasks), pending.size());
        int completed = 0;
        long began = clock.getAsLong();
        for (int i = 0; i < scans && (i == 0 || clock.getAsLong() - began < budgetNanos); i++) {
            Task<K> task = ready.poll();
            if (task == null) break;
            if (pending.get(task.key) != task) continue;
            if (tick < task.retryTick) {
                ready.add(task);
                continue;
            }
            try {
                if (valid.test(task.key.value)) task.sync.run();
                pending.remove(task.key, task);
                completed++;
            } catch (RuntimeException | LinkageError failure) {
                // Preserve LDLib's per-holder error isolation, with bounded retries and explicit logs.
                // No dirty flags, node NBT or ledger data are cleared by this bridge.
                // An earlier field may already have cleared its incremental dirty flag before
                // a later field threw. Re-export every field on retries rather than losing it.
                task.sync = task.fullSync;
                task.retryTick = tick + RETRY_TICKS;
                ready.add(task);
                if (!task.reported || tick - task.lastReportTick >= ERROR_LOG_TICKS) {
                    task.reported = true;
                    task.lastReportTick = tick;
                    reportFailure.accept(task.key.value, failure);
                }
            }
        }
        return completed;
    }

    int pendingCount() { return pending.size(); }

    void clear() {
        pending.clear();
        ready.clear();
    }

    private static final class Task<K> {
        final IdentityKey<K> key;
        Runnable sync;
        final Runnable fullSync;
        long retryTick = Long.MIN_VALUE;
        long lastReportTick;
        boolean reported;
        Task(IdentityKey<K> key, Runnable sync, Runnable fullSync) {
            this.key = key; this.sync = sync; this.fullSync = fullSync;
        }
    }

    private static final class IdentityKey<K> {
        final K value;
        IdentityKey(K value) { this.value = value; }
        @Override public int hashCode() { return System.identityHashCode(value); }
        @Override public boolean equals(Object other) {
            return other instanceof IdentityKey<?> key && value == key.value;
        }
    }
}
