package com.sorrowmist.useless.stretcher.content.catalog;

import java.util.*;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Owner-thread, resumable publication. The sink must keep the category unavailable until publish. */
public final class OwnerRecipePublication<K, V> {
    public interface Sink<V> {
        void add(List<V> entries);
        void hide(List<V> entries);
        void unhide(List<V> entries);
        void publish();
    }
    public enum Phase { PLAN, HIDE, ADD, UNHIDE, COMMIT, DONE, CANCELLED, FAILED }
    private final Thread owner = Thread.currentThread();
    private final List<V> input;
    private final Map<K, V> known;
    private final Set<K> visible;
    private final Function<V, K> key;
    private final Sink<V> sink;
    private final LongSupplier clock;
    private final LinkedHashMap<K, V> desired = new LinkedHashMap<>();
    private final List<V> batch = new ArrayList<>();
    private Iterator<V> iterator;
    private Phase phase = Phase.PLAN;
    private int planned, added, hidden, unhidden;
    private long calls, maximumCallNanos, maximumSliceNanos, activeNanos;
    private Phase maximumCallPhase;
    private int maximumCallSize;
    private final EnumMap<Phase, MutableCallStatistics> statistics = new EnumMap<>(Phase.class);
    private RuntimeException failure;

    public record CallStatistics(long calls, long entries, long activeNanos, long maximumNanos, int maximumSize) {}

    public OwnerRecipePublication(List<V> input, Map<K, V> known, Set<K> visible,
                                  Function<V, K> key, Sink<V> sink) {
        this(input, known, visible, key, sink, System::nanoTime);
    }
    OwnerRecipePublication(List<V> input, Map<K, V> known, Set<K> visible,
                           Function<V, K> key, Sink<V> sink, LongSupplier clock) {
        this.input = input; this.known = known; this.visible = visible;
        this.key = key; this.sink = sink; this.clock = clock;
        iterator = input.iterator();
    }

    /** Budgets are cooperative; an individual JEI call cannot be preempted. */
    public void advance(long nanos, int maxSteps, int batchLimit) {
        advance(nanos, maxSteps, batchLimit, null);
    }

    /** Optional adaptive native-call limits; the original three-argument overload retains fixed batching. */
    public void advance(long nanos, int maxSteps, int hardLimit, PublicationCallBudget budget) {
        checkOwner();
        if (nanos <= 0 || maxSteps <= 0 || hardLimit <= 0 || terminal()) return;
        long start = clock.getAsLong();
        try {
            for (int i = 0; i < maxSteps && !terminal() && clock.getAsLong() - start < nanos; i++) {
                long remaining = nanos - (clock.getAsLong() - start);
                if (remaining <= 0) break;
                int limit = budget == null ? hardLimit : budget.limit(phase, hardLimit, remaining);
                if (limit <= 0) break;
                step(limit, budget);
            }
            // Pending entries wait for another slice; a lowered limit also applies to previously queued entries.
        } catch (RuntimeException error) {
            failure = error;
            phase = Phase.FAILED;
            // A sink can throw after a partial commit. Never automatically replay this batch.
        } finally {
            long elapsed = Math.max(0L, clock.getAsLong() - start);
            maximumSliceNanos = Math.max(maximumSliceNanos, elapsed);
            activeNanos += elapsed;
        }
    }

    private void step(int limit, PublicationCallBudget budget) {
        if (phase == Phase.COMMIT) {
            call(Phase.COMMIT, 0, sink::publish, budget);
            if (phase == Phase.COMMIT) phase = Phase.DONE;
            return;
        }
        if (batch.size() >= limit || !iterator.hasNext() && !batch.isEmpty()) { flush(limit, budget); return; }
        if (!iterator.hasNext()) {
            phase = switch (phase) {
                case PLAN -> Phase.HIDE;
                case HIDE -> Phase.ADD;
                case ADD -> Phase.UNHIDE;
                case UNHIDE -> Phase.COMMIT;
                default -> throw new IllegalStateException("Invalid publication phase " + phase);
            };
            iterator = phase == Phase.HIDE ? known.values().iterator() : desired.values().iterator();
            return;
        }
        V entry = iterator.next();
        K id = key.apply(entry);
        switch (phase) {
            case PLAN -> { desired.putIfAbsent(id, entry); planned++; }
            case HIDE -> { if (visible.contains(id) && !desired.containsKey(id)) batch.add(entry); }
            case ADD -> { if (!known.containsKey(id)) batch.add(entry); }
            case UNHIDE -> { if (!visible.contains(id)) batch.add(known.get(id)); }
            default -> throw new IllegalStateException("Invalid publication phase " + phase);
        }
    }

    private void flush(int limit, PublicationCallBudget budget) {
        int size = Math.min(limit, batch.size());
        List<V> entries = List.copyOf(batch.subList(0, size));
        // Isolate this exact prefix before external code can cancel and clear pending entries.
        batch.subList(0, size).clear();
        Phase calledPhase = phase;
        switch (calledPhase) {
            case HIDE -> { call(calledPhase, size, () -> sink.hide(entries), budget); for (V e : entries) visible.remove(key.apply(e)); hidden += entries.size(); }
            case ADD -> { call(calledPhase, size, () -> sink.add(entries), budget); for (V e : entries) { K id = key.apply(e); known.put(id, e); visible.add(id); } added += entries.size(); }
            case UNHIDE -> { call(calledPhase, size, () -> sink.unhide(entries), budget); for (V e : entries) visible.add(key.apply(e)); unhidden += entries.size(); }
            default -> throw new IllegalStateException("Cannot flush " + phase);
        }
    }
    private void call(Phase calledPhase, int size, Runnable action, PublicationCallBudget budget) {
        long start = clock.getAsLong();
        try { action.run(); } finally {
            long elapsed = Math.max(0L, clock.getAsLong() - start);
            calls++;
            if (maximumCallPhase == null || elapsed > maximumCallNanos) {
                maximumCallNanos = elapsed;
                maximumCallPhase = calledPhase;
                maximumCallSize = size;
            }
            MutableCallStatistics measured = statistics.computeIfAbsent(calledPhase, ignored -> new MutableCallStatistics());
            measured.calls++;
            measured.entries += size;
            measured.activeNanos += elapsed;
            measured.maximumNanos = Math.max(measured.maximumNanos, elapsed);
            measured.maximumSize = Math.max(measured.maximumSize, size);
            if (budget != null) budget.record(calledPhase, size, elapsed);
        }
    }
    public void cancel() { checkOwner(); if (!terminal()) { phase = Phase.CANCELLED; batch.clear(); } }
    private void checkOwner() { if (Thread.currentThread() != owner) throw new IllegalStateException("Publication advanced off owner thread"); }
    public boolean terminal() { return phase == Phase.DONE || phase == Phase.CANCELLED || phase == Phase.FAILED; }
    public boolean ready() { return phase == Phase.DONE; }
    public Phase phase() { return phase; }
    public int planned() { return planned; }
    public int total() { return input.size(); }
    public int added() { return added; }
    public int hidden() { return hidden; }
    public int unhidden() { return unhidden; }
    public long calls() { return calls; }
    public long maximumCallNanos() { return maximumCallNanos; }
    public long maximumSliceNanos() { return maximumSliceNanos; }
    /** Time spent inside advance, excluding time between frames. Includes failed and cancelled slices. */
    public long activeNanos() { return activeNanos; }
    public Phase maximumCallPhase() { return maximumCallPhase; }
    public int maximumCallSize() { return maximumCallSize; }
    public CallStatistics statistics(Phase measuredPhase) {
        MutableCallStatistics measured = statistics.get(measuredPhase);
        return measured == null ? new CallStatistics(0, 0, 0, 0, 0)
                : new CallStatistics(measured.calls, measured.entries, measured.activeNanos, measured.maximumNanos, measured.maximumSize);
    }
    public RuntimeException failure() { return failure; }

    private static final class MutableCallStatistics {
        long calls, entries, activeNanos, maximumNanos;
        int maximumSize;
    }
}
