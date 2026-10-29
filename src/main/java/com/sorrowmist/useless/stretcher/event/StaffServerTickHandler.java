package com.sorrowmist.useless.stretcher.event;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.entity.PlayerAccelerationManager;
import com.sorrowmist.useless.stretcher.content.entity.StaffPhaseManager;
import com.sorrowmist.useless.stretcher.content.entity.TimeStopManager;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;

@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class StaffServerTickHandler {
    private StaffServerTickHandler() { }

    @SubscribeEvent
    public static void tick(ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        TimeStopManager.tick(event.getServer());
        PlayerAccelerationManager.tick(event.getServer(), TimeStopManager.remaining(event.getServer()) > 0);
        StaffPhaseManager.tick(event.getServer());
        com.sorrowmist.useless.stretcher.content.item.StaffFluidCollector.tick(event.getServer());
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        StaffPhaseManager.clear();
        com.sorrowmist.useless.stretcher.content.item.StaffFluidCollector.clear();
    }
}
