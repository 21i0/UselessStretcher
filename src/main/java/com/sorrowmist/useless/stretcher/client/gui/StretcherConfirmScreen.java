package com.sorrowmist.useless.stretcher.client.gui;

import com.sorrowmist.useless.client.gui.ldl.api.Ui;
import com.sorrowmist.useless.client.gui.ldl.api.UiNode;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Reclaim confirmations retain vanilla behavior and explain both choices on hover.
 *
 * <p>Rebuilt on Useless Mod's Apple UI API: the question is a
 * {@code FloatingScreen.wrappedText} node whose height follows its real line count and the answer
 * row is declared with {@code Ui.column/row}, so no message height or button coordinate is measured
 * by hand. Both answers stay native {@link SelectableAE2Button}s embedded with
 * {@code FloatingScreen.widgetNode}, so keyboard focus, narration and hover help keep working.</p>
 */
public final class StretcherConfirmScreen extends FloatingScreen {
    /** Row height of an embedded native button: compact AE2 look while staying clickable. */
    private static final int BUTTON_HEIGHT = 20;
    private static final int COLUMN_GAP = 3;
    /** Breathing room between the wrapped question and the answer row. */
    private static final int MESSAGE_GAP = 10;

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
        layoutBody(Ui.column(
                wrappedText(() -> message, StretcherScreenStyle.TEXT_COLOR),
                answers()).gap(MESSAGE_GAP));
    }

    /** Confirm and cancel share the width evenly: width(0) + grow splits the row in two halves. */
    private UiNode answers() {
        SelectableAE2Button confirm = new SelectableAE2Button(0, 0, 80, BUTTON_HEIGHT, yes, ignored -> answer(true));
        SelectableAE2Button cancel = new SelectableAE2Button(0, 0, 80, BUTTON_HEIGHT, no, ignored -> answer(false));
        ButtonHelp.set(confirm, "confirm_reclaim");
        ButtonHelp.set(cancel, "cancel_reclaim");
        return Ui.row(flexible(confirm), flexible(cancel)).gap(COLUMN_GAP).height(BUTTON_HEIGHT);
    }

    /** Native widget embedded by the shared layout: no width of its own, the row spreads it evenly. */
    private UiNode flexible(AbstractWidget widget) {
        return widgetNode(widget).width(0).grow().height(BUTTON_HEIGHT);
    }

    /** Idempotent: Esc, the title-bar close button and either answer all reach this exactly once. */
    private void answer(boolean confirmed) {
        if (answered) return;
        answered = true;
        callback.accept(confirmed);
    }

    /** The question and the answer row are painted by the shared Apple host; nothing is drawn by hand. */
    @Override protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override public void onClose() { answer(false); }
    @Override public boolean isPauseScreen() { return true; }
    @Override public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(super.getNarrationMessage(), message);
    }
}
