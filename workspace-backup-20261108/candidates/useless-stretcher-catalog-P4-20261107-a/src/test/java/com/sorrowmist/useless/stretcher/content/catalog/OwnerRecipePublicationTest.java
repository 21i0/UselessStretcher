package com.sorrowmist.useless.stretcher.content.catalog;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OwnerRecipePublicationTest {
    private record Entry(String key, String payload) {}
    private record Event(String operation, List<String> keys) {}

    @Test void preservesInputOrderFirstDuplicateAndHardBatchLimitAcrossSlices() {
        var fixture = new Fixture();
        List<Entry> input = List.of(entry("c"), entry("a"), new Entry("c", "duplicate"), entry("b"), entry("d"), entry("e"));
        var publication = fixture.publication(input);
        drain(publication, 2);

        assertEquals(List.of("c", "a", "b", "d", "e"), fixture.addedKeys());
        assertEquals(List.of("c", "a", "b", "d", "e"), new ArrayList<>(fixture.known.keySet()));
        assertEquals(entry("c"), fixture.known.get("c"));
        assertTrue(fixture.events.stream().filter(e -> !e.operation.equals("publish")).allMatch(e -> e.keys.size() <= 2));
        assertEquals(6, publication.planned());
        assertEquals(5, publication.added());
        assertEquals(1, fixture.commits);
        assertTrue(publication.ready());
        assertEquals(OwnerRecipePublication.Phase.DONE, publication.phase());
    }

    @Test void successfulBatchLedgerSurvivesCancellationAndRebuildDoesNotAddItTwice() {
        var fixture = new Fixture();
        List<Entry> desired = entries("a", "b", "c", "d", "e");
        var stale = fixture.publication(desired);
        stepUntil(stale, () -> stale.added() == 2, 2);
        assertEquals(Set.of("a", "b"), fixture.known.keySet());
        stale.cancel();
        int calls = fixture.events.size();
        stale.advance(1000, 1000, 2);
        assertEquals(calls, fixture.events.size());
        assertEquals(OwnerRecipePublication.Phase.CANCELLED, stale.phase());
        assertFalse(stale.ready());

        var replacement = fixture.publication(desired);
        drain(replacement, 2);
        assertEquals(List.of("a", "b", "c", "d", "e"), fixture.addedKeys());
        assertEquals(5, fixture.registered.size());
        assertEquals(Set.of("a", "b", "c", "d", "e"), fixture.visible);
        assertEquals(1, fixture.commits, "cancelled revision never commits readiness");
    }

    @Test void rebuildHidesStaleEntriesThenRestoresKnownEntriesWithoutAddingAgain() {
        var fixture = new Fixture();
        drain(fixture.publication(entries("a", "b", "c")), 2);
        var replacement = fixture.publication(entries("b", "d"));
        drain(replacement, 1);
        assertEquals(Set.of("b", "d"), fixture.visible);
        assertEquals(Set.of("a", "b", "c", "d"), fixture.known.keySet());
        assertEquals(2, replacement.hidden());
        assertEquals(1, replacement.added());

        var restored = fixture.publication(entries("a", "b", "d"));
        drain(restored, 2);
        assertEquals(0, restored.added());
        assertEquals(1, restored.unhidden());
        assertEquals(Set.of("a", "b", "d"), fixture.visible);
        assertEquals(fixture.visible, fixture.externalVisible);
        assertEquals(List.of("a", "b", "c", "d"), fixture.addedKeys());
        assertEquals(3, fixture.commits);
    }

    @Test void cancellationAfterConfirmedHideCanBeRevertedByTheNextRevision() {
        var fixture = new Fixture();
        drain(fixture.publication(entries("a", "b", "c")), 1);
        var stale = fixture.publication(entries("b", "d"));
        stepUntil(stale, () -> stale.hidden() == 1, 1);
        stale.cancel();
        assertEquals(Set.of("b", "c"), fixture.visible);
        assertEquals(fixture.visible, fixture.externalVisible);
        int commits = fixture.commits;

        var current = fixture.publication(entries("a", "b", "c"));
        drain(current, 1);
        assertEquals(0, current.added());
        assertEquals(1, current.unhidden());
        assertEquals(Set.of("a", "b", "c"), fixture.visible);
        assertEquals(List.of("a", "b", "c"), fixture.addedKeys());
        assertEquals(commits + 1, fixture.commits);
    }

    @Test void partiallyAcceptedThrowingBatchStopsPermanentlyWithoutAutomaticReplay() {
        var fixture = new Fixture();
        fixture.throwAfterFirstAdd = true;
        var publication = fixture.publication(entries("a", "b", "c"));
        drain(publication, 2);
        assertEquals(OwnerRecipePublication.Phase.FAILED, publication.phase());
        assertFalse(publication.ready());
        assertInstanceOf(IllegalStateException.class, publication.failure());
        assertEquals(Set.of("a"), fixture.registered.keySet(), "sink demonstrably committed before throwing");
        assertTrue(fixture.known.isEmpty(), "the accepted extent is unknown, not falsely recorded as the full batch");
        assertEquals(0, publication.added());
        int calls = fixture.events.size();
        fixture.throwAfterFirstAdd = false;
        publication.advance(Long.MAX_VALUE, 10000, 2);
        publication.cancel();
        assertEquals(calls, fixture.events.size());
        assertEquals(0, fixture.commits);
        assertEquals(OwnerRecipePublication.Phase.FAILED, publication.phase());
    }

    @Test void singleExternalCallMayOverrunBudgetButTheNextCallWaitsForAnotherSlice() {
        var fixture = new Fixture();
        fixture.callNanos = 10;
        var publication = fixture.publication(entries("a", "b", "c", "d"));
        publication.advance(5, 1000, 2);
        assertEquals(1, fixture.events.size());
        assertEquals(new Event("add", List.of("a", "b")), fixture.events.getFirst());
        assertEquals(2, publication.added());
        assertEquals(1, publication.calls());
        assertEquals(10, publication.maximumCallNanos());
        assertEquals(10, publication.maximumSliceNanos());
        assertFalse(publication.ready());
        drain(publication, 2);
        assertEquals(List.of("a", "b", "c", "d"), fixture.addedKeys());
    }

    @Test void nonpositiveBudgetsDoNoWorkAndStepLimitIsHonored() {
        var fixture = new Fixture();
        var publication = fixture.publication(entries("a", "b", "c"));
        publication.advance(0, 100, 2);
        publication.advance(10, 0, 2);
        publication.advance(10, 100, 0);
        assertEquals(0, publication.planned());
        assertTrue(fixture.events.isEmpty());
        publication.advance(10, 1, 2);
        assertEquals(1, publication.planned());
        assertTrue(fixture.events.isEmpty());
        publication.advance(10, 1, 2);
        assertEquals(2, publication.planned());
    }

    @Test void offOwnerAdvanceAndCancellationAreRejectedBeforeTouchingTheSink() throws Exception {
        var fixture = new Fixture();
        var publication = fixture.publication(entries("a"));
        AtomicReference<Throwable> advance = new AtomicReference<>(), cancel = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { publication.advance(10, 100, 1); } catch (Throwable failure) { advance.set(failure); }
            try { publication.cancel(); } catch (Throwable failure) { cancel.set(failure); }
        });
        worker.start(); worker.join(2000);
        assertFalse(worker.isAlive());
        assertInstanceOf(IllegalStateException.class, advance.get());
        assertInstanceOf(IllegalStateException.class, cancel.get());
        assertEquals(0, publication.planned());
        assertTrue(fixture.events.isEmpty());
        drain(publication, 1);
        assertEquals(Thread.currentThread(), fixture.sinkOwner);
    }

    @Test void emptyValidCatalogCommitsReadinessOnlyAfterFinalPublishAndHidesOldEntries() {
        var fixture = new Fixture();
        drain(fixture.publication(entries("old")), 1);
        var publication = fixture.publication(List.of());
        fixture.onCommit = () -> assertFalse(publication.ready(), "ready cannot precede successful publish return");
        stepUntil(publication, () -> publication.phase() == OwnerRecipePublication.Phase.COMMIT, 1);
        assertFalse(publication.ready());
        assertTrue(fixture.visible.isEmpty());
        int beforeCommit = fixture.commits;
        publication.advance(10, 1, 1);
        assertTrue(publication.ready());
        assertEquals(beforeCommit + 1, fixture.commits);
        assertEquals(0, publication.added());
        assertEquals(1, publication.hidden());
        assertEquals(0, publication.total());
        publication.advance(10, 100, 1);
        assertEquals(beforeCommit + 1, fixture.commits);
    }

    @Test void throwingFinalPublishNeverExposesReadyAndIsNotRetried() {
        var fixture = new Fixture();
        fixture.onCommit = () -> { throw new IllegalStateException("publish failed"); };
        var publication = fixture.publication(List.of());
        drain(publication, 1);
        assertEquals(OwnerRecipePublication.Phase.FAILED, publication.phase());
        assertFalse(publication.ready());
        int calls = fixture.events.size();
        publication.advance(10, 100, 1);
        assertEquals(calls, fixture.events.size());
    }

    private static void drain(OwnerRecipePublication<?, ?> publication, int batch) {
        stepUntil(publication, publication::terminal, batch);
    }

    private static void stepUntil(OwnerRecipePublication<?, ?> publication, java.util.function.BooleanSupplier done, int batch) {
        for (int step = 0; step < 10000 && !done.getAsBoolean(); step++) {
            publication.advance(1000, 1, batch);
        }
        assertTrue(done.getAsBoolean(), "publication failed to reach the expected phase");
    }

    private static Entry entry(String key) { return new Entry(key, "payload-" + key); }
    private static List<Entry> entries(String... keys) { return java.util.Arrays.stream(keys).map(OwnerRecipePublicationTest::entry).toList(); }

    private static final class Fixture implements OwnerRecipePublication.Sink<Entry> {
        final Map<String, Entry> known = new LinkedHashMap<>(), registered = new LinkedHashMap<>();
        final Set<String> visible = new LinkedHashSet<>(), externalVisible = new LinkedHashSet<>();
        final List<Event> events = new ArrayList<>();
        final AtomicLong clock = new AtomicLong();
        Thread sinkOwner;
        int commits;
        long callNanos;
        boolean throwAfterFirstAdd;
        Runnable onCommit = () -> {};

        OwnerRecipePublication<String, Entry> publication(List<Entry> entries) {
            return new OwnerRecipePublication<>(entries, known, visible, Entry::key, this, clock::get);
        }

        @Override public void add(List<Entry> entries) {
            record("add", entries);
            for (Entry entry : entries) {
                assertNull(registered.putIfAbsent(entry.key(), entry), "duplicate external registration: " + entry.key());
                externalVisible.add(entry.key());
                if (throwAfterFirstAdd) throw new IllegalStateException("partially accepted external batch");
            }
        }

        @Override public void hide(List<Entry> entries) {
            record("hide", entries);
            entries.forEach(entry -> assertTrue(externalVisible.remove(entry.key())));
        }

        @Override public void unhide(List<Entry> entries) {
            record("unhide", entries);
            entries.forEach(entry -> {
                assertTrue(registered.containsKey(entry.key()));
                assertTrue(externalVisible.add(entry.key()));
            });
        }

        @Override public void publish() {
            record("publish", List.of());
            onCommit.run();
            commits++;
        }

        void record(String operation, List<Entry> entries) {
            if (sinkOwner == null) sinkOwner = Thread.currentThread();
            assertEquals(sinkOwner, Thread.currentThread());
            events.add(new Event(operation, entries.stream().map(Entry::key).toList()));
            clock.addAndGet(callNanos);
        }

        List<String> addedKeys() { return events.stream().filter(event -> event.operation.equals("add")).flatMap(event -> event.keys.stream()).toList(); }
    }
}
