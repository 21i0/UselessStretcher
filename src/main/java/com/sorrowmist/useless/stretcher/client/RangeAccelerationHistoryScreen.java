package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.network.RangeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Time-sorted list of the current player's placed ranges with remote enable switches. */
public final class RangeAccelerationHistoryScreen extends Screen {
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 226;
    private static final int ROWS_PER_PAGE = 6;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private List<RangeAccelerationSavedData.Summary> fields = List.of();
    private int page;
    private int panelLeft;
    private int panelTop;
    private boolean requested;

    public RangeAccelerationHistoryScreen(Screen parent) {
        super(Component.translatable("gui.useless_stretcher.range.history_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int panelWidth = Math.min(PANEL_WIDTH, width - 12);
        panelLeft = (width - panelWidth) / 2;
        panelTop = Math.max(6, (height - PANEL_HEIGHT) / 2);
        int start = page * ROWS_PER_PAGE;
        int end = Math.min(fields.size(), start + ROWS_PER_PAGE);
        for (int index = start; index < end; index++) {
            RangeAccelerationSavedData.Summary field = fields.get(index);
            int row = index - start;
            int actionsLeft = panelLeft + panelWidth - 142;
            addRenderableWidget(new SelectableAE2Button(
                    actionsLeft, panelTop + 31 + row * 27,
                    37, 18, Component.translatable("gui.useless_stretcher.range.edit"), ignored ->
                    minecraft.setScreen(new RangeAccelerationHistoryEditScreen(this, field))));
            SelectableAE2Button toggle = addRenderableWidget(new SelectableAE2Button(
                    actionsLeft + 40, panelTop + 31 + row * 27,
                    37, 18, enabledMessage(field.enabled()), ignored ->
                    RangeNetwork.setHistoryEnabled(field.id(), !field.enabled())));
            toggle.setSelected(field.enabled());
            addRenderableWidget(new SelectableAE2Button(
                    actionsLeft + 80, panelTop + 31 + row * 27,
                    54, 18, Component.translatable("gui.useless_stretcher.range.reclaim"), ignored ->
                    confirmReclaim(field)));
        }

        int pages = Math.max(1, (fields.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        SelectableAE2Button previous = addRenderableWidget(new SelectableAE2Button(
                panelLeft + 9, panelTop + 199, 34, 18, Component.literal("<"), ignored -> {
                    if (page > 0) {
                        page--;
                        rebuildWidgets();
                    }
                }));
        previous.active = page > 0;
        SelectableAE2Button next = addRenderableWidget(new SelectableAE2Button(
                panelLeft + 46, panelTop + 199, 34, 18, Component.literal(">"), ignored -> {
                    if (page + 1 < pages) {
                        page++;
                        rebuildWidgets();
                    }
                }));
        next.active = page + 1 < pages;
        addRenderableWidget(new SelectableAE2Button(
                panelLeft + panelWidth - 76, panelTop + 199, 67, 18,
                Component.translatable("gui.useless_stretcher.back"), ignored -> onClose()));

        if (!requested) {
            requested = true;
            RangeNetwork.requestHistory();
        }
    }

    public void onHistory(List<RangeAccelerationSavedData.Summary> updated) {
        fields = List.copyOf(updated);
        int pages = Math.max(1, (fields.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        page = Math.min(page, pages - 1);
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x33000000);
        int panelWidth = Math.min(PANEL_WIDTH, width - 12);
        StretcherScreenStyle.drawPanel(graphics, panelLeft, panelTop, panelWidth, PANEL_HEIGHT);
        graphics.drawString(font, title, panelLeft + 9, panelTop + 9,
                StretcherScreenStyle.TEXT_COLOR, false);

        int start = page * ROWS_PER_PAGE;
        int end = Math.min(fields.size(), start + ROWS_PER_PAGE);
        if (fields.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.useless_stretcher.range.no_history"),
                    panelLeft + panelWidth / 2, panelTop + 101, StretcherScreenStyle.MUTED_TEXT_COLOR);
        }
        for (int index = start; index < end; index++) {
            RangeAccelerationSavedData.Summary field = fields.get(index);
            int y = panelTop + 27 + (index - start) * 27;
            StretcherScreenStyle.drawInset(graphics, panelLeft + 8, y,
                    panelLeft + panelWidth - 8, y + 24);
            var center = field.center().offset(field.offsetX(), field.offsetY(), field.offsetZ());
            String location = field.dimension() + "  " + center.getX() + ", "
                    + center.getY() + ", " + center.getZ();
            String details = TIME_FORMAT.format(Instant.ofEpochMilli(field.createdAt()))
                    + "  x" + field.speed() + "  " + field.sizeX() + "x" + field.sizeY() + "x" + field.sizeZ()
                    + "  偏" + field.offsetX() + "," + field.offsetY() + "," + field.offsetZ();
            graphics.drawString(font, font.plainSubstrByWidth(location, panelWidth - 158),
                    panelLeft + 12, y + 3, StretcherScreenStyle.TEXT_COLOR, false);
            graphics.drawString(font, font.plainSubstrByWidth(details, panelWidth - 158), panelLeft + 12, y + 13,
                    StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        }
        int pages = Math.max(1, (fields.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        graphics.drawString(font, (page + 1) + "/" + pages,
                panelLeft + 87, panelTop + 204, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Component enabledMessage(boolean enabled) {
        return Component.translatable(enabled
                ? "gui.useless_stretcher.staff_config.on"
                : "gui.useless_stretcher.staff_config.off");
    }

    private void confirmReclaim(RangeAccelerationSavedData.Summary field) {
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) RangeNetwork.reclaimHistory(field.id());
        }, Component.translatable("gui.useless_stretcher.range.reclaim_title"),
                Component.translatable("gui.useless_stretcher.range.reclaim_message",
                        field.center().getX(), field.center().getY(), field.center().getZ()),
                Component.translatable("gui.useless_stretcher.range.reclaim"), CommonComponents.GUI_CANCEL));
    }
}
