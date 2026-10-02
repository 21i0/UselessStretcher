package com.sorrowmist.useless.stretcher.client.gui;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Reclaim confirmations retain vanilla behavior and explain both choices on hover. */
public final class StretcherConfirmScreen extends FloatingScreen {
    private final BooleanConsumer callback;
    private final Component message;
    private final Component yes;
    private final Component no;
    private boolean answered;
    public StretcherConfirmScreen(BooleanConsumer callback, Component title, Component message) {
        this(callback, title, message, CommonComponents.GUI_YES, CommonComponents.GUI_NO);
    }
    public StretcherConfirmScreen(BooleanConsumer callback, Component title, Component message, Component yes, Component no) {
        super(title, "reclaim_confirm", 360, 190, 250, 140);
        this.callback = callback;
        this.message = message;
        this.yes = yes;
        this.no = no;
    }
    @Override protected void initContent() {
        int messageHeight = font.split(message, Math.max(1, contentWidth())).size() * (font.lineHeight + 3);
        int buttonTop = messageHeight + 18;
        int buttonWidth = Math.max(1, (contentWidth() - 8) / 2);
        var confirm = addRenderableWidget(new SelectableAE2Button(contentLeft(), contentTop() + buttonTop,
                buttonWidth, 20, yes, ignored -> answer(true)));
        var cancel = addRenderableWidget(new SelectableAE2Button(contentLeft() + buttonWidth + 8,
                contentTop() + buttonTop, buttonWidth, 20, no, ignored -> answer(false)));
        ButtonHelp.set(confirm, "confirm_reclaim");
        ButtonHelp.set(cancel, "cancel_reclaim");
        setContentExtent(buttonTop + 24);
    }

    private void answer(boolean confirmed) {
        if (answered) return;
        answered = true;
        callback.accept(confirmed);
    }

    @Override protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int y = contentTop() + 4;
        for (var line : font.split(message, Math.max(1, contentWidth()))) {
            graphics.drawString(font, line, contentLeft(), y, StretcherScreenStyle.TEXT_COLOR, false);
            y += font.lineHeight + 3;
        }
    }

    @Override public void onClose() { answer(false); }
    @Override public boolean isPauseScreen() { return true; }
    @Override public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(super.getNarrationMessage(), message);
    }
}
