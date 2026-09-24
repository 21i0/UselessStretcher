package com.sorrowmist.useless.stretcher.content.entity;

import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded, server-authoritative summon action for the staff catalog. */
public final class WondrousStaffSummoning {
    public static final int MAX_SELECTION = 32;
    private static final int SUMMON_COOLDOWN_TICKS = 10;
    private static final Map<UUID, Long> NEXT_ALLOWED_TICK = new HashMap<>();

    private WondrousStaffSummoning() {
    }

    public static void summon(ServerPlayer player, ItemStack staff, List<String> rawIds) {
        if (!StretcherConfig.enableStaffSummon()
                || !staff.is(ModItems.WONDROUS_STAFF.get())
                || !(player.level() instanceof ServerLevel level)) return;

        // Clicking the summon action is also the enable action. The client UI
        // mirrors this component, but the server applies it authoritatively so
        // packet ordering cannot make the first summon silently do nothing.
        if (!WondrousStaffAcceleration.isSummonEnabled(staff)) {
            staff.set(StretcherComponents.WONDROUS_STAFF_SUMMON_ENABLED.get(), true);
        }

        long now = level.getGameTime();
        long next = NEXT_ALLOWED_TICK.getOrDefault(player.getUUID(), 0L);
        if (now < next) return;
        NEXT_ALLOWED_TICK.put(player.getUUID(), now + SUMMON_COOLDOWN_TICKS);

        HashSet<ResourceLocation> ids = new HashSet<>();
        int spawned = 0;
        for (String rawId : rawIds) {
            if (spawned >= MAX_SELECTION) break;
            ResourceLocation id = ResourceLocation.tryParse(rawId);
            if (id == null || !ids.add(id)) continue;
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type == null || !type.canSummon() || type.getCategory() == MobCategory.MISC) continue;

            BlockPos spawnPos = findSpawnPos(player, spawned);
            if (spawnPos == null) continue;
            Entity entity = type.spawn(level, spawnPos, MobSpawnType.COMMAND);
            if (entity != null) {
                if (entity instanceof Mob mob && !(mob instanceof EnderDragon)) mob.setNoAi(true);
                spawned++;
            }
        }
        if (spawned > 0) {
            player.displayClientMessage(Component.translatable(
                    "msg.useless_stretcher.staff_summoned", spawned, MAX_SELECTION), false);
        } else {
            player.displayClientMessage(Component.translatable(
                    "msg.useless_stretcher.staff_summon_failed"), false);
        }
    }

    private static BlockPos findSpawnPos(ServerPlayer player, int index) {
        // Anchor the formation in the direction the player is looking. The old
        // world-axis ring could place every candidate behind the player.
        Vec3 view = player.getLookAngle();
        Vec3 look = new Vec3(view.x, 0.0D, view.z);
        if (look.lengthSqr() < 1.0E-6D) look = new Vec3(0.0D, 0.0D, 1.0D);
        look = look.normalize();

        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = look.cross(up);
        if (right.lengthSqr() < 1.0E-6D) right = new Vec3(1.0D, 0.0D, 0.0D);
        right = right.normalize();

        int layer = index / 8;
        int slot = index % 8;
        double forwardDistance = 2.0D + layer * 1.25D;
        double lateralDistance = switch (slot) {
            case 0, 4 -> 0.0D;
            case 1, 7 -> 0.9D;
            case 2, 6 -> 1.8D;
            default -> 2.7D;
        };
        if (slot >= 4) lateralDistance = -lateralDistance;

        Vec3 origin = player.position().add(0.0D, 0.1D, 0.0D);
        Vec3 target = origin.add(look.scale(forwardDistance))
                .add(right.scale(lateralDistance));
        BlockPos candidate = BlockPos.containing(target);
        if (player.level().getBlockState(candidate).isAir()
                && player.level().getBlockState(candidate.above()).isAir()) return candidate;

        // Keep the fallback in front of the player as well, while avoiding a
        // second broad search that would make a large summon request expensive.
        BlockPos fallback = BlockPos.containing(origin.add(look.scale(1.5D)));
        if (player.level().getBlockState(fallback).isAir()
                && player.level().getBlockState(fallback.above()).isAir()) return fallback;
        return null;
    }
}
