package com.sorrowmist.useless.stretcher.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

/** Calls the real upstream aura and destructive fallback methods after mixin transformation. */
final class TwoIDollUselessKillChecks {
    private TwoIDollUselessKillChecks() { }

    static void run(ServerLevel level, TwoIDollEntity doll) throws Exception {
        var version = ModList.get().getModContainerById("useless_mod").orElseThrow().getModInfo().getVersion();
        String semanticVersion = version.toString().replaceFirst("^1\\.21\\.1-", "");
        Class<?> upstream = Class.forName("com.sorrowmist.useless.content.items.EndlessBeafItem");
        Method aura;
        Method protectedTarget;
        Method blacklisted;
        Method forceKill;
        Method forceDie;
        Method purge;
        try {
            aura = method(upstream, "tickKillAura", Player.class, ItemStack.class);
            protectedTarget = method(upstream, "isProtectedTarget", Player.class, Entity.class);
            blacklisted = method(upstream, "isForceKillBlacklisted", Entity.class);
            forceKill = method(upstream, "forceKillLivingEntity", ItemStack.class, LivingEntity.class, Player.class);
            forceDie = method(upstream, "forceDie", ServerLevel.class, LivingEntity.class, DamageSource.class, boolean.class);
            purge = method(upstream, "removeEntityFromServerStorage", ServerLevel.class, Entity.class);
        } catch (NoSuchMethodException missing) {
            check(new DefaultArtifactVersion(semanticVersion).compareTo(new DefaultArtifactVersion("2.4.5.11")) < 0,
                    "current upstream must expose the real aura and forced-removal entrypoints: " + missing);
            LogUtils.getLogger().info("REGRESSION SKIP upstream {} forced-kill checks: legacy API has no {}",
                    version, missing.getMessage());
            return;
        }

        // A different player avoids upstream's owner/alliance protection masking the 2i guard.
        var attacker = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "UselessAuraTest"));
        attacker.setPos(doll.getX(), doll.getY(), doll.getZ());
        attacker.setHealth(20);
        check(!attacker.getUUID().equals(doll.getOwnerId()), "aura fixture is not doll owner");
        ItemStack staff = new ItemStack(com.sorrowmist.useless.init.ModItems.ENDLESS_BEAF_ITEM.get());
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(staff, UComponents.ForceKillEnabledComponent.get(), true);
        method(upstream, "setProtectModeEnabled", ItemStack.class, boolean.class).invoke(null, staff, false);
        method(upstream, "setKillAuraEnabled", ItemStack.class, boolean.class).invoke(null, staff, true);
        check(com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(staff, UComponents.ForceKillEnabledComponent.get(), false), "real force-kill component enabled");

        EntityTickList ticks = (EntityTickList) field(ServerLevel.class, "entityTickList").get(level);
        // ServerStartedEvent can run before chunk entity-ticking tickets. Seed this fixture's real
        // tick-list entry so a raw storage purge cannot pass simply because it started absent.
        boolean wasTicking = ticks.contains(doll);
        if (!wasTicking) ticks.add(doll);
        UUID uuid = doll.getUUID();
        int id = doll.getId();
        var auraChicken = EntityType.CHICKEN.create(level);
        var forceDieChicken = EntityType.CHICKEN.create(level);
        var purgeChicken = EntityType.CHICKEN.create(level);
        check(auraChicken != null && forceDieChicken != null && purgeChicken != null, "ordinary control fixtures exist");
        try {
            preserved(level, doll, ticks, uuid, id, "baseline");
            check((boolean) protectedTarget.invoke(null, attacker, doll), "real protected-target predicate recognizes 2i");
            check((boolean) blacklisted.invoke(null, doll), "real forced-kill blacklist recognizes 2i");

            auraChicken.setPos(attacker.getX(), attacker.getY(), attacker.getZ());
            check(level.addFreshEntity(auraChicken), "ordinary aura target registered");
            check(!(boolean) protectedTarget.invoke(null, attacker, auraChicken)
                            && !(boolean) blacklisted.invoke(null, auraChicken),
                    "ordinary chicken is not protected by the doll guard");
            aura.invoke(null, attacker, staff);
            check(auraChicken.isDeadOrDying() || auraChicken.isRemoved(), "actual upstream kill aura still kills ordinary chicken");
            preserved(level, doll, ticks, uuid, id, "actual forced-kill aura");
            aura.invoke(null, attacker, staff);
            preserved(level, doll, ticks, uuid, id, "repeated actual aura");

            check(!(boolean) forceKill.invoke(null, staff, doll, attacker), "direct real force-kill rejects 2i");
            preserved(level, doll, ticks, uuid, id, "direct forceKillLivingEntity");
            forceDie.invoke(null, level, doll, level.damageSources().genericKill(), true);
            preserved(level, doll, ticks, uuid, id, "direct private forceDie");
            purge.invoke(null, level, doll);
            preserved(level, doll, ticks, uuid, id, "direct private removeEntityFromServerStorage");

            forceDieChicken.setPos(attacker.getX(), attacker.getY(), attacker.getZ());
            check(level.addFreshEntity(forceDieChicken), "ordinary forceDie control registered");
            forceDie.invoke(null, level, forceDieChicken, level.damageSources().genericKill(), false);
            check((boolean) field(LivingEntity.class, "dead").get(forceDieChicken)
                            && forceDieChicken.getPose() == Pose.DYING,
                    "private forceDie still mutates ordinary target, proving guard is doll-only");
            purgeChicken.setPos(attacker.getX(), attacker.getY(), attacker.getZ());
            check(level.addFreshEntity(purgeChicken), "ordinary purge control registered");
            ticks.add(purgeChicken);
            UUID purgeUuid = purgeChicken.getUUID();
            int purgeId = purgeChicken.getId();
            purge.invoke(null, level, purgeChicken);
            check(purgeChicken.isRemoved() && level.getEntity(purgeUuid) == null
                            && level.getEntity(purgeId) == null && !ticks.contains(purgeChicken),
                    "private storage purge still removes ordinary target from both indexes and ticking");
            preserved(level, doll, ticks, uuid, id, "after ordinary controls");
            LogUtils.getLogger().info("REGRESSION actual Useless Mod {}: forced-kill aura, forceKillLivingEntity, private forceDie and direct storage purge preserve original 2i UUID/id/world indexes/tick entry; ordinary chicken controls still die/remove", version);
        } finally {
            auraChicken.discard();
            forceDieChicken.discard();
            purgeChicken.discard();
            if (!wasTicking) ticks.remove(doll);
        }
    }

    private static void preserved(ServerLevel level, TwoIDollEntity doll, EntityTickList ticks,
                                  UUID uuid, int id, String step) throws Exception {
        check(doll.getUUID().equals(uuid) && doll.getId() == id, step + ": original UUID and numeric id");
        check(doll.isAlive() && !doll.isRemoved() && doll.getHealth() == 20
                        && !(boolean) field(LivingEntity.class, "dead").get(doll) && doll.getPose() != Pose.DYING,
                step + ": no death state or removal (checked before any repair tick)");
        check(level.getEntity(uuid) == doll && level.getEntity(id) == doll,
                step + ": both world indexes still resolve the exact original entity");
        Object manager = field(ServerLevel.class, "entityManager").get(level);
        @SuppressWarnings("unchecked") Set<UUID> known = (Set<UUID>) field(manager.getClass(), "knownUuids").get(manager);
        check(known.contains(uuid), step + ": persistent entity manager retains UUID");
        check(ticks.contains(doll), step + ": real server tick list retains entity");
        int[] occurrences = {0};
        ticks.forEach(entity -> { if (entity == doll) occurrences[0]++; });
        check(occurrences[0] == 1, step + ": tick list contains exact same object once");
        check(level.getEntitiesOfClass(TwoIDollEntity.class, doll.getBoundingBox().inflate(0.01))
                        .stream().anyMatch(entity -> entity == doll),
                step + ": spatial queries retain original entity");
    }

    private static Method method(Class<?> type, String name, Class<?>... arguments) throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(name, arguments);
        method.setAccessible(true);
        return method;
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError("2i upstream kill guard: " + name);
    }
}
