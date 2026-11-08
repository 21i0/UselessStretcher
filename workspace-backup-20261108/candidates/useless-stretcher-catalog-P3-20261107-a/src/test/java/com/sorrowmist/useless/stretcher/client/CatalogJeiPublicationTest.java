package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.compat.jei.AdvancedAlloyFurnaceRecipeCategory;
import com.sorrowmist.useless.compat.jei.JEIPlugin;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog.Entry;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import com.sorrowmist.useless.stretcher.VanillaTestRegistries;
import com.sorrowmist.useless.stretcher.content.catalog.OwnerRecipePublication;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Lifecycle regression against the real publication class, without launching a game or world. */
class CatalogJeiPublicationTest {
    @BeforeAll static void initializeVanillaCodecs() { VanillaTestRegistries.initialize(); }

    @Test void identicalEntriesObjectKeepsOneTaskAndNeverAddsTheSameNativeEntryTwice() throws Exception {
        try (var fixture = new Fixture(entries(4))) {
            CatalogJeiPublication.request();
            Object firstTask = fixture.task();
            for (int i = 0; i < 12; i++) CatalogJeiPublication.request();
            assertSame(firstTask, fixture.task());
            assertTrue(fixture.current.adds.isEmpty(), "request schedules owner work, it does not call JEI");

            fixture.drainReady();
            for (int i = 0; i < 12; i++) {
                CatalogJeiPublication.request();
                CatalogJeiPublication.frame();
            }
            assertSame(firstTask, fixture.task());
            assertEquals(fixture.entries.get(), fixture.current.registered);
            assertEquals(1, fixture.current.adds.size());
            fixture.assertNoDuplicateEntryAdds();
            assertFalse(CatalogJeiPublication.blocked());
        }
    }

    @Test void normallyReturnedBatchSurvivesCancellationAndInvalidationWithoutReaddingItsObjects() throws Exception {
        try (var fixture = new Fixture(entries(35))) {
            CatalogJeiPublication.request();
            var cancelled = fixture.task();
            fixture.stepUntil(() -> fixture.current.adds.size() == 1);
            assertEquals(16, fixture.ledger.size());
            List<Entry> returned = List.copyOf(fixture.current.registered);
            for (Entry entry : returned) assertSame(entry, fixture.ledger.get(entry.identity()));

            CatalogJeiPublication.invalidate();
            assertEquals(OwnerRecipePublication.Phase.CANCELLED, cancelled.phase());
            assertFalse(CatalogJeiPublication.ready());
            fixture.drainReady();

            assertEquals(fixture.entries.get(), fixture.current.registered);
            for (Entry entry : returned) assertSame(entry, fixture.ledger.get(entry.identity()));
            fixture.assertNoDuplicateEntryAdds();
            assertEquals(3, fixture.current.adds.size());
        }
    }

    @Test void partiallyAcceptedAddBlocksInvalidateAndResetReplayInTheSameRuntime() throws Exception {
        try (var fixture = new Fixture(entries(4))) {
            fixture.current.addAction = batch -> {
                fixture.current.registered.add(batch.getFirst());
                throw new IllegalStateException("JEI accepted one recipe before failing");
            };
            fixture.drainStopped();
            assertEquals(List.of(fixture.entries.get().getFirst()), fixture.current.registered);
            assertTrue(fixture.ledger.isEmpty(), "the unknown accepted extent is not marked fully committed");
            assertEquals(1, fixture.current.adds.size());

            fixture.current.addAction = fixture.current.registered::addAll;
            for (int i = 0; i < 3; i++) {
                CatalogJeiPublication.invalidate();
                CatalogJeiPublication.request();
                CatalogJeiPublication.frame();
                CatalogJeiPublication.reset();
                CatalogJeiPublication.runtimeAvailable();
                CatalogJeiPublication.frame();
            }
            assertEquals(1, fixture.current.adds.size(), "same-runtime partial commit must never auto-replay");
            assertTrue(CatalogJeiPublication.blocked());
            assertFalse(CatalogJeiPublication.ready());
            assertTrue(CatalogJeiPublication.progress().startsWith("JEI stopped:"));
        }
    }

    @Test void replacementRuntimeRecoversAfterUnknownPartialCommit() throws Exception {
        try (var fixture = new Fixture(entries(4))) {
            RuntimeSink failed = fixture.current;
            failed.addAction = batch -> {
                failed.registered.add(batch.getFirst());
                throw new IllegalStateException("partial old-runtime add");
            };
            fixture.drainStopped();
            CatalogJeiPublication.reset();
            fixture.replaceRuntime();
            fixture.drainReady();

            assertEquals(1, failed.adds.size());
            assertEquals(fixture.entries.get(), fixture.current.registered);
            assertEquals(1, fixture.current.adds.size());
            fixture.assertNoDuplicateEntryAdds();
            assertFalse(CatalogJeiPublication.blocked());
        }
    }

    @Test void partiallyAppliedHideCannotBeReplayedByInvalidatingTheSameRuntime() throws Exception {
        try (var fixture = new Fixture(entries(3))) {
            fixture.drainReady();
            List<Entry> externallyHidden = new ArrayList<>();
            fixture.current.hideAction = batch -> {
                externallyHidden.add(batch.getFirst());
                throw new IllegalStateException("JEI hid one recipe before failing");
            };
            fixture.entries.set(List.of());
            fixture.drainStopped();
            assertEquals(1, externallyHidden.size());
            assertEquals(1, fixture.current.hides.size());
            assertEquals(3, fixture.ledger.size());

            CatalogJeiPublication.invalidate();
            for (int i = 0; i < 6; i++) CatalogJeiPublication.frame();
            assertEquals(1, fixture.current.hides.size());
            assertTrue(fixture.current.unhides.isEmpty());
            assertFalse(CatalogJeiPublication.ready());
        }
    }

    @Test void partiallyAppliedUnhideCannotBeReplayedByInvalidatingTheSameRuntime() throws Exception {
        try (var fixture = new Fixture(entries(3))) {
            fixture.drainReady();
            List<Entry> originals = fixture.entries.get();
            fixture.entries.set(List.of());
            fixture.drainReady();
            List<Entry> externallyUnhidden = new ArrayList<>();
            fixture.current.unhideAction = batch -> {
                externallyUnhidden.add(batch.getFirst());
                throw new IllegalStateException("JEI unhid one recipe before failing");
            };
            fixture.entries.set(entries(3));
            fixture.drainStopped();
            assertSame(originals.getFirst(), externallyUnhidden.getFirst());
            assertEquals(1, fixture.current.unhides.size());

            CatalogJeiPublication.invalidate();
            for (int i = 0; i < 6; i++) CatalogJeiPublication.frame();
            assertEquals(1, fixture.current.unhides.size());
            assertEquals(1, fixture.current.adds.size());
            assertFalse(CatalogJeiPublication.ready());
        }
    }

    @Test void replacingEntriesObjectWithinOneManagerRevisionReplansAndRetainsCanonicalEntries() throws Exception {
        try (var fixture = new Fixture(entries(3))) {
            fixture.drainReady();
            List<Entry> originals = fixture.entries.get();
            Object manager = fixture.level.getRecipeManager();
            Entry sameIdentityNewObject = entry(1);
            Entry appended = entry(9);
            assertNotSame(originals.get(1), sameIdentityNewObject);
            fixture.entries.set(List.of(sameIdentityNewObject, appended));
            CatalogJeiPublication.request();
            assertTrue(CatalogJeiPublication.blocked());
            fixture.drainReady();

            assertSame(manager, fixture.level.getRecipeManager());
            assertEquals(List.of(originals.get(0), originals.get(1), originals.get(2), appended),
                    fixture.current.registered);
            assertSame(originals.get(1), fixture.ledger.get(sameIdentityNewObject.identity()),
                    "a new catalog record must reuse the JEI record registered for the same identity");
            assertEquals(Set.of(sameIdentityNewObject.identity(), appended.identity()), fixture.visible());
            assertEquals(1, fixture.current.hides.size());
            fixture.assertNoDuplicateEntryAdds();

            fixture.entries.set(List.of(entry(0), entry(1), entry(9)));
            fixture.drainReady();
            assertEquals(1, fixture.current.unhides.size());
            assertSame(originals.getFirst(), fixture.current.unhides.getFirst().getFirst());
            fixture.assertNoDuplicateEntryAdds();
        }
    }

    @Test void staleEntriesPreconditionFailsBeforeCallingJeiAndCanSafelyReplan() throws Exception {
        try (var fixture = new Fixture(entries(3))) {
            CatalogJeiPublication.request();
            var stale = fixture.task();
            List<Entry> replacement = List.of(entry(7), entry(8));
            fixture.entries.set(replacement);
            // Simulate the source changing after frame's request and before its first JEI callback.
            stale.advance(Long.MAX_VALUE, 8192, 16);
            assertNotNull(stale.failure());
            assertEquals("StaleRevision", stale.failure().getClass().getSimpleName());
            assertTrue(fixture.current.adds.isEmpty(), "pre-call revision guard must prevent stale registration");
            assertTrue(fixture.ledger.isEmpty());
            assertFalse((boolean) fixture.field("unsafeFailure"));

            fixture.drainReady();
            assertEquals(replacement, fixture.current.registered);
            assertFalse((boolean) fixture.field("unsafeFailure"));
        }
    }

    @Test void catalogBecomingUnreadyDuringFrameCannotCallJeiAndCanRecover() throws Exception {
        try (var fixture = new Fixture(entries(2))) {
            CatalogJeiPublication.request();
            List<Entry> replacement = List.of(entry(8));
            AtomicBoolean changed = new AtomicBoolean();
            fixture.catalog.when(() -> AlloyFurnaceRecipeCatalog.isReady(fixture.level))
                    .thenAnswer(call -> changed.compareAndSet(false, true));
            // frame.request initially sees ready; the pre-call check (or the next request
            // if this frame exhausts its budget) sees pending and cancels safely.
            for (int i = 0; i < 500 && fixture.task() != null; i++) CatalogJeiPublication.frame();
            assertTrue(fixture.current.adds.isEmpty());
            assertFalse(CatalogJeiPublication.ready());
            assertFalse((boolean) fixture.field("unsafeFailure"));
            assertNull(fixture.task());

            fixture.catalog.when(() -> AlloyFurnaceRecipeCatalog.isReady(fixture.level)).thenReturn(true);
            fixture.entries.set(replacement);
            fixture.drainReady();
            assertEquals(replacement, fixture.current.registered);
        }
    }

    @Test void callbackReentrantInvalidationKeepsAcceptedBatchAndFrameDoesNotDereferenceCancelledTask() throws Exception {
        try (var fixture = new Fixture(entries(4))) {
            AtomicBoolean invalidateOnce = new AtomicBoolean(true);
            fixture.current.addAction = batch -> {
                fixture.current.registered.addAll(batch);
                if (invalidateOnce.getAndSet(false)) CatalogJeiPublication.invalidate();
            };
            for (int i = 0; i < 200 && fixture.current.adds.isEmpty(); i++) CatalogJeiPublication.frame();
            assertFalse(fixture.current.adds.isEmpty());
            assertNull(fixture.task());
            assertFalse((boolean) fixture.field("unsafeFailure"), "reentrant invalidation is not a JEI failure");
            assertFalse(CatalogJeiPublication.ready());
            assertEquals(4, fixture.ledger.size(), "ledger retains the normally returned batch");

            fixture.drainReady();
            assertEquals(1, fixture.current.adds.size());
            assertEquals(fixture.entries.get(), fixture.current.registered);
            fixture.assertNoDuplicateEntryAdds();
        }
    }

    @Test void callbackCanInvalidateAndInstallAnotherTaskWithoutOldFrameInspectingTheNewTask() throws Exception {
        try (var fixture = new Fixture(entries(3))) {
            List<Entry> initial = fixture.entries.get();
            AtomicBoolean replaceOnce = new AtomicBoolean(true);
            fixture.current.addAction = batch -> {
                fixture.current.registered.addAll(batch);
                if (replaceOnce.getAndSet(false)) {
                    CatalogJeiPublication.invalidate();
                    fixture.entries.set(List.of(initial.get(1), entry(9)));
                    CatalogJeiPublication.request();
                }
            };
            fixture.drainReady();

            assertFalse((boolean) fixture.field("unsafeFailure"));
            assertEquals(Set.of(initial.get(1).identity(), entry(9).identity()), fixture.visible());
            assertEquals(4, fixture.current.registered.size());
            fixture.assertNoDuplicateEntryAdds();
        }
    }

    @Test void runtimeReplacementInsideCallbackPreventsOldRevisionCommitAndRecoversOnNewRuntime() throws Exception {
        try (var fixture = new Fixture(entries(3))) {
            RuntimeSink old = fixture.current;
            old.addAction = batch -> {
                old.registered.addAll(batch);
                fixture.replaceRuntime();
            };
            fixture.drainReady();

            assertEquals(1, old.adds.size());
            assertEquals(fixture.entries.get(), old.registered);
            assertEquals(fixture.entries.get(), fixture.current.registered);
            assertFalse((boolean) fixture.field("unsafeFailure"));
            assertFalse(CatalogJeiPublication.blocked());
        }
    }

    @ParameterizedTest(name = "{0}: throw after reentry, replacementTask={1}")
    @MethodSource("operationReentryCases")
    void partialMutationThenReentrantInvalidationThenThrowCannotReplayInSameRuntime(
            Operation operation, boolean replacementTask) throws Exception {
        try (var fixture = new Fixture(entries(3))) {
            fixture.prepare(operation);
            RuntimeSink receiver = fixture.current;
            var advancing = fixture.task();
            int before = operation.calls(receiver);
            AtomicBoolean failOnce = new AtomicBoolean(true);
            operation.setAction(receiver, batch -> {
                boolean failing = failOnce.getAndSet(false);
                operation.mutate(receiver, batch, failing);
                if (failing) {
                    CatalogJeiPublication.invalidate();
                    if (replacementTask) CatalogJeiPublication.request();
                    throw new IllegalStateException("partial " + operation + " after reentrant invalidation");
                }
            });

            fixture.framesUntil(() -> advancing.terminal());
            assertEquals(OwnerRecipePublication.Phase.FAILED, advancing.phase(),
                    "the old advancing task must retain its callback failure after reentry");
            Object afterThrow = fixture.task();
            int callsAfterThrow = operation.calls(receiver) - before;
            for (int i = 0; i < 8; i++) {
                CatalogJeiPublication.request();
                CatalogJeiPublication.frame();
            }
            CatalogJeiPublication.invalidate();
            CatalogJeiPublication.reset();
            for (int i = 0; i < 3; i++) {
                CatalogJeiPublication.runtimeAvailable();
                CatalogJeiPublication.frame();
            }

            int callsAfterRetry = operation.calls(receiver) - before;
            System.out.printf("REENTRY_THROW operation=%s replacementTask=%s old_phase=%s "
                            + "task_replaced=%s task_null=%s calls_after_throw=%d calls_after_retry=%d "
                            + "blocked=%s ready=%s unsafe=%s registered=%d%n",
                    operation, replacementTask, advancing.phase(), advancing != afterThrow,
                    afterThrow == null, callsAfterThrow, callsAfterRetry, CatalogJeiPublication.blocked(),
                    CatalogJeiPublication.ready(), fixture.field("unsafeFailure"), receiver.registered.size());
            assertAll(
                    () -> assertEquals(1, callsAfterThrow),
                    () -> assertEquals(1, callsAfterRetry, "failed callback must not be replayed in the same runtime"),
                    () -> assertTrue(CatalogJeiPublication.blocked()),
                    () -> assertFalse(CatalogJeiPublication.ready()),
                    () -> assertTrue((boolean) fixture.field("unsafeFailure")));
        }
    }

    @ParameterizedTest(name = "{0}: normal return after reentry, replacementTask={1}")
    @MethodSource("operationReentryCases")
    void normallyReturnedReentrantCallbackKeepsLedgerSemantics(
            Operation operation, boolean replacementTask) throws Exception {
        try (var fixture = new Fixture(entries(3))) {
            List<Entry> originals = fixture.prepare(operation);
            RuntimeSink receiver = fixture.current;
            int before = operation.calls(receiver);
            AtomicBoolean reenterOnce = new AtomicBoolean(true);
            operation.setAction(receiver, batch -> {
                operation.mutate(receiver, batch, false);
                if (reenterOnce.getAndSet(false)) {
                    CatalogJeiPublication.invalidate();
                    if (replacementTask) CatalogJeiPublication.request();
                }
            });
            fixture.drainReady();

            assertEquals(1, operation.calls(receiver) - before);
            assertEquals(3, fixture.ledger.size());
            for (Entry entry : originals) assertSame(entry, fixture.ledger.get(entry.identity()));
            assertEquals(operation == Operation.HIDE ? Set.of() :
                    originals.stream().map(Entry::identity).collect(java.util.stream.Collectors.toSet()), fixture.visible());
            fixture.assertNoDuplicateEntryAdds();
            assertFalse((boolean) fixture.field("unsafeFailure"));
            assertFalse(CatalogJeiPublication.blocked());
        }
    }

    @ParameterizedTest(name = "{0}: old runtime throws after replacement, observeNewNow={1}")
    @MethodSource("operationReentryCases")
    void callbackFailureBelongsToOriginRuntimeAndReturningFailedRuntimeCannotReplay(
            Operation operation, boolean observeNewNow) throws Exception {
        try (var fixture = new Fixture(entries(3))) {
            List<Entry> originals = fixture.prepare(operation);
            RuntimeSink old = fixture.current;
            var advancing = fixture.task();
            operation.setAction(old, batch -> {
                operation.mutate(old, batch, true);
                fixture.replaceRuntime();
                fixture.entries.set(originals);
                if (observeNewNow) CatalogJeiPublication.runtimeAvailable();
                throw new IllegalStateException("old runtime " + operation + " failed after installing new runtime");
            });
            fixture.framesUntil(() -> advancing.terminal());
            assertEquals(OwnerRecipePublication.Phase.FAILED, advancing.phase());
            RuntimeSink fresh = fixture.current;
            assertNotSame(old.runtime, fresh.runtime);
            fixture.drainReady();
            assertEquals(originals, fresh.registered, "failure in old runtime cannot poison replacement runtime");
            assertFalse((boolean) fixture.field("unsafeFailure"));
            int oldCalls = old.totalCalls();

            // A registration reset can clear the native ledger while reusing that same failed
            // runtime object. Its unknown partial commit remains unsafe even after another runtime.
            fixture.installRuntime(old);
            for (int i = 0; i < 6; i++) {
                CatalogJeiPublication.request();
                CatalogJeiPublication.frame();
            }
            CatalogJeiPublication.reset();
            CatalogJeiPublication.runtimeAvailable();
            CatalogJeiPublication.frame();
            System.out.printf("RETURN_FAILED_RUNTIME operation=%s observeNewNow=%s "
                            + "old_calls_before_return=%d old_calls_after_return=%d "
                            + "fresh_registered=%d old_blocked=%s ready=%s unsafe=%s%n",
                    operation, observeNewNow, oldCalls, old.totalCalls(), fresh.registered.size(),
                    CatalogJeiPublication.blocked(), CatalogJeiPublication.ready(), fixture.field("unsafeFailure"));
            assertAll(
                    () -> assertEquals(oldCalls, old.totalCalls(), "a returning failed runtime must not replay any JEI mutation"),
                    () -> assertTrue(CatalogJeiPublication.blocked()),
                    () -> assertFalse(CatalogJeiPublication.ready()),
                    () -> assertTrue((boolean) fixture.field("unsafeFailure")));
        }
    }

    @ParameterizedTest(name = "{0}: nested frame before old callback settles, throwing={1}")
    @MethodSource("operationReentryCases")
    void nestedFrameCannotAdvanceReplacementTaskBeforeOuterCallbackSettles(
            Operation operation, boolean throwing) throws Exception {
        try (var fixture = new Fixture(entries(3))) {
            fixture.prepare(operation);
            RuntimeSink receiver = fixture.current;
            var advancing = fixture.task();
            int before = receiver.totalCalls();
            AtomicBoolean reenterOnce = new AtomicBoolean(true);
            AtomicInteger nestedCalls = new AtomicInteger(-1);
            operation.setAction(receiver, batch -> {
                boolean first = reenterOnce.getAndSet(false);
                operation.mutate(receiver, batch, first && throwing);
                if (first) {
                    CatalogJeiPublication.invalidate();
                    CatalogJeiPublication.request();
                    CatalogJeiPublication.frame();
                    nestedCalls.set(receiver.totalCalls() - before);
                    if (throwing) throw new IllegalStateException("partial " + operation + " after nested frame");
                }
            });
            fixture.framesUntil(advancing::terminal);
            for (int i = 0; i < 6; i++) CatalogJeiPublication.frame();
            if (!throwing) fixture.drainReady();
            System.out.printf("NESTED_FRAME operation=%s throwing=%s nested_callbacks=%d "
                            + "total_callbacks=%d old_phase=%s blocked=%s ready=%s%n",
                    operation, throwing, nestedCalls.get(), receiver.totalCalls() - before,
                    advancing.phase(), CatalogJeiPublication.blocked(), CatalogJeiPublication.ready());
            assertAll(
                    () -> assertEquals(1, nestedCalls.get(), "nested frame must not enter JEI while outer callback is unsettled"),
                    () -> assertEquals(1, receiver.totalCalls() - before),
                    () -> assertEquals(throwing, CatalogJeiPublication.blocked()),
                    () -> assertEquals(!throwing, CatalogJeiPublication.ready()));
            if (!throwing) fixture.assertNoDuplicateEntryAdds();
        }
    }

    @ParameterizedTest(name = "{0}: registration reset, throwing={1}, replacementTask={2}")
    @MethodSource("registrationResetCases")
    void registrationResetDuringCallbackCannotReplayOrPolluteTheClearedNativeLedger(
            Operation operation, boolean throwing, boolean replacementTask) throws Exception {
        try (var fixture = new Fixture(entries(3))) {
            fixture.prepare(operation);
            RuntimeSink receiver = fixture.current;
            var advancing = fixture.task();
            int before = receiver.totalCalls();
            AtomicBoolean resetOnce = new AtomicBoolean(true);
            operation.setAction(receiver, batch -> {
                boolean first = resetOnce.getAndSet(false);
                operation.mutate(receiver, batch, first && throwing);
                if (first) {
                    // Match native registerRecipes: our reset hook runs, then native clears
                    // the shared canonical map. The runtime object itself may be reused.
                    CatalogJeiPublication.reset();
                    fixture.ledger.clear();
                    if (replacementTask) CatalogJeiPublication.request();
                    if (throwing) throw new IllegalStateException("partial " + operation + " after registration reset");
                }
            });
            fixture.framesUntil(advancing::terminal);
            int ledgerAfterReturn = fixture.ledger.size();
            for (int i = 0; i < 8; i++) {
                CatalogJeiPublication.request();
                CatalogJeiPublication.frame();
            }
            System.out.printf("REGISTRATION_RESET operation=%s throwing=%s replacementTask=%s "
                            + "old_phase=%s ledger_after_return=%d ledger_after_retry=%d "
                            + "callbacks=%d blocked=%s ready=%s unsafe=%s%n",
                    operation, throwing, replacementTask, advancing.phase(), ledgerAfterReturn,
                    fixture.ledger.size(), receiver.totalCalls() - before,
                    CatalogJeiPublication.blocked(), CatalogJeiPublication.ready(), fixture.field("unsafeFailure"));
            assertAll(
                    () -> assertEquals(OwnerRecipePublication.Phase.FAILED, advancing.phase()),
                    () -> assertEquals(0, ledgerAfterReturn, "old callback cannot populate the newly cleared native ledger"),
                    () -> assertTrue(fixture.ledger.isEmpty()),
                    () -> assertEquals(1, receiver.totalCalls() - before),
                    () -> assertTrue(CatalogJeiPublication.blocked()),
                    () -> assertFalse(CatalogJeiPublication.ready()),
                    () -> assertTrue((boolean) fixture.field("unsafeFailure")));
        }
    }

    @ParameterizedTest(name = "{0}: observed runtime A-B-A within callback, throwing={1}")
    @MethodSource("operationReentryCases")
    void observedRuntimeExcursionBackToOriginStillInvalidatesTheOldCallbackLedger(
            Operation operation, boolean throwing) throws Exception {
        try (var fixture = new Fixture(entries(3))) {
            List<Entry> originals = fixture.prepare(operation);
            RuntimeSink origin = fixture.current;
            var advancing = fixture.task();
            int before = origin.totalCalls();
            AtomicReference<RuntimeSink> healthy = new AtomicReference<>();
            AtomicBoolean switchOnce = new AtomicBoolean(true);
            operation.setAction(origin, batch -> {
                boolean first = switchOnce.getAndSet(false);
                operation.mutate(origin, batch, first && throwing);
                if (first) {
                    fixture.replaceRuntime();
                    healthy.set(fixture.current);
                    fixture.entries.set(originals);
                    CatalogJeiPublication.runtimeAvailable(); // observe B
                    fixture.installRuntime(origin);
                    CatalogJeiPublication.runtimeAvailable(); // observe A again before old callback settles
                    if (throwing) throw new IllegalStateException("partial " + operation + " after observed A-B-A");
                }
            });
            fixture.framesUntil(advancing::terminal);
            int ledgerAfterReturn = fixture.ledger.size();
            for (int i = 0; i < 6; i++) CatalogJeiPublication.frame();
            int originCalls = origin.totalCalls() - before;
            boolean originBlocked = CatalogJeiPublication.blocked();
            boolean originReady = CatalogJeiPublication.ready();
            boolean originUnsafe = (boolean) fixture.field("unsafeFailure");
            int originLedger = fixture.ledger.size();

            fixture.installRuntime(healthy.get());
            fixture.drainReady();
            System.out.printf("OBSERVED_RUNTIME_ABA operation=%s throwing=%s old_phase=%s "
                            + "ledger_after_return=%d origin_ledger=%d origin_callbacks=%d "
                            + "origin_blocked=%s origin_ready=%s origin_unsafe=%s healthy_registered=%d%n",
                    operation, throwing, advancing.phase(), ledgerAfterReturn, originLedger,
                    originCalls, originBlocked, originReady, originUnsafe, healthy.get().registered.size());
            assertAll(
                    () -> assertEquals(OwnerRecipePublication.Phase.FAILED, advancing.phase()),
                    () -> assertEquals(0, ledgerAfterReturn),
                    () -> assertEquals(0, originLedger),
                    () -> assertEquals(1, originCalls),
                    () -> assertTrue(originBlocked),
                    () -> assertFalse(originReady),
                    () -> assertTrue(originUnsafe),
                    () -> assertEquals(originals, healthy.get().registered),
                    () -> assertTrue(CatalogJeiPublication.ready()),
                    () -> assertFalse((boolean) fixture.field("unsafeFailure")));
        }
    }

    @ParameterizedTest(name = "{0}: installed runtime returns to A without observing, then callback throws")
    @MethodSource("operations")
    void failureFlagsFromUnobservedOriginRuntimeCannotBlockTheHealthyObservedRuntime(Operation operation) throws Exception {
        try (var fixture = new Fixture(entries(3))) {
            List<Entry> originals = fixture.prepare(operation);
            RuntimeSink origin = fixture.current;
            var advancing = fixture.task();
            operation.setAction(origin, batch -> {
                operation.mutate(origin, batch, true);
                fixture.replaceRuntime();
                fixture.entries.set(originals);
                CatalogJeiPublication.request(); // global observed runtime and task now belong to B
                fixture.installed.set(origin.runtime); // native returns to A without request/observe
                throw new IllegalStateException("partial " + operation + " after unobserved return to A");
            });
            fixture.framesUntil(advancing::terminal);
            assertEquals(OwnerRecipePublication.Phase.FAILED, advancing.phase());
            RuntimeSink healthy = fixture.current;
            assertSame(healthy.runtime, fixture.field("runtime"));
            assertSame(origin.runtime, fixture.installed.get());

            // B remains the already observed identity. request must reselect B's own fence
            // even though observeRuntime is skipped and flags were last written for A.
            fixture.installed.set(healthy.runtime);
            for (int i = 0; i < 500 && !CatalogJeiPublication.ready(); i++) CatalogJeiPublication.frame();
            boolean healthyReady = CatalogJeiPublication.ready();
            boolean healthyBlocked = CatalogJeiPublication.blocked();
            boolean healthyUnsafe = (boolean) fixture.field("unsafeFailure");
            List<Entry> healthyRegistered = List.copyOf(healthy.registered);
            int originCalls = origin.totalCalls();
            fixture.installRuntime(origin);
            for (int i = 0; i < 6; i++) CatalogJeiPublication.frame();
            System.out.printf("RUNTIME_FLAG_ALIAS operation=%s healthy_ready=%s healthy_blocked=%s "
                            + "healthy_unsafe=%s healthy_registered=%d origin_before=%d origin_after=%d origin_blocked=%s%n",
                    operation, healthyReady, healthyBlocked, healthyUnsafe, healthyRegistered.size(),
                    originCalls, origin.totalCalls(), CatalogJeiPublication.blocked());
            assertAll(
                    () -> assertTrue(healthyReady, "origin A failure must not fence already observed healthy B"),
                    () -> assertFalse(healthyBlocked),
                    () -> assertFalse(healthyUnsafe),
                    () -> assertEquals(originals, healthyRegistered),
                    () -> assertEquals(originCalls, origin.totalCalls()),
                    () -> assertTrue(CatalogJeiPublication.blocked()));
        }
    }

    @ParameterizedTest(name = "{0}: inactive callback failure must be selected when the observed runtime returns")
    @MethodSource("operations")
    void recordedRuntimeFenceCannotBeSkippedAfterAnUnobservedNativeRuntimeSwitch(Operation operation) throws Exception {
        try (var fixture = new Fixture(entries(3))) {
            fixture.prepare(operation);
            RuntimeSink failed = fixture.current;
            var advancing = fixture.task();
            RuntimeSink other = new RuntimeSink();
            AtomicBoolean failOnce = new AtomicBoolean(true);
            operation.setAction(failed, batch -> {
                boolean first = failOnce.getAndSet(false);
                operation.mutate(failed, batch, first);
                if (first) {
                    fixture.installed.set(other.runtime); // native A, but observed controller is still B
                    throw new IllegalStateException("recorded partial " + operation + " in inactive B");
                }
            });
            fixture.framesUntil(advancing::terminal);
            assertEquals(OwnerRecipePublication.Phase.FAILED, advancing.phase());
            int before = failed.totalCalls();
            assertFalse((boolean) fixture.field("unsafeFailure"), "inactive B failure cannot fence healthy native A");
            assertSame(failed.runtime, fixture.field("runtime"));
            assertSame(other.runtime, fixture.installed.get());
            fixture.installed.set(failed.runtime); // no request/observe ever saw A
            CatalogJeiPublication.invalidate();
            for (int i = 0; i < 6; i++) CatalogJeiPublication.frame();
            System.out.printf("RUNTIME_REGISTRY_RESELECT operation=%s callbacks_before=%d callbacks_after=%d "
                            + "blocked=%s ready=%s unsafe=%s%n",
                    operation, before, failed.totalCalls(), CatalogJeiPublication.blocked(),
                    CatalogJeiPublication.ready(), fixture.field("unsafeFailure"));
            assertAll(
                    () -> assertEquals(before, failed.totalCalls(), "recorded B must stay fenced despite healthy detached flags"),
                    () -> assertTrue(CatalogJeiPublication.blocked()),
                    () -> assertFalse(CatalogJeiPublication.ready()),
                    () -> assertTrue((boolean) fixture.field("unsafeFailure")));
        }
    }

    static Stream<Operation> operations() { return Stream.of(Operation.values()); }

    static Stream<Arguments> operationReentryCases() {
        return Stream.of(Operation.values()).flatMap(operation ->
                Stream.of(Arguments.of(operation, false), Arguments.of(operation, true)));
    }

    static Stream<Arguments> registrationResetCases() {
        return Stream.of(Operation.values()).flatMap(operation -> Stream.of(false, true).flatMap(throwing ->
                Stream.of(Arguments.of(operation, throwing, false), Arguments.of(operation, throwing, true))));
    }

    private enum Operation {
        ADD, HIDE, UNHIDE;

        int calls(RuntimeSink receiver) {
            return switch (this) {
                case ADD -> receiver.adds.size();
                case HIDE -> receiver.hides.size();
                case UNHIDE -> receiver.unhides.size();
            };
        }

        void setAction(RuntimeSink receiver, Consumer<List<Entry>> action) {
            switch (this) {
                case ADD -> receiver.addAction = action;
                case HIDE -> receiver.hideAction = action;
                case UNHIDE -> receiver.unhideAction = action;
            }
        }

        void mutate(RuntimeSink receiver, List<Entry> batch, boolean partial) {
            List<Entry> accepted = partial ? List.of(batch.getFirst()) : batch;
            switch (this) {
                case ADD -> receiver.registered.addAll(accepted);
                case HIDE -> receiver.externallyHidden.addAll(accepted);
                case UNHIDE -> receiver.externallyUnhidden.addAll(accepted);
            }
        }
    }

    private static List<Entry> entries(int count) { return IntStream.range(0, count).mapToObj(CatalogJeiPublicationTest::entry).toList(); }

    private static Entry entry(int id) {
        // Publication reads only identity; no recipe conversion or native helper is invoked here.
        return new Entry(new AlloyFurnaceRecipeIdentity(ResourceLocation.parse("publication_test:r" + id),
                "fingerprint-" + id), null, "publication_test");
    }

    private static final class RuntimeSink {
        final IJeiRuntime runtime = mock(IJeiRuntime.class);
        final IRecipeManager manager = mock(IRecipeManager.class);
        final List<List<Entry>> adds = new ArrayList<>(), hides = new ArrayList<>(), unhides = new ArrayList<>();
        final List<Entry> registered = new ArrayList<>();
        final List<Entry> externallyHidden = new ArrayList<>(), externallyUnhidden = new ArrayList<>();
        Consumer<List<Entry>> addAction = registered::addAll;
        Consumer<List<Entry>> hideAction = batch -> {}, unhideAction = batch -> {};

        int totalCalls() { return adds.size() + hides.size() + unhides.size(); }

        RuntimeSink() {
            when(runtime.getRecipeManager()).thenReturn(manager);
            doAnswer(call -> {
                List<Entry> batch = List.copyOf(call.<List<Entry>>getArgument(1));
                adds.add(batch);
                addAction.accept(batch);
                return null;
            }).when(manager).addRecipes(eq(AdvancedAlloyFurnaceRecipeCategory.TYPE), anyList());
            doAnswer(call -> {
                List<Entry> batch = List.copyOf(call.<List<Entry>>getArgument(1));
                hides.add(batch);
                hideAction.accept(batch);
                return null;
            }).when(manager).hideRecipes(eq(AdvancedAlloyFurnaceRecipeCategory.TYPE), anyList());
            doAnswer(call -> {
                List<Entry> batch = List.copyOf(call.<List<Entry>>getArgument(1));
                unhides.add(batch);
                unhideAction.accept(batch);
                return null;
            }).when(manager).unhideRecipes(eq(AdvancedAlloyFurnaceRecipeCategory.TYPE), anyList());
        }
    }

    /** Every static field and the native ledger are restored, so this fixture cannot leak a failed runtime. */
    private static final class Fixture implements AutoCloseable {
        final Map<Field, Object> previous = new LinkedHashMap<>();
        final Map<Field, Object> previousContainerContents = new LinkedHashMap<>();
        final Set<AlloyFurnaceRecipeIdentity> previousVisible;
        final boolean previousQueued;
        final Map<AlloyFurnaceRecipeIdentity, Entry> ledger;
        final Map<AlloyFurnaceRecipeIdentity, Entry> previousLedger;
        final AtomicReference<List<Entry>> entries;
        final AtomicReference<IJeiRuntime> installed = new AtomicReference<>();
        final Minecraft mc = mock(Minecraft.class);
        final ClientLevel level = mock(ClientLevel.class);
        MockedStatic<Minecraft> minecraft;
        MockedStatic<JEIPlugin> plugin;
        MockedStatic<AlloyFurnaceRecipeCatalog> catalog;
        RuntimeSink current;

        @SuppressWarnings("unchecked") Fixture(List<Entry> input) throws Exception {
            for (Field field : CatalogJeiPublication.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (!Modifier.isFinal(field.getModifiers())) previous.put(field, value);
                    if (value instanceof Map<?, ?> map) previousContainerContents.put(field, new LinkedHashMap<>(map));
                    else if (value instanceof Collection<?> collection)
                        previousContainerContents.put(field, new ArrayList<>(collection));
                }
            }
            previousVisible = Set.copyOf(visible());
            previousQueued = queued().get();
            Field ledgerField = JEIPlugin.class.getDeclaredField("registeredAlloyFurnaceRecipes");
            ledgerField.setAccessible(true);
            ledger = (Map<AlloyFurnaceRecipeIdentity, Entry>) ledgerField.get(null);
            previousLedger = new HashMap<>(ledger);
            entries = new AtomicReference<>(input);
            try {
                when(mc.isSameThread()).thenReturn(true);
                when(level.getRecipeManager()).thenReturn(mock(RecipeManager.class));
                mc.level = level;
                mc.screen = null;
                minecraft = mockStatic(Minecraft.class);
                minecraft.when(Minecraft::getInstance).thenReturn(mc);
                plugin = mockStatic(JEIPlugin.class);
                plugin.when(JEIPlugin::getRuntime).thenAnswer(call -> installed.get());
                catalog = mockStatic(AlloyFurnaceRecipeCatalog.class);
                catalog.when(() -> AlloyFurnaceRecipeCatalog.isReady(level)).thenReturn(true);
                catalog.when(() -> AlloyFurnaceRecipeCatalog.entries(level)).thenAnswer(call -> entries.get());
                // Do not cancel a pre-existing task from another test when detaching its saved state.
                setField("task", null);
                clearContainers();
                setField("unsafeFailure", false);
                setOptionalField("failedRuntime", new WeakReference<IJeiRuntime>(null));
                setOptionalField("taskRuntime", null);
                setOptionalField("frameInProgress", false);
                setField("failure", "");
                CatalogJeiPublication.reset();
                queued().set(false);
                ledger.clear();
                replaceRuntime();
            } catch (Throwable error) {
                close();
                throw error;
            }
        }

        void replaceRuntime() {
            // The real JEI registration clears its canonical ledger for a newly installed runtime.
            installRuntime(new RuntimeSink());
        }

        void installRuntime(RuntimeSink receiver) {
            ledger.clear();
            current = receiver;
            installed.set(receiver.runtime);
        }

        List<Entry> prepare(Operation operation) {
            List<Entry> originals = entries.get();
            if (operation != Operation.ADD) drainReady();
            if (operation == Operation.HIDE || operation == Operation.UNHIDE) entries.set(List.of());
            if (operation == Operation.UNHIDE) {
                drainReady();
                entries.set(originals);
            }
            CatalogJeiPublication.request();
            return originals;
        }

        void drainReady() { CatalogJeiPublication.request(); framesUntil(CatalogJeiPublication::ready); }
        void drainStopped() { framesUntil(() -> CatalogJeiPublication.progress().startsWith("JEI stopped:")); }

        void framesUntil(BooleanSupplier done) {
            for (int i = 0; i < 500 && !done.getAsBoolean(); i++) CatalogJeiPublication.frame();
            assertTrue(done.getAsBoolean(), CatalogJeiPublication.progress());
        }

        void stepUntil(BooleanSupplier done) throws Exception {
            for (int i = 0; i < 10000 && !done.getAsBoolean(); i++) task().advance(Long.MAX_VALUE, 1, 16);
            assertTrue(done.getAsBoolean());
        }

        void assertNoDuplicateEntryAdds() {
            var identities = new IdentityHashMap<Entry, Boolean>();
            current.adds.forEach(batch -> batch.forEach(entry ->
                    assertNull(identities.put(entry, Boolean.TRUE), "same native Entry object added twice")));
            assertEquals(current.registered.size(), identities.size());
        }

        @SuppressWarnings("unchecked") OwnerRecipePublication<AlloyFurnaceRecipeIdentity, Entry> task() throws Exception {
            return (OwnerRecipePublication<AlloyFurnaceRecipeIdentity, Entry>) field("task");
        }

        @SuppressWarnings("unchecked") Set<AlloyFurnaceRecipeIdentity> visible() throws Exception {
            return (Set<AlloyFurnaceRecipeIdentity>) field("VISIBLE");
        }

        AtomicBoolean queued() throws Exception { return (AtomicBoolean) field("REQUEST_QUEUED"); }

        Object field(String name) throws Exception {
            Field field = CatalogJeiPublication.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        }

        void setField(String name, Object value) throws Exception {
            Field field = CatalogJeiPublication.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
        }

        void setOptionalField(String name, Object value) throws Exception {
            try { setField(name, value); } catch (NoSuchFieldException ignored) { /* P2/P3 internal field differs. */ }
        }

        private void clearContainers() throws Exception {
            for (Field field : previousContainerContents.keySet()) {
                Object value = field.get(null);
                if (value instanceof Map<?, ?> map) map.clear();
                else if (value instanceof Collection<?> collection) collection.clear();
            }
        }

        @Override public void close() throws Exception {
            try {
                if (minecraft != null && plugin != null) CatalogJeiPublication.reset();
            } finally {
                if (catalog != null) catalog.close();
                if (plugin != null) plugin.close();
                if (minecraft != null) minecraft.close();
                ledger.clear();
                ledger.putAll(previousLedger);
                visible().clear();
                visible().addAll(previousVisible);
                queued().set(previousQueued);
                for (Map.Entry<Field, Object> saved : previous.entrySet()) saved.getKey().set(null, saved.getValue());
                restoreContainers();
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"}) private void restoreContainers() throws Exception {
            for (Map.Entry<Field, Object> saved : previousContainerContents.entrySet()) {
                Object value = saved.getKey().get(null);
                if (value instanceof Map map) { map.clear(); map.putAll((Map) saved.getValue()); }
                else if (value instanceof Collection collection) { collection.clear(); collection.addAll((Collection) saved.getValue()); }
            }
        }
    }
}
