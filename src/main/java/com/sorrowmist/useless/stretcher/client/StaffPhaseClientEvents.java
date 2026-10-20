package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.network.ClientStateReceiver;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ClientTickEvent;

/** Keeps client collision aligned with the server while an enabled phase staff is carried. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT)
public final class StaffPhaseClientEvents {
    private StaffPhaseClientEvents() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        ClientStateReceiver.tickStaffPhase();
    }

    @SubscribeEvent
    public static void overlay(net.minecraftforge.client.event.RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() == net.minecraftforge.client.event.RenderBlockScreenEffectEvent.OverlayType.BLOCK
                && com.sorrowmist.useless.stretcher.content.entity.StaffPhaseManager.state(event.getPlayer()).phase())
            event.setCanceled(true);
    }
}
