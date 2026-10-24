package com.sorrowmist.useless.stretcher.storytest;

import com.sorrowmist.useless.stretcher.client.TwoIDialogueScreen;
import com.sorrowmist.useless.stretcher.client.TwoIStoryGalleryScreen;
import com.sorrowmist.useless.stretcher.client.TwoIStoryLibraryScreen;
import com.sorrowmist.useless.stretcher.client.TwoIStoryScreen;
import com.sorrowmist.useless.stretcher.client.dialogue.TwoIStoryGallery;
import com.sorrowmist.useless.stretcher.content.entity.TwoIStoryData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;

import java.lang.reflect.Field;
import java.util.UUID;

/** Offline-client checks: replay would fail immediately if it attempted a network choice. */
public final class StoryLibraryChecks {
    private StoryLibraryChecks() { }

    public static void validate(Minecraft client) throws Exception {
        check(client.getConnection() == null, "library fixture is disconnected");
        UUID player = UUID.randomUUID();
        TwoIStoryData progress = new TwoIStoryData();
        check(progress.completedMask(player) == 0 && !progress.metBefore(player), "fresh library is empty");
        progress.complete(player, 3);
        progress.setDirty(false);
        check(progress.completedMask(player) == 8 && !progress.isDirty(), "reading unlocks does not dirty progress");
        check(progress.completedMask(UUID.randomUUID()) == 0, "players cannot read another player's progress");

        var parent = new TwoIDialogueScreen(7, false, 25);
        var library = new TwoIStoryLibraryScreen(parent, 25);
        check(library.interactionEntityId() == 7, "library retains original entity interaction");
        set(library, "requested", true);
        set(library, "requestId", 42);
        client.setScreen(library);
        library.acceptLibrary(41, 63);
        check(!(boolean) get(library, "loaded"), "late response ignored");
        library.acceptLibrary(42, 9);
        var chapters = (Button[]) get(library, "chapters");
        check(chapters[0].active && chapters[3].active && !chapters[1].active && !chapters[5].active,
                "only server-completed chapters enable replay");
        library.resize(client, 320, 240);
        check((int) get(library, "completedMask") == 9, "resize retains authoritative unlocks");

        for (int choice = 0; choice < 3; choice++) {
            var replay = TwoIStoryScreen.replay(library, 3, 25);
            check(replay.interactionEntityId() == 7 && replay.entityId() == -1,
                    "replay keeps original interaction without acquiring a story session");
            client.setScreen(replay);
            set(replay, "stage", 1);
            var choose = TwoIStoryScreen.class.getDeclaredMethod("choose", int.class);
            choose.setAccessible(true);
            choose.invoke(replay, choice);
            check((int) get(replay, "stage") == 2 && !(boolean) get(replay, "waiting"),
                    "replay enters every branch without server packets");
            replay.onChoiceResult(0L, -100);
            check((int) get(replay, "bond") == 25, "replay cannot apply a bond acknowledgement");
            var advance = TwoIStoryScreen.class.getDeclaredMethod("advance");
            advance.setAccessible(true);
            for (int round = 1; round < 3; round++) {
                for (int i = 0; i < 400 && (int) get(replay, "stage") != 1; i++) advance.invoke(replay);
                check((int) get(replay, "round") == round && (int) get(replay, "stage") == 1,
                        "replay reaches next choice round");
                choose.invoke(replay, choice);
                check((int) get(replay, "stage") == 2 && !(boolean) get(replay, "waiting")
                        && (int) get(replay, "bond") == 25, "all replay rounds remain local and reward-free");
            }
            replay.onClose();
            check(client.screen == library, "replay returns to chapter library");
        }
        check(progress.completedMask(player) == 8 && !progress.isDirty(), "replay did not mutate saved unlocks");

        check(TwoIStoryGallery.pictures(0).size() == 17, "all original supplied art is in the gallery");
        check(TwoIStoryGallery.pictures(1).size() == 3 && TwoIStoryGallery.pictures(2).size() == 10
                && TwoIStoryGallery.pictures(3).size() == 4, "gallery categories cover all seventeen images");
        for (var picture : TwoIStoryGallery.pictures(0)) {
            check(picture.width() > 0 && picture.height() > 0, "source aspect ratio is positive");
            check(client.getResourceManager().getResource(picture.texture()).isPresent(), "gallery asset exists: " + picture.file());
        }
        var gallery = new TwoIStoryGalleryScreen(library);
        check(gallery.interactionEntityId() == 7, "gallery retains original entity interaction");
        client.setScreen(gallery);
        gallery.mouseScrolled(0, 0, 1);
        check((int) get(gallery, "index") == 16, "gallery wraps backwards");
        gallery.resize(client, 320, 240);
        check((int) get(gallery, "index") == 16, "gallery resize keeps selected art");
        gallery.onClose();
        check(client.screen == library, "gallery returns to its caller");
        client.setScreen(parent);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
