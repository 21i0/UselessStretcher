package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Client-side controls for the wondrous staff:
 * <ul>
 *   <li>Shift + mouse wheel cycles the multiplier gear (off / x2 / x4 / x16 / x32 / x64 / x128
 *       / x256 / x512 / x1024).</li>
 *   <li>The X key opens a button-based speed and duration menu.</li>
 * </ul>
 * Shift is only used together with the wheel and with right-click, never captured on its own.
 *
 * <p>Gear presets are modeled after JDT Extras' {@code TimeMultitoolSpeedMode} (MIT).
 */
@EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT)
public final class WondrousStaffClient {
    /** Preset multiplier gears. 0 = off. See {@code TimeMultitoolSpeedMode} (JDTE, MIT). */
    private static final int[] GEARS = {0, 2, 4, 16, 32, 64, 128, 256, 512, 1024};

    private WondrousStaffClient() {
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !player.isShiftKeyDown()) return;
        InteractionHand hand = findStaffHand(player);
        if (hand == null) return;
        ItemStack held = player.getItemInHand(hand);

        double delta = event.getScrollDeltaY();
        if (delta == 0) return;
        boolean up = delta > 0;

        int speed = cycleGear(WondrousStaffAcceleration.getSpeed(held), up);
        held.set(StretcherComponents.WONDROUS_STAFF_SPEED.get(), speed);
        Network.sendWondrousStaffSpeed(speed, WondrousStaffAcceleration.getMode(held),
                WondrousStaffAcceleration.isEnabled(held), hand);
        showGearStatus(speed);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.screen != null) return;
        if (!StretcherKeyBindings.WONDROUS_STAFF_MODE.consumeClick()) return;

        InteractionHand hand = findStaffHand(player);
        if (hand != null) {
            Network.sendStaffTutorialOpened();
            mc.setScreen(new WondrousStaffConfigScreen(hand));
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getAction() != org.lwjgl.glfw.GLFW.GLFW_PRESS || mc.screen != null) return;
        if (event.getKey() != org.lwjgl.glfw.GLFW.GLFW_KEY_X) return;
        Player player = mc.player;
        InteractionHand hand = player == null ? null : findStaffHand(player);
        if (hand == null) return;
        StretcherKeyBindings.WONDROUS_STAFF_MODE.consumeClick();
        Network.sendStaffTutorialOpened();
        mc.setScreen(new WondrousStaffConfigScreen(hand));
    }

    private static int cycleGear(int current, boolean up) {
        int idx = indexOf(current);
        int next = up ? (idx + 1) % GEARS.length : (idx - 1 + GEARS.length) % GEARS.length;
        return GEARS[next];
    }

    private static int indexOf(int speed) {
        if (speed <= 0) return 0;
        for (int i = 1; i < GEARS.length; i++) {
            if (speed <= GEARS[i]) return i;
        }
        return GEARS.length - 1;
    }

    private static void showGearStatus(int speed) {
        Minecraft mc = Minecraft.getInstance();
        Component text = speed <= 0
                ? Component.translatable("gui.useless_stretcher.speed_off").withStyle(ChatFormatting.GRAY)
                : Component.literal("x" + speed).withStyle(ChatFormatting.AQUA);
        mc.gui.setOverlayMessage(text, false);
    }

    private static InteractionHand findStaffHand(Player player) {
        if (player.getMainHandItem().is(ModItems.WONDROUS_STAFF.get())) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().is(ModItems.WONDROUS_STAFF.get())) return InteractionHand.OFF_HAND;
        return null;
    }
}
