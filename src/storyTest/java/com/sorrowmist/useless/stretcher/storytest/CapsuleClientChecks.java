package com.sorrowmist.useless.stretcher.storytest;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.client.DimensionCapsuleScreen;
import com.sorrowmist.useless.stretcher.client.TwoIDialogueScreen;
import com.sorrowmist.useless.stretcher.init.ModEntities;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.network.CapsuleNetwork;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** Real integrated-server catalogue, map packets, gift bubble and cursor-following preview. */
public final class CapsuleClientChecks {
    private static int stage, ticks, frames, dollId;
    private static DimensionCapsuleScreen capsule;
    private static TwoIDialogueScreen dialogue;
    private static int[] previewPixels;
    private static int previousProgress;
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> returnDimension;
    private static net.minecraft.world.phys.Vec3 returnPosition, firstMirrorEntry;
    private static int journeys;
    private static final java.util.Set<String> progressScreenshots = new java.util.HashSet<>();
    private CapsuleClientChecks() { }

    static void start(Minecraft client) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CapsuleClientChecks::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(CapsuleClientChecks::rendered);
        client.getSingleplayerServer().execute(() -> {
            var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DIMENSION_CAPSULE.get()));
            Network.sendDimensionCapsuleCatalog(player, false);
        });
    }

    private static void tick(ClientTickEvent.Post event) {
        if (stage == 9) return;
        Minecraft client = Minecraft.getInstance();
        try {
            check(++ticks < 4000, "capsule client timed out at stage " + stage);
            if (client.getOverlay() != null) return;
            if (frames > 0) { frames--; return; }
            if (stage == 0) {
                if (!(client.screen instanceof DimensionCapsuleScreen screen)) return;
                if (capsule == null) { capsule = screen; frames = 12; return; }
                check(!((java.util.List<?>)field(capsule, "entries")).isEmpty(), "live server catalogue received");
                for (String phase : java.util.List.of("empty", "planning", "cleaning", "placing", "bedrock", "trimming", "generating", "paused", "ready"))
                    check(net.minecraft.locale.Language.getInstance().has("gui.useless_stretcher.dimension_capsule.phase." + phase),
                            "translated capsule phase: " + phase);
                screenshot(client, "capsule-catalogue.png");
                EditBox search = (EditBox)field(capsule, "search");
                search.setValue("@minecraft pillager outpost");
                var rows = (java.util.List<?>)field(capsule, "rows");
                check(rows.size() == 2, "mod and multi-word search narrow to one native structure");
                int left = (int)field(capsule, "left"), top = (int)field(capsule, "top");
                capsule.mouseClicked(left + 35, top + 28 + 18 + 8, 0);
                capsule.mouseReleased(left + 35, top + 54, 0);
                stage = 1; frames = 12; return;
            }
            if (stage == 1) {
                screenshot(client, "capsule-search.png");
                CapsuleNetwork.request("minecraft:pillager_outpost",
                        com.sorrowmist.useless.stretcher.content.capsule.DimensionCapsuleCatalog.CAPSULE_OVERWORLD, 1, 0, false);
                stage = 2; return;
            }
            if (stage == 2) {
                var state = (CapsuleNetwork.State)field(capsule, "state");
                check(state == null || !java.util.Set.of("below_world", "failed", "too_large", "planning_timeout",
                        "planning_memory", "allocation_conflict", "timeout").contains(state.phase()),
                        "native flat-world generation failed: " + (state == null ? "no state" : state.phase()));
                if (state != null) {
                    check(state.progress() >= previousProgress && state.progress() <= 100, "network progress is monotonic and bounded");
                    previousProgress = state.progress();
                }
                if (state == null || !state.phase().equals("ready") || !progressScreenshots.contains("ready")) return;
                check(state.progress() == 100 && progressScreenshots.containsAll(java.util.Set.of("cleaning", "placing", "trimming")),
                        "real generation stages displayed before completion");
                check(state.pixels().length == 4096 && java.util.Arrays.stream(state.pixels()).anyMatch(c -> c != 0), "real map payload is nonblank");
                check(((net.minecraft.client.gui.components.Button)field(capsule, "enter")).active, "ready packet enables entry");
                screenshot(client, "capsule-generated-map.png");
                int x = (int)field(capsule, "mapX") + 20, y = (int)field(capsule, "mapY") + 20;
                capsule.mouseClicked(x, y, 0); capsule.mouseDragged(x + 53, y, 0, 53, 0); capsule.mouseReleased(x + 53, y, 0);
                check(Math.abs((float)field(capsule, "angle")) > 40, "map responds to mouse rotation");
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 800, 600);
                stage = 3; frames = 12; return;
            }
            if (stage == 3) {
                screenshot(client, "capsule-map-compact.png");
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 800);
                client.getSingleplayerServer().execute(() -> {
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                    var doll = ModEntities.TWO_I_DOLL.get().create(player.serverLevel());
                    doll.setOwnerId(player.getUUID());
                    doll.moveTo(player.getX() + 1, player.getY(), player.getZ(), 0, 0);
                    player.serverLevel().addFreshEntity(doll);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 2));
                    doll.acceptGift(player); doll.acceptGift(player);
                    dollId = doll.getId();
                    doll.openDialogue(player);
                });
                stage = 4; return;
            }
            if (stage == 4) {
                if (!(client.screen instanceof TwoIDialogueScreen screen)) return;
                dialogue = screen;
                var actual = (com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity)client.level.getEntity(dollId);
                if (actual == null || actual.getBubbleText().isEmpty()) return;
                check(actual.getBubbleText().equals("msg.useless_stretcher.two_i.gift_daily_limit"), "gift limit bubble synchronized from server");
                check(actual.getBubbleMessage().getString().contains("2i"), "bubble is a localized Component");
                moveCursor(client, 350, 260);
                stage = 5; frames = 4; return;
            }
            if (stage == 5) {
                check(field(dialogue, "preview") != null, "interaction UI creates the current doll preview");
                screenshot(client, "two-i-bubble-cursor-left.png");
                previewPixels = screenshotPixels(client);
                moveCursor(client, 670, 520);
                stage = 6; frames = 4; return;
            }
            if (stage == 6) {
                screenshot(client, "two-i-bubble-cursor-right.png");
                int[] after = screenshotPixels(client);
                int changed = 0;
                for (int i = 0; i < after.length; i++) if (after[i] != previewPixels[i]) changed++;
                check(changed > 100, "rendered doll region changes with the cursor");
                LogUtils.getLogger().info("CAPSULE CLIENT CHECKS PASSED: live catalogue, @mod multiword search, native map, mouse rotation, compact layout, actual gift bubble and doll cursor preview ({} changed pixels)", changed);
                returnDimension = client.level.dimension();
                returnPosition = client.player.position();
                client.setScreen(null);
                client.getSingleplayerServer().execute(() -> {
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DIMENSION_CAPSULE.get()));
                });
                stage = 7; return;
            }
            if (stage == 7) {
                if (!client.player.getMainHandItem().is(ModItems.DIMENSION_CAPSULE.get())) return;
                CapsuleNetwork.request("minecraft:pillager_outpost",
                        com.sorrowmist.useless.stretcher.content.capsule.DimensionCapsuleCatalog.CAPSULE_OVERWORLD, 2, 0, false);
                stage = 8; return;
            }
            if (stage == 8) {
                if (!client.level.dimension().location().toString().equals(
                        com.sorrowmist.useless.stretcher.content.capsule.DimensionCapsuleCatalog.CAPSULE_OVERWORLD)) return;
                stage = 10; frames = 20; return;
            }
            if (stage == 10) {
                if (!client.level.hasChunkAt(client.player.blockPosition())) return;
                check(client.level.getMinBuildHeight() == -64 && client.level.getHeight() == 384,
                        "client receives registered mirror environment type");
                if (firstMirrorEntry == null) firstMirrorEntry = client.player.position();
                else check(client.player.position().distanceToSqr(firstMirrorEntry) < 4,
                        "repeated client entry returns to the same saved room");
                screenshot(client, "capsule-mirror-client-entry-" + journeys + ".png");
                client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                stage = 11; return;
            }
            if (stage == 11) {
                if (!client.level.dimension().equals(returnDimension)) return;
                check(client.player.position().distanceToSqr(returnPosition) < 16,
                        "actual item-use packet returns client to its original position");
                if (++journeys < 2) { stage = 7; frames = 10; return; }
                LogUtils.getLogger().info("CAPSULE CLIENT ROUND TRIPS PASSED: registered mirror type, actual dimension packets/chunks, two entries into the same room, two item-use returns");
                stage = 9; StaffRadiationClientChecks.start(client);
            }
        } catch (Throwable failure) {
            stage = 9;
            LogUtils.getLogger().error("CAPSULE CLIENT CHECKS FAILED", failure);
            client.stop();
        }
    }
    private static void rendered(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        if (stage != 2) return;
        var client = Minecraft.getInstance();
        if (client.screen != capsule || client.getOverlay() != null) return;
        try {
            var state = (CapsuleNetwork.State)field(capsule, "state");
            if (state == null || progressScreenshots.contains(state.phase())) return;
            // A client tick can run twice without rendering. Inspect only after the real frame.
            screenshot(client, "capsule-progress-" + state.phase() + ".png");
            verifyProgressPixels(client, state);
            progressScreenshots.add(state.phase());
            LogUtils.getLogger().info("CAPSULE CLIENT PROGRESS: {} {}%", state.phase(), state.progress());
        } catch (Throwable failure) {
            stage = 9;
            LogUtils.getLogger().error("CAPSULE CLIENT CHECKS FAILED", failure);
            client.stop();
        }
    }
    private static void verifyProgressPixels(Minecraft client, CapsuleNetwork.State state) throws Exception {
        if (state.progress() == 0 && !state.phase().equals("planning")) return;
        int x = (int)field(capsule, "mapX"), size = (int)field(capsule, "mapSize");
        int y = (int)field(capsule, "mapY") + size + 16;
        try (NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            int colored = 0;
            for (int i = x + 1; i < x + size - 1; i++)
                if ((image.getPixelRGBA(i, y) & 0xFFFFFF) == 0xB59870) colored++;
            check(colored > 0, "visible progress fill for " + state.phase());
        }
    }
    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void moveCursor(Minecraft client, int x, int y) {
        GLFW.glfwSetCursorPos(client.getWindow().getWindow(), x, y);
    }
    private static void screenshot(Minecraft client, String file) throws Exception {
        try (NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(client.gameDirectory.toPath().resolve("screenshots").resolve(file));
        }
    }
    private static int[] screenshotPixels(Minecraft client) {
        try (NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            int[] pixels = new int[125 * 200];
            int left = (image.getWidth() - 430) / 2 + 12, top = (image.getHeight() - 250) / 2 + 35;
            for (int y = 0; y < 200; y++) for (int x = 0; x < 125; x++) pixels[y * 125 + x] = image.getPixelRGBA(left + x, top + y);
            return pixels;
        }
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
