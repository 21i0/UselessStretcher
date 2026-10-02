package com.sorrowmist.useless.stretcher.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Common input and clipping; subclasses lay out real widgets without scaling fonts. */
public abstract class FloatingScreen extends Screen {
    private final FloatingWindow window;
    private int contentScroll, contentExtent;
    private boolean scrollbarDragging;
    private double scrollbarGrab;

    protected FloatingScreen(Component title, String key, int defaultWidth, int defaultHeight, int minWidth, int minHeight) {
        super(title);
        window = new FloatingWindow(key, defaultWidth, defaultHeight, minWidth, minHeight);
    }
    public final FloatingWindow floatingWindow() { return window; }
    protected final int contentLeft() { return window.bodyLeft(); }
    protected final int contentTop() { return window.bodyTop() - contentScroll; }
    protected final int contentWidth() { return Math.max(1, window.bodyWidth() - 5); }
    protected final int contentHeight() { return window.bodyHeight(); }
    protected final void setContentExtent(int height) {
        contentExtent = Math.max(0, height);
        contentScroll = Mth.clamp(contentScroll, 0, maxScroll());
    }
    protected abstract void initContent();
    protected abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);
    protected void renderContentTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override protected final void init() {
        window.init(width, height);
        window.consumeLayoutChanged();
        relayoutContent();
    }
    private void relayoutContent() {
        var focused = getFocused();
        int focusedIndex = children().indexOf(focused);
        clearWidgets();
        setFocused(null);
        int oldScroll = contentScroll;
        initContent();
        if (oldScroll != contentScroll) { clearWidgets(); initContent(); }
        restoreFocus(focused, focusedIndex);
    }
    @Override protected void rebuildWidgets() {
        var focused = getFocused();
        int focusedIndex = children().indexOf(focused);
        super.rebuildWidgets();
        restoreFocus(focused, focusedIndex);
    }
    private void restoreFocus(GuiEventListener focused, int focusedIndex) {
        Component focusLabel = focused instanceof AbstractWidget widget ? widget.getMessage() : null;
        if (focused != null && children().contains(focused)) setFocused(focused);
        else if (focusLabel != null && focusedIndex >= 0 && focusedIndex < children().size()
                && children().get(focusedIndex) instanceof AbstractWidget widget
                && widget.getClass() == focused.getClass() && widget.getMessage().equals(focusLabel)) {
            setFocused(widget);
        }
        else if (focusLabel != null) for (var child : children()) {
            if (child instanceof AbstractWidget widget && child.getClass() == focused.getClass() && widget.getMessage().equals(focusLabel)) {
                setFocused(child); break;
            }
        }
    }

    @Override public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x33000000);
        window.renderFrame(graphics, font, title, mouseX, mouseY);
        boolean inside = window.containsBody(mouseX, mouseY) && !window.capturing();
        int contentMouseX = inside ? mouseX : -10000, contentMouseY = inside ? mouseY : -10000;
        graphics.enableScissor(window.bodyLeft(), window.bodyTop(), window.bodyLeft() + window.bodyWidth(), window.bodyTop() + window.bodyHeight());
        try {
            renderContent(graphics, contentMouseX, contentMouseY, partialTick);
            super.render(graphics, contentMouseX, contentMouseY, partialTick);
        } finally { graphics.disableScissor(); }
        if (maxScroll() > 0) {
            int x = window.bodyLeft() + window.bodyWidth() - 3;
            graphics.fill(x, window.bodyTop(), x + 2, window.bodyTop() + contentHeight(), StretcherScreenStyle.SLOT_SHADOW_COLOR);
            graphics.fill(x, thumbTop(), x + 2, thumbTop() + thumbHeight(), StretcherScreenStyle.ACTIVE_COLOR);
        }
        if (inside) renderContentTooltip(graphics, mouseX, mouseY, partialTick);
        window.renderTooltip(graphics, font, mouseX, mouseY);
    }
    @Override public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override public final boolean mouseClicked(double x, double y, int button) {
        if (window.mouseClicked(x, y, button)) {
            if (window.consumeLayoutChanged()) { contentScroll = 0; relayoutContent(); }
            if (window.consumeCloseRequested()) onClose();
            return true;
        }
        if (!window.containsBody(x, y)) return false;
        if (button == 0 && maxScroll() > 0 && x >= window.bodyLeft() + window.bodyWidth() - 5) {
            scrollbarDragging = true;
            scrollbarGrab = y >= thumbTop() && y <= thumbTop() + thumbHeight() ? y - thumbTop() : thumbHeight() / 2.0;
            dragScrollbar(y);
            return true;
        }
        return clickContent(x, y, button);
    }
    protected boolean clickContent(double x, double y, int button) { return super.mouseClicked(x, y, button); }
    @Override public final boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (window.mouseDragged(x, y, button)) { if (window.consumeLayoutChanged()) relayoutContent(); return true; }
        if (scrollbarDragging && button == 0) { dragScrollbar(y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public final boolean mouseReleased(double x, double y, int button) {
        if (window.mouseReleased(button)) return true;
        if (button == 0 && scrollbarDragging) { scrollbarDragging = false; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public final boolean mouseScrolled(double x, double y, double dx, double dy) {
        return window.containsBody(x, y) && !window.capturing() && scrollContent(x, y, dx, dy);
    }
    protected boolean scrollContent(double x, double y, double dx, double dy) {
        if (super.mouseScrolled(x, y, dx, dy)) return true;
        int next = Mth.clamp(contentScroll - (int) Math.round(dy * 20), 0, maxScroll());
        if (next != contentScroll) { contentScroll = next; relayoutContent(); return true; }
        return false;
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        if (handled && minecraft.screen == this && maxScroll() > 0 && getFocused() instanceof AbstractWidget widget) {
            int delta = widget.getY() < window.bodyTop() ? widget.getY() - window.bodyTop()
                    : Math.max(0, widget.getBottom() - window.bodyTop() - contentHeight());
            int next = Mth.clamp(contentScroll + delta, 0, maxScroll());
            if (next != contentScroll) { contentScroll = next; relayoutContent(); }
        }
        return handled;
    }
    private int maxScroll() { return Math.max(0, contentExtent - contentHeight()); }
    private int thumbHeight() { return Math.min(contentHeight(), Math.max(16, contentHeight() * contentHeight() / Math.max(1, contentExtent))); }
    private int thumbTop() { return window.bodyTop() + (contentHeight() - thumbHeight()) * contentScroll / Math.max(1, maxScroll()); }
    private void dragScrollbar(double y) {
        int next = Mth.clamp((int) Math.round((y - window.bodyTop() - scrollbarGrab) * maxScroll() / Math.max(1, contentHeight() - thumbHeight())), 0, maxScroll());
        if (next != contentScroll) { contentScroll = next; relayoutContent(); }
    }
    @Override public void removed() { window.save(); super.removed(); }
    @Override public boolean isPauseScreen() { return false; }
}
