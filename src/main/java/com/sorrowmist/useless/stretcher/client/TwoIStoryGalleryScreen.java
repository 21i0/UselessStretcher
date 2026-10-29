package com.sorrowmist.useless.stretcher.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sorrowmist.useless.stretcher.client.dialogue.TwoIStoryGallery;
import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Standalone, aspect-preserving viewer; opening it never changes story progress. */
public final class TwoIStoryGalleryScreen extends Screen implements TwoIInteractionScreen {
    private final Screen parent;
    private final int interactionEntityId;
    private int category, index;
    private boolean hidden;
    private List<TwoIStoryGallery.Picture> pictures = TwoIStoryGallery.pictures(0);

    public TwoIStoryGalleryScreen(Screen parent) {
        super(ui("gallery"));
        this.parent = parent;
        this.interactionEntityId = TwoIInteractionScreen.entityFor(parent);
    }

    private static Component ui(String key) {
        return Component.translatable("gui.useless_stretcher.two_i.vn." + key);
    }

    @Override protected void init() {
        int gap = 4;
        int buttonWidth = Math.max(40, Math.min(112, (width - 24 - 4 * gap) / 5));
        int x = (width - 5 * buttonWidth - 4 * gap) / 2;
        int y = height - 29;
        addRenderableWidget(new SelectableAE2Button(x, y, buttonWidth, 20, ui("previous"), b -> move(-1)));
        addRenderableWidget(new SelectableAE2Button(x + buttonWidth + gap, y, buttonWidth, 20,
                ui("gallery_category." + category), b -> {
                    category = (category + 1) % 4;
                    pictures = TwoIStoryGallery.pictures(category);
                    index = 0;
                    rebuildWidgets();
                }));
        addRenderableWidget(new SelectableAE2Button(x + 2 * (buttonWidth + gap), y, buttonWidth, 20,
                ui("next_page"), b -> move(1)));
        addRenderableWidget(new SelectableAE2Button(x + 3 * (buttonWidth + gap), y, buttonWidth, 20,
                ui("gallery_hide"), b -> setHidden(true)));
        addRenderableWidget(new SelectableAE2Button(x + 4 * (buttonWidth + gap), y, buttonWidth, 20,
                ui("return"), b -> onClose()));
        setHidden(hidden);
    }

    private void move(int delta) { index = Math.floorMod(index + delta, pictures.size()); }

    private void setHidden(boolean hidden) {
        this.hidden = hidden;
        for (var child : children()) if (child instanceof Button button) button.visible = !hidden;
        if (hidden) setFocused(null);
    }

    @Override public void renderBackground(GuiGraphics g) { }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xFF182238);
        var picture = pictures.get(index);
        int imageTop = hidden ? 4 : 36;
        int imageBottom = hidden ? height - 4 : height - 54;
        int availableWidth = Math.max(1, width - 16);
        int availableHeight = Math.max(1, imageBottom - imageTop);
        double scale = Math.min(availableWidth / (double) picture.width(),
                availableHeight / (double) picture.height());
        int imageWidth = Math.max(1, (int) Math.floor(picture.width() * scale));
        int imageHeight = Math.max(1, (int) Math.floor(picture.height() * scale));
        int imageX = (width - imageWidth) / 2;
        int imageY = imageTop + (availableHeight - imageHeight) / 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(picture.texture(), imageX, imageY, imageWidth, imageHeight, 0F, 0F,
                picture.width(), picture.height(), picture.width(), picture.height());
        RenderSystem.disableBlend();
        if (!hidden) {
            g.drawCenteredString(font, Component.translatable(picture.titleKey()), width / 2, 10, 0xFFF1F4FF);
            g.drawCenteredString(font, Component.literal((index + 1) + " / " + pictures.size()),
                    width / 2, 23, 0xFFBCCBED);
            g.drawCenteredString(font, ui("gallery_hint"), width / 2, height - 44, 0xFFBCCBED);
            super.render(g, mouseX, mouseY, partial);
        }
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (hidden) { setHidden(false); return true; }
        if (button == 1) { setHidden(true); return true; }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double vertical) {
        if (vertical != 0) move(vertical > 0 ? -1 : 1);
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT) { move(-1); return true; }
        if (key == GLFW.GLFW_KEY_RIGHT) { move(1); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE && hidden) { setHidden(false); return true; }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public int interactionEntityId() { return interactionEntityId; }
    @Override public boolean isPauseScreen() { return false; }
}
