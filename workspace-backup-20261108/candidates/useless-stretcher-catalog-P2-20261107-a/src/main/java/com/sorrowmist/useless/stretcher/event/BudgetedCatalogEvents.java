package com.sorrowmist.useless.stretcher.event;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.catalog.BudgetedRecipeCatalog;
import com.sorrowmist.useless.stretcher.content.catalog.CatalogPatchSupport;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class BudgetedCatalogEvents {
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (CatalogPatchSupport.enabled() && event.getServer().overworld() != null)
            BudgetedRecipeCatalog.tick(event.getServer().overworld());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        if (CatalogPatchSupport.enabled())
            for (var level : event.getServer().getAllLevels()) BudgetedRecipeCatalog.cancel(level);
    }
}
