package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.client.dialogue.TwoIStoryScript;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Server-backed chapter unlocks; all replay choices are local and never grant rewards. */
public final class TwoIStoryLibraryScreen extends Screen implements TwoIInteractionScreen {
    private static int nextRequestId;
    private final Screen parent;
    private final int bond;
    private final int interactionEntityId;
    private int requestId;
    private int completedMask;
    private int waitingTicks;
    private boolean requested, loaded, failed;
    private int panelX, panelY, panelWidth, panelHeight;
    private Button retry;
    private final Button[] chapters = new Button[6];

    public TwoIStoryLibraryScreen(Screen parent, int bond) {
        super(ui("library"));
        this.parent = parent;
        this.interactionEntityId = TwoIInteractionScreen.entityFor(parent);
        this.bond = bond;
    }

    private static Component ui(String key) {
        return Component.translatable("gui.useless_stretcher.two_i.vn." + key);
    }

    @Override protected void init() {
        panelWidth = Math.min(560, width - 16);
        panelHeight = Math.min(270, height - 16);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        int half = (panelWidth - 34) / 2;
        int rowsY = panelY + 70;
        for (int i = 0; i < chapters.length; i++) {
            final int chapter = i;
            chapters[i] = addRenderableWidget(Button.builder(TwoIStoryScript.title(i), b -> {
                if (loaded && (completedMask & (1 << chapter)) != 0) {
                    minecraft.setScreen(TwoIStoryScreen.replay(this, chapter, bond));
                }
            }).bounds(panelX + 12 + i % 2 * (half + 10), rowsY + i / 2 * 31, half, 25).build());
        }
        int bottom = panelY + panelHeight - 34;
        addRenderableWidget(Button.builder(ui("gallery"), b ->
                minecraft.setScreen(new TwoIStoryGalleryScreen(this)))
                .bounds(panelX + 12, bottom, half, 22).build());
        addRenderableWidget(Button.builder(ui("return"), b -> onClose())
                .bounds(panelX + 22 + half, bottom, half, 22).build());
        retry = addRenderableWidget(Button.builder(ui("library_retry"), b -> requestLibrary())
                .bounds(panelX + panelWidth - 92, panelY + 12, 80, 20).build());
        updateButtons();
        if (!requested) requestLibrary();
    }

    private void requestLibrary() {
        nextRequestId = nextRequestId == Integer.MAX_VALUE ? 1 : nextRequestId + 1;
        requestId = nextRequestId;
        requested = true;
        loaded = false;
        failed = false;
        waitingTicks = 0;
        updateButtons();
        Network.requestTwoIStoryLibrary(requestId);
    }

    public void acceptLibrary(int requestId, int completedMask) {
        if (requestId != this.requestId) return;
        this.completedMask = completedMask & 63;
        loaded = true;
        failed = false;
        updateButtons();
    }

    private void updateButtons() {
        for (int i = 0; i < chapters.length; i++) {
            if (chapters[i] == null) continue;
            boolean unlocked = loaded && (completedMask & (1 << i)) != 0;
            chapters[i].active = unlocked;
            chapters[i].setTooltip(Tooltip.create(ui(unlocked ? "replay_hint" : "chapter_locked")));
        }
        if (retry != null) retry.visible = failed;
    }

    @Override public void tick() {
        if (!loaded && !failed && ++waitingTicks >= 200) {
            failed = true;
            updateButtons();
        }
    }

    @Override public void renderBackground(GuiGraphics g, int x, int y, float partial) { }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xB8192039);
        StretcherScreenStyle.drawPanel(g, panelX, panelY, panelWidth, panelHeight);
        g.drawString(font, title, panelX + 12, panelY + 15, StretcherScreenStyle.TEXT_COLOR, false);
        g.drawWordWrap(font, ui(failed ? "library_failed" : !loaded ? "library_loading" : "library_hint"),
                panelX + 12, panelY + 40, panelWidth - 24, StretcherScreenStyle.SUBTLE_TEXT_COLOR);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void removed() {
        // A reply arriving while the gallery is on top is deliberately ignored. Re-fetch
        // on return instead of retaining a request whose response can no longer arrive.
        if (!loaded) requested = false;
    }
    @Override public int interactionEntityId() { return interactionEntityId; }
    @Override public boolean isPauseScreen() { return false; }
}
