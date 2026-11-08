package com.sorrowmist.useless.stretcher.verification;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sorrowmist.useless.stretcher.content.catalog.CatalogPatchSupport;
import com.sorrowmist.useless.stretcher.mixin.UselessStretcherMixinPlugin;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Focused checks against the actual production gate methods, without launching Minecraft,
 * a mod loader, a world or a server. Loader metadata is mocked; gate code is never mocked.
 *
 * This is a post-ABI-audit contract: .5 is expected to be enabled only after its real ABI
 * has been reviewed. Passing these checks proves gate selection, not that ABI review.
 *
 * Run main with a new result JSON path, public-publication JEI versions CSV, and the
 * independently audited internal-mixin JEI versions CSV ("-" means an empty allowlist).
 * For example, public APIs may support both 19.57.0.451 and 19.70.0.460 while the four
 * internal mixins still support only 19.57.0.451. Inputs never imply private ABI proof.
 * Compile and run with the candidate production
 * classes/JAR first on the classpath, plus the existing Mockito, ByteBuddy, Gson and FML
 * libraries. No vanilla bootstrap or existing 184-test regression run is required.
 */
public final class VersionGateChecks {
    private static final String MIXIN_PACKAGE = "com.sorrowmist.useless.stretcher.mixin.";
    private static final String EXACT_JEI = "19.57.0.451";
    private static final String NEW_JEI = "19.70.0.460";
    private static final List<String> VERIFIED_BASES = List.of(
            "1.21.1-2.4.6", "1.21.1-2.4.6.2", "1.21.1-2.4.6.5");
    private static final List<String> BUDGET_MIXINS = List.of(
            "BudgetedRecipeCatalogMixin", "BudgetedClientCatalogMixin", "CatalogJeiTimingMixin");
    private static final List<String> EXACT_JEI_MIXINS = List.of(
            "CatalogJeiLookupMixin", "CatalogJeiGuiMixin",
            "CatalogJeiBuildTimingMixin", "CatalogJeiIndexTimingMixin");
    private static final String[] MATRIX_BASES = {
            null, "1.21.1-2.4.6", "1.21.1-2.4.6.2", "1.21.1-2.4.6.5",
            "1.21.1-2.4.6.3", "1.21.1-2.4.6.4", "1.21.1-2.4.7", "1.21.1-9.9.9"
    };
    private static final String[] MATRIX_JEI = {
            null, EXACT_JEI, NEW_JEI, "19.56.0.450", "19.70.0.461", "19.70"
    };
    private static final UselessStretcherMixinPlugin PLUGIN = new UselessStretcherMixinPlugin();
    private static final JsonArray CHECKS = new JsonArray();
    private static int passed;
    private static int failed;
    private static int assertions;
    private static List<String> expectedPublicationJei;
    private static List<String> expectedInternalMixinJei;

    private VersionGateChecks() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException(
                "Expected new-result.json public-publication-jei-csv internal-mixin-jei-csv (use - for empty)");
        Path output = Path.of(args[0]);
        if (Files.exists(output)) throw new IllegalArgumentException("Refusing to overwrite " + output);
        expectedPublicationJei = parseVersions(args[1]);
        expectedInternalMixinJei = parseVersions(args[2]);

        String[] exactInputs = {
                null, "", "1.21.1-2.4.6", "1.21.1-2.4.6.2", "1.21.1-2.4.6.5",
                "1.21.1-2.4.6.3", "1.21.1-2.4.6.4", "1.21.1-2.4.6.7", "1.21.1-2.4.7",
                "1.21.1-9.9.9", "2.4.6.5", "1.21.1-2.4.6.50", "1.21.1-2.4.6.5-beta",
                " 1.21.1-2.4.6.5", "1.21.1-2.4.6.5 ", "[1.21.1-2.4.6.5,)"
        };
        for (String version : exactInputs) {
            check("support_exact_version_" + printable(version), () ->
                    expect(CatalogPatchSupport.verifiedVersion(version), verified(version), "verifiedVersion"));
        }

        // Each matrix cell calls all seven real mixin gates and both real support entry points.
        // The early and normal metadata agree here, so Plugin and Support must agree as well.
        for (Route route : List.of(Route.EARLY, Route.NULL_EARLY)) {
            for (String base : MATRIX_BASES) {
                for (String jei : MATRIX_JEI) {
                    check("matrix_" + route + "_base_" + printable(base) + "_jei_" + printable(jei), () -> {
                        try (LoaderFixture fixture = new LoaderFixture(route, versions(base, jei), versions(base, jei))) {
                            assertPluginMatrix(base, jei);
                            if (route == Route.EARLY) fixture.lateStatic.verifyNoInteractions();
                            assertSupport(base, jei);
                        }
                    });
                }
            }
        }

        for (Route route : List.of(Route.THROW_RUNTIME, Route.THROW_LINKAGE)) {
            for (String base : List.of("1.21.1-2.4.6.5", "1.21.1-2.4.6.4")) {
                check("early_failure_falls_back_" + route + "_" + base, () -> {
                    try (LoaderFixture ignored = new LoaderFixture(route, Map.of(), versions(base, EXACT_JEI))) {
                        assertPluginMatrix(base, EXACT_JEI);
                        assertSupport(base, EXACT_JEI);
                    }
                });
            }
        }

        check("early_unknown_base_overrides_later_verified_base", () -> {
            try (LoaderFixture fixture = new LoaderFixture(Route.EARLY,
                    versions("1.21.1-2.4.6.4", EXACT_JEI), versions("1.21.1-2.4.6.5", EXACT_JEI))) {
                assertPluginMatrix("1.21.1-2.4.6.4", EXACT_JEI);
                fixture.lateStatic.verifyNoInteractions();
                assertSupport("1.21.1-2.4.6.5", EXACT_JEI);
            }
        });
        check("early_verified_base_overrides_later_unknown_base", () -> {
            try (LoaderFixture fixture = new LoaderFixture(Route.EARLY,
                    versions("1.21.1-2.4.6.5", EXACT_JEI), versions("1.21.1-2.4.7", EXACT_JEI))) {
                assertPluginMatrix("1.21.1-2.4.6.5", EXACT_JEI);
                fixture.lateStatic.verifyNoInteractions();
                assertSupport("1.21.1-2.4.7", EXACT_JEI);
            }
        });
        check("early_missing_base_does_not_borrow_late_base", () -> {
            try (LoaderFixture fixture = new LoaderFixture(Route.EARLY,
                    versions(null, EXACT_JEI), versions("1.21.1-2.4.6.5", EXACT_JEI))) {
                assertPluginMatrix(null, EXACT_JEI);
                fixture.lateStatic.verifyNoInteractions();
                assertSupport("1.21.1-2.4.6.5", EXACT_JEI);
            }
        });
        check("early_missing_jei_does_not_borrow_late_jei", () -> {
            try (LoaderFixture fixture = new LoaderFixture(Route.EARLY,
                    versions("1.21.1-2.4.6.5", null), versions("1.21.1-2.4.6.5", EXACT_JEI))) {
                assertPluginMatrix("1.21.1-2.4.6.5", null);
                fixture.lateStatic.verifyNoInteractions();
                assertSupport("1.21.1-2.4.6.5", EXACT_JEI);
            }
        });
        check("early_wrong_jei_rejects_exact_gates_even_when_late_jei_matches", () -> {
            try (LoaderFixture fixture = new LoaderFixture(Route.EARLY,
                    versions("1.21.1-2.4.6.5", "19.58.0.452"), versions("1.21.1-2.4.6.5", EXACT_JEI))) {
                assertPluginMatrix("1.21.1-2.4.6.5", "19.58.0.452");
                fixture.lateStatic.verifyNoInteractions();
                assertSupport("1.21.1-2.4.6.5", EXACT_JEI);
            }
        });

        for (Route route : List.of(Route.NULL_EARLY, Route.THROW_RUNTIME, Route.THROW_LINKAGE)) {
            check("both_lists_unavailable_fail_closed_" + route, () -> {
                try (LoaderFixture ignored = new LoaderFixture(route, Map.of(), null)) {
                    assertPluginMatrix(null, null);
                    assertSupport(null, null);
                }
            });
        }

        // Plugin deliberately guards loader failures. Support is a post-bootstrap API;
        // do not invent a new promise that its ModList.get() exceptions are swallowed.
        for (boolean linkage : List.of(false, true)) {
            check("late_loader_failure_closes_plugin_" + (linkage ? "linkage" : "runtime"), () -> {
                try (LoaderFixture fixture = new LoaderFixture(Route.NULL_EARLY, Map.of(), Map.of())) {
                    if (linkage) fixture.lateStatic.when(ModList::get).thenThrow(new NoClassDefFoundError("test-only"));
                    else fixture.lateStatic.when(ModList::get).thenThrow(new IllegalStateException("test-only"));
                    assertPluginMatrix(null, null);
                }
            });
        }

        check("gate_decision_is_not_bypassed_by_target_class_name", () -> {
            try (LoaderFixture ignored = new LoaderFixture(Route.EARLY,
                    versions("1.21.1-2.4.6.4", EXACT_JEI), Map.of())) {
                for (String target : List.of("", "unrelated.Target", "com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog")) {
                    for (String mixin : allMixins()) {
                        expect(PLUGIN.shouldApplyMixin(target, MIXIN_PACKAGE + mixin), false, target + ":" + mixin);
                    }
                }
            }
        });

        JsonObject report = new JsonObject();
        report.addProperty("status", failed == 0 ? "PASSED" : "FAILED");
        report.addProperty("scope", "real production version gates with mocked loader metadata; no game or ABI proof");
        report.addProperty("gameLaunched", false);
        report.addProperty("regression184Repeated", false);
        report.addProperty("passed", passed);
        report.addProperty("failed", failed);
        report.addProperty("assertions", assertions);
        report.addProperty("pluginCodeSource", codeSource(UselessStretcherMixinPlugin.class));
        report.addProperty("supportCodeSource", codeSource(CatalogPatchSupport.class));
        report.add("expectedPublicationJeiVersions", jsonVersions(expectedPublicationJei));
        report.add("expectedInternalMixinJeiVersions", jsonVersions(expectedInternalMixinJei));
        JsonArray allowed = new JsonArray();
        VERIFIED_BASES.forEach(allowed::add);
        report.add("expectedVerifiedBaseVersions", allowed);
        report.add("checks", CHECKS);
        if (output.toAbsolutePath().getParent() != null) Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8);
        System.out.println("VERSION GATE CHECKS: " + passed + " passed, " + failed + " failed, " + assertions + " assertions");
        if (failed != 0) throw new AssertionError("Version gate checks failed; see " + output);
    }

    private static void assertPluginMatrix(String base, String jei) {
        boolean baseAllowed = verified(base);
        for (String mixin : BUDGET_MIXINS) {
            boolean expected = baseAllowed && (!mixin.equals("CatalogJeiTimingMixin") || jei != null);
            expect(applies(mixin), expected, mixin);
        }
        for (String mixin : EXACT_JEI_MIXINS) {
            expect(applies(mixin), baseAllowed && allowedJei(expectedInternalMixinJei, jei), mixin);
        }
    }

    private static void assertSupport(String base, String jei) {
        expect(CatalogPatchSupport.enabled(), verified(base), "Support.enabled");
        expect(CatalogPatchSupport.jeiPublicationEnabled(), verified(base) && allowedJei(expectedPublicationJei, jei), "Support.jeiPublicationEnabled");
        expect(CatalogPatchSupport.verifiedVersion(base), verified(base), "Support.verifiedVersion");
    }

    private static boolean applies(String mixin) {
        return PLUGIN.shouldApplyMixin("test.only.Target", MIXIN_PACKAGE + mixin);
    }

    private static boolean verified(String version) {
        return version != null && VERIFIED_BASES.contains(version);
    }

    private static boolean allowedJei(List<String> expected, String version) {
        return version != null && expected.contains(version);
    }

    private static List<String> parseVersions(String csv) {
        if (csv.equals("-")) return List.of();
        List<String> result = new ArrayList<>();
        for (String value : csv.split(",", -1)) {
            if (value.isBlank() || !value.equals(value.trim()) || result.contains(value)) {
                throw new IllegalArgumentException("Expected distinct exact versions, got " + csv);
            }
            if (!value.equals(EXACT_JEI) && !value.equals(NEW_JEI)) {
                throw new IllegalArgumentException("JEI version has no fixture in this review: " + value);
            }
            result.add(value);
        }
        return List.copyOf(result);
    }

    private static JsonArray jsonVersions(List<String> versions) {
        JsonArray result = new JsonArray();
        versions.forEach(result::add);
        return result;
    }

    private static List<String> allMixins() {
        List<String> result = new ArrayList<>(BUDGET_MIXINS);
        result.addAll(EXACT_JEI_MIXINS);
        return result;
    }

    private static Map<String, String> versions(String base, String jei) {
        Map<String, String> result = new LinkedHashMap<>();
        if (base != null) result.put("useless_mod", base);
        if (jei != null) result.put("jei", jei);
        return result;
    }

    private static String codeSource(Class<?> type) {
        var source = type.getProtectionDomain().getCodeSource();
        return source == null ? "unknown" : source.getLocation().toExternalForm();
    }

    private static String printable(String value) {
        return value == null ? "null" : value.isEmpty() ? "empty" : value;
    }

    private static void expect(boolean actual, boolean expected, String message) {
        assertions++;
        if (actual != expected) throw new AssertionError(message + ": expected " + expected + " but got " + actual);
    }

    private static void check(String name, Checked action) {
        JsonObject row = new JsonObject();
        row.addProperty("name", name);
        int before = assertions;
        try {
            action.run();
            passed++;
            row.addProperty("status", "PASSED");
        } catch (Exception | AssertionError | LinkageError failure) {
            failed++;
            row.addProperty("status", "FAILED");
            row.addProperty("failureClass", failure.getClass().getName());
            row.addProperty("message", String.valueOf(failure.getMessage()));
            failure.printStackTrace(System.err);
        }
        row.addProperty("assertions", assertions - before);
        CHECKS.add(row);
    }

    private enum Route { EARLY, NULL_EARLY, THROW_RUNTIME, THROW_LINKAGE }
    @FunctionalInterface private interface Checked { void run() throws Exception; }

    private static final class LoaderFixture implements AutoCloseable {
        final MockedStatic<FMLLoader> earlyStatic;
        final MockedStatic<ModList> lateStatic;

        LoaderFixture(Route route, Map<String, String> earlyVersions, Map<String, String> lateVersions) {
            LoadingModList early = mock(LoadingModList.class);
            List<ModInfo> earlyMods = new ArrayList<>();
            for (var entry : earlyVersions.entrySet()) {
                earlyMods.add(modInfo(entry.getKey(), entry.getValue()));
            }
            when(early.getMods()).thenReturn(earlyMods);

            ModList late = lateVersions == null ? null : mock(ModList.class);
            if (late != null) {
                Map<String, ModContainer> containers = new LinkedHashMap<>();
                for (var entry : lateVersions.entrySet()) {
                    ModInfo info = modInfo(entry.getKey(), entry.getValue());
                    ModContainer container = mock(ModContainer.class);
                    when(container.getModInfo()).thenReturn(info);
                    containers.put(entry.getKey(), container);
                }
                doAnswer(call -> Optional.ofNullable(containers.get(call.getArgument(0, String.class))))
                        .when(late).getModContainerById(anyString());
                when(late.isLoaded(anyString())).thenAnswer(call -> containers.containsKey(call.getArgument(0, String.class)));
            }

            earlyStatic = mockStatic(FMLLoader.class);
            MockedStatic<ModList> createdLate = null;
            try {
                createdLate = mockStatic(ModList.class);
                createdLate.when(ModList::get).thenReturn(late);
                switch (route) {
                    case EARLY -> earlyStatic.when(FMLLoader::getLoadingModList).thenReturn(early);
                    case NULL_EARLY -> earlyStatic.when(FMLLoader::getLoadingModList).thenReturn(null);
                    case THROW_RUNTIME -> earlyStatic.when(FMLLoader::getLoadingModList)
                            .thenThrow(new IllegalStateException("test-only early loader failure"));
                    case THROW_LINKAGE -> earlyStatic.when(FMLLoader::getLoadingModList)
                            .thenThrow(new NoClassDefFoundError("test-only early loader failure"));
                }
                lateStatic = createdLate;
            } catch (RuntimeException | Error failure) {
                if (createdLate != null) createdLate.close();
                earlyStatic.close();
                throw failure;
            }
        }

        private static ModInfo modInfo(String id, String version) {
            ModInfo info = mock(ModInfo.class);
            when(info.getModId()).thenReturn(id);
            when(info.getVersion()).thenReturn(new DefaultArtifactVersion(version));
            return info;
        }

        @Override public void close() {
            try { lateStatic.close(); }
            finally { earlyStatic.close(); }
        }
    }
}
