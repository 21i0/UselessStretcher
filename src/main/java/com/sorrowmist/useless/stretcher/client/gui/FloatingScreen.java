package com.sorrowmist.useless.stretcher.client.gui;

import com.sorrowmist.useless.client.gui.apple.AppleHost;
import com.sorrowmist.useless.client.gui.ldl.api.Ui;
import com.sorrowmist.useless.client.gui.ldl.api.UiContext;
import com.sorrowmist.useless.client.gui.ldl.api.UiNode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import java.util.function.Supplier;

/** Common input and clipping; subclasses lay out real widgets without scaling fonts. */
public abstract class FloatingScreen extends Screen {
    private FloatingWindow window;
    private int contentScroll, contentExtent;
    private boolean scrollbarDragging;
    private double scrollbarGrab;
    private AppleHost body;

    /** Useless Mod owns the layout and widget tree; native inputs keep keyboard focus/narration. */
    public final AppleHost appleHost() { return body; }

    protected final void layoutBody(UiNode root) {
        setContentExtent(root.measureH());
        body = new AppleHost(width, height);
        UiContext.layout(body, null, body::add, root, contentLeft(), contentTop(), contentWidth(), root.measureH());
        body.layout(width, height, 0, 0);
    }

    /** Bridge native keyboard, tooltip and slider input into the base mod's layout API. */
    protected final UiNode widgetNode(AbstractWidget widget) {
        return new UiNode() {
            @Override protected int prefW() { return widget.getWidth(); }
            @Override protected int prefH() { return widget.getHeight(); }
            @Override protected void place(UiContext context, int x, int y, int w, int h) {
                widget.setX(x); widget.setY(y); widget.setWidth(Math.max(1, w));
                addRenderableWidget(widget);
            }
        };
    }

    protected final UiNode wrappedText(Supplier<Component> text, int color) {
        // Reserve normal-font line height even when the base theme renders a smaller caption.
        int lines = font.split(text.get(), Math.max(1, contentWidth() - Ui.t().cardPad * 2)).size();
        return Ui.body(text).wrap().color(color).height(Math.max(1, lines) * (font.lineHeight + 2));
    }

    protected FloatingScreen(Component title, String key, int defaultWidth, int defaultHeight, int minWidth, int minHeight) {
        super(title);
        window = new FloatingWindow(key, defaultWidth, defaultHeight, minWidth, minHeight);
    }
    public final FloatingWindow floatingWindow() { return window; }
    protected final void shareWindow(FloatingWindow shared) { window = shared; }
    protected final int contentLeft() { return window.bodyLeft() + sidebarWidth(); }
    protected final int contentTop() { return window.bodyTop() - contentScroll; }
    protected final int contentWidth() { return Math.max(1, window.bodyWidth() - sidebarWidth() - 5); }
    protected final int contentHeight() { return window.bodyHeight(); }
    protected final void setContentExtent(int height) {
        contentExtent = Math.max(0, height);
        contentScroll = Mth.clamp(contentScroll, 0, maxScroll());
    }
    protected abstract void initContent();
    protected abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);
    protected void renderContentTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    protected int sidebarWidth() { return 0; }
    protected void initChrome() { }
    protected void renderChrome(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }
    protected void closeWindow() { onClose(); }

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
        clearBody();
        initChrome();
        initContent();
        if (oldScroll != contentScroll) { clearWidgets(); clearBody(); initChrome(); initContent(); }
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
        renderChrome(graphics, mouseX, mouseY, partialTick);
        boolean inside = window.containsBody(mouseX, mouseY) && mouseX >= contentLeft() && !window.capturing();
        int contentMouseX = inside ? mouseX : -10000, contentMouseY = inside ? mouseY : -10000;
        graphics.flush();
        graphics.enableScissor(contentLeft(), window.bodyTop(), window.bodyLeft() + window.bodyWidth(), window.bodyTop() + window.bodyHeight());
        try {
            if (body != null) body.render(graphics, contentMouseX, contentMouseY, partialTick);
            renderContent(graphics, contentMouseX, contentMouseY, partialTick);
            super.render(graphics, contentMouseX, contentMouseY, partialTick);
            if (body != null) body.renderForeground(graphics, contentMouseX, contentMouseY, partialTick);
        } finally {
            // End buffered text/fills while their body clip is still active. This is
            // also required when a pack switches GuiGraphics into a batched renderer.
            try { graphics.flush(); } finally { graphics.disableScissor(); }
        }
        if (maxScroll() > 0) {
            int x = window.bodyLeft() + window.bodyWidth() - 3;
            graphics.fill(x, window.bodyTop(), x + 2, window.bodyTop() + contentHeight(), StretcherScreenStyle.SLOT_SHADOW_COLOR);
            graphics.fill(x, thumbTop(), x + 2, thumbTop() + thumbHeight(), StretcherScreenStyle.ACTIVE_COLOR);
        }
        if (inside) renderContentTooltip(graphics, mouseX, mouseY, partialTick);
        if (body != null) body.renderOverlay(graphics, mouseX, mouseY, partialTick);
        window.renderTooltip(graphics, font, mouseX, mouseY);
    }
    @Override public final void renderBackground(GuiGraphics graphics) {}

    @Override public final boolean mouseClicked(double x, double y, int button) {
        if (window.mouseClicked(x, y, button)) {
            if (window.consumeLayoutChanged()) { contentScroll = 0; relayoutContent(); }
            if (window.consumeCloseRequested()) closeWindow();
            return true;
        }
        if (!window.containsBody(x, y)) return false;
        if (x < contentLeft()) return super.mouseClicked(x, y, button);
        if (button == 0 && maxScroll() > 0 && x >= window.bodyLeft() + window.bodyWidth() - 5) {
            scrollbarDragging = true;
            scrollbarGrab = y >= thumbTop() && y <= thumbTop() + thumbHeight() ? y - thumbTop() : thumbHeight() / 2.0;
            dragScrollbar(y);
            return true;
        }
        if (body != null && body.mouseClicked(x, y, button)) return true;
        return clickContent(x, y, button);
    }
    protected boolean clickContent(double x, double y, int button) { return super.mouseClicked(x, y, button); }
    protected boolean dragContent(double x, double y, int button, double dx, double dy) { return false; }
    protected boolean releaseContent(double x, double y, int button) { return false; }
    @Override public final boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (window.mouseDragged(x, y, button)) { if (window.consumeLayoutChanged()) relayoutContent(); return true; }
        if (scrollbarDragging && button == 0) { dragScrollbar(y); return true; }
        if (body != null && body.mouseDragged(x, y, button, dx, dy)) return true;
        if (window.containsBody(x, y) && x >= contentLeft() && dragContent(x, y, button, dx, dy)) return true;
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public final boolean mouseReleased(double x, double y, int button) {
        if (window.mouseReleased(button)) return true;
        if (button == 0 && scrollbarDragging) { scrollbarDragging = false; return true; }
        if (body != null && body.mouseReleased(x, y, button)) return true;
        if (releaseContent(x, y, button)) return true;
        return super.mouseReleased(x, y, button);
    }
    @Override public final boolean mouseScrolled(double x, double y, double dy) {
        return window.containsBody(x, y) && x >= contentLeft() && !window.capturing() && scrollContent(x, y, 0, dy);
    }
    protected boolean scrollContent(double x, double y, double dx, double dy) {
        if (x < contentLeft()) return false;
        if (body != null && body.mouseScrolled(x, y, dy)) return true;
        if (super.mouseScrolled(x, y, dy)) return true;
        int next = Mth.clamp(contentScroll - (int) Math.round(dy * 20), 0, maxScroll());
        if (next != contentScroll) { contentScroll = next; relayoutContent(); return true; }
        return false;
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (body != null && body.isPopoverOpen() && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            body.closePopover();
            return true;
        }
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        if (handled && minecraft.screen == this && maxScroll() > 0 && getFocused() instanceof AbstractWidget widget
                && widget.getX() >= contentLeft()) {
            int delta = widget.getY() < window.bodyTop() ? widget.getY() - window.bodyTop()
                    : Math.max(0, (widget.getY() + widget.getHeight()) - window.bodyTop() - contentHeight());
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
    @Override public void tick() {
        super.tick();
        if (body != null) body.tick();
    }
    private void clearBody() {
        if (body != null) body.clear();
        body = null;
    }
    @Override public void removed() { clearBody(); window.save(); super.removed(); }
    @Override public boolean isPauseScreen() { return false; }
}
