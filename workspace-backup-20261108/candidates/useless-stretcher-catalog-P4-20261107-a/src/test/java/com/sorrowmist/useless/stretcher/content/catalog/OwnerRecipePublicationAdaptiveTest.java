package com.sorrowmist.useless.stretcher.content.catalog;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.ToLongFunction;
import java.util.stream.IntStream;

import static com.sorrowmist.useless.stretcher.content.catalog.OwnerRecipePublication.Phase.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure owner-thread publication tests. Native call costs are a deterministic injected clock, not wall time. */
class OwnerRecipePublicationAdaptiveTest {
    private record Entry(int id) {}

    @Test void adaptiveCallsStartAtOneGrowAndImmediatelyShrinkAfterASlowBatch() {
        var fixture = new Fixture();
        fixture.costs.put(ADD, values -> values.getFirst().id() == 4 ? 2_000_000L : values.size() * 10_000L);
        var budget = new PublicationCallBudget();
        var publication = fixture.publication(entries(40));
        fixture.drain(publication, 16, budget);

        List<List<Entry>> calls = fixture.batches.get(ADD);
        assertEquals(List.of(1, 1, 1, 1, 2, 1), calls.stream().limit(6).map(List::size).toList());
        assertEquals(entries(40), new ArrayList<>(fixture.external.values()));
        assertEquals(40, publication.added());
        assertTrue(publication.statistics(ADD).maximumSize() <= 16);
        assertEquals(ADD, publication.maximumCallPhase());
        assertEquals(2, publication.maximumCallSize());
        assertEquals(2_000_000, publication.maximumCallNanos());
        assertTrue(publication.activeNanos() >= publication.statistics(ADD).activeNanos());
    }

    @Test void pendingSixteenCanBeSplitIntoSingletonsAfterTheAdaptiveLimitFalls() {
        var fixture = new Fixture();
        fixture.costs.put(ADD, values -> 1_000_000L);
        var publication = fixture.publication(entries(20));
        fixture.until(() -> publication.phase() == ADD, () -> publication.advance(4_000_000, 1, 16));
        publication.advance(4_000_000, 16, 16); // Queue sixteen, but do not flush yet.
        assertTrue(fixture.batches.get(ADD).isEmpty());

        var budget = new PublicationCallBudget();
        for (int i = 1; i <= 16; i++) {
            publication.advance(4_000_000, 1, 16, budget);
            assertEquals(i, publication.added());
            assertEquals(1, fixture.batches.get(ADD).getLast().size());
        }
        fixture.drain(publication, 16, budget);
        assertEquals(entries(20), new ArrayList<>(fixture.external.values()));
        assertEquals(20, publication.added());
    }

    @Test void normalCancellationInsideASplitPrefixConfirmsThatPrefixWithoutCorruptingPending() {
        var fixture = new Fixture();
        List<Entry> originals = entries(20);
        var publication = fixture.publication(originals);
        fixture.until(() -> publication.phase() == ADD, () -> publication.advance(4_000_000, 1, 16));
        publication.advance(4_000_000, 16, 16);
        fixture.onCall = (phase, batch) -> { if (phase == ADD) publication.cancel(); };
        publication.advance(4_000_000, 1000, 16, new PublicationCallBudget());

        assertEquals(CANCELLED, publication.phase());
        assertNull(publication.failure());
        assertEquals(1, publication.added());
        assertSame(originals.getFirst(), fixture.known.get(0));
        fixture.onCall = (phase, batch) -> {};
        var replacement = fixture.publication(originals);
        fixture.drain(replacement, 16, new PublicationCallBudget());
        assertEquals(originals, new ArrayList<>(fixture.external.values()));
        assertEquals(19, replacement.added());
    }

    @Test void throwingAfterReentrantCancellationDoesNotCommitOrReplayAnUnknownSplitPrefix() {
        var fixture = new Fixture();
        var publication = fixture.publication(entries(20));
        fixture.until(() -> publication.phase() == ADD, () -> publication.advance(4_000_000, 1, 16));
        publication.advance(4_000_000, 16, 16);
        fixture.onCall = (phase, batch) -> {
            if (phase == ADD) { publication.cancel(); throw new IllegalStateException("partial prefix accepted"); }
        };
        var budget = new PublicationCallBudget();
        publication.advance(4_000_000, 1000, 16, budget);

        assertEquals(FAILED, publication.phase());
        assertInstanceOf(IllegalStateException.class, publication.failure());
        assertEquals(1, fixture.external.size());
        assertTrue(fixture.known.isEmpty());
        assertEquals(0, publication.added());
        assertEquals(1, publication.statistics(ADD).calls());
        assertEquals(1, budget.snapshot(ADD).samples());
        fixture.onCall = (phase, batch) -> {};
        publication.advance(Long.MAX_VALUE, 10000, 16, budget);
        publication.cancel();
        assertEquals(1, fixture.batches.get(ADD).size());
    }

    @Test void hideAndUnhideBudgetsAreIndependentAndUseTheOriginalCanonicalEntries() {
        var fixture = new Fixture();
        List<Entry> originals = entries(32);
        var budget = new PublicationCallBudget();
        fixture.drain(fixture.publication(originals), 16, budget);
        var hidden = fixture.publication(List.of());
        fixture.drain(hidden, 16, budget);
        assertEquals(1, fixture.batches.get(HIDE).getFirst().size());
        assertEquals(32, hidden.hidden());
        assertTrue(fixture.visible.isEmpty());

        // Same keys with different objects must reuse the successful native/canonical objects.
        var restored = fixture.publication(entries(32));
        fixture.drain(restored, 16, budget);
        assertEquals(1, fixture.batches.get(UNHIDE).getFirst().size());
        assertEquals(0, restored.added());
        assertEquals(32, restored.unhidden());
        fixture.batches.get(UNHIDE).forEach(batch -> batch.forEach(entry -> assertSame(originals.get(entry.id()), entry)));
        assertEquals(fixture.visible, fixture.externalVisible);
        assertEquals(0, budget.snapshot(COMMIT).samples());
    }

    @Test void confirmedSingletonHideSurvivesCancellationAndCanBeUnhiddenByTheNextRevision() {
        var fixture = new Fixture();
        List<Entry> originals = entries(20);
        fixture.drain(fixture.publication(originals), 16, null);
        var hidden = fixture.publication(List.of());
        fixture.until(() -> hidden.phase() == HIDE, () -> hidden.advance(4_000_000, 1, 16));
        hidden.advance(4_000_000, 16, 16);
        fixture.onCall = (phase, batch) -> { if (phase == HIDE) hidden.cancel(); };
        hidden.advance(4_000_000, 1000, 16, new PublicationCallBudget());
        assertEquals(CANCELLED, hidden.phase());
        assertEquals(1, hidden.hidden());
        assertEquals(19, fixture.visible.size());
        fixture.onCall = (phase, batch) -> {};
        var restored = fixture.publication(originals);
        fixture.drain(restored, 16, new PublicationCallBudget());
        assertEquals(1, restored.unhidden());
        assertEquals(0, restored.added());
        assertEquals(fixture.visible, fixture.externalVisible);
    }

    @Test void expensiveSingletonMayOverrunButAnotherNativeCallWaitsUntilTheNextSlice() {
        var fixture = new Fixture();
        fixture.costs.put(ADD, values -> 867_000_000L);
        var publication = fixture.publication(entries(3));
        var budget = new PublicationCallBudget();
        publication.advance(4_000_000, 10000, 16, budget);
        assertEquals(1, fixture.batches.get(ADD).size());
        assertEquals(1, fixture.batches.get(ADD).getFirst().size());
        assertEquals(867_000_000, publication.maximumCallNanos());
        assertEquals(867_000_000, publication.maximumSliceNanos());
        assertEquals(1, publication.added());
        assertFalse(publication.ready());
        fixture.drain(publication, 16, budget);
        assertEquals(entries(3), new ArrayList<>(fixture.external.values()));
    }

    @Test void phaseAndTotalMeasurementsIncludeThrowingCallsButExcludeIdleTimeBetweenSlices() {
        var fixture = new Fixture();
        fixture.costs.put(ADD, values -> 75);
        fixture.costs.put(COMMIT, values -> 9);
        var publication = fixture.publication(entries(3));
        var budget = new PublicationCallBudget();
        publication.advance(4_000_000, 1, 16, budget);
        fixture.clock.addAndGet(1_000_000_000L); // Not publication-active time.
        fixture.drain(publication, 16, budget);
        assertEquals(225, publication.statistics(ADD).activeNanos());
        assertEquals(9, publication.statistics(COMMIT).activeNanos());
        assertEquals(234, publication.activeNanos());
        assertEquals(3, publication.statistics(ADD).calls());
        assertEquals(3, publication.statistics(ADD).entries());
        assertEquals(1, publication.statistics(COMMIT).calls());
        assertEquals(0, publication.statistics(HIDE).calls());
        assertEquals(3, budget.snapshot(ADD).samples());
    }

    @Test void offOwnerAdaptiveAdvanceIsRejectedBeforeBudgetOrSinkWork() throws Exception {
        var fixture = new Fixture();
        var publication = fixture.publication(entries(3));
        var budget = new PublicationCallBudget();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { publication.advance(4_000_000, 10000, 16, budget); } catch (Throwable failure) { error.set(failure); }
        });
        worker.start(); worker.join(2000);
        assertFalse(worker.isAlive());
        assertInstanceOf(IllegalStateException.class, error.get());
        assertEquals(0, publication.planned());
        assertTrue(fixture.batches.get(ADD).isEmpty());
        assertEquals(0, budget.snapshot(ADD).samples());
    }

    @Test void full155024EntryCatalogRetainsEveryOrderedNativeIdentityWithoutDuplicateSubmission() {
        final int count = 155024;
        var fixture = new Fixture();
        fixture.costs.put(ADD, values -> values.size() * 1_000L);
        List<Entry> originals = entries(count);
        var publication = fixture.publication(originals);
        var budget = new PublicationCallBudget();
        fixture.drain(publication, 16, budget);

        assertTrue(publication.ready());
        assertEquals(count, publication.planned());
        assertEquals(count, publication.added());
        assertEquals(count, fixture.known.size());
        assertEquals(count, fixture.external.size());
        int offset = 0;
        for (Entry entry : fixture.external.values()) {
            assertSame(originals.get(offset), entry);
            assertSame(entry, fixture.known.get(offset));
            offset++;
        }
        assertEquals(count, offset);
        assertEquals(fixture.visible, fixture.externalVisible);
        assertTrue(fixture.batches.get(ADD).stream().allMatch(batch -> batch.size() >= 1 && batch.size() <= 16));
        assertTrue(fixture.batches.get(ADD).size() < count / 8, "fast batches recover throughput rather than submitting 155024 singletons");
        assertEquals(1, fixture.commits);
        System.out.printf("P4_ADAPTIVE_FULL_CATALOG entries=%d calls=%d max_batch=%d fake_active_ns=%d%n",
                count, publication.statistics(ADD).calls(), publication.statistics(ADD).maximumSize(), publication.activeNanos());
    }

    @Test void deterministicPerEntryWorkIsSplitInsteadOfRepeatingTheFixedSixteenEntryOverrun() {
        var fixed = new Fixture();
        var adaptive = new Fixture();
        fixed.costs.put(ADD, values -> values.size() * 600_000L);
        adaptive.costs.put(ADD, values -> values.size() * 600_000L);
        List<Entry> originals = entries(160);
        var baseline = fixed.publication(originals);
        var candidate = adaptive.publication(originals);
        fixed.drain(baseline, 16, null);
        adaptive.drain(candidate, 16, new PublicationCallBudget());
        assertEquals(9_600_000, baseline.maximumCallNanos());
        assertEquals(600_000, candidate.maximumCallNanos());
        assertEquals(new ArrayList<>(fixed.external.values()), new ArrayList<>(adaptive.external.values()));
        assertEquals(baseline.statistics(ADD).activeNanos(), candidate.statistics(ADD).activeNanos());
        assertEquals(160, candidate.added());
        System.out.printf("P4_SYNTHETIC_SPLIT entries=%d fixed_max_call_ns=%d adaptive_max_call_ns=%d total_work_ns=%d%n",
                originals.size(), baseline.maximumCallNanos(), candidate.maximumCallNanos(), candidate.statistics(ADD).activeNanos());
    }

    private static List<Entry> entries(int count) { return IntStream.range(0, count).mapToObj(Entry::new).toList(); }

    private static final class Fixture implements OwnerRecipePublication.Sink<Entry> {
        final Map<Integer, Entry> known = new LinkedHashMap<>(), external = new LinkedHashMap<>();
        final Set<Integer> visible = new LinkedHashSet<>(), externalVisible = new LinkedHashSet<>();
        final EnumMap<OwnerRecipePublication.Phase, List<List<Entry>>> batches = new EnumMap<>(OwnerRecipePublication.Phase.class);
        final EnumMap<OwnerRecipePublication.Phase, ToLongFunction<List<Entry>>> costs = new EnumMap<>(OwnerRecipePublication.Phase.class);
        final AtomicLong clock = new AtomicLong();
        final Thread owner = Thread.currentThread();
        BiConsumer<OwnerRecipePublication.Phase, List<Entry>> onCall = (phase, batch) -> {};
        int commits;

        Fixture() {
            for (var phase : OwnerRecipePublication.Phase.values()) batches.put(phase, new ArrayList<>());
        }

        OwnerRecipePublication<Integer, Entry> publication(List<Entry> input) {
            return new OwnerRecipePublication<>(input, known, visible, Entry::id, this, clock::get);
        }

        @Override public void add(List<Entry> input) {
            record(ADD, input);
            for (Entry entry : input) {
                assertNull(external.putIfAbsent(entry.id(), entry), "duplicate native registration");
                assertTrue(externalVisible.add(entry.id()));
            }
            onCall.accept(ADD, input);
        }

        @Override public void hide(List<Entry> input) {
            record(HIDE, input);
            for (Entry entry : input) { assertSame(external.get(entry.id()), entry); assertTrue(externalVisible.remove(entry.id())); }
            onCall.accept(HIDE, input);
        }

        @Override public void unhide(List<Entry> input) {
            record(UNHIDE, input);
            for (Entry entry : input) { assertSame(external.get(entry.id()), entry); assertTrue(externalVisible.add(entry.id())); }
            onCall.accept(UNHIDE, input);
        }

        @Override public void publish() {
            record(COMMIT, List.of());
            onCall.accept(COMMIT, List.of());
            commits++;
        }

        void record(OwnerRecipePublication.Phase phase, List<Entry> input) {
            assertSame(owner, Thread.currentThread());
            batches.get(phase).add(List.copyOf(input));
            clock.addAndGet(costs.getOrDefault(phase, values -> 0).applyAsLong(input));
        }

        void drain(OwnerRecipePublication<?, ?> publication, int hardLimit, PublicationCallBudget budget) {
            until(publication::terminal, () -> {
                if (budget == null) publication.advance(4_000_000, 4096, hardLimit);
                else publication.advance(4_000_000, 4096, hardLimit, budget);
            });
            assertNull(publication.failure());
        }

        void until(BooleanSupplier done, Runnable step) {
            for (int i = 0; i < 100000 && !done.getAsBoolean(); i++) step.run();
            assertTrue(done.getAsBoolean(), "publication did not reach expected state");
        }
    }
}
