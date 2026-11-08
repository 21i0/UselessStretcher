package com.sorrowmist.useless.stretcher.content.ae;

import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData;
import com.sorrowmist.useless.stretcher.VanillaTestRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real handler and NeoForge storage operations; item identity and world lookups are mocked. */
class AssemblyStretcherInventoryTest {
    @BeforeAll static void initializeVanillaStaticsOnly() {
        // Mockito initializes the mocked class. ItemStack.<clinit> itself reads
        // BuiltInRegistries.ITEM and creates EMPTY, so it needs vanilla bootstrap.
        // This creates no Minecraft instance, server, world, or mod registries.
        VanillaTestRegistries.initialize();
    }

    @Test void simulatedInsertionDoesNotForkConsumeOrNotify() {
        try (var fixture = new Fixture()) {
            ItemStack offered = fixture.stack("source", 2, false);
            UUID source = fixture.binding(offered);

            ItemStack remainder = fixture.inventory.insertItem(0, offered, true);

            assertEquals(1, remainder.getCount());
            assertEquals(source, fixture.binding(remainder));
            assertEquals(2, offered.getCount());
            assertTrue(fixture.inventory.getStackInSlot(0).isEmpty());
            assertEquals(0, fixture.forks.get());
            assertEquals(0, fixture.changes.get());
        }
    }

    @Test void realInsertionForksExactlyOnceAndRemainderKeepsOriginalBinding() {
        try (var fixture = new Fixture()) {
            ItemStack offered = fixture.stack("source", 2, false);
            UUID source = fixture.binding(offered);

            ItemStack remainder = fixture.inventory.insertItem(0, offered, false);
            ItemStack installed = fixture.inventory.getStackInSlot(0);

            assertEquals(1, installed.getCount());
            assertNotEquals(source, fixture.binding(installed));
            assertEquals(1, remainder.getCount());
            assertEquals(source, fixture.binding(remainder));
            assertEquals(source, fixture.binding(offered));
            assertEquals(2, offered.getCount(), "handler insertion must not mutate its offered argument");
            assertEquals(1, fixture.forks.get());
            assertEquals(1, fixture.changes.get());
        }
    }

    @Test void simulatedExtractionDoesNotForkClearOrNotify() {
        try (var fixture = new Fixture()) {
            fixture.inventory.insertItem(0, fixture.stack("source", 1, false), false);
            UUID installed = fixture.binding(fixture.inventory.getStackInSlot(0));
            fixture.resetCounters();

            ItemStack extracted = fixture.inventory.extractItem(0, 1, true);

            assertEquals(installed, fixture.binding(extracted));
            assertEquals(installed, fixture.binding(fixture.inventory.getStackInSlot(0)));
            assertEquals(1, fixture.inventory.getStackInSlot(0).getCount());
            assertEquals(0, fixture.forks.get());
            assertEquals(0, fixture.changes.get());
        }
    }

    @Test void occupiedSlotRejectsOtherAndSameLibraryInsertionsAndDirectReplacement() {
        try (var fixture = new Fixture()) {
            ItemStack source = fixture.stack("source-a", 1, false);
            fixture.inventory.insertItem(0, source, false);
            UUID installed = fixture.binding(fixture.inventory.getStackInSlot(0));
            ItemStack other = fixture.stack("source-b", 1, false);
            fixture.resetCounters();

            assertSame(other, fixture.inventory.insertItem(0, other, false));
            assertSame(other, fixture.inventory.insertItem(0, other, true));
            assertSame(source, fixture.inventory.insertItem(0, source, false));
            assertFalse(fixture.inventory.isItemValid(0, other));
            fixture.inventory.setStackInSlot(0, other);
            fixture.inventory.setStackInSlot(0, source);

            assertEquals(installed, fixture.binding(fixture.inventory.getStackInSlot(0)));
            assertEquals(1, other.getCount());
            assertEquals(1, source.getCount());
            assertEquals(0, fixture.forks.get());
            assertEquals(0, fixture.changes.get());
        }
    }

    @Test void missingSourceIsRejectedBeforeInsertionOrDirectSetCanAllocateSnapshot() {
        try (var fixture = new Fixture()) {
            ItemStack missing = fixture.stack("missing-source", 1, false);
            fixture.states.get(missing).available = false;

            assertFalse(fixture.inventory.isItemValid(0, missing));
            assertSame(missing, fixture.inventory.insertItem(0, missing, true));
            assertSame(missing, fixture.inventory.insertItem(0, missing, false));
            fixture.inventory.setStackInSlot(0, missing);

            assertTrue(fixture.inventory.getStackInSlot(0).isEmpty());
            assertEquals(0, fixture.forks.get());
            assertEquals(0, fixture.changes.get());
        }
    }

    @Test void unboundItemRecoverySlotAndOversizedDirectSetDoNotFork() {
        try (var fixture = new Fixture()) {
            ItemStack unbound = fixture.stack("unbound", 1, false);
            fixture.states.get(unbound).binding = null;
            ItemStack ordinary = fixture.stack("bound", 1, false);
            ItemStack oversized = fixture.stack("oversized", 2, false);

            assertFalse(fixture.inventory.isItemValid(0, unbound));
            assertSame(unbound, fixture.inventory.insertItem(0, unbound, false));
            assertFalse(fixture.inventory.isItemValid(27, ordinary));
            assertSame(ordinary, fixture.inventory.insertItem(27, ordinary, false));
            fixture.inventory.setStackInSlot(0, oversized);

            assertTrue(fixture.inventory.getStackInSlot(0).isEmpty());
            assertEquals(0, fixture.forks.get());
            assertEquals(0, fixture.changes.get());
        }
    }

    @Test void extractionUnlocksTheSlotAndReinstallationForksAnIndependentIdentity() {
        try (var fixture = new Fixture()) {
            fixture.inventory.insertItem(0, fixture.stack("source", 1, false), false);
            UUID firstBinding = fixture.binding(fixture.inventory.getStackInSlot(0));

            ItemStack extracted = fixture.inventory.extractItem(0, 1, false);
            assertEquals(firstBinding, fixture.binding(extracted));
            assertTrue(fixture.inventory.getStackInSlot(0).isEmpty());
            assertTrue(fixture.inventory.insertItem(0, extracted, false).isEmpty());

            UUID secondBinding = fixture.binding(fixture.inventory.getStackInSlot(0));
            assertNotEquals(firstBinding, secondBinding);
            assertEquals(firstBinding, fixture.binding(extracted));
            assertEquals(2, fixture.forks.get());
        }
    }

    @Test void clientMirrorAcceptsAuthoritativeUuidReplacementWithoutForking() {
        try (var fixture = new Fixture()) {
            when(fixture.owner.getLevel()).thenReturn(mock(Level.class));
            ItemStack first = fixture.stack("server-update-a", 1, true);
            ItemStack second = fixture.stack("server-update-b", 1, true);
            fixture.inventory.setStackInSlot(0, first);
            fixture.inventory.setStackInSlot(0, second);
            UUID secondBinding = fixture.binding(second);

            MyriadMoldData.writePatternRef(second, UUID.randomUUID());
            second.setCount(0);

            ItemStack displayed = fixture.inventory.getStackInSlot(0);
            assertEquals(secondBinding, fixture.binding(displayed));
            assertEquals(1, displayed.getCount());
            assertEquals(0, fixture.forks.get());
        }
    }

    @Test void returnedStackCannotMutateInstalledCountOrLibraryBinding() {
        try (var fixture = new Fixture()) {
            fixture.inventory.insertItem(0, fixture.stack("source", 1, false), false);
            ItemStack returned = fixture.inventory.getStackInSlot(0);
            UUID binding = fixture.binding(returned);

            MyriadMoldData.writePatternRef(returned, UUID.randomUUID());
            returned.setCount(0);

            ItemStack stillInstalled = fixture.inventory.getStackInSlot(0);
            assertNotSame(returned, stillInstalled);
            assertEquals(binding, fixture.binding(stillInstalled));
            assertEquals(1, stillInstalled.getCount());
            assertEquals(1, fixture.forks.get(), "reading an already installed snapshot must not fork it again");
        }
    }

    @Test void legacyGetterMigratesOnceEvenWhenChangeListenerImmediatelySerializesInventory() {
        try (var fixture = new Fixture()) {
            fixture.restoreLegacy(0, fixture.stack("legacy", 1, false));
            var registries = mock(HolderLookup.Provider.class);
            var recursiveSave = new AtomicReference<CompoundTag>();
            fixture.onChange.set(() -> recursiveSave.set(fixture.inventory.serializeNBT(registries)));

            ItemStack migrated = fixture.inventory.getStackInSlot(0);

            assertEquals(1, fixture.forks.get());
            assertEquals(1, fixture.changes.get());
            assertNotNull(recursiveSave.get());
            assertEquals(1, recursiveSave.get().getList("Items", Tag.TAG_COMPOUND).size());
            assertEquals(fixture.binding(migrated), fixture.binding(fixture.inventory.getStackInSlot(0)));
            assertEquals(1, fixture.forks.get());
        }
    }

    @Test void legacySerializationBatchesMigrationAndProtectsRecursiveExternalSave() {
        try (var fixture = new Fixture()) {
            fixture.restoreLegacy(0, fixture.stack("legacy-a", 1, false));
            fixture.restoreLegacy(1, fixture.stack("legacy-b", 1, false));
            var registries = mock(HolderLookup.Provider.class);
            var recursiveSave = new AtomicReference<CompoundTag>();
            fixture.onChange.set(() -> recursiveSave.set(fixture.inventory.serializeNBT(registries)));

            CompoundTag saved = fixture.inventory.serializeNBT(registries);

            assertEquals(2, fixture.forks.get());
            assertEquals(1, fixture.changes.get(), "multiple legacy slots must emit one native change callback");
            assertNotNull(recursiveSave.get());
            assertEquals(2, saved.getList("Items", Tag.TAG_COMPOUND).size());
            assertEquals(2, recursiveSave.get().getList("Items", Tag.TAG_COMPOUND).size());
            fixture.inventory.serializeNBT(registries);
            assertEquals(2, fixture.forks.get());
            assertEquals(1, fixture.changes.get());
        }
    }

    @Test void failedSnapshotPreparationKeepsOfferedItemAndSlotUnchanged() {
        try (var fixture = new Fixture()) {
            ItemStack offered = fixture.stack("source-disappears-after-validation", 1, false);
            fixture.patterns.when(() -> InstalledStretcherPatterns.snapshotForAssembly(fixture.level, offered))
                    .thenReturn(ItemStack.EMPTY);

            assertSame(offered, fixture.inventory.insertItem(0, offered, false));
            fixture.inventory.setStackInSlot(0, offered);

            assertTrue(fixture.inventory.getStackInSlot(0).isEmpty());
            assertEquals(1, offered.getCount());
            assertEquals(0, fixture.changes.get());
        }
    }

    private static final class StackState {
        final String name;
        final boolean snapshot;
        UUID binding;
        int count;
        boolean available = true;

        StackState(String name, UUID binding, int count, boolean snapshot) {
            this.name = name;
            this.binding = binding;
            this.count = count;
            this.snapshot = snapshot;
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Map<ItemStack, StackState> states = new IdentityHashMap<>();
        final MockedStatic<InstalledStretcherPatterns> patterns = mockStatic(InstalledStretcherPatterns.class);
        final MockedStatic<MyriadMoldData> moldData = mockStatic(MyriadMoldData.class);
        final ServerLevel level = mock(ServerLevel.class);
        final BlockEntity owner = mock(BlockEntity.class);
        final AtomicInteger forks = new AtomicInteger();
        final AtomicInteger changes = new AtomicInteger();
        final AtomicReference<Runnable> onChange = new AtomicReference<>(() -> {});
        final AssemblyStretcherInventory inventory;

        Fixture() {
            when(owner.getLevel()).thenReturn(level);
            patterns.when(() -> InstalledStretcherPatterns.isStretcher(any(ItemStack.class)))
                    .thenAnswer(call -> states.containsKey(call.getArgument(0)));
            patterns.when(() -> InstalledStretcherPatterns.isAssemblySnapshot(any(ItemStack.class)))
                    .thenAnswer(call -> {
                        StackState state = states.get(call.getArgument(0));
                        return state != null && state.snapshot;
                    });
            patterns.when(() -> InstalledStretcherPatterns.canSnapshotForAssembly(any(ServerLevel.class), any(ItemStack.class)))
                    .thenAnswer(call -> {
                        StackState state = states.get(call.getArgument(1));
                        return state != null && state.available && state.binding != null;
                    });
            patterns.when(() -> InstalledStretcherPatterns.snapshotForAssembly(any(ServerLevel.class), any(ItemStack.class)))
                    .thenAnswer(call -> {
                        StackState state = states.get(call.getArgument(1));
                        forks.incrementAndGet();
                        return state == null || !state.available ? ItemStack.EMPTY
                                : stack(state.name + "-installed", UUID.randomUUID(), 1, true);
                    });
            moldData.when(() -> MyriadMoldData.readPatternRef(any(ItemStack.class)))
                    .thenAnswer(call -> binding(call.getArgument(0)));
            moldData.when(() -> MyriadMoldData.writePatternRef(any(ItemStack.class), any(UUID.class)))
                    .thenAnswer(call -> {
                        states.get(call.getArgument(0)).binding = call.getArgument(1);
                        return null;
                    });
            inventory = new AssemblyStretcherInventory(() -> 27, ignored -> true, () -> {
                changes.incrementAndGet();
                onChange.get().run();
            }, owner);
        }

        ItemStack stack(String name, int count, boolean snapshot) {
            return stack(name, UUID.randomUUID(), count, snapshot);
        }

        ItemStack stack(String name, UUID binding, int count, boolean snapshot) {
            ItemStack item = mock(ItemStack.class, name);
            var state = new StackState(name, binding, count, snapshot);
            states.put(item, state);
            when(item.isEmpty()).thenAnswer(ignored -> state.count <= 0);
            when(item.getCount()).thenAnswer(ignored -> state.count);
            when(item.getMaxStackSize()).thenReturn(64);
            when(item.copy()).thenAnswer(ignored -> copy(state, state.count));
            when(item.copyWithCount(anyInt())).thenAnswer(call -> copy(state, call.getArgument(0)));
            doAnswer(call -> {
                state.count = call.getArgument(0);
                return null;
            }).when(item).setCount(anyInt());
            // Exercise the real handler's NBT traversal and batching, not the
            // native ItemStack codec: only a test identity is written here.
            when(item.save(any(HolderLookup.Provider.class), any(Tag.class))).thenAnswer(call -> {
                CompoundTag tag = (CompoundTag) call.getArgument(1);
                tag.putString("test_binding", String.valueOf(state.binding));
                return tag;
            });
            return item;
        }

        private ItemStack copy(StackState original, int count) {
            if (count <= 0) return ItemStack.EMPTY;
            ItemStack copy = stack(original.name + "-copy", original.binding, count, original.snapshot);
            states.get(copy).available = original.available;
            return copy;
        }

        UUID binding(ItemStack item) {
            StackState state = states.get(item);
            return state == null ? null : state.binding;
        }

        void resetCounters() {
            forks.set(0);
            changes.set(0);
        }

        void restoreLegacy(int slot, ItemStack item) {
            when(owner.getLevel()).thenReturn(null);
            inventory.setStackInSlot(slot, item);
            when(owner.getLevel()).thenReturn(level);
            resetCounters();
        }

        @Override public void close() {
            moldData.close();
            patterns.close();
        }
    }
}
