package com.sorrowmist.useless.stretcher.event;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.acceleration.AccelerationExecutionBudget;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.content.entity.TimeStopManager;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;

/** Drives all enabled world-owned ranges once per server tick. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class RangeAccelerationTicker {
    private RangeAccelerationTicker() {
    }

    @SubscribeEvent
    public static void onServerTickStart(ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.START) return;
        AccelerationExecutionBudget.beginTick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTickEnd(ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (TimeStopManager.remaining(event.getServer()) > 0) return;
        RangeAccelerationSavedData.get(event.getServer()).tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        AccelerationExecutionBudget.removeServer(event.getServer());
    }
}
