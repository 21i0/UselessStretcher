package com.sorrowmist.useless.stretcher.event;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.entity.StaffPhaseManager;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** The second phase switch deliberately controls whether ordinary interaction is suppressed. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class StaffPhaseInteractionEvents {
    private StaffPhaseInteractionEvents() {}

    @SubscribeEvent public static void rightBlock(PlayerInteractEvent.RightClickBlock event) {
        if (StaffPhaseManager.blocksInteraction(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent public static void rightItem(PlayerInteractEvent.RightClickItem event) {
        if (StaffPhaseManager.blocksInteraction(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent public static void entity(PlayerInteractEvent.EntityInteract event) {
        if (StaffPhaseManager.blocksInteraction(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent public static void entitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (StaffPhaseManager.blocksInteraction(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent public static void leftBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (StaffPhaseManager.blocksInteraction(event.getEntity())) event.setCanceled(true);
    }
}
