package com.sorrowmist.useless.stretcher.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sorrowmist.useless.stretcher.content.catalog.OwnerRecipePublication;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Focused presentation checks only. No Minecraft instance, world, mod launch or game loop. */
public final class JeiLocalizationChecks {
    private static final String PREFIX = "gui.useless_stretcher.jei_publication.";
    private static final List<String> KEYS = List.of("waiting_runtime", "waiting_catalog", "preparing",
            "organizing", "updating", "adding", "restoring", "finishing", "ready", "stopped");
    private static final JsonArray ASSERTIONS = new JsonArray();
    private static int passed, failed;
    private static TableLanguage chinese, english;

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected zh_cn.json en_us.json result.json");
        chinese = readLanguage(Path.of(args[0]));
        english = readLanguage(Path.of(args[1]));
        Language originalLanguage = Language.getInstance();
        try {
            check("ten_localization_keys_exist_in_both_resources", () -> {
                for (String key : KEYS) {
                    require(chinese.has(PREFIX + key), "Missing Chinese key " + key);
                    require(english.has(PREFIX + key), "Missing English key " + key);
                    require(!chinese.getOrDefault(PREFIX + key).isBlank(), "Empty Chinese " + key);
                    require(!english.getOrDefault(PREFIX + key).isBlank(), "Empty English " + key);
                }
            });
            phaseCheck(OwnerRecipePublication.Phase.PLAN, "organizing", new Object[]{17, 155024});
            phaseCheck(OwnerRecipePublication.Phase.HIDE, "updating", new Object[]{5});
            phaseCheck(OwnerRecipePublication.Phase.ADD, "adding", new Object[]{3});
            phaseCheck(OwnerRecipePublication.Phase.UNHIDE, "restoring", new Object[]{7});
            phaseCheck(OwnerRecipePublication.Phase.COMMIT, "finishing", new Object[]{});
            phaseCheck(OwnerRecipePublication.Phase.DONE, "ready", new Object[]{});
            phaseCheck(OwnerRecipePublication.Phase.CANCELLED, "waiting_catalog", new Object[]{});
            phaseCheck(OwnerRecipePublication.Phase.FAILED, "stopped", new Object[]{});
            check("waiting_and_preparing_messages_really_resolve_from_both_json_files", () -> {
                for (String suffix : List.of("waiting_runtime", "waiting_catalog", "preparing", "stopped")) {
                    Component text = Component.translatable(PREFIX + suffix);
                    Language.inject(chinese);
                    require(text.getString().equals(chinese.getOrDefault(PREFIX + suffix)), "Chinese " + suffix);
                    require(text.getString().codePoints().anyMatch(c -> c >= 0x3400 && c <= 0x9FFF), "Not Chinese " + suffix);
                    Language.inject(english);
                    require(text.getString().equals(english.getOrDefault(PREFIX + suffix)), "English " + suffix);
                    require(!text.getString().contains(PREFIX), "Unresolved translation " + suffix);
                }
            });
            check("initial_hud_snapshot_is_translatable_waiting_runtime", () -> {
                Component initial = CatalogJeiPublication.progressComponent();
                require(key(initial).equals(PREFIX + "waiting_runtime"), "Wrong initial HUD status");
                Language.inject(chinese);
                require(initial.getString().equals(chinese.getOrDefault(PREFIX + "waiting_runtime")), "Initial Chinese HUD");
            });
            check("hud_snapshot_is_defensive_copy", () -> {
                Language.inject(chinese);
                String before = CatalogJeiPublication.progressComponent().getString();
                Component returned = CatalogJeiPublication.progressComponent();
                require(returned instanceof MutableComponent, "Unexpected snapshot implementation");
                ((MutableComponent) returned).append(" TEST_MUTATION");
                require(CatalogJeiPublication.progressComponent().getString().equals(before), "Caller mutated stored HUD snapshot");
            });
            check("diagnostic_progress_string_abi_and_initial_text_are_preserved", () -> {
                require(CatalogJeiPublication.class.getMethod("progress").getReturnType() == String.class, "String ABI changed");
                require(CatalogJeiPublication.progress().equals("JEI waiting: runtime"), "Diagnostic initial text changed");
            });
            check("same_component_retranslates_after_language_switch", () -> {
                Component text = CatalogJeiPublication.progressMessage(OwnerRecipePublication.Phase.PLAN, 17, 155024, 3, 5, 7);
                Language.inject(chinese);
                String zh = text.getString();
                Language.inject(english);
                String en = text.getString();
                require(!zh.equals(en), "Translation is frozen into a literal");
                Language.inject(chinese);
                require(text.getString().equals(zh), "Switching language back did not refresh text");
            });
            check("new_addition_count_is_not_total_or_a_false_percentage", () -> {
                Component text = CatalogJeiPublication.progressMessage(OwnerRecipePublication.Phase.ADD, 155024, 155024, 0, 5, 7);
                require(Arrays.equals(contents(text).getArgs(), new Object[]{0}), "ADD uses total instead of additions");
                for (Language language : List.of(chinese, english)) {
                    Language.inject(language);
                    require(text.getString().contains("0"), "Zero additions absent");
                    require(!text.getString().contains("155024") && !text.getString().contains("%"), "False catalog percentage");
                }
            });
            check("ledger_status_updates_actual_detached_hud_snapshot", () -> withSavedFields(() -> {
                set("unsafeFailure", false);
                set("complete", false);
                set("runtime", Proxy.newProxyInstance(IJeiRuntime.class.getClassLoader(), new Class<?>[]{IJeiRuntime.class},
                        (proxy, method, arguments) -> null));
                OwnerRecipePublication<Object, Object> emptyTask = new OwnerRecipePublication<>(List.of(), new HashMap<>(),
                        new HashSet<>(), value -> value, new OwnerRecipePublication.Sink<>() {
                    public void add(List<Object> entries) { throw new AssertionError("No native calls allowed"); }
                    public void hide(List<Object> entries) { throw new AssertionError("No native calls allowed"); }
                    public void unhide(List<Object> entries) { throw new AssertionError("No native calls allowed"); }
                    public void publish() { throw new AssertionError("No native calls allowed"); }
                });
                set("task", emptyTask);
                set("seedVisible", List.of().iterator());
                updateProgress();
                require(key(CatalogJeiPublication.progressComponent()).equals(PREFIX + "preparing"), "LEDGER not localized");
                Language.inject(chinese);
                require(CatalogJeiPublication.progressComponent().getString().equals(chinese.getOrDefault(PREFIX + "preparing")), "LEDGER Chinese");
            }));
            check("failure_details_stay_in_diagnostic_text_and_out_of_hud", () -> withSavedFields(() -> {
                String raw = "java.lang.IllegalStateException: RAW_FAILURE_SENTINEL";
                set("unsafeFailure", true);
                set("failure", raw);
                updateProgress();
                require(CatalogJeiPublication.progress().equals("JEI stopped: " + raw), "Diagnostic exception was lost");
                for (Language language : List.of(chinese, english)) {
                    Language.inject(language);
                    Component hud = CatalogJeiPublication.progressComponent();
                    require(key(hud).equals(PREFIX + "stopped"), "Failure HUD key");
                    require(hud.getString().equals(language.getOrDefault(PREFIX + "stopped")), "Failure translation");
                    require(!hud.getString().contains("RAW_FAILURE_SENTINEL") && !hud.getString().contains("java.lang"), "Raw failure exposed in HUD");
                }
            }));
            check("off_owner_getters_only_read_detached_snapshot", () -> {
                Language.inject(chinese);
                String diagnostic = CatalogJeiPublication.progress();
                String translated = CatalogJeiPublication.progressComponent().getString();
                Object runtime = field("runtime").get(null), task = field("task").get(null);
                AtomicReference<Throwable> error = new AtomicReference<>();
                Thread worker = new Thread(() -> {
                    try {
                        for (int i = 0; i < 100; i++) {
                            require(CatalogJeiPublication.progress().equals(diagnostic), "Worker diagnostic changed");
                            require(CatalogJeiPublication.progressComponent().getString().equals(translated), "Worker translation changed");
                        }
                    } catch (Throwable failure) { error.set(failure); }
                }, "localization-off-owner-read-only");
                worker.setDaemon(true);
                worker.start();
                worker.join(5000);
                require(!worker.isAlive(), "Off-owner read did not finish");
                require(error.get() == null, "Off-owner read failed: " + error.get());
                require(field("runtime").get(null) == runtime && field("task").get(null) == task, "Getter mutated business state");
            });
        } finally { Language.inject(originalLanguage); }
        JsonObject report = new JsonObject();
        report.addProperty("status", failed == 0 ? "PASS" : "FAIL");
        report.addProperty("checksFound", passed + failed);
        report.addProperty("checksStarted", passed + failed);
        report.addProperty("checksPassed", passed);
        report.addProperty("checksFailed", failed);
        report.addProperty("gameLaunched", false);
        report.addProperty("scope", "Actual Component/Language translation and detached presentation snapshots only; no Minecraft runtime or 184-test regression rerun.");
        report.add("assertions", ASSERTIONS);
        Path output = Path.of(args[2]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8);
        System.out.println("JEI LOCALIZATION CHECKS " + (failed == 0 ? "PASSED" : "FAILED") + ": " + passed + "/" + (passed + failed));
        if (failed != 0) System.exit(1);
    }

    private static void phaseCheck(OwnerRecipePublication.Phase phase, String expectedSuffix, Object[] args) {
        check("phase_" + phase + "_uses_correct_translation_and_counters", () -> {
            Component text = CatalogJeiPublication.progressMessage(phase, 17, 155024, 3, 5, 7);
            require(key(text).equals(PREFIX + expectedSuffix), "Wrong key for " + phase);
            require(Arrays.equals(contents(text).getArgs(), args), "Wrong arguments for " + phase);
            for (Language language : List.of(chinese, english)) {
                Language.inject(language);
                String expected = String.format(java.util.Locale.ROOT, language.getOrDefault(PREFIX + expectedSuffix), args);
                require(text.getString().equals(expected), phase + " did not use actual locale table");
                require(!text.getString().contains(PREFIX), "Unresolved phase key");
            }
        });
    }

    private static TranslatableContents contents(Component component) {
        require(component.getContents() instanceof TranslatableContents, "HUD is a literal instead of a translation");
        return (TranslatableContents) component.getContents();
    }
    private static String key(Component component) { return contents(component).getKey(); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void check(String name, Throwing action) {
        JsonObject result = new JsonObject();
        result.addProperty("name", name);
        try { action.run(); passed++; result.addProperty("status", "PASS"); }
        catch (Throwable error) {
            failed++;
            result.addProperty("status", "FAIL");
            result.addProperty("error", error.toString());
            error.printStackTrace(System.out);
        }
        ASSERTIONS.add(result);
    }
    private static Field field(String name) throws Exception {
        Field field = CatalogJeiPublication.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static void set(String name, Object value) throws Exception { field(name).set(null, value); }
    private static void updateProgress() throws Exception {
        Method update = CatalogJeiPublication.class.getDeclaredMethod("updateProgress");
        update.setAccessible(true);
        update.invoke(null);
    }
    private static void withSavedFields(Throwing action) throws Exception {
        Map<Field, Object> saved = new LinkedHashMap<>();
        for (String name : List.of("unsafeFailure", "complete", "runtime", "task", "seedVisible", "progress", "progressText", "failure")) {
            Field field = field(name);
            saved.put(field, field.get(null));
        }
        try { action.run(); }
        finally { for (var entry : saved.entrySet()) entry.getKey().set(null, entry.getValue()); }
    }
    private static TableLanguage readLanguage(Path path) throws Exception {
        JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, String> values = new HashMap<>();
        json.entrySet().forEach(entry -> values.put(entry.getKey(), entry.getValue().getAsString()));
        return new TableLanguage(values);
    }
    private static final class TableLanguage extends Language {
        private final Map<String, String> values;
        TableLanguage(Map<String, String> values) { this.values = Map.copyOf(values); }
        @Override public String getOrDefault(String key, String fallback) { return values.getOrDefault(key, fallback); }
        @Override public boolean has(String key) { return values.containsKey(key); }
        @Override public boolean isDefaultRightToLeft() { return false; }
        @Override public FormattedCharSequence getVisualOrder(FormattedText text) {
            return FormattedCharSequence.forward(text.getString(), Style.EMPTY);
        }
    }
    @FunctionalInterface private interface Throwing { void run() throws Exception; }
}
