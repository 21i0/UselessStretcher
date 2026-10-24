package com.sorrowmist.useless.stretcher.client.gui;

import com.sorrowmist.useless.compat.forge1201.client.PortButton;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** AE2-style button with a persistent selected state for mutually exclusive choices. */
public final class SelectableAE2Button extends PortButton {
    private boolean selected;
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

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderSurface(graphics,selected || isHoveredOrFocused());
        renderLabel(graphics,!active?0x888894:selected?0xAED4ED:0xF2F2F2);
    }
}
