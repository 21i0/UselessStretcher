package com.sorrowmist.useless.stretcher.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Queue regressions also run with plain Java, without substituting any game or AE2 classes. */
public final class Mbd2SyncQueueChecks {
    private Mbd2SyncQueueChecks() { }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("MBD2 SYNC QUEUE CHECKS PASSED");
    }

    public static void run() throws Exception {
        var queue = new CoalescedMainThreadSyncQueue<Object>();
        Object holder = new Object();
        Thread main = Thread.currentThread();
        var calledFrom = new AtomicReference<Thread>();
        var count = new AtomicInteger();
        var latestState = new AtomicInteger();
        var exportedState = new AtomicInteger();
        var producerFailure = new AtomicReference<Throwable>();
        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5_000; i++) {
                    latestState.set(i);
                    queue.submit(holder, () -> {
                        calledFrom.set(Thread.currentThread());
                        count.incrementAndGet();
                        exportedState.set(latestState.get());
                    });
                }
            } catch (Throwable failure) { producerFailure.set(failure); }
        }, "MBD2 sync regression producer");
        producer.start();
        producer.join(5_000);
        check(!producer.isAlive() && producerFailure.get() == null, "producer completed");
        check(queue.pendingCount() == 1 && count.get() == 0, "worker only requests one pending sync");
        drain(queue, 1, 128, 2_000_000, () -> 0L, value -> true, (value, failure) -> {
            throw new AssertionError("unexpected sync failure", failure);
        });
        check(calledFrom.get() == main && count.get() == 1 && exportedState.get() == 5_000,
                "main thread exports current state, not a stale worker snapshot");
        check(queue.pendingCount() == 0, "successful sync removes pending task");

        List<String> order = new ArrayList<>();
        var attempts = new AtomicInteger();
        var reports = new AtomicInteger();
        Object broken = new Object(), healthy = new Object();
        queue.submit(broken, () -> {
            order.add("broken");
            if (attempts.incrementAndGet() < 3) throw new IllegalStateException("injected serialization failure");
        });
        queue.submit(healthy, () -> order.add("healthy"));
        drain(queue, 10, 128, 2_000_000, () -> 0L, value -> true, (value, failure) -> reports.incrementAndGet());
        check(order.equals(List.of("broken", "healthy")) && queue.pendingCount() == 1,
                "failed sync remains pending and does not starve the next machine");
        check(!queue.submit(broken, () -> { throw new AssertionError("must coalesce failed task"); }),
                "repeated worker periods coalesce while the machine is backing off");
        drain(queue, 29, 128, 2_000_000, () -> 0L, value -> true, (value, failure) -> reports.incrementAndGet());
        check(attempts.get() == 1, "failed work cannot retry before 20 ticks");
        drain(queue, 30, 128, 2_000_000, () -> 0L, value -> true, (value, failure) -> reports.incrementAndGet());
        check(attempts.get() == 2 && reports.get() == 1 && queue.pendingCount() == 1,
                "failure retry is retained and logging is rate limited");
        drain(queue, 50, 128, 2_000_000, () -> 0L, value -> true, (value, failure) -> reports.incrementAndGet());
        check(attempts.get() == 3 && queue.pendingCount() == 0, "retained dirty request eventually succeeds");

        var incrementalDirty = new java.util.concurrent.atomic.AtomicBoolean(true);
        var completeExport = new AtomicInteger();
        queue.submit(new Object(), () -> {
            incrementalDirty.set(false); // Model the first LDLib field clearing its flag.
            throw new IllegalStateException("later field failed after first flag cleared");
        }, () -> {
            check(!incrementalDirty.get(), "failure really cleared the earlier incremental flag");
            completeExport.set(42); // Forced full sync exports that field regardless of its flag.
        });
        drain(queue, 51, 128, 2_000_000, () -> 0L, value -> true, (value, failure) -> reports.incrementAndGet());
        drain(queue, 71, 128, 2_000_000, () -> 0L, value -> true, (value, failure) -> reports.incrementAndGet());
        check(completeExport.get() == 42 && queue.pendingCount() == 0,
                "partial incremental serialization failure retries a complete snapshot");

        Object removed = new Object();
        queue.submit(removed, () -> { throw new AssertionError("removed holder must not sync"); });
        drain(queue, 60, 128, 2_000_000, () -> 0L, value -> false, (value, failure) -> {
            throw new AssertionError(failure);
        });
        check(queue.pendingCount() == 0, "removed machine does not retain queue references");

        // Equal user-defined holder objects still represent different machines.
        queue.submit(new String("same"), () -> order.add("first"));
        queue.submit(new String("same"), () -> order.add("second"));
        check(queue.pendingCount() == 2, "queue keys use object identity");
        drain(queue, 61, 1, 2_000_000, () -> 0L, value -> true, (value, failure) -> {
            throw new AssertionError(failure);
        });
        check(queue.pendingCount() == 1, "per-tick task limit bounds server work");
        queue.clear();

        var clock = new java.util.concurrent.atomic.AtomicLong();
        queue.submit(new Object(), () -> clock.set(2_000_000));
        queue.submit(new Object(), () -> { throw new AssertionError("time budget exhausted"); });
        drain(queue, 62, 128, 2_000_000, clock::get, value -> true, (value, failure) -> {
            throw new AssertionError(failure);
        });
        check(queue.pendingCount() == 1, "elapsed time budget leaves remaining work pending");
        queue.clear();
        check(queue.pendingCount() == 0, "server stop clears references");
    }

    private static void drain(CoalescedMainThreadSyncQueue<Object> queue, long tick, int limit, long nanos,
                              java.util.function.LongSupplier clock, java.util.function.Predicate<Object> valid,
                              java.util.function.BiConsumer<Object, Throwable> failed) {
        queue.drain(tick, limit, nanos, clock, valid, failed);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("MBD2 sync queue check failed: " + message);
    }
}
