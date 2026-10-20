package com.sorrowmist.useless.stretcher.client.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Authored prose lives in small resource-pack replaceable files, not a generated wall of filler. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "useless_stretcher",
        value = net.minecraftforge.api.distmarker.Dist.CLIENT,
        bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class TwoILongStory {
    public record Choice(String text, List<TwoIStoryScript.Line> lines) { }
    public record Round(List<TwoIStoryScript.Line> prompt, List<Choice> choices) {
        public Choice choice(int index) {
            return index >= 0 && index < choices.size() ? choices.get(index) : new Choice(
                    switch (index) { case 0 -> "我愿意听你慢慢说。"; case 1 -> "我现在不想听这些。"; default -> "我们先歇一会儿吧。"; }, List.of());
        }
    }
    public record Chapter(List<TwoIStoryScript.Line> opening, List<Round> rounds,
                          List<TwoIStoryScript.Line> ending) {
        public Round round(int index) {
            return index >= 0 && index < rounds.size() ? rounds.get(index) : new Round(List.of(), List.of());
        }
    }

    private static final Chapter EMPTY = new Chapter(List.of(), List.of(), List.of());
    private static final Chapter[] CACHE = new Chapter[6];
    private static Object resources;
    private static String language;

    private TwoILongStory() { }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void registerReload(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new net.minecraft.server.packs.resources.SimplePreparableReloadListener<Boolean>() {
            @Override protected Boolean prepare(net.minecraft.server.packs.resources.ResourceManager manager,
                                                net.minecraft.util.profiling.ProfilerFiller profiler) { return true; }
            @Override protected void apply(Boolean value, net.minecraft.server.packs.resources.ResourceManager manager,
                                            net.minecraft.util.profiling.ProfilerFiller profiler) { clearCache(); }
        });
    }

    public static void clearCache() {
        java.util.Arrays.fill(CACHE, null);
        TwoIStoryDirection.clearLongDirections();
        resources = null;
    }

    public static Chapter chapter(int chapter) {
        Minecraft client = Minecraft.getInstance();
        String selected = client.getLanguageManager().getSelected();
        if (resources != client.getResourceManager() || !selected.equals(language)) {
            resources = client.getResourceManager();
            language = selected;
            java.util.Arrays.fill(CACHE, null);
            TwoIStoryDirection.clearLongDirections();
        }
        int index = com.sorrowmist.useless.stretcher.compat.NativeMath.clamp(chapter, 0, 5);
        if (CACHE[index] == null) CACHE[index] = load(index, selected);
        return CACHE[index];
    }

    private static Chapter load(int chapter, String language) {
        var manager = Minecraft.getInstance().getResourceManager();
        var localized = new ResourceLocation("useless_stretcher",
                "dialogue/long_stories/" + language + "/chapter_" + chapter + ".json");
        var fallback = new ResourceLocation("useless_stretcher",
                "dialogue/long_stories/chapter_" + chapter + ".json");
        var resource = manager.getResource(localized).or(() -> manager.getResource(fallback));
        if (resource.isEmpty()) return EMPTY;
        try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<Round> rounds = new ArrayList<>();
            JsonArray array = root.getAsJsonArray("rounds");
            if (array != null) for (int i = 0; i < array.size(); i++) {
                JsonObject round = array.get(i).getAsJsonObject();
                List<Choice> choices = new ArrayList<>();
                JsonArray options = round.getAsJsonArray("choices");
                if (options != null) for (int option = 0; option < options.size(); option++) {
                    JsonObject value = options.get(option).getAsJsonObject();
                    choices.add(new Choice(value.get("text").getAsString(),
                            lines(chapter, "round." + i + ".branch." + option, value.getAsJsonArray("lines"))));
                }
                rounds.add(new Round(lines(chapter, "round." + i + ".prompt", round.getAsJsonArray("prompt")), List.copyOf(choices)));
            }
            return new Chapter(lines(chapter, "opening", root.getAsJsonArray("opening")), List.copyOf(rounds),
                    lines(chapter, "ending", root.getAsJsonArray("ending")));
        } catch (Exception failure) {
            LogUtils.getLogger().warn("Unable to read 2i long story chapter {}", chapter, failure);
            return EMPTY;
        }
    }

    private static List<TwoIStoryScript.Line> lines(int chapter, String section, JsonArray array) {
        if (array == null) return List.of();
        List<TwoIStoryScript.Line> lines = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JsonObject line = array.get(i).getAsJsonObject();
            String speaker = line.has("speaker") ? line.get("speaker").getAsString() : "2i";
            String expression = line.has("expression") ? line.get("expression").getAsString() : "calm";
            String key = "gui.useless_stretcher.two_i.vn." + chapter + ".long." + section + "." + i;
            TwoIStoryDirection.Mood mood = line.has("mood")
                    ? TwoIStoryDirection.Mood.valueOf(line.get("mood").getAsString()) : TwoIStoryDirection.Mood.CALM;
            TwoIStoryDirection.register(key, mood);
            lines.add(new TwoIStoryScript.Line(key, expression, "narrator".equals(speaker),
                    line.has("cg") && line.get("cg").getAsBoolean(), "player".equals(speaker), line.get("text").getAsString()));
        }
        return List.copyOf(lines);
    }
}
