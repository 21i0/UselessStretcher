package com.sorrowmist.useless.stretcher.network;

import com.sorrowmist.useless.stretcher.client.WondrousStaffCloudTime;
import com.sorrowmist.useless.stretcher.screen.OmniversalMyriadScreen;
import com.sorrowmist.useless.stretcher.screen.MyriadPatternRepositoryScreen;
import com.sorrowmist.useless.stretcher.client.RangeAccelerationHistoryScreen;
import com.sorrowmist.useless.stretcher.client.ReclaimerScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;

/** Forwards server-pushed myriad state and full-slot feedback to the client. */
public final class ClientStateReceiver {
    private ClientStateReceiver() {
    }

    public static void accept(Network.MyriadStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof OmniversalMyriadScreen screen && screen.matches(payload.pos())) {
            screen.onState(payload.enabledMolds(), payload.patternMolds(), payload.patternCount(), payload.aeBound(),
                    payload.progress(), payload.part(), payload.parts());
        }
    }

    public static void acceptMoldCatalog(Network.MoldCatalogPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof OmniversalMyriadScreen screen && screen.matches(payload.pos())) {
            screen.onCatalog(payload);
        }
    }

    public static void acceptPatternPage(Network.PatternPagePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.item() && payload.requestId() == 0 && !(minecraft.screen instanceof MyriadPatternRepositoryScreen)) {
            minecraft.setScreen(new MyriadPatternRepositoryScreen(minecraft.screen, BlockPos.ZERO,
                    payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND));
        }
        if (minecraft.screen instanceof MyriadPatternRepositoryScreen screen && screen.matches(payload)) screen.onPage(payload);
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

    public static void handleReclaimer(Network.ReclaimerStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ReclaimerScreen screen && screen.personal() == payload.personal()) {
            screen.update(payload.entries());
        } else {
            minecraft.setScreen(new ReclaimerScreen(payload.personal() ? minecraft.screen : null,
                    payload.entries(), payload.personal()));
        }
    }
}
