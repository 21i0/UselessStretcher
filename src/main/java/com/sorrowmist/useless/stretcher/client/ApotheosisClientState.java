package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.apotheosis.ApotheosisStaffSettings;
import com.sorrowmist.useless.stretcher.content.item.WondrousStaffItem;
import com.sorrowmist.useless.stretcher.network.ApotheosisNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;

/** Client display cache only; server SavedData owns all table bindings and bonuses. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT)
public final class ApotheosisClientState {
    private static Object level;
    private static ResourceLocation dimension;
    private static List<BlockPos> tables = List.of();
    private static boolean requested;
    private ApotheosisClientState() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (level != minecraft.level) {
            level = minecraft.level;
            requested = false;
            tables = List.of();
            dimension = null;
        }
        if (requested || minecraft.player == null || !ApotheosisStaffSettings.isEnchantingAvailable()) return;
        for (InteractionHand hand : InteractionHand.values()) {
            if (minecraft.player.getItemInHand(hand).getItem() instanceof WondrousStaffItem) {
                requested = true;
                ApotheosisNetwork.requestState(hand);
                break;
            }
        }
    }

    public static void accept(ApotheosisNetwork.SettingsPayload payload) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof WondrousStaffApotheosisScreen screen) screen.acceptState(payload);
    }

    public static void accept(ApotheosisNetwork.MarkersPayload payload) {
        dimension = payload.dimension();
        tables = payload.tables();
    }

    public static List<BlockPos> tables(ResourceLocation currentDimension) {
        return currentDimension.equals(dimension) ? tables : List.of();
    }
}
