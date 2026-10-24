package com.sorrowmist.useless.stretcher.storytest;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.client.WondrousStaffConfigScreen;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.client.event.ClientTickEvent;

import java.util.concurrent.CompletableFuture;

/** Exercise the actual expanded staff UI and its client-to-server switch packet. */
final class StaffRadiationClientChecks {
    private static int stage, ticks, frames;
    private static WondrousStaffConfigScreen screen;
    private static CompletableFuture<Boolean> serverValue;

    static void start(Minecraft client) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(StaffRadiationClientChecks::tick);
        client.getSingleplayerServer().execute(() -> {
            var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WONDROUS_STAFF.get()));
        });
        client.setScreen(null);
    }

    private static void tick(ClientTickEvent.Post event) {
        if (stage == 99) return;
        var client = Minecraft.getInstance();
        try {
            check(++ticks < 400, "radiation UI timed out at " + stage);
            if (frames > 0) { frames--; return; }
            if (stage == 0) {
                if (!client.player.getMainHandItem().is(ModItems.WONDROUS_STAFF.get())) return;
                screen = new WondrousStaffConfigScreen(InteractionHand.MAIN_HAND);
                client.setScreen(screen);
                ((Button)field("extrasFoldText")).onPress();
                // Resize with the actual window handle so every expanded row is visible.
                var window = screen.floatingWindow();
                int x = window.left() + window.width() - 2, y = window.top() + window.height() - 2;
                screen.mouseClicked(x, y, 0);
                screen.mouseDragged(x + 180, y + 150, 0, 180, 150);
                screen.mouseReleased(x + 180, y + 150, 0);
                stage = 1; frames = 4; return;
            }
            var button = (Button)field("radiationButton");
            if (stage == 1) {
                check(button != null && button.active == ModList.get().isLoaded("mekanism"), "optional MEK button availability");
                check(!(boolean)field("radiationEnabled"), "UI defaults to off");
                screenshot(client, "staff-radiation-off.png");
                if (!button.active) { finish(client, "optional MEK absent; disabled UI and normal client startup"); return; }
                button.onPress(); stage = 2; frames = 10; return;
            }
            if (stage == 2 || stage == 4) {
                serverValue = CompletableFuture.supplyAsync(() -> {
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                    return com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(player.getMainHandItem(), StretcherComponents.WONDROUS_STAFF_RADIATION_PROTECTION.get(), false);
                }, client.getSingleplayerServer());
                stage++; return;
            }
            if (stage == 3) {
                if (!serverValue.isDone()) return;
                check(serverValue.join() && (boolean)field("radiationEnabled"), "enabled packet reached real server");
                screenshot(client, "staff-radiation-on.png");
                button.onPress(); stage = 4; frames = 10; return;
            }
            if (stage == 5) {
                if (!serverValue.isDone()) return;
                check(!serverValue.join() && !(boolean)field("radiationEnabled"), "disabled packet reached real server");
                finish(client, "expanded button, default off, enable/disable packets and authoritative item state");
            }
        } catch (Throwable failure) {
            stage = 99;
            LogUtils.getLogger().error("STAFF RADIATION CLIENT CHECKS FAILED", failure);
            client.stop();
        }
    }
    private static Object field(String name) throws Exception {
        var field = screen.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(screen);
    }
    private static void screenshot(Minecraft client, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(client.gameDirectory.toPath().resolve("screenshots").resolve(name));
        }
    }
    private static void finish(Minecraft client, String detail) {
        stage = 99;
        LogUtils.getLogger().info("STAFF RADIATION CLIENT CHECKS PASSED: {}", detail);
        client.stop();
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
