package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.client.gui.AE2RangeSlider;
import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.network.RangeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Edits an existing Time Flow's speed and geometry without replacing its persistent field UUID. */
public final class RangeAccelerationHistoryEditScreen extends Screen {
    private static final int PANEL_WIDTH = 276;
    private static final int PANEL_HEIGHT = 246;
    private static final int[] SPEED_PRESETS = {1, 2, 4, 16, 32, 64, 128, 256, 512, 1024};

    private final Screen parent;
    private final RangeAccelerationSavedData.Summary field;
    private int panelLeft;
    private int panelTop;
    private int speed;
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    private int offsetX;
    private int offsetY;
    private int offsetZ;
    private SelectableAE2Button speedButton;

    public RangeAccelerationHistoryEditScreen(Screen parent,
                                              RangeAccelerationSavedData.Summary field) {
        super(Component.translatable("gui.useless_stretcher.range.edit_title"));
        this.parent = parent;
        this.field = field;
        speed = RangeAccelerationSavedData.clampSpeed(field.speed());
        sizeX = field.sizeX();
        sizeY = field.sizeY();
        sizeZ = field.sizeZ();
        offsetX = field.offsetX();
        offsetY = field.offsetY();
        offsetZ = field.offsetZ();
    }

    @Override
    protected void init() {
        super.init();
        int panelWidth = Math.min(PANEL_WIDTH, width - 12);
        panelLeft = (width - panelWidth) / 2;
        panelTop = Math.max(6, (height - PANEL_HEIGHT) / 2);
        int sliderLeft = panelLeft + 12;
        int sliderWidth = panelWidth - 24;

        addRenderableWidget(new SelectableAE2Button(
                sliderLeft, panelTop + 38, 18, 18, Component.literal("-"), ignored -> stepSpeed(-1)));
        speedButton = addRenderableWidget(new SelectableAE2Button(
                sliderLeft + 20, panelTop + 38, sliderWidth - 40, 18,
                speedMessage(), ignored -> { }));
        speedButton.active = false;
        addRenderableWidget(new SelectableAE2Button(
                sliderLeft + sliderWidth - 18, panelTop + 38, 18, 18,
                Component.literal("+"), ignored -> stepSpeed(1)));

        addSlider(sliderLeft, panelTop + 72, sliderWidth, 'X', false, sizeX);
        addSlider(sliderLeft, panelTop + 92, sliderWidth, 'Y', false, sizeY);
        addSlider(sliderLeft, panelTop + 112, sliderWidth, 'Z', false, sizeZ);
        addSlider(sliderLeft, panelTop + 148, sliderWidth, 'X', true, offsetX);
        addSlider(sliderLeft, panelTop + 168, sliderWidth, 'Y', true, offsetY);
        addSlider(sliderLeft, panelTop + 188, sliderWidth, 'Z', true, offsetZ);

        int half = (panelWidth - 27) / 2;
        addRenderableWidget(new SelectableAE2Button(
                panelLeft + 9, panelTop + 215, half, 18,
                Component.translatable("gui.useless_stretcher.range.save"), ignored -> saveAndClose()));
        addRenderableWidget(new SelectableAE2Button(
                panelLeft + 12 + half, panelTop + 215, panelWidth - 21 - half, 18,
                Component.translatable("gui.useless_stretcher.back"), ignored -> onClose()));
    }

    private void stepSpeed(int delta) {
        int index = 0;
        for (int i = 0; i < SPEED_PRESETS.length; i++) {
            if (SPEED_PRESETS[i] >= speed) {
                index = i;
                break;
            }
            index = i;
        }
        index = Math.max(0, Math.min(SPEED_PRESETS.length - 1, index + delta));
        speed = SPEED_PRESETS[index];
        if (speedButton != null) speedButton.setMessage(speedMessage());
    }

    private void addSlider(int left, int y, int width, char axis, boolean offset, int initialValue) {
        int min = offset ? RangeAccelerationSavedData.MIN_OFFSET : RangeAccelerationSavedData.MIN_SIZE;
        int max = offset ? RangeAccelerationSavedData.MAX_OFFSET : RangeAccelerationSavedData.MAX_SIZE;
        AE2RangeSlider slider = addRenderableWidget(new AE2RangeSlider(
                left + 21, y, width - 42, 18, String.valueOf(axis), min, max, initialValue,
                value -> setValue(axis, offset, value)));
        addRenderableWidget(new SelectableAE2Button(
                left, y, 18, 18, Component.literal("-"), ignored -> slider.step(-1)));
        addRenderableWidget(new SelectableAE2Button(
                left + width - 18, y, 18, 18, Component.literal("+"), ignored -> slider.step(1)));
    }

    private void setValue(char axis, boolean offset, int value) {
        if (offset) {
            switch (axis) {
                case 'X' -> offsetX = value;
                case 'Y' -> offsetY = value;
                default -> offsetZ = value;
            }
        } else {
            switch (axis) {
                case 'X' -> sizeX = value;
                case 'Y' -> sizeY = value;
                default -> sizeZ = value;
            }
        }
    }

    private void saveAndClose() {
        RangeNetwork.editHistory(field.id(), speed, sizeX, sizeY, sizeZ, offsetX, offsetY, offsetZ);
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x33000000);
        int panelWidth = Math.min(PANEL_WIDTH, width - 12);
        StretcherScreenStyle.drawPanel(graphics, panelLeft, panelTop, panelWidth, PANEL_HEIGHT);
        graphics.drawString(font, title, panelLeft + 9, panelTop + 9,
                StretcherScreenStyle.TEXT_COLOR, false);
        Component location = Component.literal(field.dimension() + "  "
                + field.center().getX() + ", " + field.center().getY() + ", " + field.center().getZ());
        graphics.drawString(font, font.plainSubstrByWidth(location.getString(), panelWidth - 18),
                panelLeft + 9, panelTop + 23, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);

        StretcherScreenStyle.drawInset(graphics, panelLeft + 8, panelTop + 32,
                panelLeft + panelWidth - 8, panelTop + 60);
        StretcherScreenStyle.drawInset(graphics, panelLeft + 8, panelTop + 64,
                panelLeft + panelWidth - 8, panelTop + 137);
        StretcherScreenStyle.drawInset(graphics, panelLeft + 8, panelTop + 140,
                panelLeft + panelWidth - 8, panelTop + 213);
        graphics.drawString(font, Component.translatable("gui.useless_stretcher.range.speed_group"),
                panelLeft + 12, panelTop + 34, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.useless_stretcher.range.size_group"),
                panelLeft + 12, panelTop + 66, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.useless_stretcher.range.offset_group"),
                panelLeft + 12, panelTop + 142, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
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

    private Component speedMessage() {
        return Component.literal("x" + speed);
    }
}
