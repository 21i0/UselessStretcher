package com.sorrowmist.useless.stretcher.content.mold;

import appeng.api.stacks.AEItemKey;
import com.sorrowmist.useless.stretcher.content.ae.InstalledPatternHost;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InstalledPatternStoreTest {
    private static final ResourceLocation FIRST = new ResourceLocation("test:first");
    private static final ResourceLocation SECOND = new ResourceLocation("test:second");

    @Test void snapshotsAreSharedImmutableAndInvalidatedOnEveryChange() {
        var store = new MyriadPatternStore();
        UUID id = UUID.randomUUID();
        var a = mock(AEItemKey.class);
        var b = mock(AEItemKey.class);
        assertFalse(store.contains(id));
        assertEquals(List.of(), store.snapshot(id));
        store.replace(id, FIRST, new LinkedHashSet<>(List.of(a, b)));
        var snapshot = store.snapshot(id);
        assertSame(snapshot, store.snapshot(id));
        assertEquals(List.of(a, b), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        store.replace(id, SECOND, Set.of(a));
        assertNotSame(snapshot, store.snapshot(id));
        assertEquals(2, store.count(id), "shared patterns are not published twice");
        assertEquals(2, store.removeKeys(id, List.of(a)), "remove shared membership in both molds");
        assertEquals(List.of(b), store.snapshot(id));
        assertEquals(List.of(a, b), snapshot, "existing readers keep an immutable old revision");
        store.clear(id);
        assertFalse(store.contains(id));
        assertEquals(List.of(), store.snapshot(id));
    }

    @Test void liveProvidersRefreshButRemovedAndUnrelatedReadersDoNot() {
        var store = new MyriadPatternStore();
        UUID id = UUID.randomUUID();
        var live = mock(BlockEntity.class, withSettings().extraInterfaces(InstalledPatternHost.class));
        var removed = mock(BlockEntity.class, withSettings().extraInterfaces(InstalledPatternHost.class));
        when(removed.isRemoved()).thenReturn(true);
        store.watch(id, live);
        store.watch(id, live);
        store.watch(id, removed);
        store.watch(id, mock(BlockEntity.class));
        store.watch(null, live);
        var pattern = mock(AEItemKey.class);
        store.replace(id, FIRST, Set.of(pattern));
        verify((InstalledPatternHost) live, times(1)).uselessStretcher$refreshInstalledPatterns();
        verify((InstalledPatternHost) removed, never()).uselessStretcher$refreshInstalledPatterns();
        store.removeKeys(id, List.of(pattern));
        verify((InstalledPatternHost) live, times(2)).uselessStretcher$refreshInstalledPatterns();
        store.clear(id);
        verify((InstalledPatternHost) live, times(2)).uselessStretcher$refreshInstalledPatterns();
    }
}
