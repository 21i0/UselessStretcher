package com.sorrowmist.useless.stretcher.client.gui;

import com.sorrowmist.useless.client.gui.AppleUi;
import com.sorrowmist.useless.client.gui.apple.AppleTextLayout;
import com.sorrowmist.useless.compat.forge1201.client.PortButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Native keyboard/tooltip adapter for Useless Mod's shared Apple button API. */
public final class SelectableAE2Button extends PortButton {
    private boolean selected, pressed;
    private net.minecraft.client.gui.components.Tooltip labelTooltip;

    public SelectableAE2Button(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress);
        ButtonHelp.automatic(this, message);
        labelTooltip = getTooltip();
    }

    @Override public void setMessage(Component message) {
        super.setMessage(message);
        // Refresh state-derived help, without replacing explicit contextual tooltips.
        if (getTooltip() == labelTooltip) {
            ButtonHelp.automatic(this, message);
            labelTooltip = getTooltip();
        }
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        boolean handled = super.mouseClicked(x, y, button);
        if (handled) pressed = true;
        return handled;
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        pressed = false;
        return super.mouseReleased(x, y, button);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var style = selected ? AppleUi.ButtonStyle.TINTED : AppleUi.ButtonStyle.GRAY;
        AppleUi.buttonSurface(graphics, getX(), getY(), getWidth(), getHeight(), style,
                isHoveredOrFocused(), pressed && isHovered(), active);
        AppleTextLayout.drawCentered(graphics, Minecraft.getInstance().font, getMessage(),
                getX(), getY(), getWidth(), getHeight(), Math.min(7, Math.max(3, getWidth() / 8)), 3,
                null, AppleUi.buttonTextColor(style, active));
    }
}
