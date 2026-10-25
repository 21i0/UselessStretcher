package com.sorrowmist.useless.stretcher.content.mold;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoldCatalogGroupingTest {
    private static final List<String> APP_MEK_MEKANISM_MACHINES = List.of(
            "metallurgic_infuser",
            "osmium_compressor",
            "purification_chamber",
            "chemical_injection_chamber",
            "painting_machine",
            "chemical_infuser",
            "pigment_mixer",
            "isotopic_centrifuge",
            "solar_neutron_activator",
            "electrolytic_separator",
            "chemical_dissolution_chamber",
            "chemical_crystallizer",
            "chemical_washer",
            "pressurized_reaction_chamber",
            "rotary_condensentrator",
            "chemical_oxidizer",
            "pigment_extractor",
            "antiprotonic_nucleosynthesizer",
            "sps_casing",
            "boiler_casing");

    @Test
    void appMekConvertedMachinesFollowTheirMekanismItemNamespace() {
        for (String path : APP_MEK_MEKANISM_MACHINES) {
            assertEquals("mekanism", MoldCatalog.groupFor(id("mekanism:" + path)),
                    "AppMek source must not hide Mekanism machine " + path);
        }
    }

    @Test
    void ae2MachinesFollowTheAe2ItemNamespace() {
        for (String path : List.of("charger", "inscriber", "pattern_provider", "molecular_assembler")) {
            assertEquals("ae2", MoldCatalog.groupFor(id("ae2:" + path)));
        }
    }

    @Test
    void unrelatedNamespacesRemainSeparate() {
        assertEquals("appmek", MoldCatalog.groupFor(id("appmek:chemical_storage_cell_1k")));
        assertEquals("mekanismgenerators", MoldCatalog.groupFor(id("mekanismgenerators:fusion_reactor_frame")));
        assertEquals("extendedae", MoldCatalog.groupFor(id("extendedae:circuit_cutter")));
        assertEquals("compatibility", MoldCatalog.groupFor(null));
    }

    @Test
    void persistedCacheKeyCarriesAnExplicitDirectoryVersion() {
        String fingerprint = MoldCatalog.fingerprint(List.of());
        assertTrue(fingerprint.startsWith(MoldCatalog.FINGERPRINT_VERSION + ":"),
                "old source-grouped catalogs must miss the new cache key");
        assertEquals(fingerprint, MoldCatalog.fingerprint(List.of()));
    }

    @Test
    void oldPersistedCatalogCannotMatchTheVersionedFingerprint() {
        String current = MoldCatalog.fingerprint(List.of());
        CompoundTag legacy = new CompoundTag();
        legacy.putString("fingerprint", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        assertFalse(MoldCatalogSavedData.load(legacy, null).matches(current));

        CompoundTag versioned = new CompoundTag();
        versioned.putString("fingerprint", current);
        assertTrue(MoldCatalogSavedData.load(versioned, null).matches(current));
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
