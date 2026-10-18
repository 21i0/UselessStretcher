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
    private static final java.util.List<String> capsuleEntries = new java.util.ArrayList<>();
    private static int capsuleNextPart;
    private static int capsuleParts;
    private static boolean capsuleOffhand;
    private static boolean staffPhaseEnabled;
    private static boolean staffPhaseInteraction;
    private static Boolean staffPhasePreviousNoPhysics;
    private static Boolean staffPhaseLastAppliedNoPhysics;
    private static net.minecraft.client.player.LocalPlayer staffPhasePlayer;
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

    public static void handleStaffPhase(Network.StaffPhaseStatePayload payload) {
        staffPhaseEnabled = payload.phaseEnabled();
        staffPhaseInteraction = payload.interactionEnabled();
        applyStaffPhase();
    }

    /** Reassert the local collision flag after respawn/dimension changes. */
    public static void tickStaffPhase() {
        if (Minecraft.getInstance().level == null) {
            staffPhaseEnabled = false;
            staffPhaseInteraction = true;
            staffPhasePreviousNoPhysics = null;
            staffPhaseLastAppliedNoPhysics = null;
            staffPhasePlayer = null;
            return;
        }
        applyStaffPhase();
    }

    public static boolean staffPhaseInteractionEnabled() {
        return staffPhaseInteraction;
    }

    private static void applyStaffPhase() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        if (staffPhasePlayer != minecraft.player) {
            staffPhasePlayer = minecraft.player;
            staffPhasePreviousNoPhysics = null;
            staffPhaseLastAppliedNoPhysics = null;
        }
        if (staffPhaseEnabled) {
            boolean current = com.sorrowmist.useless.stretcher.content.entity.StaffPhaseManager
                    .noPhysics(minecraft.player);
            if (staffPhasePreviousNoPhysics == null) {
                staffPhasePreviousNoPhysics = current;
                staffPhaseLastAppliedNoPhysics = true;
            } else if (staffPhaseLastAppliedNoPhysics == null || current != staffPhaseLastAppliedNoPhysics) {
                // Remember an external change as the desired value after the staff
                // releases collision control.
                staffPhasePreviousNoPhysics = current;
                staffPhaseLastAppliedNoPhysics = true;
            }
            com.sorrowmist.useless.stretcher.content.entity.StaffPhaseManager
                    .setNoPhysics(minecraft.player, true);
        } else if (staffPhasePreviousNoPhysics != null) {
            boolean current = com.sorrowmist.useless.stretcher.content.entity.StaffPhaseManager
                    .noPhysics(minecraft.player);
            if (staffPhaseLastAppliedNoPhysics != null && current == staffPhaseLastAppliedNoPhysics) {
                com.sorrowmist.useless.stretcher.content.entity.StaffPhaseManager
                        .setNoPhysics(minecraft.player, staffPhasePreviousNoPhysics);
            }
            staffPhasePreviousNoPhysics = null;
            staffPhaseLastAppliedNoPhysics = null;
        }
    }

    public static void handleTimeStop(Network.TimeStopStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.remaining() <= 0) {
            minecraft.gui.setOverlayMessage(Component.translatable("msg.useless_stretcher.time_stop.end"), false);
        } else {
            int seconds = (payload.remaining() + 19) / 20;
            minecraft.gui.setOverlayMessage(Component.translatable("msg.useless_stretcher.time_stop.countdown", seconds), false);
        }
    }

    public static void handleTwoIDialogue(Network.TwoIDialoguePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof com.sorrowmist.useless.stretcher.client.TwoIDialogueScreen screen
                && screen.entityId() == payload.entityId()) {
            screen.updateGiftStatus(payload.inviteReady(), payload.giftScore());
            return;
        }
        if (minecraft.screen instanceof com.sorrowmist.useless.stretcher.client.TwoIStoryScreen story
                && story.entityId() == payload.entityId()) {
            story.updateGiftStatus(payload.inviteReady(), payload.giftScore());
            return;
        }
        if (!payload.open()) return;
        minecraft.setScreen(new com.sorrowmist.useless.stretcher.client.TwoIDialogueScreen(
                payload.entityId(), payload.inviteReady(), payload.giftScore()));
    }

    public static void handleTwoIItemDialogue(Network.TwoIItemDialoguePayload payload) {
        Minecraft.getInstance().setScreen(com.sorrowmist.useless.stretcher.client.TwoIDialogueScreen.forItem(
                payload.offhand() ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND,
                payload.bond(), payload.invitationUsed()));
    }

    public static void handleDimensionCapsule(Network.DimensionCapsuleCatalogPayload payload) {
        if (payload.part() == 0) {
            capsuleEntries.clear();
            capsuleNextPart = 0;
            capsuleParts = payload.parts();
            capsuleOffhand = payload.offhand();
        }
        if (payload.part() != capsuleNextPart || payload.parts() != capsuleParts
                || payload.offhand() != capsuleOffhand) return;
        capsuleEntries.addAll(payload.entries());
        capsuleNextPart++;
        if (capsuleNextPart != capsuleParts) return;
        Minecraft.getInstance().setScreen(new com.sorrowmist.useless.stretcher.client.DimensionCapsuleScreen(
                java.util.List.copyOf(capsuleEntries), payload.offhand()));
        capsuleEntries.clear();
    }

    public static void handleNativeCapsule(CapsuleNetwork.State payload) {
        if (Minecraft.getInstance().screen instanceof com.sorrowmist.useless.stretcher.client.DimensionCapsuleScreen screen)
            screen.accept(payload);
    }

    public static void handleRangeHistory(RangeNetwork.HistoryStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof RangeAccelerationHistoryScreen screen) {
            screen.onHistory(payload.fields());
        }
    }

    public static void handleTwoIStoryState(Network.TwoIStoryStatePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof com.sorrowmist.useless.stretcher.client.TwoIDialogueScreen screen
                && screen.entityId() == payload.entityId()) {
            if (payload.result() == 0) {
                screen.openStory(payload.sessionId(), payload.chapter(), payload.bond(), payload.round());
            } else if (payload.result() == 2 && payload.sessionId() == 0L) {
                screen.onStoryUnavailable();
            }
        } else if (minecraft.screen instanceof com.sorrowmist.useless.stretcher.client.TwoIStoryScreen screen
                && screen.entityId() == payload.entityId()) {
            if (payload.result() == 1) {
                screen.onChoiceResult(payload.sessionId(), payload.bond(), payload.round());
            } else if (payload.result() == 2) {
                screen.onStoryUnavailable(payload.sessionId(), payload.round());
            }
        }
    }

    public static void handleTwoIStoryLibrary(Network.TwoIStoryLibraryStatePayload payload) {
        if (Minecraft.getInstance().screen instanceof
                com.sorrowmist.useless.stretcher.client.TwoIStoryLibraryScreen screen) {
            screen.acceptLibrary(payload.requestId(), payload.completedMask());
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
