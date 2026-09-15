package com.sorrowmist.useless.stretcher.network;

import com.sorrowmist.useless.stretcher.client.WondrousStaffCloudTime;
import com.sorrowmist.useless.stretcher.screen.OmniversalMyriadScreen;
import com.sorrowmist.useless.stretcher.client.RangeAccelerationHistoryScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Forwards server-pushed myriad state and full-slot feedback to the client. */
public final class ClientStateReceiver {
    private ClientStateReceiver() {
    }

    public static void accept(Network.MyriadStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof OmniversalMyriadScreen screen && screen.matches(payload.pos())) {
            screen.onState(payload.enabledMolds(), payload.patternMolds(), payload.patternCount(), payload.aeBound());
        }
    }

    public static void handleFullSlots(Network.FullSlotsPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(null);
        minecraft.gui.setTitle(Component.translatable("msg.useless_stretcher.slots_full")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        minecraft.gui.setSubtitle(Component.translatable("msg.useless_stretcher.slots_full_subtitle"));
        minecraft.gui.setTimes(15, 60, 15);
    }

    public static void handleStaffTutorial() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.setTitle(Component.translatable("msg.useless_stretcher.staff_ui_hint")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        minecraft.gui.setSubtitle(Component.translatable("msg.useless_stretcher.staff_ui_hint_subtitle")
                .withStyle(ChatFormatting.GOLD));
        minecraft.gui.setTimes(15, 80, 20);
    }

    public static void handleTimeAcceleration(Network.TimeAccelerationStatePayload payload) {
        WondrousStaffCloudTime.accept(payload.dimension(), payload.speed());
    }

    public static void handleRangeHistory(RangeNetwork.HistoryStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof RangeAccelerationHistoryScreen screen) {
            screen.onHistory(payload.fields());
        }
    }
}
