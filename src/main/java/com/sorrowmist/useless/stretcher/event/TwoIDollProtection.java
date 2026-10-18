package com.sorrowmist.useless.stretcher.event;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/** Event-driven protection, with no global entity scan or resurrection loop. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class TwoIDollProtection {
    private TwoIDollProtection() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void preventTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof TwoIDollEntity) {
            // Use null rather than cancellation: old hostile targets are also cleared.
            event.setNewAboutToBeSetTarget(null);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preventPlayerAttack(AttackEntityEvent event) {
        if (event.getTarget() instanceof TwoIDollEntity) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preventDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof TwoIDollEntity) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preventDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof TwoIDollEntity) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void preventEggSpawn(net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent event) {
        // Normal doll deployment and world loading never use mob spawn finalization.
        if (event.getEntity() instanceof TwoIDollEntity) {
            event.setSpawnCancelled(true);
        }
    }
}
