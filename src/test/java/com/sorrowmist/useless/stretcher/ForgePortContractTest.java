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
                    || e.getName().contains("/storytest/") || e.getName().contains("/capsulecheck/")
                    || e.getName().contains("/upstreamcheck/") || e.getName().matches(".*Checks.*\\.class")
                    || e.getName().contains("stretcher_capsule_check") || e.getName().contains("useless_stretcher_test")));
            assertNull(jar.getEntry("assets/useless_stretcher/textures/item/wondrous_staff.png"));
        }
    }

    @Test void upstreamBackportShipsForgeSlotMappingsAndCompleteTranslations() throws Exception {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(Path.of("gradle.properties"))) { properties.load(in); }
        try (var jar = new ZipFile("build/libs/useless_stretcher-" + properties.getProperty("mod_version") + ".jar")) {
            for (String name : new String[]{"client/StaffScreen", "client/StaffFluidCollectionScreen",
                    "content/item/StaffFluidCollector", "content/ae/InstalledStretcherPatterns",
                    "content/ae/PassiveStretcherCursor", "content/capsule/CapsulePieceGeometry"})
                assertNotNull(jar.getEntry("com/sorrowmist/useless/stretcher/" + name + ".class"), name);
            String refmap = new String(jar.getInputStream(jar.getEntry("useless_stretcher.refmap.json")).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(refmap.contains("InstalledStretcherSlotMixin"));
            assertTrue(refmap.contains("m_5857_"), "Forge release must inject the remapped Slot.mayPlace method");
            for (String language : new String[]{"en_us", "zh_cn", "zh_tw"}) {
                var resource = jar.getEntry("assets/useless_stretcher/lang/" + language + ".json");
                var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(jar.getInputStream(resource),
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (String key : new String[]{"staff_tabs.machines", "staff_tabs.fluids", "fluid.title", "fluid.pending", "fluid.uncertain"})
                    assertTrue(json.has("gui.useless_stretcher." + key), language + ": " + key);
                assertTrue(json.has("tooltip.useless_stretcher.stretcher.hint_install"));
            }
        }
    }

    @Test void configurationTranslationShipsProductionScreenMappings() throws Exception {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(Path.of("gradle.properties"))) { properties.load(in); }
        try (var jar = new ZipFile("build/libs/useless_stretcher-" + properties.getProperty("mod_version") + ".jar")) {
            String mixin = "com/sorrowmist/useless/stretcher/mixin/PortConfigScreenTranslationMixin";
            assertNotNull(jar.getEntry(mixin + ".class"));
            var refmap = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(
                    jar.getInputStream(jar.getEntry("useless_stretcher.refmap.json")), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("mappings").getAsJsonObject(mixin);
            assertNotNull(refmap, "Screen overrides need mappings even though the target is a mod class");
            assertTrue(refmap.get("init()V").getAsString().contains("m_7856_()V"));
            assertTrue(refmap.get("render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V").getAsString().contains("m_88315_"));
            assertTrue(refmap.get("Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;")
                    .getAsString().contains("m_237113_"));
        }
    }

    @Test void localModelTexturesResolveAfterResourceCleanup() throws Exception {
        Path assets = Path.of("src/main/resources/assets/useless_stretcher");
        try (var models = Files.walk(assets.resolve("models"))) {
            for (var path : models.filter(p -> p.toString().endsWith(".json")).toList()) {
                var model = com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                if (!model.has("textures")) continue;
                for (var texture : model.getAsJsonObject("textures").entrySet()) {
                    String value = texture.getValue().getAsString();
                    if (value.startsWith("useless_stretcher:"))
                        assertTrue(Files.isRegularFile(assets.resolve("textures/" + value.substring("useless_stretcher:".length()) + ".png")),
                                path + ": " + texture.getKey() + " -> " + value);
                }
            }
        }
    }

    @Test void productionHasNoTemporaryConsoleOrUnthrottledFailureLogging() throws Exception {
        try (var sources = Files.walk(Path.of("src/main/java"))) {
            for (var path : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                for (String diagnostic : new String[]{"System.out.", "System.err.", ".printStackTrace(",
                        "LogUtils.getLogger().warn(", "LogUtils.getLogger().error("})
                    assertFalse(source.contains(diagnostic), path + ": " + diagnostic);
            }
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
