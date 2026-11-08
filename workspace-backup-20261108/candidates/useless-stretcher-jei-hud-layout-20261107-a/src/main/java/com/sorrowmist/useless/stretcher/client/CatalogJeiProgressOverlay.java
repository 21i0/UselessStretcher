package com.sorrowmist.useless.stretcher.client;

import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

/** A bounded top-center notification using only the public Minecraft GUI/text APIs. */
public final class CatalogJeiProgressOverlay {
    private static final int PADDING = 4;
    private static final int TOP = 6;
    private static final int GAP = 2;
    private static final int MAX_LINES = 2;
    private static final int MAX_WIDTH = 320;
    private static final FormattedText ELLIPSIS = FormattedText.of("…");

    private CatalogJeiProgressOverlay() {}

    public static void render(GuiGraphics graphics, Font font, Component text) {
        Layout capacity = layout(graphics.guiWidth(), graphics.guiHeight(), font.lineHeight, MAX_LINES);
        if (capacity.lines() == 0) return;
        List<FormattedText> lines = fitLines(text, capacity.textWidth(), capacity.lines(), font.getSplitter());
        if (lines.isEmpty()) return;
        Layout box = layout(graphics.guiWidth(), graphics.guiHeight(), font.lineHeight, lines.size());
        graphics.fill(box.left(), box.top(), box.right(), box.bottom(), 0xB0000000);
        graphics.enableScissor(box.left(), box.top(), box.right(), box.bottom());
        try {
            for (int i = 0; i < lines.size(); i++) {
                var line = Language.getInstance().getVisualOrder(lines.get(i));
                int x = box.left() + (box.width() - font.width(line)) / 2;
                int y = box.top() + PADDING + i * (font.lineHeight + GAP);
                graphics.drawString(font, line, x, y, 0xFFFFFFFF, false);
            }
        } finally { graphics.disableScissor(); }
    }

    /** Pure GUI-unit geometry; reserve the two screen corners without depending on a minimap API. */
    static Layout layout(int screenWidth, int screenHeight, int lineHeight, int requestedLines) {
        if (screenWidth <= 0 || screenHeight <= 0 || lineHeight <= 0 || requestedLines <= 0)
            return Layout.EMPTY;
        int width = Math.min(MAX_WIDTH, screenWidth / 2);
        long minimumHeight = (long) lineHeight + 2 * PADDING;
        if (width <= 2 * PADDING || screenHeight < minimumHeight) return Layout.EMPTY;
        int top = (int) Math.min(TOP, screenHeight - minimumHeight);
        long fittingLines = ((long) screenHeight - top - 2 * PADDING + GAP) / ((long) lineHeight + GAP);
        int lines = (int) Math.min(Math.min(MAX_LINES, requestedLines), fittingLines);
        if (lines <= 0) return Layout.EMPTY;
        int height = (int) (2L * PADDING + (long) lines * lineHeight + (long) (lines - 1) * GAP);
        return new Layout((screenWidth - width) / 2, top, width, height, width - 2 * PADDING, lines);
    }

    /** Native wrapping preserves styles; trim every line and mark omitted trailing lines. */
    static List<FormattedText> fitLines(FormattedText text, int width, int maximumLines, StringSplitter splitter) {
        if (width <= 0 || maximumLines <= 0) return List.of();
        List<FormattedText> wrapped = splitter.splitLines(text, width, Style.EMPTY);
        int count = Math.min(Math.min(MAX_LINES, maximumLines), wrapped.size());
        List<FormattedText> visible = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            FormattedText line = splitter.headByWidth(wrapped.get(i), width, Style.EMPTY);
            if (i == count - 1 && wrapped.size() > count) {
                int suffixWidth = (int) Math.ceil(splitter.stringWidth(ELLIPSIS));
                line = suffixWidth > width ? FormattedText.EMPTY
                        : FormattedText.composite(splitter.headByWidth(line, width - suffixWidth, Style.EMPTY), ELLIPSIS);
            }
            visible.add(line);
        }
        return List.copyOf(visible);
    }

    record Layout(int left, int top, int width, int height, int textWidth, int lines) {
        private static final Layout EMPTY = new Layout(0, 0, 0, 0, 0, 0);
        int right() { return left + width; }
        int bottom() { return top + height; }
    }
}
