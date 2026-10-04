package com.sorrowmist.useless.stretcher.content.entity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Applies the staff's short player boost without overwriting the player's own effects permanently. */
public final class PlayerAccelerationManager {
    public static final int DURATION_TICKS = 600;
    private static final Map<UUID, Active> ACTIVE = new HashMap<>();

    private PlayerAccelerationManager() { }

    public static void apply(ServerPlayer player, int speed) {
        if (speed <= 0) {
            clear(player);
            return;
        }
        int amplifier = Math.max(0, Integer.numberOfTrailingZeros(Integer.highestOneBit(speed)) - 1);
        Active old = ACTIVE.remove(player.getUUID());
        if (old != null) restore(player, old);
        MobEffectInstance previousSpeed = copy(player, MobEffects.MOVEMENT_SPEED);
        MobEffectInstance previousHaste = copy(player, MobEffects.DIG_SPEED);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DURATION_TICKS, amplifier,
                false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, DURATION_TICKS, amplifier,
                false, true, true));
        ACTIVE.put(player.getUUID(), new Active(DURATION_TICKS, amplifier, previousSpeed, previousHaste));
    }

    public static void tick(MinecraftServer server, boolean paused) {
        if (paused || ACTIVE.isEmpty()) return;
        Iterator<Map.Entry<UUID, Active>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Active> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            Active active = entry.getValue().decrement();
            if (active.remaining() <= 0) {
                restore(player, active);
                iterator.remove();
            } else {
                entry.setValue(active);
            }
        }
    }

    public static void clear(Player player) {
        if (player == null) return;
        Active active = ACTIVE.remove(player.getUUID());
        if (active != null) restore(player, active);
    }

    private static MobEffectInstance copy(Player player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        MobEffectInstance value = player.getEffect(effect);
        return value == null ? null : new MobEffectInstance(value);
    }

    private static void restore(Player player, Active active) {
        removeIfOwned(player, MobEffects.MOVEMENT_SPEED, active.amplifier());
        removeIfOwned(player, MobEffects.DIG_SPEED, active.amplifier());
        if (active.previousSpeed() != null) player.addEffect(new MobEffectInstance(active.previousSpeed()));
        if (active.previousHaste() != null) player.addEffect(new MobEffectInstance(active.previousHaste()));
    }

    private static void removeIfOwned(Player player,
                                      net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
                                      int amplifier) {
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.getAmplifier() == amplifier
                && current.getDuration() <= DURATION_TICKS) player.removeEffect(effect);
    }

    private record Active(int remaining, int amplifier, MobEffectInstance previousSpeed,
                          MobEffectInstance previousHaste) {
        private Active decrement() {
            return new Active(remaining - 1, amplifier, previousSpeed, previousHaste);
        }
    }
}
