package com.sorrowmist.useless.stretcher.content.mold;

import appeng.api.stacks.AEItemKey;
import com.sorrowmist.useless.stretcher.content.ae.InstalledPatternHost;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Runs the production store without a game or server. Mock keys are immutable
 * identities; only the rewrite transform is stubbed, not the store or its CAS.
 */
class InstalledPatternSnapshotTest {
    private static final UUID SOURCE = UUID.fromString("cb862b1b-1751-4c1a-88eb-f94da4706d40");
    private static final UUID OTHER_SOURCE = UUID.fromString("97853a85-6be9-4546-8882-ac965b4b7303");
    private static final ResourceLocation FIRST = ResourceLocation.parse("snapshot_test:first");
    private static final ResourceLocation SECOND = ResourceLocation.parse("snapshot_test:second");

    @Test void nullSourceDoesNotCreateAnInstalledSnapshot() {
        var store = new MyriadPatternStore();

        assertNull(store.fork(null));
        assertFalse(store.contains(SOURCE));
    }

    @Test void installedSnapshotSurvivesSourceReplacementRemovalAndDeletion() {
        var store = new MyriadPatternStore();
        var a = key("a");
        var b = key("b");
        var c = key("c");
        store.replace(SOURCE, FIRST, ordered(a, b));
        UUID installed = store.fork(SOURCE);
        var originalView = store.snapshot(installed);

        assertNotEquals(SOURCE, installed);
        assertEquals(List.of(a, b), originalView);
        assertThrows(UnsupportedOperationException.class, () -> originalView.clear());

        store.replace(SOURCE, FIRST, ordered(c));
        assertEquals(List.of(c), store.snapshot(SOURCE));
        assertEquals(List.of(a, b), store.snapshot(installed));

        assertEquals(1, store.removeKeys(SOURCE, List.of(c)));
        assertEquals(List.of(), store.snapshot(SOURCE));
        assertEquals(List.of(a, b), store.snapshot(installed));

        store.replace(SOURCE, SECOND, ordered(c, a));
        store.clear(SOURCE);
        assertFalse(store.contains(SOURCE));
        assertTrue(store.contains(installed));
        assertEquals(List.of(a, b), store.snapshot(installed));
        assertEquals(List.of(a, b), originalView);
    }

    @Test void installedWritesCannotModifyItsSourceOrAnotherInstalledSnapshot() {
        var store = new MyriadPatternStore();
        var a = key("a");
        var b = key("b");
        var c = key("c");
        store.replace(SOURCE, FIRST, ordered(a, b));
        UUID firstInstallation = store.fork(SOURCE);
        UUID secondInstallation = store.fork(SOURCE);

        store.replace(firstInstallation, FIRST, ordered(c));
        assertEquals(List.of(c), store.snapshot(firstInstallation));
        assertEquals(List.of(a, b), store.snapshot(SOURCE));
        assertEquals(List.of(a, b), store.snapshot(secondInstallation));

        assertEquals(1, store.removeKeys(secondInstallation, List.of(a)));
        assertEquals(List.of(b), store.snapshot(secondInstallation));
        assertEquals(List.of(a, b), store.snapshot(SOURCE));
        assertEquals(List.of(c), store.snapshot(firstInstallation));

        store.clear(firstInstallation);
        assertEquals(List.of(a, b), store.snapshot(SOURCE));
        assertEquals(List.of(b), store.snapshot(secondInstallation));
    }

    @Test void eachInstallationGetsItsOwnIdentityAndTheContentsAtThatMoment() {
        var store = new MyriadPatternStore();
        var a = key("a");
        var b = key("b");
        var c = key("c");
        store.replace(SOURCE, FIRST, ordered(a));
        UUID early = store.fork(SOURCE);
        UUID sameMoment = store.fork(SOURCE);

        store.replace(SOURCE, FIRST, ordered(b, c));
        UUID later = store.fork(SOURCE);
        UUID copiedInstallation = store.fork(early);

        assertEquals(5, new LinkedHashSet<>(List.of(SOURCE, early, sameMoment, later, copiedInstallation)).size());
        assertEquals(List.of(a), store.snapshot(early));
        assertEquals(List.of(a), store.snapshot(sameMoment));
        assertEquals(List.of(b, c), store.snapshot(later));
        assertEquals(List.of(a), store.snapshot(copiedInstallation));

        store.clear(SOURCE);
        store.clear(early);
        assertEquals(List.of(a), store.snapshot(sameMoment));
        assertEquals(List.of(a), store.snapshot(copiedInstallation));
        assertEquals(List.of(b, c), store.snapshot(later));
    }

    @Test void twoDifferentSourceLibrariesRemainIndependentAfterBothAreInstalled() {
        var store = new MyriadPatternStore();
        var a = key("a");
        var b = key("b");
        var common = key("common");
        store.replace(SOURCE, FIRST, ordered(a, common));
        store.replace(OTHER_SOURCE, SECOND, ordered(b, common));
        UUID installedA = store.fork(SOURCE);
        UUID installedB = store.fork(OTHER_SOURCE);

        store.replace(SOURCE, FIRST, ordered(b));
        store.removeKeys(OTHER_SOURCE, List.of(common));
        store.clear(OTHER_SOURCE);

        assertEquals(List.of(a, common), store.snapshot(installedA));
        assertEquals(List.of(b, common), store.snapshot(installedB));
        assertNotEquals(installedA, installedB);
    }

    @Test void forksPreserveGlobalOrderAndSharedMembershipAcrossMoldGroups() {
        var store = new MyriadPatternStore();
        var a = key("a");
        var b = key("b");
        var c = key("c");
        var d = key("d");
        store.replace(SOURCE, FIRST, ordered(b, a));
        store.replace(SOURCE, SECOND, ordered(a, c));
        UUID installed = store.fork(SOURCE);

        assertEquals(List.of(b, a, c), store.snapshot(installed));
        assertEquals(List.of(FIRST, SECOND), List.copyOf(store.molds(installed)));
        assertEquals(3, store.count(installed), "a shared by two molds must occur only once globally");

        store.replace(SOURCE, FIRST, ordered(d, b));
        assertEquals(List.of(a, c, d, b), store.snapshot(SOURCE));
        assertEquals(List.of(b, a, c), store.snapshot(installed));
        assertEquals(2, store.removeKeys(installed, List.of(a)), "both copied memberships must still exist");
        assertEquals(List.of(b, c), store.snapshot(installed));
        assertEquals(List.of(a, c, d, b), store.snapshot(SOURCE));
    }

    @Test void removingOneCopiedGroupDoesNotDropPatternsStillUsedByAnotherGroup() {
        var store = new MyriadPatternStore();
        var a = key("a");
        var b = key("b");
        var c = key("c");
        store.replace(SOURCE, FIRST, ordered(b, a));
        store.replace(SOURCE, SECOND, ordered(a, c));
        UUID installed = store.fork(SOURCE);

        store.replace(installed, FIRST, ordered());

        assertEquals(List.of(a, c), store.snapshot(installed));
        assertEquals(List.of(SECOND), List.copyOf(store.molds(installed)));
        assertEquals(List.of(b, a, c), store.snapshot(SOURCE));
        assertEquals(List.of(FIRST, SECOND), List.copyOf(store.molds(SOURCE)));
    }

    @Test void sourceRewritePublishesOnlyToTheSourceAndPreservesInstalledOrder() {
        var store = new MyriadPatternStore();
        var a = key("a");
        var b = key("b");
        var trimmed = key("trimmed");
        store.replace(SOURCE, FIRST, ordered(a, b));
        store.replace(SOURCE, SECOND, ordered(a));

        try (var transform = mockStatic(PatternOutputs.class)) {
            transform.when(() -> PatternOutputs.withoutByproducts(a)).thenReturn(trimmed);
            transform.when(() -> PatternOutputs.withoutByproducts(b)).thenReturn(b);
            var rewrite = store.stripByproducts(SOURCE);
            UUID installed = store.fork(SOURCE);
            assertTrue(rewrite.valid(), "taking a snapshot is not a source edit");

            finish(rewrite);

            assertEquals(1, rewrite.changed());
            assertEquals(List.of(trimmed, b), store.snapshot(SOURCE));
            assertEquals(List.of(a, b), store.snapshot(installed));
            assertEquals(2, store.removeKeys(SOURCE, List.of(trimmed)));
            assertEquals(List.of(a, b), store.snapshot(installed));
        }
    }

    @Test void sourceEditsInvalidatePendingRewriteWithoutChangingItsInstalledSnapshot() {
        var store = new MyriadPatternStore();
        var a = key("a");
        var b = key("b");
        store.replace(SOURCE, FIRST, ordered(a));
        var rewrite = store.stripByproducts(SOURCE);
        UUID installed = store.fork(SOURCE);

        store.replace(SOURCE, FIRST, ordered(b));

        assertFalse(rewrite.valid());
        assertThrows(IllegalStateException.class, () -> rewrite.advance(Long.MAX_VALUE));
        assertEquals(List.of(b), store.snapshot(SOURCE));
        assertEquals(List.of(a), store.snapshot(installed));
    }

    @Test void sourceRemovalInvalidatesPendingRewriteButLeavesCopiedMembershipsUntouched() {
        var store = new MyriadPatternStore();
        var a = key("a");
        var b = key("b");
        store.replace(SOURCE, FIRST, ordered(a, b));
        store.replace(SOURCE, SECOND, ordered(a));
        UUID installed = store.fork(SOURCE);
        var rewrite = store.stripByproducts(SOURCE);

        assertEquals(2, store.removeKeys(SOURCE, List.of(a)));

        assertFalse(rewrite.valid());
        assertThrows(IllegalStateException.class, () -> rewrite.advance(Long.MAX_VALUE));
        assertEquals(List.of(b), store.snapshot(SOURCE));
        assertEquals(List.of(a, b), store.snapshot(installed));
        assertEquals(2, store.removeKeys(installed, List.of(a)));
        assertEquals(List.of(b), store.snapshot(SOURCE));
    }

    @Test void installationDuringIncrementalRewriteCapturesPublishedContentsOnly() {
        var store = new MyriadPatternStore();
        var original = new ArrayList<AEItemKey>();
        for (int i = 0; i < 129; i++) original.add(key("incremental-" + i));
        var trimmed = key("trimmed");
        store.replace(SOURCE, FIRST, new LinkedHashSet<>(original));

        try (var transform = mockStatic(PatternOutputs.class)) {
            transform.when(() -> PatternOutputs.withoutByproducts(any(AEItemKey.class)))
                    .thenAnswer(call -> call.getArgument(0));
            transform.when(() -> PatternOutputs.withoutByproducts(original.getFirst())).thenReturn(trimmed);
            var rewrite = store.stripByproducts(SOURCE);
            assertFalse(rewrite.advance(Long.MAX_VALUE), "129 keys require more than one 128-step advance");
            UUID installedMidRewrite = store.fork(SOURCE);
            assertEquals(original, store.snapshot(SOURCE), "unfinished work is not published");

            finish(rewrite);

            var rewritten = new ArrayList<>(original);
            rewritten.set(0, trimmed);
            assertEquals(rewritten, store.snapshot(SOURCE));
            assertEquals(original, store.snapshot(installedMidRewrite));
        }
    }

    @Test void clearingSourceInvalidatesPendingRewriteWithoutResurrectingOrErasingSnapshot() {
        var store = new MyriadPatternStore();
        var a = key("a");
        store.replace(SOURCE, FIRST, ordered(a));
        UUID installed = store.fork(SOURCE);
        var rewrite = store.stripByproducts(SOURCE);

        store.clear(SOURCE);

        assertFalse(rewrite.valid());
        assertThrows(IllegalStateException.class, () -> rewrite.advance(Long.MAX_VALUE));
        assertFalse(store.contains(SOURCE));
        assertEquals(List.of(a), store.snapshot(installed));
    }

    @Test void changingSnapshotDoesNotInvalidateOrRedirectPendingSourceRewrite() {
        var store = new MyriadPatternStore();
        var a = key("a");
        var b = key("b");
        var trimmed = key("trimmed");
        store.replace(SOURCE, FIRST, ordered(a));
        UUID installed = store.fork(SOURCE);

        try (var transform = mockStatic(PatternOutputs.class)) {
            transform.when(() -> PatternOutputs.withoutByproducts(a)).thenReturn(trimmed);
            var rewrite = store.stripByproducts(SOURCE);
            store.replace(installed, FIRST, ordered(b));
            assertTrue(rewrite.valid(), "a different UUID must not invalidate this source CAS");

            finish(rewrite);

            assertEquals(List.of(trimmed), store.snapshot(SOURCE));
            assertEquals(List.of(b), store.snapshot(installed));
        }
    }

    @Test void copiedReadersAreNotSubscribedToFutureSourceChanges() {
        var store = new MyriadPatternStore();
        var a = key("a");
        var b = key("b");
        store.replace(SOURCE, FIRST, ordered(a));
        var sourceReader = mock(BlockEntity.class, withSettings().extraInterfaces(InstalledPatternHost.class));
        store.watch(SOURCE, sourceReader);
        UUID installed = store.fork(SOURCE);
        var installedReader = mock(BlockEntity.class, withSettings().extraInterfaces(InstalledPatternHost.class));
        store.watch(installed, installedReader);
        clearInvocations(sourceReader, installedReader);

        store.replace(SOURCE, FIRST, ordered(b));
        verify((InstalledPatternHost) sourceReader).uselessStretcher$refreshInstalledPatterns();
        verify((InstalledPatternHost) installedReader, never()).uselessStretcher$refreshInstalledPatterns();

        clearInvocations(sourceReader, installedReader);
        store.replace(installed, FIRST, ordered(b));
        verify((InstalledPatternHost) installedReader).uselessStretcher$refreshInstalledPatterns();
        verify((InstalledPatternHost) sourceReader, never()).uselessStretcher$refreshInstalledPatterns();
    }

    @Test void unknownAndClearedSourcesProduceStableEmptySnapshotsRatherThanLiveLinks() {
        var store = new MyriadPatternStore();
        UUID unknownSnapshot = store.fork(SOURCE);
        var a = key("a");
        assertNotNull(unknownSnapshot);
        assertNotEquals(SOURCE, unknownSnapshot);
        assertTrue(store.contains(unknownSnapshot));
        assertFalse(store.contains(SOURCE));

        store.replace(SOURCE, FIRST, ordered(a));
        assertEquals(List.of(), store.snapshot(unknownSnapshot));
        store.clear(SOURCE);
        UUID clearedSnapshot = store.fork(SOURCE);
        UUID copiedEmptySnapshot = store.fork(clearedSnapshot);
        assertEquals(3, new LinkedHashSet<>(List.of(unknownSnapshot, clearedSnapshot, copiedEmptySnapshot)).size());

        store.replace(SOURCE, FIRST, ordered(a));
        assertEquals(List.of(), store.snapshot(clearedSnapshot));
        assertEquals(List.of(), store.snapshot(copiedEmptySnapshot));
        assertTrue(store.contains(clearedSnapshot));
        assertTrue(store.contains(copiedEmptySnapshot));
    }

    @Test void emptyInstalledIdentitiesSurviveRealStoreNbtRoundTrip() {
        // No ItemStack codec is exercised: these palettes are empty. This tests
        // the real store format and UUID persistence, not native item decoding.
        var store = new MyriadPatternStore();
        UUID first = store.fork(SOURCE);
        UUID second = store.fork(SOURCE);
        var registries = mock(HolderLookup.Provider.class);

        CompoundTag saved = store.save(new CompoundTag(), registries);
        var loaded = MyriadPatternStore.load(saved, registries);

        assertTrue(loaded.contains(first));
        assertTrue(loaded.contains(second));
        assertFalse(loaded.contains(SOURCE));
        assertEquals(List.of(), loaded.snapshot(first));
        assertEquals(List.of(), loaded.snapshot(second));
        UUID afterRestart = loaded.fork(first);
        assertNotEquals(first, afterRestart);
        assertNotEquals(second, afterRestart);

        var a = key("after-restart");
        loaded.replace(first, FIRST, ordered(a));
        assertEquals(List.of(a), loaded.snapshot(first));
        assertEquals(List.of(), loaded.snapshot(second));
        assertEquals(List.of(), loaded.snapshot(afterRestart));
        assertEquals(List.of(), store.snapshot(first));
    }

    private static AEItemKey key(String name) {
        return mock(AEItemKey.class, name);
    }

    private static LinkedHashSet<AEItemKey> ordered(AEItemKey... keys) {
        return new LinkedHashSet<>(List.of(keys));
    }

    private static void finish(MyriadPatternStore.Rewrite rewrite) {
        assertNotNull(rewrite);
        for (int pass = 0; pass < 100; pass++) {
            if (rewrite.advance(Long.MAX_VALUE)) return;
        }
        fail("small rewrite did not finish within 100 bounded advances");
    }
}
