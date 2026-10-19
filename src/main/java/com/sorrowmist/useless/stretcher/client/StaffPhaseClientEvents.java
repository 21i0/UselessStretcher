package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.network.ClientStateReceiver;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Keeps client collision aligned with the server while an enabled phase staff is carried. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT)
public final class StaffPhaseClientEvents {
    private StaffPhaseClientEvents() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientStateReceiver.tickStaffPhase();
    }

    @SubscribeEvent
    public static void overlay(net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() == net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent.OverlayType.BLOCK
                && com.sorrowmist.useless.stretcher.content.entity.StaffPhaseManager.state(event.getPlayer()).phase())
            event.setCanceled(true);
    }
}
