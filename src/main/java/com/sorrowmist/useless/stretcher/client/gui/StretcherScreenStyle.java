package com.sorrowmist.useless.stretcher.client.gui;

import com.sorrowmist.useless.client.gui.AppleUi;
import com.sorrowmist.useless.client.gui.apple.AppleStyle;
import net.minecraft.client.gui.GuiGraphics;

/** Shared surfaces supplied by the required Useless Mod UI API. */
public final class StretcherScreenStyle {
    public static final int PANEL_COLOR = AppleUi.PANEL_BODY;
    public static final int HIGHLIGHT_COLOR = AppleStyle.SELECTED_ROW;
    public static final int SLOT_COLOR = AppleStyle.FILL;
    public static final int SLOT_SHADOW_COLOR = AppleUi.SLOT_EDGE;
    public static final int TEXT_COLOR = AppleStyle.LABEL;
    public static final int MUTED_TEXT_COLOR = AppleStyle.SECONDARY;
    public static final int SUBTLE_TEXT_COLOR = AppleStyle.TERTIARY;
    public static final int ACTIVE_COLOR = AppleStyle.BLUE;
    public static final int SUCCESS_COLOR = AppleStyle.GREEN_TEXT;
    public static final int WARNING_COLOR = AppleStyle.ORANGE_TEXT;

    private StretcherScreenStyle() { }

    public static void drawPanel(GuiGraphics graphics, int left, int top, int width, int height) {
        AppleUi.glassPanel(graphics, left, top, width, height);
    }

    public static void drawInset(GuiGraphics graphics, int left, int top, int right, int bottom) {
        AppleUi.card(graphics, left, top, right, bottom);
    }
}
