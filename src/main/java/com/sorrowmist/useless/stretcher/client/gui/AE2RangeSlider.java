package com.sorrowmist.useless.stretcher.client.gui;

import com.sorrowmist.useless.client.gui.AppleUi;
import com.sorrowmist.useless.client.gui.apple.AppleStyle;
import com.sorrowmist.useless.client.gui.apple.AppleTextLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.IntConsumer;

/** Native slider input and narration, drawn with Useless Mod's shared controls. */
public final class AE2RangeSlider extends AbstractSliderButton {
    private final String axis;
    private final int min;
    private final int max;
    private final IntConsumer onValueChanged;
    private int lastValue;

    public AE2RangeSlider(int x, int y, int width, int height, String axis,
                          int min, int max, int initialValue, IntConsumer onValueChanged) {
        super(x, y, width, height, Component.empty(), normalize(min, max, initialValue));
        this.axis = axis;
        this.min = min;
        this.max = max;
        this.onValueChanged = onValueChanged;
        this.lastValue = currentValue();
        updateMessage();
        ButtonHelp.set(this, min < 0 ? "offset_axis" : "size_axis", axis);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), trackWidth = Math.max(1, getWidth() - 12);
        AppleUi.buttonSurface(graphics, x, y, getWidth(), getHeight(), AppleUi.ButtonStyle.GRAY,
                isHoveredOrFocused(), false, active);
        AppleUi.progress(graphics, x + 6, y + getHeight() - 5, trackWidth, 2, (float) value, AppleStyle.BLUE);
        int knobX = x + 4 + (int) Math.round(value * trackWidth);
        AppleUi.fillRound(graphics, knobX, y + getHeight() - 7, 4, 6, 2,
                active ? AppleStyle.BLUE : AppleStyle.QUATERNARY);
        AppleTextLayout.drawCentered(graphics, Minecraft.getInstance().font, getMessage(),
                x, y, getWidth(), getHeight() - 5, 6, 1, null,
                active ? AppleStyle.LABEL : AppleStyle.QUATERNARY);
    }

    @Override
    protected void updateMessage() {
        if (axis != null) setMessage(Component.literal(axis + ": " + currentValue()));
    }

    @Override
    protected void applyValue() {
        int current = currentValue();
        value = normalize(min, max, current);
        if (current != lastValue) {
            lastValue = current;
            onValueChanged.accept(current);
        }
    }

    public void step(int delta) {
        int next = Mth.clamp(currentValue() + delta, min, max);
        if (next == currentValue()) return;
        value = normalize(min, max, next);
        applyValue();
        updateMessage();
    }

    private int currentValue() {
        return Mth.clamp(min + (int) Math.round(value * (max - min)), min, max);
    }

    private static double normalize(int min, int max, int value) {
        if (max <= min) return 0.0D;
        return (Mth.clamp(value, min, max) - min) / (double) (max - min);
    }
}
