package com.sorrowmist.useless.stretcher.upstreamcheck;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.client.gui.PortConfigScreen;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Exercise the actual shared editor with in-memory defaults only; never load or modify a world. */
public final class ForgeConfigTranslationChecks {
    private static final String PREFIX = "useless_stretcher.configuration.editor.";

    static void validate(Minecraft client) throws Exception {
        check(client.getConnection() == null && !StretcherConfig.SERVER_SPEC.isLoaded(), "offline config fixture only");
        Language original = Language.getInstance();
        try {
            for (String language : List.of("en_us", "zh_cn", "zh_tw")) {
                Language.inject(ClientLanguage.loadFrom(client.getResourceManager(), List.of("en_us", language), false));
                var unloaded = screen(client, StretcherConfig.SERVER_SPEC);
                check(field(unloaded, "message").equals(text("unloaded")), language + " unloaded message");
                var memory = CommentedConfig.inMemory();
                StretcherConfig.SERVER_SPEC.correct(memory);
                StretcherConfig.SERVER_SPEC.setConfig(memory);
                try {
                    verifyPages(client, language);
                    verifyAtomicSave(client, language);
                    verifyOtherSpecUnaffected(client);
                } finally {
                    StretcherConfig.SERVER_SPEC.setConfig(null);
                }
                LogUtils.getLogger().info("FORGE CONFIG TRANSLATION CHECKS PASSED [{}]: all editor pages, labels, narration, help, controls, validation and in-memory save", language);
            }
        } finally {
            Language.inject(original);
            StretcherConfig.SERVER_SPEC.setConfig(null);
        }
    }

    private static void verifyPages(Minecraft client, String language) throws Exception {
        var screen = screen(client, StretcherConfig.SERVER_SPEC);
        check(screen.getTitle().getString().equals(Component.translatable("useless_stretcher.configuration.server").getString()),
                language + " localized title");
        check(screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .anyMatch(button -> button.getMessage().getString().equals(Component.translatable("gui.done").getString())),
                language + " localized done button");
        int checked = 0;
        while (true) {
            var graphics = new RecordingGraphics(client);
            screen.render(graphics, -1, -1, 0);
            graphics.flush();
            for (var entry : editors(screen).entrySet()) {
                var value = entry.getKey();
                ForgeConfigSpec.ValueSpec rule = StretcherConfig.SERVER_SPEC.getSpec().get(value.getPath());
                String label = Component.translatable(rule.getTranslationKey()).getString();
                check(entry.getValue().getMessage().getString().equals(label), language + " localized input narration");
                check(graphics.labels.contains(label), language + " visible label: " + label);
                String help = tooltip(client, entry.getValue());
                check(help.contains(compact(Component.translatable(rule.getTranslationKey() + ".tooltip").getString())), language + " localized help");
                check(help.contains(String.join(".", value.getPath())), "serialized key still available in tooltip");
                if (value.getDefault() instanceof Boolean) {
                    check(List.of("true", "false").contains(entry.getValue().getValue()), "boolean parser syntax retained");
                    check(help.contains(compact(text("boolean_hint"))), language + " localized boolean explanation");
                }
                checked++;
            }
            check(button(screen, "save_page").active, "loaded in-memory spec can save");
            var next = button(screen, "next");
            if (!next.active) break;
            next.onPress();
            check(button(screen, "previous").active, "translated pagination works");
        }
        check(checked == (StretcherConfig.hasApotheosisConfig() ? 18 : 17), "all config entries were visited");
        // Capture an actual frame with an explicit GUI projection (ticks do not have one).
        button(screen, "previous").onPress();
        screenshot(client, screen, language);
    }

    private static void verifyAtomicSave(Minecraft client, String language) throws Exception {
        var screen = screen(client, StretcherConfig.SERVER_SPEC);
        var options = new ArrayList<>(editors(screen).entrySet());
        var first = options.get(0);
        var second = options.get(1);
        boolean original = (Boolean) first.getKey().get();
        String secondOriginal = second.getValue().getValue();
        first.getValue().setValue(Boolean.toString(!original));
        second.getValue().setValue("invalid");
        button(screen, "save_page").onPress();
        check(first.getKey().get().equals(original), "invalid page cannot partially apply another valid setting");
        check(field(screen, "message").equals(Component.translatable(PREFIX + "not_saved",
                Component.translatable(PREFIX + "invalid_boolean")).getString()), language + " boolean validation message");
        second.getValue().setValue(secondOriginal);
        button(screen, "save_page").onPress();
        check(first.getKey().get().equals(!original) && field(screen, "message").equals(text("saved")), language + " save result");
        first.getValue().setValue(Boolean.toString(original));
        button(screen, "save_page").onPress();
        // Exercise only message presentation, without forcing real filesystem errors or writes.
        var status = screen.getClass().getDeclaredField("message"); status.setAccessible(true);
        var handler = java.util.Arrays.stream(screen.getClass().getDeclaredMethods())
                .filter(method -> method.getName().contains("stretcher$translatedResult")).findFirst().orElseThrow();
        handler.setAccessible(true);
        String failure = "Access denied: fixture-config.toml";
        status.set(screen, "未保存：" + failure);
        handler.invoke(screen, new org.spongepowered.asm.mixin.injection.callback.CallbackInfo("save", false));
        check(status.get(screen).equals(Component.translatable(PREFIX + "not_saved", failure).getString()),
                language + " unexpected save error details are retained");

        while (true) {
            var probability = editors(screen).entrySet().stream().filter(entry -> entry.getKey().getDefault() instanceof Double).findFirst();
            if (probability.isPresent()) {
                var entry = probability.get();
                Object before = entry.getKey().get();
                entry.getValue().setValue("-1");
                button(screen, "save_page").onPress();
                ForgeConfigSpec.ValueSpec rule = StretcherConfig.SERVER_SPEC.getSpec().get(entry.getKey().getPath());
                String expected = Component.translatable(PREFIX + "not_saved", Component.translatable(PREFIX + "invalid_option",
                        Component.translatable(rule.getTranslationKey()))).getString();
                check(field(screen, "message").equals(expected) && entry.getKey().get().equals(before), language + " range validation");
                entry.getValue().setValue("not-a-number");
                button(screen, "save_page").onPress();
                check(field(screen, "message").equals(Component.translatable(PREFIX + "not_saved",
                        Component.translatable(PREFIX + "invalid_value")).getString()), language + " numeric validation");
                break;
            }
            check(button(screen, "next").active, "probability setting exists");
            button(screen, "next").onPress();
        }
    }

    private static void verifyOtherSpecUnaffected(Minecraft client) throws Exception {
        var builder = new ForgeConfigSpec.Builder();
        builder.comment("Other spec original help").translation("useless_stretcher.configuration.server.staff_acceleration")
                .define("fixture_enabled", true);
        var other = builder.build();
        var memory = CommentedConfig.inMemory();
        other.correct(memory); other.setConfig(memory);
        try {
            var screen = screen(client, other);
            var input = editors(screen).values().iterator().next();
            check(input.getMessage().getString().equals("fixture_enabled"), "other config labels stay unchanged");
            check(tooltip(client, input).equals(compact("Other spec original help")), "other config help stays unchanged");
        } finally {
            other.setConfig(null);
        }
    }

    private static PortConfigScreen screen(Minecraft client, ForgeConfigSpec spec) {
        client.setScreen(new Screen(Component.empty()) { });
        var screen = new PortConfigScreen(null, spec, Component.translatable("useless_stretcher.configuration.server"));
        screen.init(client, 640, 400);
        return screen;
    }

    @SuppressWarnings("unchecked")
    private static Map<ForgeConfigSpec.ConfigValue<?>, EditBox> editors(PortConfigScreen screen) throws Exception {
        return (Map<ForgeConfigSpec.ConfigValue<?>, EditBox>) field(screen, "editors");
    }

    private static Button button(PortConfigScreen screen, String key) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(text(key))).findFirst().orElseThrow();
    }

    private static String tooltip(Minecraft client, EditBox input) {
        check(input.getTooltip() != null, "config input has help");
        StringBuilder text = new StringBuilder();
        for (var line : input.getTooltip().toCharSequence(client)) {
            line.accept((index, style, codepoint) -> { text.appendCodePoint(codepoint); return true; });
        }
        return compact(text.toString());
    }

    private static String compact(String text) { return text.replaceAll("\\s+", ""); }

    private static void screenshot(Minecraft client, Screen screen, String language) throws Exception {
        var target = new com.mojang.blaze3d.pipeline.TextureTarget(1280, 800, true, Minecraft.ON_OSX);
        var modelView = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix(); modelView.pushPose();
        try {
            modelView.setIdentity(); com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0, 640, 400, 0, -1000, 1000),
                    com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
            target.setClearColor(.12F, .13F, .16F, 1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            var graphics = new GuiGraphics(client, client.renderBuffers().bufferSource());
            screen.render(graphics, -1, -1, 0); graphics.flush();
            try (var image = Screenshot.takeScreenshot(target)) {
                image.writeToFile(client.gameDirectory.toPath().resolve("screenshots/config-" + language + ".png"));
            }
        } finally {
            modelView.popPose(); com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            com.mojang.blaze3d.systems.RenderSystem.restoreProjectionMatrix();
            target.destroyBuffers(); client.getMainRenderTarget().bindWrite(true);
        }
    }

    private static String text(String key) { return Component.translatable(PREFIX + key).getString(); }
    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final class RecordingGraphics extends GuiGraphics {
        final List<String> labels = new ArrayList<>();
        RecordingGraphics(Minecraft client) { super(client, client.renderBuffers().bufferSource()); }
        @Override public int drawString(Font font, String text, int x, int y, int color) {
            labels.add(text);
            return super.drawString(font, text, x, y, color);
        }
    }
}
