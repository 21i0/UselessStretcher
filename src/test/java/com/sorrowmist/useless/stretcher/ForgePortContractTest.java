package com.sorrowmist.useless.stretcher;

import com.sorrowmist.useless.stretcher.compat.NativeMath;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class ForgePortContractTest {
    @Test void numericBridgesClampWithoutNarrowingOverflow() {
        assertEquals(64, NativeMath.clamp(Long.MAX_VALUE, 0, 64));
        assertEquals(0, NativeMath.clamp(Long.MIN_VALUE, 0, 64));
        assertEquals(3, NativeMath.clamp(3, 0, 64));
        assertEquals(64L, NativeMath.clamp(Long.MAX_VALUE, 0L, 64L));
        assertEquals(1.0F, NativeMath.clamp(2.0F, 0.0F, 1.0F));
        assertEquals(0.0, NativeMath.clamp(-1.0, 0.0, 1.0));
    }

    @Test void releaseUsesForgeMetadataAndDoesNotContainVerificationCode() throws Exception {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(Path.of("gradle.properties"))) { properties.load(in); }
        String version = properties.getProperty("mod_version");
        try (var jar = new ZipFile("build/libs/useless_stretcher-" + version + ".jar")) {
            var entry = jar.getEntry("META-INF/mods.toml");
            assertNotNull(entry);
            String metadata = new String(jar.getInputStream(entry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(metadata.contains("version=\"" + version + "\""));
            assertNull(jar.getEntry("META-INF/neoforge.mods.toml"));
            assertNotNull(jar.getEntry("data/useless_stretcher/recipes/useless_stretcher.json"));
            assertNotNull(jar.getEntry("com/sorrowmist/useless/stretcher/compat/OccultismAccelerationCompat.class"));
            assertNotNull(jar.getEntry("com/sorrowmist/useless/stretcher/compat/RftoolsBuilderAccelerationCompat.class"));
            assertNotNull(jar.getEntry("META-INF/jarjar/metadata.json"));
            assertFalse(jar.stream().anyMatch(e -> e.getName().contains("/regression/")
                    || e.getName().contains("/storytest/") || e.getName().contains("/clientprobe/")
                    || e.getName().contains("/capsulecheck/")
                    || e.getName().contains("ForgeCapsule") || e.getName().contains("stretcher_capsule_check")));
        }
    }

    @Test void upstreamKnownIdleBowlContractIsPreservedInOptionalBridge() throws Exception {
        Path root = Path.of("src/main/java/com/sorrowmist/useless/stretcher");
        String bridge = Files.readString(root.resolve("compat/OccultismAccelerationCompat.java"));
        assertTrue(bridge.contains("public static Boolean knownActivity(BlockEntity target)"));
        assertTrue(bridge.contains("if (!(target instanceof GoldenSacrificialBowlBlockEntity bowl)) return null;"));
        assertTrue(bridge.contains("bowl.getCurrentRitualRecipe() != null"));
        assertTrue(bridge.contains("bowl.sacrificeFulfilled() && bowl.itemUseFulfilled()"));
        String probe = Files.readString(root.resolve("content/acceleration/MachineActivityProbe.java"));
        assertTrue(probe.indexOf("if (known != null) return newTarget || known;")
                < probe.indexOf("current instanceof ChangedTickAccessor"));
        assertTrue(probe.contains("boolean newTarget = current != target;"));
    }
}
