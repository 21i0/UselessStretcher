package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small server-authoritative list for the clock reclaimer's remote cleanup action. */
public final class ReclaimerScreen extends Screen {
    private static final int PANEL_WIDTH = 430;
    private static final int PANEL_HEIGHT = 270;
    private static final int LIST_TOP = 42;
    private static final int ROW_HEIGHT = 19;
    private static final int MAX_ROWS = 10;

    private final List<Network.ReclaimerEntry> entries;
    private int left;
    private int top;
    private int scroll;
    private List<Row> rows = List.of();

    private record Row(Network.ReclaimerEntry entry, String ownerHeader) {
    }

    public ReclaimerScreen(List<Network.ReclaimerEntry> entries) {
        super(Component.translatable("gui.useless_stretcher.reclaimer.title"));
        this.entries = List.copyOf(entries);
    }

    @Override
    protected void init() {
        super.init();
        int width = Math.min(PANEL_WIDTH, this.width - 12);
        left = (this.width - width) / 2;
        top = Math.max(6, (this.height - PANEL_HEIGHT) / 2);
        Map<String, String> owners = new LinkedHashMap<>();
        for (Network.ReclaimerEntry entry : entries) {
            owners.putIfAbsent(entry.owner().toString(),
                    entry.ownerName() + " (" + entry.owner() + ")");
        }
        Map<String, Boolean> seen = new LinkedHashMap<>();
        rows = entries.stream().map(entry -> {
            String id = entry.owner().toString();
            String header = seen.putIfAbsent(id, true) == null ? owners.get(id) : null;
            return new Row(entry, header);
        }).toList();
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                Component.translatable("gui.useless_stretcher.back"), ignored -> onClose())
                .bounds(left + width - 78, top + PANEL_HEIGHT - 25, 69, 18).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x33000000);
        int panelWidth = Math.min(PANEL_WIDTH, width - 12);
        StretcherScreenStyle.drawPanel(graphics, left, top, panelWidth, PANEL_HEIGHT);
        graphics.drawString(font, title, left + 9, top + 9, StretcherScreenStyle.TEXT_COLOR, false);
        long ranges = entries.stream().filter(Network.ReclaimerEntry::range).count();
        long machines = entries.size() - ranges;
        graphics.drawString(font, Component.translatable("gui.useless_stretcher.reclaimer.hint",
                        entries.size(), ranges, machines),
                left + 9, top + 25, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        StretcherScreenStyle.drawInset(graphics, left + 8, top + LIST_TOP - 4,
                left + panelWidth - 8, top + LIST_TOP + MAX_ROWS * ROW_HEIGHT + 2);

        int first = Math.min(scroll, Math.max(0, entries.size() - MAX_ROWS));
        for (int i = 0; i < MAX_ROWS && first + i < entries.size(); i++) {
            Row row = rows.get(first + i);
            Network.ReclaimerEntry entry = row.entry();
            int y = top + LIST_TOP + i * ROW_HEIGHT;
            boolean hovered = mouseX >= left + 10 && mouseX < left + panelWidth - 10
                    && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) graphics.fill(left + 10, y, left + panelWidth - 10, y + ROW_HEIGHT, 0x22517497);
            String kind = entry.range() ? "范围" : "永久机器";
            if (row.ownerHeader() != null) {
                graphics.drawString(font, font.plainSubstrByWidth("玩家 " + row.ownerHeader(), panelWidth - 32),
                        left + 14, y, 0xFFFFD479, false);
                y += 8;
            }
            String line = "  [" + kind + "] " + entry.label()
                    + "  " + entry.dimension() + " " + entry.pos().toShortString();
            graphics.drawString(font, font.plainSubstrByWidth(line, panelWidth - 32),
                    left + 14, y + 5, StretcherScreenStyle.TEXT_COLOR, false);
        }
        if (rows.size() > MAX_ROWS) {
            int max = rows.size() - MAX_ROWS;
            graphics.drawString(font, Component.literal("↑/↓ " + (first + 1) + "-" +
                            Math.min(entries.size(), first + MAX_ROWS)),
                    left + 10, top + PANEL_HEIGHT - 25, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        int panelWidth = Math.min(PANEL_WIDTH, width - 12);
        int row = (int) ((mouseY - (top + LIST_TOP)) / ROW_HEIGHT);
        int first = Math.min(scroll, Math.max(0, entries.size() - MAX_ROWS));
        int index = first + row;
        if (mouseX >= left + 10 && mouseX < left + panelWidth - 10
                && row >= 0 && row < MAX_ROWS && index >= 0 && index < entries.size()) {
            Network.ReclaimerEntry entry = entries.get(index);
            Network.reclaimTarget(entry.range(), entry.id());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int max = Math.max(0, rows.size() - MAX_ROWS);
        scroll = Mth.clamp(scroll - (int) Math.signum(deltaY), 0, max);
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
