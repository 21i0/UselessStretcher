package com.sorrowmist.useless.stretcher.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.IntConsumer;

/** Native compact slider paired with the screen's existing AE2-style step buttons. */
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
