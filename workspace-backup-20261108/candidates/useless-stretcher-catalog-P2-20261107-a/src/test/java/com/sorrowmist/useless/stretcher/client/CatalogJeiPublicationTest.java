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
import org.mockito.MockedStatic;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.IntStream;

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
        Consumer<List<Entry>> addAction = registered::addAll;
        Consumer<List<Entry>> hideAction = batch -> {}, unhideAction = batch -> {};

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
                if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                    field.setAccessible(true);
                    previous.put(field, field.get(null));
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
                setField("unsafeFailure", false);
                setField("failedRuntime", new WeakReference<IJeiRuntime>(null));
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
            ledger.clear();
            current = new RuntimeSink();
            installed.set(current.runtime);
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
            }
        }
    }
}
