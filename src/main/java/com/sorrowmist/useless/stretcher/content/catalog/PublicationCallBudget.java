package com.sorrowmist.useless.stretcher.content.catalog;

import java.util.EnumMap;

/** Owner-thread estimates for native publication calls. Individual calls remain non-preemptible. */
public final class PublicationCallBudget {
    public static final long TARGET_CALL_NANOS = 1_000_000L;
    private static final long FAST_CALL_NANOS = TARGET_CALL_NANOS / 2;
    private static final int FAST_SAMPLES_TO_GROW = 4;
    private final Thread owner = Thread.currentThread();
    private final EnumMap<OwnerRecipePublication.Phase, State> states =
            new EnumMap<>(OwnerRecipePublication.Phase.class);

    public record Snapshot(int proposedBatchSize, int fastSamples, long estimatedNanosPerEntry, long samples) {}

    public PublicationCallBudget() {
        states.put(OwnerRecipePublication.Phase.ADD, new State());
        states.put(OwnerRecipePublication.Phase.HIDE, new State());
        states.put(OwnerRecipePublication.Phase.UNHIDE, new State());
    }

    /** Always permits one entry with a positive budget, so an expensive singleton cannot starve forever. */
    public int limit(OwnerRecipePublication.Phase phase, int hardLimit, long remainingNanos) {
        checkOwner();
        if (hardLimit <= 0 || remainingNanos <= 0) return 0;
        State state = states.get(phase);
        if (state == null) return hardLimit;
        int result = Math.min(hardLimit, state.proposed);
        if (state.estimatedPerEntry > 0) {
            long allowance = Math.min(TARGET_CALL_NANOS, remainingNanos);
            long estimatedLimit = Math.max(1L, allowance / state.estimatedPerEntry);
            result = (int) Math.min(result, estimatedLimit);
        }
        return Math.max(1, result);
    }

    /** Publish/COMMIT is deliberately excluded from add/hide/unhide throughput estimates. */
    public void record(OwnerRecipePublication.Phase phase, int size, long elapsedNanos) {
        checkOwner();
        State state = states.get(phase);
        if (state == null || size <= 0) return;
        long elapsed = Math.max(0L, elapsedNanos);
        long perEntry = elapsed / size + (elapsed % size == 0 ? 0 : 1);
        if (state.samples == 0 || perEntry >= state.estimatedPerEntry) state.estimatedPerEntry = perEntry;
        else state.estimatedPerEntry -= (state.estimatedPerEntry - perEntry) / 4;
        state.samples++;
        if (elapsed >= TARGET_CALL_NANOS) {
            state.proposed = 1;
            state.fastSamples = 0;
        } else if (elapsed < FAST_CALL_NANOS) {
            if (++state.fastSamples >= FAST_SAMPLES_TO_GROW) {
                // The caller supplies the hard cap; retain a bounded estimate across calls with lower caps.
                state.proposed = Math.min(16, state.proposed * 2);
                state.fastSamples = 0;
            }
        } else state.fastSamples = 0;
    }

    public Snapshot snapshot(OwnerRecipePublication.Phase phase) {
        checkOwner();
        State state = states.get(phase);
        return state == null ? new Snapshot(0, 0, 0, 0)
                : new Snapshot(state.proposed, state.fastSamples, state.estimatedPerEntry, state.samples);
    }

    private void checkOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Publication budget accessed off owner thread");
    }

    private static final class State {
        int proposed = 1, fastSamples;
        long estimatedPerEntry, samples;
    }
}
