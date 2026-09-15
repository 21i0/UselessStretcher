package com.sorrowmist.useless.stretcher.event;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Drives all enabled world-owned ranges once per server tick. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class RangeAccelerationTicker {
    private RangeAccelerationTicker() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        RangeAccelerationSavedData.get(event.getServer()).tick(event.getServer());
    }
}
