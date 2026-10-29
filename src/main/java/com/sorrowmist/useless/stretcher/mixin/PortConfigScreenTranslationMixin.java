package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.client.gui.PortConfigScreen;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** Localize only the addon's spec; the base editor still owns validation, saving and navigation. */
@Mixin(value = PortConfigScreen.class, remap = false)
public abstract class PortConfigScreenTranslationMixin {
    @Shadow private ForgeConfigSpec spec;
    @Shadow @Final private Map<ForgeConfigSpec.ConfigValue<?>, EditBox> editors;
    @Shadow private String message;

    @Redirect(method = {"init()V", "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"}, remap = true,
            at = @At(value = "INVOKE", remap = false,
                    target = "Ljava/lang/String;join(Ljava/lang/CharSequence;Ljava/lang/Iterable;)Ljava/lang/String;"))
    private String stretcher$translatedPath(CharSequence delimiter, Iterable<? extends CharSequence> segments) {
        String path = String.join(delimiter, segments);
        if (spec != StretcherConfig.SERVER_SPEC) return path;
        ForgeConfigSpec.ValueSpec rule = spec.getSpec().get(path);
        return stretcher$label(rule, path).getString();
    }

    @Redirect(method = "init()V", remap = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;",
            remap = true))
    private MutableComponent stretcher$translatedButton(String text) {
        if (spec != StretcherConfig.SERVER_SPEC) return Component.literal(text);
        return switch (text) {
            case "上一页" -> stretcher$text("previous");
            case "下一页" -> stretcher$text("next");
            case "保存本页" -> stretcher$text("save_page");
            default -> Component.literal(text);
        };
    }

    @Inject(method = "init()V", remap = true, at = @At("RETURN"))
    private void stretcher$translatedHelp(CallbackInfo ci) {
        if (spec != StretcherConfig.SERVER_SPEC) return;
        for (var entry : editors.entrySet()) {
            var value = entry.getKey();
            String path = String.join(".", value.getPath());
            ForgeConfigSpec.ValueSpec rule = spec.getSpec().get(value.getPath());
            entry.getValue().setMessage(stretcher$label(rule, path));
            String key = rule.getTranslationKey();
            MutableComponent help = key != null && Language.getInstance().has(key + ".tooltip")
                    ? Component.translatable(key + ".tooltip")
                    : Component.literal(rule.getComment() == null ? "" : rule.getComment());
            // Keep the original serialized keys and true/false syntax available to pack authors.
            if (value.getDefault() instanceof Boolean) help.append("\n").append(stretcher$text("boolean_hint"));
            help.append("\n").append(stretcher$text("path", path));
            entry.getValue().setTooltip(Tooltip.create(help));
        }
        if (!spec.isLoaded()) message = stretcher$text("unloaded").getString();
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void stretcher$translatedResult(CallbackInfo ci) {
        if (spec != StretcherConfig.SERVER_SPEC) return;
        if (message.equals("已保存。标记为需要重启的配置将在重启后生效。")) {
            message = stretcher$text("saved").getString();
        } else if (message.startsWith("未保存：")) {
            String detail = message.substring("未保存：".length());
            // Unknown exceptions can be file/permission failures, not bad input. Keep
            // their actual details instead of replacing every failure with a format error.
            Component reason = Component.literal(detail);
            if (detail.equals("请输入 true 或 false")) reason = stretcher$text("invalid_boolean");
            else if (detail.equals("配置值无效") || detail.startsWith("For input string: \""))
                reason = stretcher$text("invalid_value");
            // A failed range/validator check reports its original key. Display its localized name.
            for (var value : editors.keySet()) {
                if (detail.equals(String.join(".", value.getPath()))) {
                    reason = stretcher$text("invalid_option", stretcher$label(
                            spec.getSpec().get(value.getPath()), detail));
                    break;
                }
            }
            message = stretcher$text("not_saved", reason).getString();
        }
    }

    @Unique private static MutableComponent stretcher$label(ForgeConfigSpec.ValueSpec rule, String fallback) {
        String key = rule == null ? null : rule.getTranslationKey();
        return key != null && Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(fallback);
    }

    @Unique private static MutableComponent stretcher$text(String key, Object... arguments) {
        return Component.translatable("useless_stretcher.configuration.editor." + key, arguments);
    }
}
