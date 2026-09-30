package com.sorrowmist.useless.stretcher.content.item;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Targeted clicks open the same UI before a machine or entity can consume the gesture. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class RangeReclaimerInteractionHandler {
    private RangeReclaimerInteractionHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onBlock(PlayerInteractEvent.RightClickBlock event) {
        InteractionResult result = RangeReclaimerItem.tryOpen(event.getEntity(), event.getItemStack());
        if (result == InteractionResult.PASS) return;
        event.setCanceled(true);
        event.setCancellationResult(result);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onEntity(PlayerInteractEvent.EntityInteract event) {
        InteractionResult result = RangeReclaimerItem.tryOpen(event.getEntity(), event.getItemStack());
        if (result == InteractionResult.PASS) return;
        event.setCanceled(true);
        event.setCancellationResult(result);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        InteractionResult result = RangeReclaimerItem.tryOpen(event.getEntity(), event.getItemStack());
        if (result == InteractionResult.PASS) return;
        event.setCanceled(true);
        event.setCancellationResult(result);
    }
}
