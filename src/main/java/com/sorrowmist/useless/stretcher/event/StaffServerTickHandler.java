package com.sorrowmist.useless.stretcher.event;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.entity.PlayerAccelerationManager;
import com.sorrowmist.useless.stretcher.content.entity.StaffPhaseManager;
import com.sorrowmist.useless.stretcher.content.entity.TimeStopManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class StaffServerTickHandler {
    private StaffServerTickHandler() { }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        TimeStopManager.tick(event.getServer());
        PlayerAccelerationManager.tick(event.getServer(), TimeStopManager.remaining(event.getServer()) > 0);
        StaffPhaseManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        StaffPhaseManager.clear();
    }
}
