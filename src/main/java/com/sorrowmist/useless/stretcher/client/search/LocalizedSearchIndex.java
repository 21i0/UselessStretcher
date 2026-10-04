package com.sorrowmist.useless.stretcher.client.search;

import com.ibm.icu.text.Transliterator;
import com.mojang.logging.LogUtils;
import net.minecraft.locale.Language;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Client-language search terms. The worker only sees strings, never game objects or registries. */
public final class LocalizedSearchIndex {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "useless-stretcher-search-index");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile LocalizedSearchIndex current;
    private final Language language;
    private final Map<String, RomanizedName> names = new HashMap<>();
    private final AtomicLong revision = new AtomicLong();
    private final java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger();

    private LocalizedSearchIndex(Language language) {
        this.language = language;
    }

    public static LocalizedSearchIndex current() {
        Language language = Language.getInstance();
        if (current == null || current.language != language) current = new LocalizedSearchIndex(language);
        return current;
    }

    public long revision() { return revision.get(); }
    public boolean pending() { return pending.get() != 0; }

    public Document document(String translatedName, String id, String source) {
        String normalizedName = normalize(translatedName);
        RomanizedName name = names.computeIfAbsent(normalizedName, text -> {
            RomanizedName result = new RomanizedName();
            if (text.codePoints().anyMatch(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN)) {
                pending.incrementAndGet();
                WORKER.execute(() -> {
                    // A language/resource reload supersedes queued work for the old translations.
                    if (current != this) { pending.decrementAndGet(); return; }
                    try {
                        result.value = romanize(text);
                    } catch (RuntimeException exception) {
                        LogUtils.getLogger().warn("Could not index a translated search name", exception);
                    } finally {
                        revision.incrementAndGet();
                        pending.decrementAndGet();
                    }
                });
            }
            return result;
        });
        String mod = normalize(source);
        String plain = normalizedName + " " + normalize(id) + " "
                + normalize(id.replace('_', ' ')) + " " + mod;
        return new Document(plain, mod, name);
    }

    static String romanize(String text) {
        String latin = TransliteratorHolder.INSTANCE.transliterate(text);
        StringBuilder full = new StringBuilder();
        StringBuilder initials = new StringBuilder();
        boolean start = true;
        for (int i = 0; i < latin.length(); i++) {
            char character = latin.charAt(i);
            if (Character.isLetterOrDigit(character)) {
                full.append(character);
                if (start) initials.append(character);
                start = false;
            } else {
                start = true;
            }
        }
        return latin + " " + full + " " + initials;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .trim().toLowerCase(Locale.ROOT);
    }

    public record Query(String mod, List<String> terms) {
        public static Query parse(String input) {
            String normalized = normalize(input);
            String mod = "";
            if (normalized.startsWith("@")) {
                int separator = normalized.indexOf(' ');
                mod = normalized.substring(1, separator < 0 ? normalized.length() : separator);
                normalized = separator < 0 ? "" : normalized.substring(separator + 1).trim();
            }
            return new Query(mod, normalized.isEmpty() ? List.of() : List.of(normalized.split("\\s+")));
        }

        public boolean empty() { return mod.isEmpty() && terms.isEmpty(); }

        public boolean matches(Document document) {
            if (!document.mod.contains(mod)) return false;
            for (String term : terms) {
                if (!document.plain.contains(term) && !document.name.value.contains(term)) return false;
            }
            return true;
        }
    }

    public static final class Document {
        private final String plain;
        private final String mod;
        private final RomanizedName name;

        private Document(String plain, String mod, RomanizedName name) {
            this.plain = plain;
            this.mod = mod;
            this.name = name;
        }
    }

    private static final class RomanizedName {
        private volatile String value = "";
    }

    private static final class TransliteratorHolder {
        // Used only by the single worker; ICU transliterators are mutable and not thread-safe.
        private static final Transliterator INSTANCE = Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower");
    }
}
