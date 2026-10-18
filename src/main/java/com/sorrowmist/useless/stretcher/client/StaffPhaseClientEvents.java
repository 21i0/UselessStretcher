package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.network.ClientStateReceiver;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Keeps the client collision flag aligned with the server while the phase staff is held. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT)
public final class StaffPhaseClientEvents {
    private StaffPhaseClientEvents() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientStateReceiver.tickStaffPhase();
    }
}
