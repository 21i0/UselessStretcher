package com.sorrowmist.useless.stretcher.regression;

import com.mojang.authlib.GameProfile;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import com.sorrowmist.useless.stretcher.init.ModEntities;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.UUID;

/** Focused server checks for invulnerability AND lawful lifecycle removal. */
public final class TwoIDollProtectionChecks {
    private TwoIDollProtectionChecks() { }

    public static void run(ServerLevel level) throws Exception {
        TwoIDollSpawnEggChecks.run(level);
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "DollSafetyCheck"));
        var ownerSpawn = level.getSharedSpawnPos();
        owner.setPos(ownerSpawn.getX() + .5D, ownerSpawn.getY() + 1.0D, ownerSpawn.getZ() + .5D);
        TwoIDollEntity doll = spawn(level, owner.getUUID());
        try {
            doll.setInvulnerable(false);
            check(doll.isInvulnerable() && !doll.canBeSeenAsEnemy() && !doll.isAttackable()
                    && doll.isPickable() && !doll.canBeHitByProjectile(), "not an enemy but still right-clickable");
            for (var source : new net.minecraft.world.damagesource.DamageSource[]{
                    level.damageSources().generic(), level.damageSources().genericKill(),
                    level.damageSources().fellOutOfWorld(), level.damageSources().inWall()}) {
                check(!doll.hurt(source, Float.MAX_VALUE) && doll.isInvulnerableTo(source), "bypass/void/crush immunity");
            }
            for (float health : new float[]{0, -10, Float.NaN, Float.NEGATIVE_INFINITY}) {
                doll.setHealth(health);
                check(doll.getHealth() == 20 && doll.isAlive() && !doll.isDeadOrDying(), "setHealth attack blocked");
            }
            doll.getAttribute(Attributes.MAX_HEALTH).setBaseValue(0);
            var healthField = LivingEntity.class.getDeclaredField("DATA_HEALTH_ID");
            healthField.setAccessible(true);
            @SuppressWarnings("unchecked") EntityDataAccessor<Float> healthId = (EntityDataAccessor<Float>)healthField.get(null);
            for (float health : new float[]{0, -10, Float.NaN, Float.NEGATIVE_INFINITY}) {
                doll.getEntityData().set(healthId, health);
                check(doll.getEntityData().get(healthId) == 20,
                        "direct tracked-data poisoning is corrected before the next tick");
                check(doll.getMaxHealth() == 20, "poisoned maximum-health attribute does not alter doll maximum");
                check(doll.getHealth() == 20 && doll.isAlive() && !doll.isDeadOrDying(),
                        "max-health poisoning and direct tracked-data write cannot fake death");
                if (net.neoforged.fml.ModList.get().isLoaded("manaita_plus_legacy")) {
                    Class<?> helper = Class.forName("sen.manaita_plus_legacy_core.util.CallBackHelper");
                    check((float) helper.getMethod("getHealth", LivingEntity.class).invoke(null, doll) == 20,
                            "actual Manaita raw-health bypass sees healthy synchronized data");
                    check((boolean) helper.getMethod("isAlive", LivingEntity.class).invoke(null, doll)
                                    && !(boolean) helper.getMethod("isDeadOrDying", LivingEntity.class).invoke(null, doll),
                            "actual Manaita liveness callbacks respect the protected doll");
                }
            }
            doll.deathTime = 19;
            doll.tick();
            check(doll.getEntityData().get(healthId) == 20 && doll.deathTime == 0 && !doll.isRemoved(),
                    "invalid tracked health normalized without resurrection");
            doll.die(level.damageSources().genericKill());
            doll.kill();
            doll.remove(Entity.RemovalReason.KILLED);
            doll.discard();
            doll.setRemoved(Entity.RemovalReason.DISCARDED);
            doll.setRemoved(Entity.RemovalReason.KILLED);
            check(doll.isAlive() && !doll.isRemoved() && level.getEntity(doll.getUUID()) == doll,
                    "kill/remove/discard/final-setRemoved preserve the same registered entity");

            TwoIDollUselessKillChecks.run(level, doll);

            if (net.neoforged.fml.ModList.get().isLoaded("manaita_plus_legacy")) {
                Class<?> utils = Class.forName("sen.manaita_plus_legacy.common.util.ManaitaPlusUtils");
                var attack = utils.getMethod("attack", Entity.class, net.minecraft.world.entity.player.Player.class, boolean.class);
                attack.invoke(null, doll, owner, false);
                attack.invoke(null, doll, owner, true);
                utils.getMethod("removeOnServer", Entity.class).invoke(null, doll);
                Class<?> marks = Class.forName("sen.manaita_plus_legacy.common.util.entity.ManaitaPlusLegacyEntityData");
                var ordinary = EntityType.CHICKEN.create(level);
                check(ordinary != null, "ordinary Manaita marker fixture exists");
                for (String mark : new String[]{"death", "remove"}) {
                    Object value = marks.getField(mark).get(null);
                    marks.getMethod("add", Entity.class).invoke(value, doll);
                    int flag = (int) marks.getMethod("getFlag").invoke(value);
                    marks.getMethod("putInt", Entity.class, int.class).invoke(value, doll, flag);
                    check(!(boolean) marks.getMethod("accept", Entity.class).invoke(value, doll)
                                    && !(boolean) marks.getMethod("acceptSide", Entity.class).invoke(value, doll),
                            "actual Manaita death/remove marker rejects doll");
                    try {
                        marks.getMethod("add", Entity.class).invoke(value, ordinary);
                        check((boolean) marks.getMethod("accept", Entity.class).invoke(value, ordinary),
                                "ordinary mobs still accept actual Manaita destructive markers");
                    } finally {
                        marks.getMethod("remove", Entity.class).invoke(value, ordinary);
                    }
                }
                ordinary.discard();
                check(doll.isAlive() && !doll.isRemoved() && level.getEntity(doll.getUUID()) == doll,
                        "actual Manaita attacks and index removal preserve the original entity");
                utils.getMethod("godKill", int.class, boolean.class, boolean.class,
                                net.minecraft.world.entity.player.Player.class).invoke(null, 0, true, true, owner);
                check(doll.isAlive() && !doll.isRemoved() && level.getEntity(doll.getUUID()) == doll,
                        "actual all-entity area purge preserves the same registered doll");
                var purgeTarget = EntityType.CHICKEN.create(level);
                check(purgeTarget != null && level.addFreshEntity(purgeTarget), "ordinary removal fixture exists");
                utils.getMethod("removeOnServer", Entity.class).invoke(null, purgeTarget);
                check(purgeTarget.isRemoved() && level.getEntity(purgeTarget.getUUID()) == null,
                        "Manaita still removes ordinary entities; guard is doll-only");
                com.mojang.logging.LogUtils.getLogger().info("REGRESSION actual Manaita: damage attack, removal attack, direct index purge and death/remove flags blocked for 2i");
            }

            var zombie = EntityType.ZOMBIE.create(level);
            var piglin = EntityType.PIGLIN.create(level);
            var chicken = EntityType.CHICKEN.create(level);
            check(zombie != null && piglin != null && chicken != null, "target fixtures exist");
            zombie.setTarget(chicken);
            zombie.setTarget(doll);
            check(zombie.getTarget() == null && !zombie.canAttack(doll), "mob target event clears doll target");
            piglin.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, chicken);
            check(piglin.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == chicken,
                    "ordinary brain attack memory remains available");
            piglin.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, doll);
            check(piglin.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty(), "brain direct attack target denied");
            piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_TARGET, doll, 100);
            check(piglin.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty(), "expiring attack target denied");
            zombie.discard(); piglin.discard(); chicken.discard();

            int before = owner.getInventory().countItem(ModItems.TWO_I_DOLL.get());
            doll.reclaim(owner);
            doll.reclaim(owner);
            check(doll.isRemoved() && !doll.isAlive()
                            && owner.getInventory().countItem(ModItems.TWO_I_DOLL.get()) == before + 1,
                    "authorized recall removes exactly once and cannot duplicate");
        } finally {
            doll.discardForReclaim();
        }
        for (var reason : new Entity.RemovalReason[]{Entity.RemovalReason.UNLOADED_TO_CHUNK,
                Entity.RemovalReason.UNLOADED_WITH_PLAYER, Entity.RemovalReason.CHANGED_DIMENSION}) {
            TwoIDollEntity lifecycle = spawn(level, owner.getUUID());
            lifecycle.setRemoved(reason);
            check(lifecycle.isRemoved() && !lifecycle.isAlive(), "unload/dimension lifecycle is not blocked: " + reason);
            check(level.getEntity(lifecycle.getUUID()) == null && level.getEntity(lifecycle.getId()) == null,
                    "lawful unload/transfer clears world indexes: " + reason);
        }
        TwoIDollEntity unregistered = ModEntities.TWO_I_DOLL.get().create(level);
        unregistered.discard();
        check(unregistered.isRemoved(), "failed spawn and pre-registration cleanup remain safe");
    }

    private static TwoIDollEntity spawn(ServerLevel level, UUID owner) {
        TwoIDollEntity doll = ModEntities.TWO_I_DOLL.get().create(level);
        check(doll != null, "doll factory");
        doll.setOwnerId(owner);
        var spawn = level.getSharedSpawnPos();
        doll.setPos(spawn.getX() + .5D, spawn.getY() + 1.0D, spawn.getZ() + .5D);
        level.getChunkAt(doll.blockPosition());
        check(level.addFreshEntity(doll), "doll registration");
        return doll;
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError("2i protection: " + name);
    }
}
