package com.sorrowmist.useless.stretcher.storytest;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.client.TwoIDialogueScreen;
import com.sorrowmist.useless.stretcher.client.TwoIStoryGalleryScreen;
import com.sorrowmist.useless.stretcher.client.TwoIStoryLibraryScreen;
import com.sorrowmist.useless.stretcher.client.TwoIStoryScreen;
import com.sorrowmist.useless.stretcher.client.dialogue.TwoIStoryGallery;
import com.sorrowmist.useless.stretcher.client.dialogue.TwoIStoryScript;
import com.sorrowmist.useless.stretcher.client.dialogue.TwoIStoryTimeline;
import com.sorrowmist.useless.stretcher.client.dialogue.TwoIStoryDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Opt-in, title-screen regression fixture. Never creates a world or sends a choice packet. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT)
public final class StoryChecks {
    private static final String[] LANGUAGES = {"en_us", "zh_cn"};
    private static final int CHAPTER_COUNT = 6;
    private static final String[] VIEWS = {"cg", "expression", "choice", "branch", "history", "settings", "choice-small", "history-small"};
    private static int ticks, language, chapter, view, frames;
    private static boolean initialized, checked, finished;
    private static CompletableFuture<Void> reload;
    private static TwoIStoryScreen screen;
    private static TwoIDialogueScreen parent;
    private static boolean dollPreview;
    private static boolean libraryPreview;
    private static boolean courtyardPreview;
    private static int dollShots;
    private static int libraryShots;
    private static TwoIStoryLibraryScreen previewLibrary;
    private static final String[] LIBRARY_VIEWS = {"library", "library-small", "gallery-cg", "gallery-expression-small"};
    private static long firstDollScreenshotNanos;
    private static int[] firstDollPixels;
    private static final java.util.Map<TwoIStoryScreen, java.util.concurrent.atomic.AtomicLong> CLOCKS = new java.util.WeakHashMap<>();

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("useless_stretcher.story_test") || finished) return;
        Minecraft client = Minecraft.getInstance();
        if (Boolean.getBoolean("useless_stretcher.forced_kill_client_only")) {
            ForcedKillClientChecks.tick(client);
            return;
        }
        if (Boolean.getBoolean("useless_stretcher.feature_client_only")) {
            FeatureClientChecks.tick(client);
            return;
        }
        if (Boolean.getBoolean("useless_stretcher.armor_jei_only")) {
            UsefulArmorJeiChecks.tick(client);
            return;
        }
        try {
            check(++ticks < 6000, "story preview timed out");
            if (client.getOverlay() != null || client.screen == null) return;
            if (!initialized) {
                check(client.level == null && client.getConnection() == null, "preview starts without world or connection");
                Files.createDirectories(client.gameDirectory.toPath().resolve("screenshots"));
                client.options.guiScale().set(1);
                client.resizeDisplay();
                initialized = true;
                if (Boolean.getBoolean("useless_stretcher.capsule_ui_only")) language = 1;
                selectLanguage(client);
                return;
            }
            if (reload != null) {
                if (!reload.isDone()) return;
                reload.join();
                reload = null;
            }
            if (Boolean.getBoolean("useless_stretcher.capsule_ui_only")) {
                finished = true;
                TwoICuriosClientChecks.start(client);
                return;
            }
            if (courtyardPreview) {
                if (frames-- > 0) return;
                try (NativeImage shot = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                    shot.writeToFile(client.gameDirectory.toPath().resolve("screenshots/courtyard-blueprint.png"));
                }
                finished = true;
                LogUtils.getLogger().info("STORY CHECKS PASSED: English/Chinese UI with Chinese long-form story; original voice/mute/pitch checks; unbound travel key; animated plush rig, library/gallery and courtyard block-model preview");
                if (net.neoforged.fml.ModList.get().isLoaded("curios")) TwoICuriosClientChecks.start(client);
                else client.stop();
                return;
            }
            if (libraryPreview) {
                captureLibrary(client);
                return;
            }
            if (dollPreview) {
                captureDolls(client);
                return;
            }
            if (!checked) {
                validateResources(client);
                DollRenderChecks.validate(client);
                StoryLibraryChecks.validate(client);
                exercise(client);
                checked = true;
                LogUtils.getLogger().info("STORY FUNCTIONAL CHECKS PASSED [{}]: typewriter, advance, auto/fast choice stop, ACK/token/idempotence, resize, read-only backlog, settings, hide, timeout and return", LANGUAGES[language]);
            }
            if (frames == 0) {
                prepareView(client);
                frames = 8;
                return;
            }
            if (--frames != 0) return;
            String name = "story-" + LANGUAGES[language] + "-chapter" + (chapter + 1) + "-" + VIEWS[view] + ".png";
            try (NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                image.writeToFile(client.gameDirectory.toPath().resolve("screenshots").resolve(name));
            }
            LogUtils.getLogger().info("STORY SCREENSHOT: {}", name);
            if (++view < VIEWS.length) return;
            view = 0;
            if (++chapter < CHAPTER_COUNT) return;
            chapter = 0;
            if (++language < LANGUAGES.length) {
                checked = false;
                selectLanguage(client);
                return;
            }
            dollPreview = true;
            frames = 8;
            GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 800);
            client.setScreen(DollRenderChecks.preview());
        } catch (Throwable failure) {
            LogUtils.getLogger().error("STORY CHECKS FAILED", failure);
            finished = true;
            client.stop();
        }
    }

    private static void captureDolls(Minecraft client) throws Exception {
        if (frames > 0) { frames--; return; }
        // Non-blocking wait: retain normal client rendering for at least one second.
        if (dollShots == 1 && System.nanoTime() - firstDollScreenshotNanos < 1_000_000_000L) return;
        String name = "doll-animation-" + (dollShots + 1) + ".png";
        try (NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(client.gameDirectory.toPath().resolve("screenshots").resolve(name));
            // The lower strip exercises actual item contexts, rather than the
            // deterministic pose gallery. Prove that successive frames differ.
            int stripHeight = dollShots < 2 ? Math.min(220, image.getHeight()) : 0;
            int[] pixels = new int[image.getWidth() * stripHeight];
            for (int y = 0; y < stripHeight; y++) for (int x = 0; x < image.getWidth(); x++) {
                pixels[y * image.getWidth() + x] = image.getPixelRGBA(x, image.getHeight() - stripHeight + y);
            }
            if (dollShots == 0) {
                firstDollPixels = pixels;
                firstDollScreenshotNanos = System.nanoTime();
            } else if (dollShots == 1) {
                check(firstDollPixels.length == pixels.length, "doll preview resolution is stable");
                int changed = 0;
                for (int i = 0; i < pixels.length; i++) if (firstDollPixels[i] != pixels[i]) changed++;
                check(changed > 0, "live inventory/hand/frame models visibly animate between screenshots");
                LogUtils.getLogger().info("DOLL ANIMATION PIXEL CHECK PASSED: {} item-region pixels changed after {} ms",
                        changed, (System.nanoTime() - firstDollScreenshotNanos) / 1_000_000);
            }
        }
        LogUtils.getLogger().info("DOLL SCREENSHOT: {}", name);
        if (++dollShots < 2) { frames = 8; return; }
        if (dollShots == 2) { frames = 8; client.setScreen(DollRenderChecks.environmentPreview()); return; }
        firstDollPixels = null;
        dollPreview = false;
        libraryPreview = true;
        prepareLibrary(client);
    }

    private static void prepareLibrary(Minecraft client) throws Exception {
        boolean narrow = libraryShots == 1 || libraryShots == 3;
        GLFW.glfwSetWindowSize(client.getWindow().getWindow(), narrow ? 800 : 1280, narrow ? 600 : 800);
        if (libraryShots < 2) {
            previewLibrary = new TwoIStoryLibraryScreen(parent, 70);
            // Disconnected fixture: inject the server response and never send a request.
            set(previewLibrary, "requested", true);
            set(previewLibrary, "requestId", 42);
            client.setScreen(previewLibrary);
            previewLibrary.acceptLibrary(42, libraryShots == 0 ? 63 : 9);
        } else {
            var gallery = new TwoIStoryGalleryScreen(previewLibrary);
            int category = libraryShots == 2 ? 1 : 2;
            set(gallery, "category", category);
            set(gallery, "pictures", TwoIStoryGallery.pictures(category));
            set(gallery, "index", libraryShots == 2 ? 2 : 3);
            client.setScreen(gallery);
        }
        frames = 8;
    }

    private static void captureLibrary(Minecraft client) throws Exception {
        if (frames-- > 0) return;
        String name = "story-" + LIBRARY_VIEWS[libraryShots] + ".png";
        try (NativeImage shot = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            shot.writeToFile(client.gameDirectory.toPath().resolve("screenshots").resolve(name));
        }
        LogUtils.getLogger().info("STORY LIBRARY/GALLERY SCREENSHOT: {}", name);
        if (++libraryShots < LIBRARY_VIEWS.length) {
            prepareLibrary(client);
            return;
        }
        libraryPreview = false;
        courtyardPreview = true;
        frames = 12;
        GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 800);
        client.setScreen(CourtyardPreviewChecks.preview());
    }

    private static void selectLanguage(Minecraft client) {
        client.getLanguageManager().setSelected(LANGUAGES[language]);
        client.options.languageCode = LANGUAGES[language];
        reload = client.reloadResourcePacks();
    }

    private static TwoIStoryScreen open(Minecraft client, int chapter, int bond) {
        parent = new TwoIDialogueScreen(7, false, bond);
        TwoIStoryScreen result = new TwoIStoryScreen(parent, 7, 123L, chapter, bond);
        client.setScreen(result);
        try {
            var time = new java.util.concurrent.atomic.AtomicLong(System.nanoTime());
            CLOCKS.put(result, time);
            set(result, "clock", (java.util.function.LongSupplier)time::get);
            set(result, "lastFrameNanos", time.get());
        } catch (Exception failure) { throw new AssertionError(failure); }
        return result;
    }

    private static void exercise(Minecraft client) throws Exception {
        if (net.neoforged.fml.ModList.get().isLoaded("manaita_plus_legacy")) {
            Class.forName("sen.manaita_plus_legacy.common.util.ManaitaPlusUtils$Client").getDeclaredMethods();
            LogUtils.getLogger().info("STORY optional Manaita client attack/removal class verified after transformation");
        }
        voiceChecks(client);
        TwoIStoryScreen target = open(client, 0, 0);
        // Fast virtual frames must not queue thousands of real sound instances.
        // The dedicated voice checks above own audible verification.
        set(target, "voiceEnabled", false);
        check(integer(target, "revealed") == 0, "new line starts concealed");
        step(target);
        check(integer(target, "revealed") > 0 && integer(target, "revealed") < integer(target, "characterCount"), "typewriter reveals gradually");
        invoke(target, "advance");
        check(integer(target, "revealed") == integer(target, "characterCount") && integer(target, "lineIndex") == 0, "first advance reveals current line");
        invoke(target, "advance");
        check(integer(target, "lineIndex") == 1 && integer(target, "revealed") == 0, "second advance moves one line");
        step(target);
        int revealed = integer(target, "revealed"), history = history(target).size();
        String text = (String) field(target, "fullText");
        target.resize(client, 320, 240);
        check(integer(target, "lineIndex") == 1 && integer(target, "revealed") == revealed
                && history(target).size() == history && field(target, "fullText").equals(text), "compact resize preserves current line/typewriter/history");
        target.resize(client, 1600, 900);
        check(integer(target, "revealed") == revealed && history(target).size() == history, "wide resize preserves state");
        target.resize(client, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
        click(target, button(target, "autoButton"));
        check(bool(target, "automatic"), "scaled real click enables auto");
        tickToChoices(target, 6000);
        check(!bool(target, "automatic") && !bool(target, "fastForward"), "auto stops at choice");
        assertChoiceIdle(target);

        target = open(client, 1, 0);
        click(target, button(target, "fastButton"));
        check(bool(target, "fastForward") && !bool(target, "automatic"), "fast-forward toggles independently");
        tickToChoices(target, 1200);
        check(!bool(target, "automatic") && !bool(target, "fastForward"), "fast-forward stops at choice");
        assertChoiceIdle(target);
        set(target, "chosen", 1);
        set(target, "waiting", true);
        invoke(target, "syncWidgets");
        target.resize(client, 320, 240);
        check(bool(target, "waiting") && integer(target, "chosen") == 1 && integer(target, "stage") == 1, "resize preserves pending server selection");
        target.onChoiceResult(999L, 77);
        check(bool(target, "waiting") && integer(target, "stage") == 1 && integer(target, "bond") == 0, "wrong token cannot accept a choice");
        target.onChoiceResult(123L, 8);
        check(!bool(target, "waiting") && integer(target, "stage") == 2 && integer(target, "lineIndex") == 0, "ACK enters selected branch");
        check(integer(target, "bond") == 8 && integer(parent, "bond") == 8, "only acknowledged bond reaches story and parent");
        history = history(target).size();
        target.onChoiceResult(123L, 88);
        check(integer(target, "bond") == 8 && integer(target, "lineIndex") == 0 && history(target).size() == history, "duplicate ACK does not award or advance");
        target.onChoiceResult(123L, -100, 2);
        check(integer(target, "bond") == 8, "wrong-round ACK cannot alter current story bond");
        target.resize(client, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
        target.mouseScrolled(0, 0, 0, 1);
        check(integer(target, "overlay") == 1, "scroll opens backlog");
        int priorReveal = integer(target, "revealed");
        invoke(target, "advance");
        for (int i = 0; i < 120; i++) step(target);
        target.mouseScrolled(0, 0, 0, -10);
        target.resize(client, 320, 240);
        check(integer(target, "stage") == 2 && integer(target, "lineIndex") == 0 && integer(target, "revealed") == priorReveal
                && integer(target, "bond") == 8 && history(target).size() == history, "backlog stays read-only through scroll, advance, tick and resize");
        target.keyPressed(256, 0, 0);
        check(integer(target, "overlay") == 0, "escape dismisses backlog");
        invoke(target, "openOverlay", int.class, 2);
        click(target, button(target, "speedButton"));
        int speed = integer(target, "textSpeed");
        target.resize(client, 800, 450);
        check(integer(target, "textSpeed") == speed && integer(target, "overlay") == 2, "preferences survive resize");
        target.keyPressed(256, 0, 0);
        set(target, "textSpeed", 2);
        target.mouseClicked(20, 20, 1);
        step(target);
        check(bool(target, "hidden") && integer(target, "revealed") == priorReveal, "hidden UI pauses progression");
        target.keyPressed(256, 0, 0);
        check(!bool(target, "hidden") && client.screen == target, "escape shows hidden UI before returning");
        completeStory(client, target, 0);
        check(client.screen == parent, "three rounds and ending return to dialogue parent");

        target = open(client, 0, 25);
        set(target, "round", 1);
        set(target, "stage", 1);
        set(target, "chosen", 0);
        set(target, "waiting", true);
        target.onChoiceResult(123L, 99, 0);
        target.onStoryUnavailable(123L, 0);
        check(bool(target, "waiting") && integer(target, "bond") == 25 && !bool(target, "failed"),
                "old first-round ACK/rejection cannot consume a second-round selection");
        target.onChoiceResult(123L, 26, 1);
        check(!bool(target, "waiting") && integer(target, "bond") == 26 && integer(target, "stage") == 2,
                "matching second-round ACK enters its own branch");
        target.onClose();
        var resumeParent = new TwoIDialogueScreen(7, false, 26);
        var resumed = new TwoIStoryScreen(resumeParent, 7, 456L, 0, 26, 2);
        client.setScreen(resumed);
        check(integer(resumed, "round") == 2 && integer(resumed, "stage") == 0
                        && ((String) field(resumed, "fullText")).equals(TwoIStoryScript.text(
                        TwoIStoryScript.prompt(0, 2).getFirst()).getString()),
                "reopened session resumes server-selected round without replaying earlier choices");
        resumed.onClose();

        target = open(client, 2, -10);
        target.onStoryUnavailable(999L);
        check(!bool(target, "failed"), "unrelated rejection ignored");
        set(target, "waiting", true);
        set(target, "chosen", 2);
        for (int i = 0; i < 200; i++) step(target);
        check(bool(target, "failed") && !bool(target, "waiting") && button(target, "nextButton").active, "missing ACK times out with available return");
        click(target, button(target, "nextButton"));
        check(client.screen == parent, "failed story returns through real button");

        for (int chapter = 0; chapter < CHAPTER_COUNT; chapter++) {
            for (int choice = 0; choice < 3; choice++) {
                target = open(client, chapter, 15);
                set(target, "fastForward", true);
                tickToChoices(target, 1200);
                set(target, "chosen", choice);
                set(target, "waiting", true);
                target.onChoiceResult(123L, 16);
                check(integer(target, "stage") == 2 && ((String)field(target, "fullText")).equals(
                        TwoIStoryScript.text(TwoIStoryScript.branch(chapter, choice).getFirst()).getString()),
                        "all six chapters enter the intended translated branch");
                completeStory(client, target, choice);
                check(client.screen == parent, "all eighteen paths complete all three rounds");
            }
        }
    }

    private static void completeStory(Minecraft client, TwoIStoryScreen target, int choice) throws Exception {
        for (int i = 0; i < 1600 && client.screen == target; i++) {
            if (integer(target, "stage") == 1) {
                set(target, "chosen", choice);
                set(target, "waiting", true);
                target.onChoiceResult(123L, 15);
            } else invoke(target, "advance");
        }
    }

    private static void voiceChecks(Minecraft client) throws Exception {
        set(open(client, 0, 0), "voiceEnabled", true);
        var tones = new com.sorrowmist.useless.stretcher.client.dialogue.TwoIStoryVoice();
        for (var cue : new TwoIStoryTimeline("", "你好呀", false, true).cues()) tones.play(cue, true);
        for (var cue : new TwoIStoryTimeline("", "你好呀", false, false).cues()) tones.play(cue, false);
        for (var cue : new TwoIStoryTimeline("", "……！", false, false).cues()) tones.play(cue, true);
        check(tones.pulses() == 0, "narration, disabled voice and punctuation are silent");
        for (var cue : new TwoIStoryTimeline("", "你好呀", false, false).cues()) tones.play(cue, true);
        check(tones.pulses() == 3, "every spoken glyph emits precisely one syllable, no cooldown drops");
        tones.stop();
        check(!tones.hasActiveCue(), "stop releases the voice without a pending queue");
        timelineChecks();
        screenVoiceChecks(client);
        exportVoicePreview(client);
        check(TwoIStoryScript.branch(0, 0).getFirst().player(), "chosen player reply appears as a voiced story line");
        check(com.sorrowmist.useless.stretcher.client.StretcherKeyBindings.STANDALONE_TELEPORT
                .getDefaultKey().getValue() == GLFW.GLFW_KEY_UNKNOWN, "independent travel defaults unbound");
        check(!net.neoforged.fml.ModList.get().isLoaded("poppet"), "all doll/voice checks run without the reference mod");
        check(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(
                com.sorrowmist.useless.stretcher.init.ModSounds.TWO_I_PAT.get()).toString().equals("useless_stretcher:two_i_pat"),
                "server head-pat sound is registered instead of falling back to an allay sound");
        for (String name : List.of("two_i_voice", "two_i_voice_soft", "two_i_voice_bright", "player_voice", "two_i_pat")) {
            var id = ResourceLocation.parse("useless_stretcher:" + name);
            check(client.getSoundManager().getSoundEvent(id) != null, "voice sound event loaded: " + name);
            check(client.getResourceManager().getResource(ResourceLocation.parse(
                    "useless_stretcher:sounds/" + (name.equals("two_i_pat") ? "two_i_pat_1" : name) + ".ogg")).isPresent(), "original voice asset exists");
        }
    }

    private static void timelineChecks() {
        String key = "gui.useless_stretcher.two_i.vn.0.opening.1";
        String text = "你好……你呢？ A\u0301! 🐆";
        for (int speed : new int[]{1, 2, 4}) {
            var timeline = new TwoIStoryTimeline(key, text, false, false);
            int revealed = 0, pulses = 0, events = 0;
            for (int frame = 0; frame < 4000 && !timeline.complete(); frame++) {
                var cue = timeline.advance(8, speed);
                if (cue == null) continue;
                check(cue.revealed() > revealed, "each reveal occurs exactly once");
                revealed = cue.revealed();
                if (cue.voiced()) pulses++;
                events++;
                check(timeline.advance(0, speed) == null, "tick and render cannot duplicate a character");
            }
            check(timeline.complete() && revealed == text.codePointCount(0, text.length()), "Unicode timeline completes at every speed");
            check(pulses == 5 && events == 11, "Chinese, Latin accent and emoji do not split or add bogus sounds");
        }
        var stalled = new TwoIStoryTimeline(key, "你好你好你好", false, false);
        check(stalled.advance(30_000, 4).revealed() == 1 && stalled.advance(0, 4) == null, "long stall emits one cue, never catch-up audio burst");
        var pauses = new TwoIStoryTimeline(key, "你……好", false, false);
        pauses.advance(0, 2);
        check(pauses.advance(500, 2).revealed() == 3, "ellipsis reveals as one punctuation event");
        check(pauses.advance(300, 2) == null, "ellipsis holds back both next glyph and its syllable");
        check(pauses.advance(250, 2).revealed() == 4, "text and voice resume together after punctuation");
        pauses.finish();
        check(pauses.advance(60_000, 2) == null, "instant reveal cannot leave queued syllables");
        var shy = new TwoIStoryTimeline(key, "你愿意等我吗？", false, false).cues();
        var happy = new TwoIStoryTimeline("gui.useless_stretcher.two_i.vn.1.opening.1", "你愿意等我吗？", false, false).cues();
        var hurt = new TwoIStoryTimeline("gui.useless_stretcher.two_i.vn.4.branch.1.2", "你愿意等我吗？", false, false).cues();
        check(shy.getFirst().timbre() == TwoIStoryTimeline.Timbre.SOFT && happy.getFirst().timbre() == TwoIStoryTimeline.Timbre.BRIGHT,
                "shy and happy differ in actual sound timbre");
        check(shy.getFirst().cadence() > happy.getFirst().cadence() && hurt.getFirst().volume() < shy.getFirst().volume()
                && hurt.getFirst().pitch() < shy.getFirst().pitch(), "emotion changes timing, strength and contour");
        var player = new TwoIStoryTimeline(key, "你好", true, false).cues().getFirst();
        check(player.timbre() == TwoIStoryTimeline.Timbre.PLAYER, "player has a separate voice");
        var question = new TwoIStoryTimeline("gui.useless_stretcher.two_i.vn.0.opening.4", "abcdefghij?", false, false).cues();
        check(question.get(6).pitch() < question.get(7).pitch() && question.get(7).pitch() < question.get(8).pitch()
                && question.get(8).pitch() < question.get(9).pitch(), "last four audible syllables rise into the question");
        String turn = "等下！草里有东西——哦，叶子。咳，你就当没看见。";
        check(TwoIStoryDirection.mood("gui.useless_stretcher.two_i.vn.0.opening.2", turn, 0) == TwoIStoryDirection.Mood.STARTLED
                && TwoIStoryDirection.mood("gui.useless_stretcher.two_i.vn.0.opening.2", turn, 4) == TwoIStoryDirection.Mood.UNEASY
                && TwoIStoryDirection.mood("gui.useless_stretcher.two_i.vn.0.opening.2", turn, 12) == TwoIStoryDirection.Mood.CALM
                && TwoIStoryDirection.mood("gui.useless_stretcher.two_i.vn.0.opening.2", turn, 17) == TwoIStoryDirection.Mood.SHY,
                "one sentence follows surprise, worry, relief and shy recovery");
    }

    private static void exportVoicePreview(Minecraft client) throws Exception {
        var samples = new com.google.gson.JsonArray();
        for (String suffix : List.of("0.opening.1", "1.opening.1", "4.branch.1.2", "0.opening.2", "5.choice.2", "2.branch.0.2")) {
            String key = "gui.useless_stretcher.two_i.vn." + suffix;
            String text = Component.translatable(key).getString();
            var sample = new com.google.gson.JsonObject();
            sample.addProperty("key", key);
            sample.addProperty("text", text);
            var events = new com.google.gson.JsonArray();
            var track = new TwoIStoryTimeline(key, text, suffix.contains("choice"), false);
            int ms = 0;
            while (!track.complete() && ms < 60_000) {
                var cue = track.advance(10, 2);
                if (cue != null && cue.voiced()) {
                    var event = new com.google.gson.JsonObject();
                    event.addProperty("start_ms", ms);
                    event.addProperty("codepoint", cue.codePoint());
                    event.addProperty("pitch", cue.pitch());
                    event.addProperty("volume", cue.volume());
                    event.addProperty("timbre", cue.timbre().name());
                    events.add(event);
                }
                ms += 10;
            }
            sample.add("cues", events);
            samples.add(sample);
        }
        var path = client.gameDirectory.toPath().resolve("../audio-preview").normalize();
        Files.createDirectories(path);
        Files.writeString(path.resolve("dialogue-cues-" + LANGUAGES[language] + ".json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(samples), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void screenVoiceChecks(Minecraft client) throws Exception {
        TwoIStoryScreen target = open(client, 0, 0);
        invoke(target, "reveal");
        invoke(target, "advance");
        var voice = (com.sorrowmist.useless.stretcher.client.dialogue.TwoIStoryVoice)field(target, "voice");
        for (int i = 0; i < 6; i++) { CLOCKS.get(target).addAndGet(50_000_000L); target.tick(); }
        check(voice.pulses() == 0 && integer(target, "revealed") == 0, "low FPS logic ticks cannot speak ahead of displayed frames");
        step(target);
        check(voice.pulses() == 1 && integer(target, "revealed") == 1, "screen shows first speaking glyph at its sound event");
        invoke(target, "openOverlay", int.class, 1);
        check(!voice.hasActiveCue(), "opening history immediately silences current sound");
        for (int i = 0; i < 100; i++) step(target);
        check(voice.pulses() == 1 && integer(target, "revealed") == 1, "history does not advance or queue voice");
        target.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        step(target);
        target.resize(client, 800, 450);
        long before = voice.pulses();
        for (int i = 0; i < 1000 && integer(target, "revealed") < integer(target, "characterCount"); i++) step(target);
        String text = (String)field(target, "fullText");
        check(voice.pulses() == text.codePoints().filter(Character::isLetterOrDigit).count(), "every displayed speaking letter accounted for through pause and resize");
        check(voice.pulses() > before, "resume continues from same timeline");
        invoke(target, "advance");
        step(target);
        invoke(target, "reveal");
        long stopped = voice.pulses();
        step(target);
        check(!voice.hasActiveCue() && voice.pulses() == stopped, "skip reveals silently and leaves no tail");
        target.onClose();
        check(!voice.hasActiveCue(), "closing leaves no voice");
    }

    private static void assertChoiceIdle(TwoIStoryScreen target) throws Exception {
        for (int i = 0; i < 240; i++) step(target);
        check(integer(target, "stage") == 1 && integer(target, "chosen") == -1 && !bool(target, "waiting"), "choice never submits itself");
        check(!button(target, "autoButton").active && !button(target, "fastButton").active, "auto/fast unavailable at choice");
        for (Button choice : (Button[]) field(target, "choices")) check(choice.visible && choice.active, "three choices remain interactive");
    }

    private static void tickToChoices(TwoIStoryScreen target, int limit) throws Exception {
        for (int i = 0; i < limit && integer(target, "stage") != 1; i++) step(target);
        check(integer(target, "stage") == 1, "playback reaches choice within bounded ticks");
    }

    private static void prepareView(Minecraft client) throws Exception {
        if (view == 0) {
            GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 800);
            screen = open(client, chapter, chapter * 20);
        }
        if (view == 1) { invoke(screen, "reveal"); invoke(screen, "advance"); }
        if (view == 2) {
            set(screen, "fastForward", true);
            tickToChoices(screen, 1200);
        }
        if (view == 3) {
            set(screen, "chosen", chapter % 3);
            set(screen, "waiting", true);
            screen.onChoiceResult(123L, chapter * 20 + 8);
        }
        if (view == 4) invoke(screen, "openOverlay", int.class, 1);
        if (view == 5) invoke(screen, "openOverlay", int.class, 2);
        if (view == 6) {
            screen = open(client, chapter, chapter * 20);
            set(screen, "fastForward", true);
            tickToChoices(screen, 1200);
            GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 800, 600);
        }
        if (view == 7) {
            check(integer(screen, "stage") == 1 && integer(screen, "chosen") == -1, "native window resize retains choice without submitting");
            invoke(screen, "openOverlay", int.class, 1);
        }
        invoke(screen, "reveal");
    }

    private static void validateResources(Minecraft client) throws Exception {
        String prefix = "gui.useless_stretcher.two_i.vn.";
        long shortestPath = 0, longestPath = 0;
        for (int chapter = 0; chapter < CHAPTER_COUNT; chapter++) {
            long shared = hanCount(TwoIStoryScript.opening(chapter, 0))
                    + hanCount(TwoIStoryScript.ending(chapter));
            long shortestBranch = 0, longestBranch = 0;
            List<TwoIStoryScript.Line> lines = new ArrayList<>(TwoIStoryScript.opening(chapter, -10));
            for (int round = 0; round < 3; round++) {
              if (round > 0) {
                  lines.addAll(TwoIStoryScript.prompt(chapter, round));
                  shared += hanCount(TwoIStoryScript.prompt(chapter, round));
              }
              long minimum = Long.MAX_VALUE, maximum = 0;
              for (int choice = 0; choice < 3; choice++) {
                lines.addAll(TwoIStoryScript.branch(chapter, round, choice));
                long branch = hanCount(TwoIStoryScript.branch(chapter, round, choice));
                minimum = Math.min(minimum, branch);
                maximum = Math.max(maximum, branch);
                Component label = TwoIStoryScript.choice(chapter, round, choice);
                check(!label.getString().startsWith(prefix), "choice translation exists");
                check(client.font.width(label) <= 350, "choice plus bond delta fits without truncation: " + label.getString());
              }
              shortestBranch += minimum;
              longestBranch += maximum;
            }
            shortestPath += shared + shortestBranch;
            longestPath += shared + longestBranch;
            lines.addAll(TwoIStoryScript.ending(chapter));
            for (TwoIStoryScript.Line line : lines) {
                Component text = TwoIStoryScript.text(line);
                check(!text.getString().equals(line.key()), "line translation exists: " + line.key());
                check(client.font.split(text, 528).size() <= 4, "dialogue fits above controls at 125% text scale: " + line.key());
                texture(client, "expressions/" + line.expression(), 512, 768);
                var cues = new TwoIStoryTimeline(line.key(), text.getString(), line.player(), line.narration()).cues();
                check(!cues.isEmpty(), "every authored and fallback line has a text/audio timeline");
                if (line.narration()) check(cues.stream().noneMatch(TwoIStoryTimeline.Cue::voiced), "narrator remains silent");
            }
            check(!TwoIStoryScript.title(chapter).getString().startsWith(prefix), "chapter title translated");
        }
        if (LANGUAGES[language].equals("zh_cn")) {
            check(shortestPath >= 10_000 && longestPath <= 15_000,
                    "single full reading path has forty-minute-scale prose without summing exclusive branches");
            LogUtils.getLogger().info("STORY LENGTH: shortest {} / longest {} Chinese characters across six chapters", shortestPath, longestPath);
        }
        texture(client, "cg/first_meet", 836, 471);
        texture(client, "cg/friends", 836, 471);
        texture(client, "cg/starfield_invite", 836, 471);
        texture(client, "ui/dialogue_frame", 1086, 362);
        texture(client, "ui/bow", 627, 627);
    }

    private static long hanCount(List<TwoIStoryScript.Line> lines) {
        return lines.stream().map(TwoIStoryScript::text).map(Component::getString)
                .flatMapToInt(String::codePoints)
                .filter(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN).count();
    }

    private static void texture(Minecraft client, String name, int width, int height) throws Exception {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("useless_stretcher", "textures/dialogue/" + name + ".png");
        try (var stream = client.getResourceManager().open(id); NativeImage image = NativeImage.read(stream)) {
            check(image.getWidth() == width && image.getHeight() == height, "actual texture dimensions: " + name);
        }
    }

    private static void click(TwoIStoryScreen target, Button button) {
        float scale = Math.min(target.width / 800f, target.height / 450f);
        double x = (target.width - 800 * scale) / 2 + (button.getX() + button.getWidth() / 2f) * scale;
        double y = (target.height - 450 * scale) / 2 + (button.getY() + button.getHeight() / 2f) * scale;
        check(target.mouseClicked(x, y, 0), "scaled button handles click");
        target.mouseReleased(x, y, 0);
        target.setFocused(null);
    }

    private static List<?> history(Object target) throws Exception { return (List<?>) field(target, "history"); }
    private static Button button(Object target, String name) throws Exception { return (Button) field(target, name); }
    private static int integer(Object target, String name) throws Exception { return (int) field(target, name); }
    private static boolean bool(Object target, String name) throws Exception { return (boolean) field(target, name); }
    private static Field reflected(Object target, String name) throws Exception {
        Field result = target.getClass().getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
    private static Object field(Object target, String name) throws Exception { return reflected(target, name).get(target); }
    private static void set(Object target, String name, Object value) throws Exception { reflected(target, name).set(target, value); }
    private static void invoke(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }
    private static void invoke(Object target, String name, Class<?> type, Object value) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, type);
        method.setAccessible(true);
        method.invoke(target, value);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void step(TwoIStoryScreen target) {
        CLOCKS.get(target).addAndGet(50_000_000L);
        target.tick();
        try { invoke(target, "updatePlayback"); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }
}
