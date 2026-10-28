package com.sorrowmist.useless.stretcher.config;

import com.google.gson.stream.JsonReader;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationTranslationsTest {
    private static final Path ROOT = Path.of("src/main");
    private static final String PREFIX = "useless_stretcher.configuration.";

    @Test void everyConfigLabelAndValueHelpIsTranslatedInAllThreeLanguages() throws Exception {
        String config = Files.readString(ROOT.resolve("java/com/sorrowmist/useless/stretcher/config/StretcherConfig.java"));
        var titles = Pattern.compile("\\.translation\\(\"([^\"]+)\"\\)");
        var options = Pattern.compile("\\.translation\\(\"([^\"]+)\"\\)\\s*\\.define(?:InRange)?\\(");
        for (String language : new String[]{"en_us", "zh_cn", "zh_tw"}) {
            var translations = translations(language);
            var title = titles.matcher(config);
            while (title.find()) assertText(translations, title.group(1), language);
            var option = options.matcher(config);
            int count = 0;
            while (option.find()) {
                assertText(translations, option.group(1) + ".tooltip", language);
                count++;
            }
            assertEquals(18, count, "include optional Apotheosis settings even when the mod is absent");
            for (String control : new String[]{"previous", "next", "save_page", "unloaded", "saved", "not_saved",
                    "invalid_boolean", "invalid_value", "invalid_option", "boolean_hint", "path"})
                assertText(translations, PREFIX + "editor." + control, language);
            for (String key : new String[]{"gui.useless_stretcher.staff_apotheosis.tier_unavailable",
                    "gui.useless_stretcher.staff_summon.recall"}) assertText(translations, key, language);
        }
    }

    @Test void localizedConfigFormatsHaveMatchingPlaceholders() throws Exception {
        var english = translations("en_us");
        var placeholders = Pattern.compile("%(?:\\d+\\$)?[sd]");
        for (String language : new String[]{"zh_cn", "zh_tw"}) {
            var translated = translations(language);
            for (var entry : english.entrySet()) {
                if (!entry.getKey().startsWith(PREFIX)) continue;
                assertText(translated, entry.getKey(), language);
                assertEquals(placeholders.matcher(entry.getValue()).results().map(result -> result.group()).toList(),
                        placeholders.matcher(translated.get(entry.getKey())).results().map(result -> result.group()).toList(),
                        language + ": " + entry.getKey());
            }
        }
    }

    private static Map<String, String> translations(String language) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        try (var json = new JsonReader(Files.newBufferedReader(ROOT.resolve(
                "resources/assets/useless_stretcher/lang/" + language + ".json")))) {
            json.beginObject();
            while (json.hasNext()) {
                String key = json.nextName();
                assertNull(values.put(key, json.nextString()), language + " duplicate translation: " + key);
            }
            json.endObject();
        }
        return values;
    }

    private static void assertText(Map<String, String> values, String key, String language) {
        assertNotNull(values.get(key), language + " missing: " + key);
        assertFalse(values.get(key).isBlank(), language + " empty: " + key);
        assertFalse(values.get(key).contains("\uFFFD"), language + " malformed Unicode: " + key);
    }
}
