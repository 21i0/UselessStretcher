package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.FloatingScreen;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.network.RangeNetwork;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import com.sorrowmist.useless.stretcher.client.gui.StretcherConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Time-sorted list of the current player's placed ranges with remote enable switches. */
public final class RangeAccelerationHistoryScreen extends FloatingScreen {
    private static final int PANEL_WIDTH = 430;
    private static final int PANEL_HEIGHT = 226;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private List<RangeAccelerationSavedData.Summary> fields = List.of();
    private int page;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int rowsPerPage = 6;
    private int rowHeight = 27;
    private boolean stackedActions;
    private int footerTop;
    private boolean requested;
    private EditBox renameBox;
    private int renameIndex = -1;
    private UUID renameId;
    private long lastNameClick;
    private int lastNameIndex = -1;

    public RangeAccelerationHistoryScreen(Screen parent) {
        super(Component.translatable("gui.useless_stretcher.range.history_title"),
                "range_history", PANEL_WIDTH, PANEL_HEIGHT, 270, 170);
        this.parent = parent;
    }

    @Override
    protected void initContent() {
        setContentExtent(contentHeight());
        panelWidth = contentWidth();
        panelLeft = contentLeft();
        panelTop = contentTop();
        stackedActions = panelWidth < 380;
        rowHeight = stackedActions ? 48 : 27;
        int previousRows = rowsPerPage;
        rowsPerPage = Math.max(1, (contentHeight() - 27) / rowHeight);
        if (previousRows != rowsPerPage) page = page * previousRows / rowsPerPage;
        if (renameIndex >= 0) page = renameIndex / rowsPerPage;
        page = Math.min(page, pageCount() - 1);
        footerTop = panelTop + contentHeight() - 20;
        int start = page * rowsPerPage;
        int end = Math.min(fields.size(), start + rowsPerPage);
        for (int index = start; index < end; index++) {
            RangeAccelerationSavedData.Summary field = fields.get(index);
            int row = index - start;
            int actionsLeft = stackedActions ? panelLeft + 4 : panelLeft + panelWidth - 140;
            int actionsTop = panelTop + row * rowHeight + (stackedActions ? 25 : 4);
            addRenderableWidget(new SelectableAE2Button(
                    actionsLeft, actionsTop,
                    37, 18, Component.translatable("gui.useless_stretcher.range.edit"), ignored ->
                    minecraft.setScreen(new RangeAccelerationHistoryEditScreen(this, field))));
            SelectableAE2Button toggle = addRenderableWidget(new SelectableAE2Button(
                    actionsLeft + 40, actionsTop,
                    37, 18, enabledMessage(field.enabled()), ignored ->
                    RangeNetwork.setHistoryEnabled(field.id(), !field.enabled())));
            toggle.setSelected(field.enabled());
            addRenderableWidget(new SelectableAE2Button(
                    actionsLeft + 80, actionsTop,
                    54, 18, Component.translatable("gui.useless_stretcher.range.reclaim"), ignored ->
                    confirmReclaim(field)));
        }

        int pages = pageCount();
        SelectableAE2Button previous = addRenderableWidget(new SelectableAE2Button(
                panelLeft + 2, footerTop, 28, 18, Component.literal("<"), ignored -> {
                    if (page > 0) {
                        page--;
                        rebuildWidgets();
                    }
                }));
        previous.active = page > 0 && renameIndex < 0;
        SelectableAE2Button next = addRenderableWidget(new SelectableAE2Button(
                panelLeft + 33, footerTop, 28, 18, Component.literal(">"), ignored -> {
                    if (page + 1 < pages) {
                        page++;
                        rebuildWidgets();
                    }
                }));
        next.active = page + 1 < pages && renameIndex < 0;
        addRenderableWidget(new SelectableAE2Button(
                panelLeft + 101, footerTop, panelWidth - 173, 18,
                Component.translatable("gui.useless_stretcher.history.machines"),
                ignored -> Network.requestPersonalReclaimer()));
        addRenderableWidget(new SelectableAE2Button(
                panelLeft + panelWidth - 69, footerTop, 67, 18,
                Component.translatable("gui.useless_stretcher.back"), ignored -> onClose()));

        if (renameIndex >= 0 && renameIndex < fields.size()) {
            createRenameBox();
        }

        if (!requested) {
            requested = true;
            RangeNetwork.requestHistory();
        }
    }

    public void onHistory(List<RangeAccelerationSavedData.Summary> updated) {
        fields = List.copyOf(updated);
        if (renameId != null) {
            renameIndex = -1;
            for (int i = 0; i < fields.size(); i++) if (renameId.equals(fields.get(i).id())) { renameIndex = i; break; }
            if (renameIndex < 0) { renameId = null; renameBox = null; }
        }
        page = Math.min(page, pageCount() - 1);
        rebuildWidgets();
    }

    private int pageCount() { return Math.max(1, (fields.size() + rowsPerPage - 1) / rowsPerPage); }
    private int nameWidth() { return stackedActions ? panelWidth - 12 : panelWidth - 150; }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int start = page * rowsPerPage;
        int end = Math.min(fields.size(), start + rowsPerPage);
        if (fields.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.useless_stretcher.range.no_history"),
                    panelLeft + panelWidth / 2, panelTop + (contentHeight() - 27) / 2, StretcherScreenStyle.MUTED_TEXT_COLOR);
        }
        for (int index = start; index < end; index++) {
            RangeAccelerationSavedData.Summary field = fields.get(index);
            int y = panelTop + (index - start) * rowHeight;
            StretcherScreenStyle.drawInset(graphics, panelLeft, y,
                    panelLeft + panelWidth, y + rowHeight - 3);
            var center = field.center().offset(field.offsetX(), field.offsetY(), field.offsetZ());
            String location = field.dimension() + "  " + center.getX() + ", "
                    + center.getY() + ", " + center.getZ();
            if (!field.name().isBlank()) location = field.name() + "  ·  " + location;
            String details = TIME_FORMAT.format(Instant.ofEpochMilli(field.createdAt()))
                    + "  x" + field.speed() + "  " + field.sizeX() + "x" + field.sizeY() + "x" + field.sizeZ()
                    + "  偏" + field.offsetX() + "," + field.offsetY() + "," + field.offsetZ();
            if (index != renameIndex) graphics.drawString(font, font.plainSubstrByWidth(location, nameWidth()),
                    panelLeft + 5, y + 3, StretcherScreenStyle.TEXT_COLOR, false);
            if (index != renameIndex) graphics.drawString(font, font.plainSubstrByWidth(details, nameWidth()), panelLeft + 5, y + 13,
                    StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        }
        graphics.drawString(font, font.plainSubstrByWidth((page + 1) + "/" + pageCount(), 36),
                panelLeft + 64, footerTop + 5, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
    }

    @Override
    protected void renderContentTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (renameIndex < 0) {
            int hovered = nameRowAt(mouseX, mouseY);
            if (hovered >= 0) graphics.renderTooltip(font,
                    Component.translatable("gui.useless_stretcher.range.rename_hint"), mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected boolean clickContent(double mouseX, double mouseY, int button) {
        if (renameIndex >= 0) return super.clickContent(mouseX, mouseY, button);
        int index = nameRowAt(mouseX, mouseY);
        if (button == 0 && index >= 0) {
            long now = System.currentTimeMillis();
            if (index == lastNameIndex && now - lastNameClick <= 350) {
                beginRename(index);
                lastNameIndex = -1;
                return true;
            }
            lastNameIndex = index;
            lastNameClick = now;
            return true;
        }
        return super.clickContent(mouseX, mouseY, button);
    }

    private int nameRowAt(double mouseX, double mouseY) {
        if (mouseY < panelTop) return -1;
        int start = page * rowsPerPage;
        int row = (int) ((mouseY - panelTop) / rowHeight);
        int index = start + row;
        if (mouseX < panelLeft + 3 || mouseX > panelLeft + 5 + nameWidth()
                || row < 0 || row >= rowsPerPage || index >= fields.size()
                || mouseY >= panelTop + row * rowHeight + 24) return -1;
        return index;
    }

    private void beginRename(int index) {
        renameIndex = index;
        renameId = fields.get(index).id();
        renameBox = null;
        rebuildWidgets();
        setFocused(renameBox);
    }

    private void createRenameBox() {
        if (renameBox == null) {
            renameBox = new EditBox(font, panelLeft + 5, panelTop + 2, nameWidth(), 18,
                    Component.translatable("gui.useless_stretcher.range.rename_title"));
            renameBox.setMaxLength(48);
            renameBox.setValue(fields.get(renameIndex).name());
            renameBox.setCanLoseFocus(false);
            renameBox.setFocused(true);
            renameBox.setEditable(true);
        }
        renameBox.setX(panelLeft + 5);
        renameBox.setY(panelTop + 2 + (renameIndex - page * rowsPerPage) * rowHeight);
        renameBox.setWidth(nameWidth());
        addRenderableWidget(renameBox);
    }

    private void finishRename(boolean save) {
        if (renameBox == null) return;
        if (save && renameId != null) {
            RangeNetwork.renameHistory(renameId, renameBox.getValue());
        }
        removeWidget(renameBox);
        renameBox = null;
        renameIndex = -1;
        renameId = null;
        rebuildWidgets();
    }

    @Override
    protected boolean scrollContent(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (renameIndex < 0 && mouseY >= panelTop && mouseY < footerTop) {
            int next = net.minecraft.util.Mth.clamp(page - (int) Math.signum(deltaY), 0, pageCount() - 1);
            if (next != page) { page = next; rebuildWidgets(); }
            return true;
        }
        return super.scrollContent(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renameIndex >= 0) {
            if (keyCode == 257 || keyCode == 335) { finishRename(true); return true; }
            if (keyCode == 256) { finishRename(false); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static Component enabledMessage(boolean enabled) {
        return Component.translatable(enabled
                ? "gui.useless_stretcher.staff_config.on"
                : "gui.useless_stretcher.staff_config.off");
    }

    private void confirmReclaim(RangeAccelerationSavedData.Summary field) {
        minecraft.setScreen(new StretcherConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) RangeNetwork.reclaimHistory(field.id());
        }, Component.translatable("gui.useless_stretcher.range.reclaim_title"),
                Component.translatable("gui.useless_stretcher.range.reclaim_message",
                        field.center().getX(), field.center().getY(), field.center().getZ()),
                Component.translatable("gui.useless_stretcher.range.reclaim"), CommonComponents.GUI_CANCEL));
    }
}
