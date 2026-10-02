package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.client.gui.AE2RangeSlider;
import com.sorrowmist.useless.stretcher.client.gui.FloatingScreen;
import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.content.entity.EntityTimerAcceleration;
import com.sorrowmist.useless.stretcher.network.RangeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Edits an existing Time Flow's speed and geometry without replacing its persistent field UUID. */
public final class RangeAccelerationHistoryEditScreen extends FloatingScreen {
    private static final int[] SPEED_PRESETS = {1, 2, 4, 16, 32, 64, 128, 256, 512, 1024};

    private final Screen parent;
    private final RangeAccelerationSavedData.Summary field;
    private int groupWidth;
    private int offsetLeft;
    private int offsetTop;
    private int speed;
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    private int offsetX;
    private int offsetY;
    private int offsetZ;
    private boolean entityTimerMode;
    private int entityTimerSpeed;
    private SelectableAE2Button timerButton;
    private SelectableAE2Button timerSpeedButton;
    private SelectableAE2Button speedButton;

    public RangeAccelerationHistoryEditScreen(Screen parent,
                                              RangeAccelerationSavedData.Summary field) {
        super(Component.translatable("gui.useless_stretcher.range.edit_title"),
                "range_edit", 300, 304, 250, 140);
        this.parent = parent;
        this.field = field;
        speed = RangeAccelerationSavedData.clampSpeed(field.speed());
        sizeX = field.sizeX();
        sizeY = field.sizeY();
        sizeZ = field.sizeZ();
        offsetX = field.offsetX();
        offsetY = field.offsetY();
        offsetZ = field.offsetZ();
        entityTimerMode = field.entityTimerMode();
        entityTimerSpeed = EntityTimerAcceleration.normalizeSpeed(field.entityTimerSpeed());
    }

    @Override
    protected void initContent() {
        int left = contentLeft();
        int top = contentTop();
        int bodyWidth = contentWidth();
        int sliderLeft = left + 4;
        int sliderWidth = Math.max(1, bodyWidth - 8);

        addRenderableWidget(new SelectableAE2Button(
                sliderLeft, top + 32, 18, 18, Component.literal("-"), ignored -> stepSpeed(-1)))
                .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        com.sorrowmist.useless.stretcher.client.gui.ButtonHelp.text("edit_speed")));
        speedButton = addRenderableWidget(new SelectableAE2Button(
                sliderLeft + 20, top + 32, Math.max(1, sliderWidth - 40), 18,
                speedMessage(), ignored -> { }));
        speedButton.active = false;
        com.sorrowmist.useless.stretcher.client.gui.ButtonHelp.set(speedButton, "edit_speed");
        addRenderableWidget(new SelectableAE2Button(
                sliderLeft + sliderWidth - 18, top + 32, 18, 18,
                Component.literal("+"), ignored -> stepSpeed(1)))
                .setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                com.sorrowmist.useless.stretcher.client.gui.ButtonHelp.text("edit_speed")));

        int timerY = top + 56;
        timerButton = addRenderableWidget(new SelectableAE2Button(
                sliderLeft, timerY, Math.max(1, sliderWidth / 2 - 2), 18,
                timerMessage(), ignored -> {
                    entityTimerMode = !entityTimerMode;
                    timerButton.setMessage(timerMessage());
                    timerSpeedButton.active = entityTimerMode;
                    persist();
                }));
        timerButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                com.sorrowmist.useless.stretcher.client.gui.ButtonHelp.text("entity_timer")));
        timerSpeedButton = addRenderableWidget(new SelectableAE2Button(
                sliderLeft + sliderWidth / 2 + 2, timerY,
                Math.max(1, sliderWidth / 2 - 2), 18,
                timerSpeedMessage(), ignored -> stepTimerSpeed(1)));
        timerSpeedButton.active = entityTimerMode;
        timerSpeedButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                com.sorrowmist.useless.stretcher.client.gui.ButtonHelp.text("entity_timer_speed")));

        boolean sideBySide = bodyWidth >= 480;
        groupWidth = sideBySide ? (bodyWidth - 8) / 2 : bodyWidth;
        offsetLeft = sideBySide ? groupWidth + 8 : 0;
        offsetTop = sideBySide ? 64 : 156;
        int groupSliderWidth = Math.max(1, groupWidth - 8);
        addSlider(left + 4, top + 80, groupSliderWidth, 'X', false, sizeX);
        addSlider(left + 4, top + 100, groupSliderWidth, 'Y', false, sizeY);
        addSlider(left + 4, top + 120, groupSliderWidth, 'Z', false, sizeZ);
        addSlider(left + offsetLeft + 4, top + offsetTop + 16, groupSliderWidth, 'X', true, offsetX);
        addSlider(left + offsetLeft + 4, top + offsetTop + 36, groupSliderWidth, 'Y', true, offsetY);
        addSlider(left + offsetLeft + 4, top + offsetTop + 56, groupSliderWidth, 'Z', true, offsetZ);

        addRenderableWidget(new SelectableAE2Button(
                left, top + offsetTop + 92, bodyWidth, 18,
                Component.translatable("gui.useless_stretcher.back"), ignored -> onClose()));
        setContentExtent(offsetTop + 114);
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
        if (speed == SPEED_PRESETS[index]) return;
        speed = SPEED_PRESETS[index];
        if (speedButton != null) speedButton.setMessage(speedMessage());
        persist();
    }

    private void addSlider(int left, int y, int width, char axis, boolean offset, int initialValue) {
        int min = offset ? RangeAccelerationSavedData.MIN_OFFSET : RangeAccelerationSavedData.MIN_SIZE;
        int max = offset ? RangeAccelerationSavedData.MAX_OFFSET : RangeAccelerationSavedData.MAX_SIZE;
        AE2RangeSlider slider = addRenderableWidget(new AE2RangeSlider(
                left + 21, y, width - 42, 18, String.valueOf(axis), min, max, initialValue,
                value -> setValue(axis, offset, value)));
        addRenderableWidget(new SelectableAE2Button(
                left, y, 18, 18, Component.literal("-"), ignored -> slider.step(-1))).setTooltip(slider.getTooltip());
        addRenderableWidget(new SelectableAE2Button(
                left + width - 18, y, 18, 18, Component.literal("+"), ignored -> slider.step(1))).setTooltip(slider.getTooltip());
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
        persist();
    }

    private void persist() {
        RangeNetwork.editHistory(field.id(), speed, sizeX, sizeY, sizeZ, offsetX, offsetY, offsetZ,
                entityTimerMode, entityTimerSpeed);
    }

    private void stepTimerSpeed(int delta) {
        entityTimerSpeed = EntityTimerAcceleration.normalizeSpeed(entityTimerSpeed << delta);
        if (timerSpeedButton != null) timerSpeedButton.setMessage(timerSpeedMessage());
        persist();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = contentLeft();
        int top = contentTop();
        int bodyWidth = contentWidth();
        Component location = Component.literal(field.dimension() + "  "
                + field.center().getX() + ", " + field.center().getY() + ", " + field.center().getZ());
        graphics.drawString(font, font.plainSubstrByWidth(location.getString(), bodyWidth),
                left, top, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);

        StretcherScreenStyle.drawInset(graphics, left, top + 16, left + bodyWidth, top + 56);
        StretcherScreenStyle.drawInset(graphics, left, top + 64, left + groupWidth, top + 148);
        StretcherScreenStyle.drawInset(graphics, left + offsetLeft, top + offsetTop,
                left + offsetLeft + groupWidth, top + offsetTop + 84);
        graphics.drawString(font, Component.translatable("gui.useless_stretcher.range.speed_group"),
                left + 4, top + 20, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.useless_stretcher.range.size_group"),
                left + 4, top + 68, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.useless_stretcher.range.offset_group"),
                left + offsetLeft + 4, top + offsetTop + 4, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
        // Edit replies received while this screen is open do not update the history screen.
        RangeNetwork.requestHistory();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private Component speedMessage() {
        return Component.literal("x" + speed);
    }

    private Component timerMessage() {
        return Component.translatable("gui.useless_stretcher.range.entity_timer_" +
                (entityTimerMode ? "on" : "off"));
    }

    private Component timerSpeedMessage() {
        return Component.literal("T x" + entityTimerSpeed);
    }
}
