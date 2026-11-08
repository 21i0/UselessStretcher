package com.sorrowmist.useless.stretcher.content.ae;

import appeng.api.stacks.AEItemKey;
import com.sorrowmist.useless.stretcher.VanillaTestRegistries;
import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData;
import com.sorrowmist.useless.stretcher.content.mold.MyriadPatternStore;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real binding, ItemStack components and store; only world lookup is substituted. */
class InstalledStretcherBindingTest {
    @BeforeAll static void vanillaRegistries() { VanillaTestRegistries.initialize(); }

    @Test void actualInstallationAndReinstallationCreateDistinctIndependentReferences() {
        try (var fixture = new Fixture()) {
            UUID source = UUID.randomUUID();
            var iron = AEItemKey.of(new ItemStack(Items.IRON_INGOT));
            fixture.localStore.replace(source, ResourceLocation.parse("minecraft:furnace"),
                    new LinkedHashSet<>(List.of(iron)));
            ItemStack offered = fixture.item(source);
            InstalledStretcherPatterns.bindDimension(offered, fixture.local);

            assertTrue(InstalledStretcherPatterns.canSnapshotForAssembly(fixture.local, offered));
            ItemStack installed = InstalledStretcherPatterns.snapshotForAssembly(fixture.local, offered);
            ItemStack reinstalled = InstalledStretcherPatterns.snapshotForAssembly(fixture.local, installed);
            UUID first = MyriadMoldData.readPatternRef(installed);
            UUID second = MyriadMoldData.readPatternRef(reinstalled);

            assertNotEquals(source, first);
            assertNotEquals(first, second);
            assertEquals(source, MyriadMoldData.readPatternRef(offered));
            assertEquals(2, offered.getCount());
            assertEquals(2, installed.getCount(), "preparation must preserve the offered quantity");
            assertTrue(InstalledStretcherPatterns.isAssemblySnapshot(installed));
            assertTrue(InstalledStretcherPatterns.isAssemblySnapshot(reinstalled));
            fixture.localStore.clear(source);
            assertEquals(List.of(iron), fixture.localStore.snapshot(first));
            assertEquals(List.of(iron), fixture.localStore.snapshot(second));
            fixture.localStore.clear(first);
            assertEquals(List.of(iron), fixture.localStore.snapshot(second));
        }
    }

    @Test void explicitDimensionControlsSourceAndBothSnapshotMarkers() {
        try (var fixture = new Fixture()) {
            UUID source = UUID.randomUUID();
            UUID retained = fixture.remoteStore.fork(source);
            ItemStack offered = fixture.item(retained);
            InstalledStretcherPatterns.bindDimension(offered, fixture.remote);

            ItemStack installed = InstalledStretcherPatterns.snapshotForAssembly(fixture.local, offered);
            UUID ref = MyriadMoldData.readPatternRef(installed);
            CompoundTag data = installed.get(DataComponents.CUSTOM_DATA).copyTag();

            assertTrue(fixture.remoteStore.contains(ref));
            assertFalse(fixture.localStore.contains(ref));
            assertEquals("minecraft:the_end", data.getString("pattern_dimension"));
            assertEquals(data.getString("pattern_dimension"), data.getString("assembly_snapshot_dimension"));
            assertEquals(ref.toString(), data.getString("assembly_snapshot_ref"));
        }
    }

    @Test void missingAndMalformedExplicitDimensionsNeverFallbackToLocalLibrary() {
        try (var fixture = new Fixture()) {
            UUID retained = fixture.localStore.fork(UUID.randomUUID());
            for (String dimension : List.of("missing:dimension", "invalid dimension!")) {
                ItemStack offered = fixture.item(retained);
                CustomData.update(DataComponents.CUSTOM_DATA, offered,
                        tag -> tag.putString("pattern_dimension", dimension));

                assertFalse(InstalledStretcherPatterns.canSnapshotForAssembly(fixture.local, offered));
                assertTrue(InstalledStretcherPatterns.snapshotForAssembly(fixture.local, offered).isEmpty());
                assertEquals(retained, MyriadMoldData.readPatternRef(offered));
            }
        }
    }

    @Test void legacyUuidWithoutDimensionResolvesItsActualLoadedDimension() {
        try (var fixture = new Fixture()) {
            UUID retained = fixture.remoteStore.fork(UUID.randomUUID());
            ItemStack offered = fixture.item(retained);

            ItemStack installed = InstalledStretcherPatterns.snapshotForAssembly(fixture.local, offered);

            assertEquals("minecraft:the_end", installed.get(DataComponents.CUSTOM_DATA).copyTag()
                    .getString("pattern_dimension"));
            assertTrue(fixture.remoteStore.contains(MyriadMoldData.readPatternRef(installed)));
            assertFalse(fixture.localStore.contains(MyriadMoldData.readPatternRef(installed)));
            assertFalse(offered.get(DataComponents.CUSTOM_DATA).copyTag().contains("pattern_dimension"));
        }
    }

    @Test void unsupportedSourceFormatIsRejectedWithoutChangingOfferedBinding() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var unknown = new CompoundTag();
        unknown.putInt("format", 99);
        unknown.putString("future", "retain-me");
        MyriadPatternStore opaque = MyriadPatternStore.load(unknown, registries);
        try (var fixture = new Fixture()) {
            fixture.lookup.when(() -> MyriadPatternStore.get(fixture.remote)).thenReturn(opaque);
            UUID source = UUID.randomUUID();
            ItemStack offered = fixture.item(source);
            InstalledStretcherPatterns.bindDimension(offered, fixture.remote);

            assertFalse(InstalledStretcherPatterns.canSnapshotForAssembly(fixture.local, offered));
            assertTrue(InstalledStretcherPatterns.snapshotForAssembly(fixture.local, offered).isEmpty());
            assertEquals(source, MyriadMoldData.readPatternRef(offered));
            assertEquals(unknown, opaque.save(new CompoundTag(), registries));
        }
    }

    @Test void unboundItemsAreRejectedAndSnapshotMarkerMustMatchBindingAndDimension() {
        try (var fixture = new Fixture()) {
            ItemStack unbound = new ItemStack(Items.PAPER);
            assertFalse(InstalledStretcherPatterns.canSnapshotForAssembly(fixture.local, unbound));
            assertTrue(InstalledStretcherPatterns.snapshotForAssembly(fixture.local, unbound).isEmpty());
            ItemStack snapshot = InstalledStretcherPatterns.snapshotForAssembly(fixture.local,
                    fixture.item(UUID.randomUUID()));
            assertTrue(InstalledStretcherPatterns.isAssemblySnapshot(snapshot));
            ItemStack wrongUuid = snapshot.copy();
            MyriadMoldData.writePatternRef(wrongUuid, UUID.randomUUID());
            assertFalse(InstalledStretcherPatterns.isAssemblySnapshot(wrongUuid));
            ItemStack wrongDimension = snapshot.copy();
            InstalledStretcherPatterns.bindDimension(wrongDimension, fixture.remote);
            assertFalse(InstalledStretcherPatterns.isAssemblySnapshot(wrongDimension));
            InstalledStretcherPatterns.clearAssemblySnapshot(snapshot);
            assertFalse(InstalledStretcherPatterns.isAssemblySnapshot(snapshot));
            assertNotNull(MyriadMoldData.readPatternRef(snapshot));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final ServerLevel local = mock(ServerLevel.class);
        final ServerLevel remote = mock(ServerLevel.class);
        final MinecraftServer server = mock(MinecraftServer.class);
        final MyriadPatternStore localStore = new MyriadPatternStore();
        final MyriadPatternStore remoteStore = new MyriadPatternStore();
        final MockedStatic<MyriadPatternStore> lookup = mockStatic(MyriadPatternStore.class);

        Fixture() {
            when(local.getServer()).thenReturn(server);
            when(remote.getServer()).thenReturn(server);
            when(local.dimension()).thenReturn(Level.OVERWORLD);
            when(remote.dimension()).thenReturn(Level.END);
            when(server.getLevel(Level.OVERWORLD)).thenReturn(local);
            when(server.getLevel(Level.END)).thenReturn(remote);
            when(server.getAllLevels()).thenReturn(List.of(local, remote));
            lookup.when(() -> MyriadPatternStore.get(local)).thenReturn(localStore);
            lookup.when(() -> MyriadPatternStore.get(remote)).thenReturn(remoteStore);
        }

        ItemStack item(UUID binding) {
            ItemStack item = new ItemStack(Items.PAPER, 2);
            MyriadMoldData.writePatternRef(item, binding);
            return item;
        }

        @Override public void close() { lookup.close(); }
    }
}
