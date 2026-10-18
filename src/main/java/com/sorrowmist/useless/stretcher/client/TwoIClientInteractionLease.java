package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** One heartbeat per second while any 2i interaction page is open, with a single close signal. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT)
public final class TwoIClientInteractionLease {
    private static ClientPacketListener connection;
    private static ClientLevel level;
    private static int entityId = -1;
    private static int ticks;

    private TwoIClientInteractionLease() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != connection || client.level != level) {
            // A new connection/world may reuse an entity id. Never send the old release to it.
            connection = client.getConnection();
            level = client.level;
            entityId = -1;
            ticks = 0;
        }
        if (connection == null || level == null || client.player == null) return;
        int current = TwoIInteractionScreen.entityFor(client.screen);
        if (current != entityId) {
            if (entityId >= 0) Network.sendTwoIInteraction(entityId, false);
            entityId = current;
            ticks = 0;
        }
        if (entityId >= 0 && ticks++ % 20 == 0) Network.sendTwoIInteraction(entityId, true);
    }
}
