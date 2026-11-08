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
    private long calls, maximumCallNanos, maximumSliceNanos;
    private RuntimeException failure;

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
        checkOwner();
        if (nanos <= 0 || maxSteps <= 0 || batchLimit <= 0 || terminal()) return;
        long start = clock.getAsLong();
        try {
            for (int i = 0; i < maxSteps && !terminal() && clock.getAsLong() - start < nanos; i++) {
                step(batchLimit);
            }
            // Pending entries are at most batchLimit. Flush on the next slice, not past this budget.
        } catch (RuntimeException error) {
            failure = error;
            phase = Phase.FAILED;
            // A sink can throw after a partial commit. Never automatically replay this batch.
        } finally { maximumSliceNanos = Math.max(maximumSliceNanos, clock.getAsLong() - start); }
    }

    private void step(int limit) {
        if (phase == Phase.COMMIT) {
            call(sink::publish); phase = Phase.DONE; return;
        }
        if (batch.size() >= limit || !iterator.hasNext() && !batch.isEmpty()) { flush(); return; }
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

    private void flush() {
        List<V> entries = List.copyOf(batch);
        switch (phase) {
            case HIDE -> { call(() -> sink.hide(entries)); for (V e : entries) visible.remove(key.apply(e)); hidden += entries.size(); }
            case ADD -> { call(() -> sink.add(entries)); for (V e : entries) { K id = key.apply(e); known.put(id, e); visible.add(id); } added += entries.size(); }
            case UNHIDE -> { call(() -> sink.unhide(entries)); for (V e : entries) visible.add(key.apply(e)); unhidden += entries.size(); }
            default -> throw new IllegalStateException("Cannot flush " + phase);
        }
        batch.clear();
    }
    private void call(Runnable action) {
        long start = clock.getAsLong();
        try { action.run(); } finally { calls++; maximumCallNanos = Math.max(maximumCallNanos, clock.getAsLong() - start); }
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
    public RuntimeException failure() { return failure; }
}
