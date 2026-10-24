package com.sorrowmist.useless.stretcher.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sorrowmist.useless.stretcher.client.dialogue.TwoIStoryScript;
import com.sorrowmist.useless.stretcher.client.dialogue.TwoIStoryTimeline;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/** Presentation only: no local bond awards, and history/auto never replay network actions. */
public final class TwoIStoryScreen extends Screen implements TwoIInteractionScreen {
    private static final int CANVAS_W = 800, CANVAS_H = 450;
    private static final int HISTORY_ROWS = 17;
    private static final ResourceLocation FRAME = texture("ui/dialogue_frame");
    private static final ResourceLocation BOW = texture("ui/bow");
    private static int textSpeed = 2;
    private static boolean voiceEnabled = true;
    private final com.sorrowmist.useless.stretcher.client.dialogue.TwoIStoryVoice voice =
            new com.sorrowmist.useless.stretcher.client.dialogue.TwoIStoryVoice();
    private final TwoIDialogueScreen parent;
    private final Screen returnScreen;
    private final boolean replay;
    private final int interactionEntityId;
    private final int entityId, chapter;
    private final long sessionId;
    private final ResourceLocation cg;
    private int bond;
    private List<TwoIStoryScript.Line> lines;
    private int stage, lineIndex, round, chosen = -1;
    private String fullText = "", visibleText = "";
    private int revealed, characterCount, readTicks, waitingTicks;
    private LongSupplier clock = System::nanoTime;
    private long lastFrameNanos;
    private TwoIStoryTimeline timeline;
    private ResourceLocation expression;
    private boolean automatic, fastForward, hidden, waiting, failed;
    private int overlay; // 0: story, 1: read-only backlog, 2: preferences
    private final List<Component> history = new ArrayList<>();
    private List<FormattedCharSequence> historyLines = List.of();
    private int historyScroll;
    private final List<Button> controls = new ArrayList<>();
    private final List<Button> overlayControls = new ArrayList<>();
    private final Button[] choices = new Button[3];
    private Button nextButton, autoButton, fastButton, speedButton, voiceButton, libraryButton, galleryButton;

    public TwoIStoryScreen(TwoIDialogueScreen parent, int entityId, long sessionId, int chapter, int bond) {
        this(parent, entityId, sessionId, chapter, bond, 0);
    }

    public TwoIStoryScreen(TwoIDialogueScreen parent, int entityId, long sessionId, int chapter, int bond, int round) {
        this(parent, parent, entityId, sessionId, chapter, bond, false, round);
    }

    public static TwoIStoryScreen replay(Screen parent, int chapter, int bond) {
        return new TwoIStoryScreen(parent, null, -1, 0L, chapter, bond, true, 0);
    }

    private TwoIStoryScreen(Screen returnScreen, TwoIDialogueScreen parent, int entityId,
                            long sessionId, int chapter, int bond, boolean replay, int round) {
        super(TwoIStoryScript.title(chapter));
        this.parent = parent;
        this.returnScreen = returnScreen;
        this.interactionEntityId = TwoIInteractionScreen.entityFor(returnScreen);
        this.replay = replay;
        this.entityId = entityId;
        this.sessionId = sessionId;
        this.chapter = Mth.clamp(chapter, 0, 5);
        this.bond = bond;
        this.round = Mth.clamp(round, 0, 2);
        this.cg = texture("cg/" + switch (this.chapter) {
            case 1, 3, 4 -> "friends";
            case 2, 5 -> "starfield_invite";
            default -> "first_meet";
        });
        lines = this.round == 0 ? TwoIStoryScript.opening(this.chapter, bond)
                : TwoIStoryScript.prompt(this.chapter, this.round);
        enterLine();
    }

    private static ResourceLocation texture(String file) {
        return new ResourceLocation("useless_stretcher", "textures/dialogue/" + file + ".png");
    }

    private static Component ui(String key) {
        return Component.translatable("gui.useless_stretcher.two_i.vn." + key);
    }

    private static Component speaker(TwoIStoryScript.Line line) {
        return line.narration() ? ui("narrator") : line.player() ? ui("you") : Component.literal("2i");
    }

    @Override protected void init() {
        controls.clear();
        overlayControls.clear();
        autoButton = control(246, 410, 90, "auto", b -> {
            automatic = !automatic && stage != 1 && !failed;
            fastForward = false;
            autoButton.setMessage(ui(automatic ? "auto_on" : "auto"));
            readTicks = 0;
            syncWidgets();
        });
        controls.add(autoButton);
        controls.add(control(341, 410, 90, "log", b -> openOverlay(1)));
        fastButton = control(436, 410, 90, "fast", b -> {
            fastForward = !fastForward && stage != 1 && !failed;
            automatic = false;
            readTicks = 0;
            syncWidgets();
        });
        controls.add(fastButton);
        controls.add(control(531, 410, 90, "settings", b -> openOverlay(2)));
        nextButton = control(626, 410, 142, "continue", b -> advance());
        controls.add(nextButton);
        libraryButton = control(458, 12, 102, "library", b -> {
            automatic = false;
            fastForward = false;
            voice.stop();
            minecraft.setScreen(new TwoIStoryLibraryScreen(this, bond));
        });
        controls.add(libraryButton);
        galleryButton = control(565, 12, 102, "gallery", b -> {
            automatic = false;
            fastForward = false;
            voice.stop();
            minecraft.setScreen(new TwoIStoryGalleryScreen(this));
        });
        controls.add(galleryButton);
        controls.add(control(672, 12, 104, "return", b -> onClose()));
        for (int i = 0; i < choices.length; i++) {
            int choice = i;
            choices[i] = addRenderableWidget(new NovelButton(200, 164 + i * 40, 400, 34,
                    choiceLabel(i), b -> choose(choice)));
            choices[i].setTooltip(net.minecraft.client.gui.components.Tooltip.create(ui(replay
                    ? "replay_hint" : "choice_bond_hint")));
        }
        overlayControls.add(control(546, 90, 96, "close", b -> { overlay = 0; syncWidgets(); }));
        overlayControls.add(control(170, 312, 106, "previous", b -> scrollHistory(-HISTORY_ROWS)));
        overlayControls.add(control(284, 312, 106, "next_page", b -> scrollHistory(HISTORY_ROWS)));
        speedButton = control(270, 180, 260, "speed", b -> {
            textSpeed = textSpeed == 1 ? 2 : textSpeed == 2 ? 4 : textSpeed == 4 ? 0 : 1;
            speedButton.setMessage(speedLabel());
        });
        speedButton.setMessage(speedLabel());
        overlayControls.add(speedButton);
        voiceButton = control(270, 216, 260, "voice_on", b -> {
            voiceEnabled = !voiceEnabled;
            if (!voiceEnabled) voice.stop();
            voiceButton.setMessage(ui(voiceEnabled ? "voice_on" : "voice_off"));
        });
        voiceButton.setMessage(ui(voiceEnabled ? "voice_on" : "voice_off"));
        overlayControls.add(voiceButton);
        // init also runs after window/GUI-scale changes; retain story, pending choice and text position.
        if (overlay == 1) rebuildHistory();
        syncWidgets();
    }

    private Button control(int x, int y, int w, String key, Button.OnPress action) {
        return addRenderableWidget(new NovelButton(x, y, w, 24, ui(key), action));
    }

    private Component speedLabel() {
        return Component.translatable("gui.useless_stretcher.two_i.vn.speed_value",
                ui("speed." + (textSpeed == 0 ? "instant" : textSpeed == 1 ? "slow" : textSpeed == 2 ? "normal" : "fast")));
    }

    private Component choiceLabel(int choice) {
        Component text = TwoIStoryScript.choice(chapter, round, choice);
        int delta = com.sorrowmist.useless.stretcher.content.entity.TwoIStorySession.bondDelta(choice);
        return text.copy().append(Component.literal(replay ? "  [↶]"
                : delta > 0 ? "  [+1]" : delta < 0 ? "  [-1]" : "  [±0]"));
    }

    private void syncWidgets() {
        lastFrameNanos = clock.getAsLong();
        if (hidden || overlay != 0 || fastForward || waiting || failed || stage == 1) voice.stop();
        for (Button b : controls) b.visible = overlay == 0 && !hidden;
        for (Button b : overlayControls) b.visible = false;
        if (overlay > 0) {
            overlayControls.get(0).visible = true;
            if (overlay == 1) {
                overlayControls.get(1).visible = true;
                overlayControls.get(2).visible = true;
            } else { speedButton.visible = true; voiceButton.visible = true; }
        }
        for (int i = 0; i < choices.length; i++) {
            Button b = choices[i];
            b.setMessage(choiceLabel(i));
            b.visible = stage == 1 && overlay == 0 && !hidden;
            b.active = !waiting && !failed;
        }
        if (nextButton != null) {
            libraryButton.active = !waiting;
            galleryButton.active = !waiting;
            nextButton.active = (!waiting && stage != 1) || failed;
            nextButton.setMessage(ui(failed || stage == 3 && lineIndex == lines.size() - 1 ? "return" : "continue"));
            autoButton.active = stage != 1 && !failed;
            autoButton.setMessage(ui(automatic ? "auto_on" : "auto"));
            fastButton.active = stage != 1 && !failed;
            fastButton.setMessage(ui(fastForward ? "fast_on" : "fast"));
        }
        if (getFocused() instanceof Button b && (!b.visible || !b.active)) setFocused(null);
    }

    private void enterLine() {
        var line = lines.get(lineIndex);
        fullText = TwoIStoryScript.text(line).getString();
        characterCount = fullText.codePointCount(0, fullText.length());
        revealed = 0;
        visibleText = "";
        voice.stop();
        timeline = new TwoIStoryTimeline(line.key(), fullText, line.player(), line.narration());
        lastFrameNanos = clock.getAsLong();
        readTicks = 0;
        expression = texture("expressions/" + line.expression());
        String who = speaker(line).getString();
        history.add(Component.literal(who + "  ·  " + fullText));
        if (history.size() > 512) history.remove(0);
        if (minecraft != null) minecraft.getNarrator().sayNow(Component.literal(who + ": " + fullText));
    }

    private void reveal() {
        timeline.finish();
        voice.stop();
        revealed = characterCount;
        visibleText = fullText;
        readTicks = 0;
    }

    private void advance() {
        if (overlay != 0 || hidden || waiting) return;
        if (failed) { onClose(); return; }
        if (revealed < characterCount) { reveal(); return; }
        if (stage == 1) return;
        if (++lineIndex < lines.size()) {
            enterLine();
        } else if (stage == 0) {
            lineIndex = lines.size() - 1;
            stage = 1;
            automatic = false;
            fastForward = false;
        } else if (stage == 2) {
            if (++round < 3) {
                stage = 0;
                chosen = -1;
                lines = TwoIStoryScript.prompt(chapter, round);
            } else {
                stage = 3;
                lines = TwoIStoryScript.ending(chapter);
            }
            lineIndex = 0;
            enterLine();
        } else {
            onClose();
            return;
        }
        syncWidgets();
    }

    private void choose(int choice) {
        if (stage != 1 || waiting || failed || chosen >= 0) return;
        automatic = false;
        fastForward = false;
        chosen = choice;
        if (replay) {
            // Replaying a chapter is presentation only: no session is created or consumed,
            // and no network packet can award bond or unlock another chapter.
            showBranch();
            return;
        }
        waiting = true;
        waitingTicks = 0;
        Network.sendTwoIDollStory(entityId, sessionId, round * 3 + choice);
        syncWidgets();
    }

    public int entityId() { return entityId; }

    @Override public int interactionEntityId() { return interactionEntityId; }

    public void onChoiceResult(long token, int value) {
        onChoiceResult(token, value, round);
    }

    public void onChoiceResult(long token, int value, int acceptedRound) {
        if (acceptedRound != round) return;
        if (replay || token != sessionId || !waiting || chosen < 0) return;
        waiting = false;
        bond = value;
        parent.updateStoryBond(value);
        showBranch();
    }

    private void showBranch() {
        stage = 2;
        lineIndex = 0;
        lines = TwoIStoryScript.branch(chapter, round, chosen);
        enterLine();
        syncWidgets();
    }

    public void updateGiftStatus(boolean ready, int value) {
        bond = value;
        if (parent != null) parent.updateGiftStatus(ready, value);
    }

    public void onStoryUnavailable(long token) {
        onStoryUnavailable(token, round);
    }

    public void onStoryUnavailable(long token, int rejectedRound) {
        if (rejectedRound != round) return;
        if (token != sessionId || !waiting) return;
        waiting = false;
        automatic = false;
        fastForward = false;
        failed = true;
        syncWidgets();
    }

    @Override public void tick() {
        if (waiting && ++waitingTicks >= 200) onStoryUnavailable(sessionId);
        if (hidden || overlay != 0 || waiting || failed || stage == 1) return;
        // Audible typewriting advances only on a displayed frame. Several logic ticks may run
        // between low-FPS frames; playing here would get ahead of what the player can see.
        if (revealed < characterCount) {
            if (fastForward || textSpeed == 0) reveal();
            return;
        }
        if ((automatic || fastForward)
                && ++readTicks >= (fastForward ? 4 : Math.max(40, characterCount + 20))) advance();
    }

    private void updatePlayback() {
        long now = clock.getAsLong();
        double elapsedMs = Math.max(0, (now - lastFrameNanos) / 1_000_000.0);
        lastFrameNanos = now;
        if (hidden || overlay != 0 || waiting || failed || stage == 1 || revealed >= characterCount) return;
        if (fastForward || textSpeed == 0) { reveal(); return; }
        var cue = timeline.advance(elapsedMs, textSpeed);
        if (cue != null) {
            revealed = cue.revealed();
            visibleText = fullText.substring(0, fullText.offsetByCodePoints(0, revealed));
            // The exact same event reveals the glyph and starts its syllable, never a separate sound timer.
            voice.play(cue, voiceEnabled);
        }
    }

    private void openOverlay(int type) {
        automatic = false;
        fastForward = false;
        overlay = type;
        if (type == 1) { rebuildHistory(); historyScroll = Math.max(0, historyLines.size() - HISTORY_ROWS); }
        syncWidgets();
    }

    private void rebuildHistory() {
        List<FormattedCharSequence> result = new ArrayList<>();
        for (Component entry : history) {
            result.addAll(font.split(entry, 450));
            result.add(FormattedCharSequence.EMPTY);
        }
        historyLines = List.copyOf(result);
    }

    private void scrollHistory(int delta) { historyScroll = Mth.clamp(historyScroll + delta, 0, Math.max(0, historyLines.size() - HISTORY_ROWS)); }

    private float scale() { return Math.min(width / (float) CANVAS_W, height / (float) CANVAS_H); }
    private float left() { return (width - CANVAS_W * scale()) / 2; }
    private float top() { return (height - CANVAS_H * scale()) / 2; }

    @Override public void renderBackground(GuiGraphics g) { }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        updatePlayback();
        g.fill(0, 0, width, height, 0xFF192039);
        float scale = scale();
        int mx = (int) ((mouseX - left()) / scale), my = (int) ((mouseY - top()) / scale);
        g.pose().pushPose();
        g.pose().translate(left(), top(), 0);
        g.pose().scale(scale, scale, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Explicit destination and source sizes are essential: the short blit overload crops instead of scaling.
        g.blit(cg, 0, 0, CANVAS_W, CANVAS_H, 0f, 0f, 836, 471, 836, 471);
        if (!hidden) {
            g.fillGradient(0, 0, CANVAS_W, 80, 0xB0182448, 0x00182448);
            g.drawString(font, title, 24, 21, 0xFFFFFFFF, true);
            g.drawString(font, Component.translatable("gui.useless_stretcher.two_i.bond", bond), 24, 37, 0xFFE0E9FF, true);
            if (replay) g.drawString(font, ui("replay_hint"), 24, 54, 0xFFE0E9FF, true);
            var line = lines.get(lineIndex);
            if (!line.cg()) {
                // The supplied full-body expressions have a white background. Present them as a character card.
                g.fill(579, 48, 751, 298, 0xFFF6F8FF);
                g.blit(expression, 583, 50, 164, 246, 0f, 0f, 512, 768, 512, 768);
                g.renderOutline(579, 48, 172, 250, 0xFFB3C7F5);
            }
            drawFrame(g, 24, 314, 752, 128);
            g.blit(BOW, 4, 285, 82, 82, 0f, 0f, 627, 627, 627, 627);
            drawFrame(g, 86, 305, 122, 28);
            g.drawCenteredString(font, speaker(line), 147, 315, 0xFF2E4476);
            Component text = failed ? ui("rejected") : waiting ? ui("waiting") : Component.literal(visibleText);
            g.pose().pushPose();
            g.pose().translate(67, 342, 0);
            g.pose().scale(1.25F, 1.25F, 1);
            g.drawWordWrap(font, text, 0, 0, 528, 0xFF213653);
            g.pose().popPose();
            if (stage == 1 && !waiting && !failed) g.drawCenteredString(font, ui("choose"), 400, 148, 0xFFFFFFFF);
            g.drawString(font, ui("click_hint"), 63, 399, 0xFF647797, false);
            if (overlay > 0) drawOverlay(g);
            super.render(g, mx, my, partial);
        } else g.drawString(font, ui("show_hint"), 24, 425, 0xFFFFFFFF, true);
        RenderSystem.disableBlend();
        g.pose().popPose();
    }

    private void drawOverlay(GuiGraphics g) {
        g.fill(0, 0, CANVAS_W, CANVAS_H, 0x980E1428);
        drawFrame(g, 140, 58, 520, 324);
        g.drawString(font, ui(overlay == 1 ? "log" : "settings"), 172, 97, 0xFF243960, false);
        if (overlay == 1) {
            int end = Math.min(historyLines.size(), historyScroll + HISTORY_ROWS);
            for (int i = historyScroll; i < end; i++) g.drawString(font, historyLines.get(i), 172,
                    122 + (i - historyScroll) * 11, 0xFF344461, false);
            g.drawWordWrap(font, ui("log_hint"), 400, 318, 222, 0xFF647797);
        } else {
            g.drawWordWrap(font, ui("help"), 172, 118, 454, 0xFF344461);
            g.drawWordWrap(font, ui("settings_hint"), 172, 264, 454, 0xFF647797);
        }
    }

    private static void drawFrame(GuiGraphics g, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        g.blit(FRAME, x, y, w, h, 16f, 60f, 1060, 252, 1086, 362);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        double mx = (x - left()) / scale(), my = (y - top()) / scale();
        if (button == 1) {
            if (overlay > 0) overlay = 0;
            else hidden = !hidden;
            automatic = false;
            fastForward = false;
            syncWidgets();
            return true;
        }
        if (hidden) { hidden = false; syncWidgets(); return true; }
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0 && overlay == 0 && mx >= 24 && mx <= 776 && my >= 314 && my < 410) {
            advance();
            return true;
        }
        return true; // Never leak clicks into world interactions.
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        return super.mouseReleased((x - left()) / scale(), (y - top()) / scale(), button);
    }

    @Override public boolean mouseScrolled(double x, double y, double vertical) {
        if (overlay == 1) scrollHistory((int) -Math.signum(vertical) * 3);
        else if (vertical > 0) openOverlay(1);
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (overlay > 0) { overlay = 0; syncWidgets(); }
            else if (hidden) { hidden = false; syncWidgets(); }
            else onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (getFocused() instanceof Button button && button.visible && button.active) return super.keyPressed(key, scan, modifiers);
            advance();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void onClose() { voice.stop(); minecraft.setScreen(returnScreen); }
    @Override public void removed() { voice.stop(); }
    @Override public boolean isPauseScreen() { return false; }

    private final class NovelButton extends Button {
        private NovelButton(int x, int y, int w, int h, Component text, OnPress callback) {
            super(x, y, w, h, text, callback, DEFAULT_NARRATION);
        }

        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            drawFrame(g, getX(), getY(), getWidth(), getHeight());
            boolean selected = isHoveredOrFocused() && active;
            if (selected) g.renderOutline(getX() + 3, getY() + 2, getWidth() - 6, getHeight() - 4, 0xFF6A92E0);
            String label = font.plainSubstrByWidth(getMessage().getString(), getWidth() - 16);
            g.drawString(font, label, getX() + (getWidth() - font.width(label)) / 2,
                    getY() + (getHeight() - 8) / 2, active ? 0xFF304675 : 0xFF8994AD, false);
        }
    }
}
