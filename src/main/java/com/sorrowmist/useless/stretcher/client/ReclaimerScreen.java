package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/** Server-authoritative personal history and the clock reclaimer's remote management list. */
public final class ReclaimerScreen extends Screen {
    private static final int PANEL_WIDTH = 430;
    private static final int PANEL_HEIGHT = 270;
    private static final int LIST_TOP = 42;
    private static final int ROW_HEIGHT = 27;
    private static final int MAX_ROWS = 7;
    private final Screen parent;
    private final boolean personal;
    private List<Network.ReclaimerEntry> entries;
    private int left;
    private int top;
    private int scroll;

    public ReclaimerScreen(Screen parent, List<Network.ReclaimerEntry> entries, boolean personal) {
        super(Component.translatable(personal ? "gui.useless_stretcher.range.history_title"
                : "gui.useless_stretcher.reclaimer.title"));
        this.parent = parent;
        this.personal = personal;
        this.entries = List.copyOf(entries);
    }

    public boolean personal() { return personal; }

    public void update(List<Network.ReclaimerEntry> entries) {
        this.entries = List.copyOf(entries);
        scroll = Mth.clamp(scroll, 0, Math.max(0, entries.size() - MAX_ROWS));
        rebuildWidgets();
    }

    @Override protected void init() {
        int panelWidth = Math.min(PANEL_WIDTH, width - 12);
        left = (width - panelWidth) / 2;
        top = Math.max(6, (height - PANEL_HEIGHT) / 2);
        for (int i = 0; i < MAX_ROWS && scroll + i < entries.size(); i++) {
            var entry = entries.get(scroll + i);
            addRenderableWidget(new SelectableAE2Button(left + panelWidth - 58,
                    top + LIST_TOP + i * ROW_HEIGHT + 4, 46, 18,
                    Component.translatable("gui.useless_stretcher.range.reclaim"), ignored ->
                    minecraft.setScreen(new ConfirmScreen(confirmed -> {
                        minecraft.setScreen(this);
                        if (confirmed) Network.reclaimTarget(entry.range(), entry.id(), personal);
                    }, Component.translatable("gui.useless_stretcher.range.reclaim"),
                            Component.literal(entry.label() + " · " + entry.dimension() + " " + entry.pos().toShortString())))));
        }
        addRenderableWidget(new SelectableAE2Button(left + panelWidth - 78, top + PANEL_HEIGHT - 25, 69, 18,
                Component.translatable("gui.useless_stretcher.back"), ignored -> onClose()));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x33000000);
        int panelWidth = Math.min(PANEL_WIDTH, width - 12);
        StretcherScreenStyle.drawPanel(graphics, left, top, panelWidth, PANEL_HEIGHT);
        graphics.drawString(font, title, left + 9, top + 9, StretcherScreenStyle.TEXT_COLOR, false);
        long ranges = entries.stream().filter(Network.ReclaimerEntry::range).count();
        graphics.drawString(font, Component.translatable("gui.useless_stretcher.reclaimer.hint",
                entries.size(), ranges, entries.size() - ranges),
                left + 9, top + 25, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        StretcherScreenStyle.drawInset(graphics, left + 8, top + LIST_TOP - 4,
                left + panelWidth - 8, top + LIST_TOP + MAX_ROWS * ROW_HEIGHT + 2);
        Network.ReclaimerEntry hovered = null;
        for (int i = 0; i < MAX_ROWS && scroll + i < entries.size(); i++) {
            var entry = entries.get(scroll + i);
            int y = top + LIST_TOP + i * ROW_HEIGHT;
            if (mouseX >= left + 10 && mouseX < left + panelWidth - 64 && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                graphics.fill(left + 10, y, left + panelWidth - 64, y + ROW_HEIGHT, 0x22517497);
                hovered = entry;
            }
            String kind = entry.range() ? "范围" : "永久机器";
            String line = entry.ownerName() + " · [" + kind + "] " + entry.label();
            graphics.drawString(font, font.plainSubstrByWidth(line, panelWidth - 82),
                    left + 14, y + 3, StretcherScreenStyle.TEXT_COLOR, false);
            graphics.drawString(font, font.plainSubstrByWidth(
                    entry.dimension() + " " + entry.pos().toShortString(), panelWidth - 82),
                    left + 14, y + 15, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        }
        if (entries.size() > MAX_ROWS) {
            graphics.drawString(font, Component.literal("↑/↓ " + (scroll + 1) + "-" +
                    Math.min(entries.size(), scroll + MAX_ROWS)),
                    left + 10, top + PANEL_HEIGHT - 25, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hovered != null) graphics.renderTooltip(font, List.of(
                Component.literal(hovered.ownerName() + " (" + hovered.owner() + ")"),
                Component.literal(hovered.label()),
                Component.literal(hovered.dimension() + " " + hovered.pos().toShortString())),
                java.util.Optional.empty(), mouseX, mouseY);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int next = Mth.clamp(scroll - (int) Math.signum(deltaY), 0, Math.max(0, entries.size() - MAX_ROWS));
        if (next != scroll) { scroll = next; rebuildWidgets(); }
        return true;
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public boolean isPauseScreen() { return false; }
}
