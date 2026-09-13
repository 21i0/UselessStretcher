package com.sorrowmist.useless.stretcher.client.gui;

import appeng.client.gui.widgets.AE2Button;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** AE2-style button with a persistent selected state for mutually exclusive choices. */
public final class SelectableAE2Button extends AE2Button {
    private boolean selected;

    public SelectableAE2Button(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress);
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        graphics.blitSprite(SPRITES.get(active, selected || isHoveredOrFocused()),
                getX(), getY(), getWidth(), getHeight());
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        int alphaChannel = Mth.ceil(alpha * 255.0F) << 24;
        int color = selected ? 0x517497 | alphaChannel : 0xF2F2F2 | alphaChannel;
        renderButtonText(graphics, Minecraft.getInstance().font, 2, color, selected ? 0 : 1);
    }
}
