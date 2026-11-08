package com.sorrowmist.useless.stretcher.content.catalog;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static com.sorrowmist.useless.stretcher.content.catalog.OwnerRecipePublication.Phase.*;
import static org.junit.jupiter.api.Assertions.*;

class PublicationCallBudgetTest {
    @Test void eachNativeOperationStartsAtOneAndCommitDoesNotTrainAnOperation() {
        var budget = new PublicationCallBudget();
        assertEquals(1, budget.limit(ADD, 16, 4_000_000));
        assertEquals(1, budget.limit(HIDE, 16, 4_000_000));
        assertEquals(1, budget.limit(UNHIDE, 16, 4_000_000));
        budget.record(COMMIT, 0, 100_000_000);
        assertEquals(0, budget.snapshot(ADD).samples());
        assertEquals(0, budget.snapshot(COMMIT).samples());
    }

    @Test void growthRequiresFourConsecutiveFastSamplesAtEverySize() {
        var budget = new PublicationCallBudget();
        for (int i = 0; i < 3; i++) budget.record(ADD, 1, 100_000);
        assertEquals(1, budget.limit(ADD, 16, 4_000_000));
        budget.record(ADD, 1, 100_000);
        assertEquals(2, budget.limit(ADD, 16, 4_000_000));
        for (int i = 0; i < 3; i++) budget.record(ADD, 2, 100_000);
        assertEquals(2, budget.limit(ADD, 16, 4_000_000));
        budget.record(ADD, 2, 100_000);
        assertEquals(4, budget.limit(ADD, 16, 4_000_000));
        assertEquals(8, budget.snapshot(ADD).samples());
    }

    @Test void thresholdSlowCallImmediatelyResetsOnlyItsOwnOperation() {
        var budget = new PublicationCallBudget();
        grow(budget, ADD);
        grow(budget, HIDE);
        grow(budget, UNHIDE);
        budget.record(ADD, 16, PublicationCallBudget.TARGET_CALL_NANOS);
        assertEquals(1, budget.limit(ADD, 16, 4_000_000));
        assertEquals(0, budget.snapshot(ADD).fastSamples());
        assertEquals(16, budget.limit(HIDE, 16, 4_000_000));
        assertEquals(16, budget.limit(UNHIDE, 16, 4_000_000));
    }

    @Test void mediumSampleBreaksTheFastStreakWithoutPrematureGrowth() {
        var budget = new PublicationCallBudget();
        for (int i = 0; i < 3; i++) budget.record(ADD, 1, 100_000);
        budget.record(ADD, 1, 750_000);
        for (int i = 0; i < 3; i++) budget.record(ADD, 1, 100_000);
        assertEquals(1, budget.limit(ADD, 16, 4_000_000));
        budget.record(ADD, 1, 100_000);
        assertEquals(2, budget.limit(ADD, 16, 4_000_000));
    }

    @Test void remainingBudgetShrinksAGrownBatchButCannotStarveAnExpensiveSingleton() {
        var budget = new PublicationCallBudget();
        grow(budget, ADD);
        assertEquals(16, budget.limit(ADD, 16, 4_000_000));
        budget.record(ADD, 16, 800_000);
        assertEquals(2, budget.limit(ADD, 16, 100_000));
        budget.record(ADD, 1, 867_000_000);
        assertEquals(1, budget.limit(ADD, 16, 1));
    }

    @Test void hardCapAndInternalSixteenCapAreAlwaysHonored() {
        var budget = new PublicationCallBudget();
        for (int i = 0; i < 100; i++) budget.record(ADD, 16, 0);
        assertEquals(16, budget.snapshot(ADD).proposedBatchSize());
        assertEquals(16, budget.limit(ADD, 1000, 4_000_000));
        assertEquals(3, budget.limit(ADD, 3, 4_000_000));
        assertEquals(1, budget.limit(ADD, 1, 4_000_000));
    }

    @Test void nonpositiveAllowancesAndEmptySamplesDoNoWork() {
        var budget = new PublicationCallBudget();
        assertEquals(0, budget.limit(ADD, 0, 10));
        assertEquals(0, budget.limit(ADD, 16, 0));
        assertEquals(0, budget.limit(ADD, 16, -1));
        budget.record(ADD, 0, 10);
        budget.record(ADD, -1, 10);
        assertEquals(0, budget.snapshot(ADD).samples());
    }

    @Test void snapshotsAreImmutableHistoricalValues() {
        var budget = new PublicationCallBudget();
        var before = budget.snapshot(ADD);
        budget.record(ADD, 1, 100_000);
        assertEquals(0, before.samples());
        assertEquals(1, budget.snapshot(ADD).samples());
    }

    @Test void offOwnerLimitRecordAndSnapshotAreRejected() throws Exception {
        var budget = new PublicationCallBudget();
        AtomicReference<Throwable> limit = new AtomicReference<>(), record = new AtomicReference<>(), snapshot = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { budget.limit(ADD, 16, 10); } catch (Throwable error) { limit.set(error); }
            try { budget.record(ADD, 1, 1); } catch (Throwable error) { record.set(error); }
            try { budget.snapshot(ADD); } catch (Throwable error) { snapshot.set(error); }
        });
        worker.start(); worker.join(2000);
        assertFalse(worker.isAlive());
        assertInstanceOf(IllegalStateException.class, limit.get());
        assertInstanceOf(IllegalStateException.class, record.get());
        assertInstanceOf(IllegalStateException.class, snapshot.get());
        assertEquals(0, budget.snapshot(ADD).samples());
    }

    private static void grow(PublicationCallBudget budget, OwnerRecipePublication.Phase phase) {
        for (int i = 0; i < 16; i++) budget.record(phase, 16, 0);
        assertEquals(16, budget.limit(phase, 16, 4_000_000));
    }
}
