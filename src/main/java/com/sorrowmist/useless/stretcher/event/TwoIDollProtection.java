package com.sorrowmist.useless.stretcher.event;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

/** Event-driven protection, with no global entity scan or resurrection loop. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class TwoIDollProtection {
    private TwoIDollProtection() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void preventTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof TwoIDollEntity) {
            // Use null rather than cancellation: old hostile targets are also cleared.
            event.setNewTarget(null);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preventPlayerAttack(AttackEntityEvent event) {
        if (event.getTarget() instanceof TwoIDollEntity) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preventDamage(LivingAttackEvent event) {
        if (event.getEntity() instanceof TwoIDollEntity) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preventDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof TwoIDollEntity) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void preventEggSpawn(net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn event) {
        // Normal doll deployment and world loading never use mob spawn finalization.
        if (event.getEntity() instanceof TwoIDollEntity) {
            event.setSpawnCancelled(true);
        }
    }
}
