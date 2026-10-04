package com.sorrowmist.useless.stretcher.client.gui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Shared action help; contextual axis/list controls explicitly override their generic label help. */
public final class ButtonHelp {
    private ButtonHelp() {}
    public static Component text(String id, Object... args) {
        return Component.translatable("help.useless_stretcher." + id, args);
    }
    public static void set(AbstractWidget widget, String id, Object... args) {
        widget.setTooltip(Tooltip.create(text(id, args)));
    }
    public static void automatic(AbstractWidget widget, Component label) {
        String id = null;
        if (label.getContents() instanceof TranslatableContents translated) {
            id = switch (translated.getKey()) {
                case "gui.useless_stretcher.back", "gui.done" -> "back";
                case "gui.useless_stretcher.speed_off" -> "speed_off";
                case "gui.useless_stretcher.mode_normal" -> "normal";
                case "gui.useless_stretcher.mode_permanent" -> "permanent";
                case "gui.useless_stretcher.mode_permanent_no_throttle" -> "no_throttle";
                case "gui.useless_stretcher.staff_config.master" -> "master";
                case "gui.useless_stretcher.range.open" -> "range_setup";
                case "gui.useless_stretcher.range.history" -> "history";
                case "gui.useless_stretcher.range.edit" -> "edit";
                case "gui.useless_stretcher.range.reclaim" -> "reclaim";
                case "gui.useless_stretcher.staff_config.on", "gui.useless_stretcher.staff_config.off" -> "pause";
                case "gui.useless_stretcher.history.machines" -> "machine_history";
                case "gui.useless_stretcher.range.placement" -> "placement";
                case "gui.useless_stretcher.range.marking" -> "marking";
                case "gui.useless_stretcher.range.offset_short" -> "offset";
                case "gui.useless_stretcher.reclaimer.category_count" -> "category";
                case "gui.useless_stretcher.reclaimer.refresh" -> "refresh";
                case "gui.useless_stretcher.staff_summon.open" -> "summon_open";
                case "gui.useless_stretcher.staff_apotheosis.open" -> "apotheosis_open";
                case "gui.useless_stretcher.staff_config.loot_refresh" -> "loot";
                case "gui.useless_stretcher.staff_config.loot_refresh_disabled", "gui.useless_stretcher.staff_config.summon_disabled",
                        "gui.useless_stretcher.staff_summon.disabled",
                        "gui.useless_stretcher.staff_apotheosis.disabled",
                        "gui.useless_stretcher.staff_apotheosis.missing_enchanting" -> "disabled";
                case "gui.useless_stretcher.staff_summon.mode", "gui.useless_stretcher.staff_summon.mode_short" -> "summon_mode";
                case "gui.useless_stretcher.staff_summon.select_visible" -> "select_visible";
                case "gui.useless_stretcher.staff_summon.clear" -> "clear_selection";
                case "gui.useless_stretcher.staff_summon.summon" -> "summon";
                case "gui.useless_stretcher.staff_summon.recall" -> "recall";
                case "gui.useless_stretcher.clear" -> "clear_patterns";
                default -> null;
            };
        } else {
            id = switch (label.getString()) {
                case "+" -> "increase";
                case "-" -> "decrease";
                case "<" -> "previous";
                case ">" -> "next";
                default -> label.getString().startsWith("x") ? "speed" : null;
            };
        }
        if (id != null) set(widget, id);
    }
}
