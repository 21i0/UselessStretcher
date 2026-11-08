package com.sorrowmist.useless.stretcher.content.mold;

import appeng.api.stacks.AEItemKey;
import com.sorrowmist.useless.stretcher.VanillaTestRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Registry bootstrap only: no client, world, server, mod loader or game loop is created. */
class PatternSnapshotPersistenceTest {
    @BeforeAll static void bootstrapRegistries() {
        VanillaTestRegistries.initialize();
    }

    @Test void nonemptyNativeItemCodecRoundTripPreservesSnapshotAndLaterSourceEdits() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var store = new MyriadPatternStore();
        UUID source = UUID.randomUUID();
        var group = ResourceLocation.parse("minecraft:furnace");
        var iron = AEItemKey.of(new ItemStack(Items.IRON_INGOT));
        var gold = AEItemKey.of(new ItemStack(Items.GOLD_INGOT));
        store.replace(source, group, new LinkedHashSet<>(List.of(iron, gold)));
        UUID installed = store.fork(source);
        var saved = store.save(new CompoundTag(), registries);
        assertEquals(1, saved.getList("states", 10).size(), "one shared palette is persisted once");

        var restored = MyriadPatternStore.load(saved, registries);
        assertTrue(restored.isResolved(source));
        assertTrue(restored.isResolved(installed));
        assertEquals(List.of(iron, gold), restored.snapshot(installed));
        restored.removeKeys(source, List.of(iron));
        assertEquals(List.of(gold), restored.snapshot(source));
        assertEquals(List.of(iron, gold), restored.snapshot(installed));
        var twice = MyriadPatternStore.load(restored.save(new CompoundTag(), registries), registries);
        assertEquals(List.of(iron, gold), twice.snapshot(installed));
    }

    @Test void oldPaletteFormatMigratesWithoutChangingUuidOrContents() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        UUID id = UUID.randomUUID();
        var entry = emptyState();
        entry.putString("id", id.toString());
        var entries = new ListTag(); entries.add(entry);
        var old = new CompoundTag(); old.putInt("format", 2); old.put("entries", entries);

        var store = MyriadPatternStore.load(old, registries);
        assertTrue(store.contains(id));
        assertTrue(store.isResolved(id));
        UUID installed = store.fork(id);
        var restored = MyriadPatternStore.load(store.save(new CompoundTag(), registries), registries);
        assertTrue(restored.contains(id));
        assertTrue(restored.contains(installed));
        assertTrue(restored.snapshot(installed).isEmpty());
    }

    @Test void damagedPaletteIndexIsQuarantinedAndItsRawDataSurvivesRepeatedSaves() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        UUID id = UUID.randomUUID();
        var state = emptyState();
        state.putString("original_marker", "preserve-the-unreadable-library");
        var group = new CompoundTag(); group.putString("mold", "minecraft:furnace");
        group.putIntArray("indices", new int[]{42});
        var groups = new ListTag(); groups.add(group); state.put("groups", groups);
        var states = new ListTag(); states.add(state);
        var entry = new CompoundTag(); entry.putString("id", id.toString()); entry.putInt("state", 0);
        var entries = new ListTag(); entries.add(entry);
        var tag = new CompoundTag(); tag.putInt("format", 3); tag.put("states", states); tag.put("entries", entries);

        var store = MyriadPatternStore.load(tag, registries);
        assertTrue(store.contains(id), "an unreadable UUID must not resolve to a different dimension");
        assertFalse(store.isResolved(id));
        assertTrue(store.snapshot(id).isEmpty());
        var saved = store.save(new CompoundTag(), registries);
        assertTrue(saved.toString().contains("preserve-the-unreadable-library"));
        var again = MyriadPatternStore.load(saved, registries);
        assertFalse(again.isResolved(id));
        assertEquals(saved, again.save(new CompoundTag(), registries));
        assertThrows(IllegalStateException.class, () -> again.fork(id));
        assertEquals(saved, again.save(new CompoundTag(), registries), "rejected forks must not add UUIDs");
        assertThrows(IllegalStateException.class, () -> again.replace(id,
                ResourceLocation.parse("minecraft:furnace"), new LinkedHashSet<>()));
    }

    @Test void nonemptyOldPaletteAndDirectPatternFormatsRetainAllPatternsWhenMigrated() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var iron = AEItemKey.of(new ItemStack(Items.IRON_INGOT));
        var gold = AEItemKey.of(new ItemStack(Items.GOLD_INGOT));
        UUID id = UUID.randomUUID();
        for (boolean paletteFormat : List.of(false, true)) {
            var palette = new ListTag();
            palette.add(iron.toStack(1).saveOptional(registries));
            palette.add(gold.toStack(1).saveOptional(registries));
            var group = new CompoundTag(); group.putString("mold", "minecraft:furnace");
            if (paletteFormat) group.putIntArray("indices", new int[]{0, 1});
            else group.put("patterns", palette.copy());
            var groups = new ListTag(); groups.add(group);
            var entry = new CompoundTag(); entry.putString("id", id.toString()); entry.put("groups", groups);
            if (paletteFormat) entry.put("palette", palette);
            var entries = new ListTag(); entries.add(entry);
            var old = new CompoundTag(); old.put("entries", entries);
            if (paletteFormat) old.putInt("format", 2);

            var migrated = MyriadPatternStore.load(old, registries);
            assertTrue(migrated.isResolved(id));
            assertEquals(List.of(iron, gold), migrated.snapshot(id));
            UUID installed = migrated.fork(id);
            var restored = MyriadPatternStore.load(migrated.save(new CompoundTag(), registries), registries);
            restored.clear(id);
            assertEquals(List.of(iron, gold), restored.snapshot(installed));
        }
    }

    @Test void unavailableItemCodecPreservesRawLibraryAcrossSaves() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        UUID id = UUID.randomUUID();
        var stack = new CompoundTag(); stack.putString("id", "missing_mod:encoded_pattern"); stack.putInt("count", 1);
        var components = new CompoundTag(); components.putString("missing_mod:private_component", "keep-exactly");
        stack.put("components", components);
        var palette = new ListTag(); palette.add(stack);
        var group = new CompoundTag(); group.putString("mold", "minecraft:furnace"); group.putIntArray("indices", new int[]{0});
        var groups = new ListTag(); groups.add(group);
        var state = new CompoundTag(); state.put("palette", palette); state.put("groups", groups);
        var states = new ListTag(); states.add(state);
        var entry = new CompoundTag(); entry.putString("id", id.toString()); entry.putInt("state", 0);
        var entries = new ListTag(); entries.add(entry);
        var original = new CompoundTag(); original.putInt("format", 3); original.put("states", states); original.put("entries", entries);

        var store = MyriadPatternStore.load(original, registries);
        assertTrue(store.contains(id));
        assertFalse(store.isResolved(id));
        var saved = store.save(new CompoundTag(), registries);
        assertEquals(state, saved.getList("states", 10).getCompound(0).getCompound("useless_stretcher_preserved_state"));
        var restored = MyriadPatternStore.load(saved, registries);
        assertFalse(restored.isResolved(id));
        assertEquals(saved, restored.save(new CompoundTag(), registries));
    }

    @Test void futureFormatIsPreservedAsOpaqueDataInsteadOfOverwrittenWithAnEmptyStore() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var future = new CompoundTag(); future.putInt("format", 99);
        future.putString("future_data", "keep-exactly");
        var store = MyriadPatternStore.load(future, registries);
        assertFalse(store.isResolved(UUID.randomUUID()));
        assertEquals(future, store.save(new CompoundTag(), registries));
        assertThrows(IllegalStateException.class, () -> store.fork(UUID.randomUUID()));
    }

    private static CompoundTag emptyState() {
        var tag = new CompoundTag(); tag.put("palette", new ListTag()); tag.put("groups", new ListTag());
        return tag;
    }
}
