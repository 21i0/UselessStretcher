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
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Client-side controls for the wondrous staff:
 * <ul>
 *   <li>Shift + mouse wheel cycles the multiplier gear (off / x2 / x4 / x16 / x256 / x1024).</li>
 *   <li>The dedicated mode key (unbound by default, bind in controls) toggles normal ↔ permanent.</li>
 * </ul>
 * Shift is only used together with the wheel and with right-click, never captured on its own.
 */
@EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT)
public final class WondrousStaffClient {
    /** Preset multiplier gears. 0 = off. */
    private static final int[] GEARS = {0, 2, 4, 16, 256, 1024};

    private WondrousStaffClient() {
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !player.isShiftKeyDown()) return;
        ItemStack held = player.getMainHandItem();
        if (held.getItem() != ModItems.WONDROUS_STAFF.get()) return;

        double delta = event.getScrollDeltaY();
        if (delta == 0) return;
        boolean up = delta > 0;

        int speed = cycleGear(WondrousStaffAcceleration.getSpeed(held), up);
        held.set(StretcherComponents.WONDROUS_STAFF_SPEED.get(), speed);
        Network.sendWondrousStaffSpeed(speed, WondrousStaffAcceleration.isPermanent(held));
        showGearStatus(speed);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.screen != null) return;
        if (!StretcherKeyBindings.WONDROUS_STAFF_MODE.consumeClick()) return;

        ItemStack held = player.getMainHandItem();
        if (held.getItem() != ModItems.WONDROUS_STAFF.get()) return;

        boolean permanent = !WondrousStaffAcceleration.isPermanent(held);
        held.set(StretcherComponents.WONDROUS_STAFF_PERMANENT.get(), permanent);
        Network.sendWondrousStaffSpeed(WondrousStaffAcceleration.getSpeed(held), permanent);
        showModeStatus(permanent);
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

    private static void showModeStatus(boolean permanent) {
        Component text = permanent
                ? Component.literal("\u6A21\u5F0F\uFF1A\u6C38\u4E45").withStyle(ChatFormatting.GOLD)
                : Component.literal("\u6A21\u5F0F\uFF1A\u666E\u901A").withStyle(ChatFormatting.AQUA);
        Minecraft.getInstance().gui.setOverlayMessage(text, false);
    }
}
