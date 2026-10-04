package com.sorrowmist.useless.stretcher.client.search;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** ICU is a vanilla client library, so these checks use the opt-in guide client, not a server. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT)
public final class LocalizedSearchChecks {
    private static boolean ran;

    @SubscribeEvent
    public static void start(ClientTickEvent.Post event) {
        if (ran || !Boolean.getBoolean("useless_stretcher.guide_test")) return;
        ran = true;
        try {
            var index = LocalizedSearchIndex.current();
            String ironIngot = "\u94c1\u952d";
            var iron = index.document(ironIngot, "minecraft:iron_ingot", "minecraft");
            var dragon = index.document("\u672b\u5f71\u9f99", "minecraft:ender_dragon", "minecraft");
            check(matches(iron, ironIngot), "Chinese display name is immediately searchable");
            check(matches(iron, "IRON INGOT"), "English id words are case-insensitive");
            check(matches(iron, "minecraft:iron_ingot"), "full registry id is searchable");
            check(matches(iron, "@mine iron"), "mod filtering can be combined with a name");
            check(!matches(iron, "@mekanism"), "mod filter excludes other sources");
            check(matches(dragon, "ender dragon"), "entity registry aliases remain searchable");
            flushWorker();
            check(matches(iron, "tieding"), "full pinyin matches the translated name");
            check(matches(iron, "tie ding"), "spaced pinyin matches the translated name");
            check(matches(iron, "td"), "pinyin initials match the translated name");
            check(matches(dragon, "myl"), "entity pinyin initials are searchable");
            check(matches(dragon, "moyinglong"), "entity full pinyin is searchable");
            check(!matches(iron, "moyinglong"), "unrelated pinyin does not match");
            long revision = index.revision();
            int names = nameCount(index);
            for (int i = 0; i < 10_000; i++) {
                var repeated = index.document(ironIngot, "test:mold_" + i, "test");
                check(matches(repeated, "td"), "repeated molds reuse an already completed transliteration");
            }
            flushWorker();
            check(index.revision() == revision, "repeated names do not schedule new transliterations");
            check(nameCount(index) == names, "repeated molds do not grow the translated-name cache");
            checkLanguageReload(index);
            LogUtils.getLogger().info("REGRESSION: Chinese, English, id, pinyin and shared search-cache checks passed");
        } catch (Throwable failure) {
            LogUtils.getLogger().error("REGRESSION FAILED: localized search", failure);
            throw new IllegalStateException("Localized search regression failed", failure);
        }
    }

    private static boolean matches(LocalizedSearchIndex.Document document, String query) {
        return LocalizedSearchIndex.Query.parse(query).matches(document);
    }

    private static void flushWorker() throws Exception {
        Field field = LocalizedSearchIndex.class.getDeclaredField("WORKER");
        field.setAccessible(true);
        ((ExecutorService) field.get(null)).submit(() -> { }).get(10, TimeUnit.SECONDS);
    }

    private static int nameCount(LocalizedSearchIndex index) throws Exception {
        Field field = LocalizedSearchIndex.class.getDeclaredField("names");
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(index)).size();
    }

    private static void checkLanguageReload(LocalizedSearchIndex previous) throws Exception {
        Language original = Language.getInstance();
        try {
            Language.inject(new Language() {
                @Override
                public String getOrDefault(String key, String fallback) {
                    return key.equals("test.localized_search.name") ? "\u91d1\u952d" : original.getOrDefault(key, fallback);
                }

                @Override
                public boolean has(String key) { return key.equals("test.localized_search.name") || original.has(key); }

                @Override
                public boolean isDefaultRightToLeft() { return original.isDefaultRightToLeft(); }

                @Override
                public FormattedCharSequence getVisualOrder(FormattedText text) { return original.getVisualOrder(text); }
            });
            var changed = LocalizedSearchIndex.current();
            check(changed != previous, "language/resource reload creates a fresh translation index");
            var renamed = changed.document(Component.translatable("test.localized_search.name").getString(),
                    "test:renamed", "test");
            flushWorker();
            check(matches(renamed, "jinding"), "the updated translation gets new pinyin terms");
            check(!matches(renamed, "tieding"), "old-language terms do not leak into the new index");
        } finally {
            Language.inject(original);
            LocalizedSearchIndex.current();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
