package com.sorrowmist.useless.stretcher.content.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent collision-free private-area assignments for the shared starfield dimension. */
public final class TwoIStarfieldData extends SavedData {
    private static final String NAME = "useless_stretcher_two_i_starfield";
    private static final int GRID_WIDTH = 1024;
    public static final int AREA_SPACING = 16_384;
    private static final int SLOT_COUNT = GRID_WIDTH * GRID_WIDTH;

    private final Map<UUID, Integer> slots = new HashMap<>();
    private final Set<Integer> occupied = new HashSet<>();
    private final Map<UUID, ReturnPoint> returnPoints = new HashMap<>();
    // Reward progress belongs to the player/save, not a disposable or cloned doll item.
    private final Map<UUID, Integer> rewardDrops = new HashMap<>();
    private final Map<UUID, Long> celebrationEnds = new HashMap<>();
    private final Set<UUID> awaitingEntry = new HashSet<>();

    public static final int MAX_REWARD_DROPS = 8;
    public static final int CELEBRATION_TICKS = 600;

    public static TwoIStarfieldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                tag -> TwoIStarfieldData.load(tag, net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().registryAccess()), TwoIStarfieldData::new, NAME);
    }

    public synchronized int slotFor(UUID player) {
        Integer existing = slots.get(player);
        if (existing != null) return existing;
        int candidate = Math.floorMod(player.hashCode(), SLOT_COUNT);
        int start = candidate;
        while (occupied.contains(candidate)) {
            candidate = (candidate + 1) % SLOT_COUNT;
            if (candidate == start) throw new IllegalStateException("2i starfield area limit reached");
        }
        slots.put(player, candidate);
        occupied.add(candidate);
        setDirty();
        return candidate;
    }

    public synchronized void setReturnPoint(UUID player, Level dimension, Vec3 position, float yaw, float pitch) {
        returnPoints.put(player, new ReturnPoint(dimension.dimension().location(), position.x, position.y, position.z,
                yaw, pitch));
        setDirty();
    }

    public synchronized ReturnPoint returnPoint(UUID player) {
        return returnPoints.get(player);
    }

    public synchronized void beginVisit(UUID player, long gameTime, boolean firstInvitation) {
        // A legacy doll with invitation_used=true has already paid its reward.
        rewardDrops.putIfAbsent(player, firstInvitation ? 0 : MAX_REWARD_DROPS);
        celebrationEnds.remove(player);
        awaitingEntry.add(player);
        setDirty();
    }

    public synchronized boolean awaitsCourtyardEntry(UUID player) {
        return awaitingEntry.contains(player);
    }

    public synchronized boolean beginCelebration(UUID player, long gameTime) {
        if (!awaitingEntry.remove(player)) return false;
        celebrationEnds.put(player, gameTime + CELEBRATION_TICKS);
        setDirty();
        return true;
    }

    public synchronized int rewardsRemaining(UUID player) {
        return MAX_REWARD_DROPS - rewardDrops.getOrDefault(player, MAX_REWARD_DROPS);
    }

    public synchronized void recordRewardDrop(UUID player) {
        rewardDrops.computeIfPresent(player, (id, count) -> Math.min(MAX_REWARD_DROPS, count + 1));
        setDirty();
    }

    public synchronized long celebrationEnd(UUID player) {
        return celebrationEnds.getOrDefault(player, 0L);
    }

    public synchronized void endCelebration(UUID player) {
        if (celebrationEnds.remove(player) != null) setDirty();
        if (awaitingEntry.remove(player)) setDirty();
    }

    public synchronized void clearReturnPoint(UUID player) {
        if (returnPoints.remove(player) != null) setDirty();
        endCelebration(player);
    }

    @Override public CompoundTag save(CompoundTag tag) {
        return save(tag, net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().registryAccess());
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag assignments = new ListTag();
        slots.forEach((player, slot) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            entry.putInt("slot", slot);
            assignments.add(entry);
        });
        tag.put("assignments", assignments);
        ListTag returns = new ListTag();
        returnPoints.forEach((player, point) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            entry.putString("dimension", point.dimension().toString());
            entry.putDouble("x", point.x());
            entry.putDouble("y", point.y());
            entry.putDouble("z", point.z());
            entry.putFloat("yaw", point.yaw());
            entry.putFloat("pitch", point.pitch());
            returns.add(entry);
        });
        tag.put("return_points", returns);
        ListTag rewards = new ListTag();
        rewardDrops.forEach((player, count) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            entry.putInt("drops", count);
            rewards.add(entry);
        });
        tag.put("reward_progress", rewards);
        ListTag celebrations = new ListTag();
        celebrationEnds.forEach((player, end) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            entry.putLong("end", end);
            celebrations.add(entry);
        });
        tag.put("celebrations", celebrations);
        ListTag waiting = new ListTag();
        awaitingEntry.forEach(player -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            waiting.add(entry);
        });
        tag.put("awaiting_entry", waiting);
        return tag;
    }

    static TwoIStarfieldData load(CompoundTag tag, HolderLookup.Provider registries) {
        TwoIStarfieldData data = new TwoIStarfieldData();
        ListTag assignments = tag.getList("assignments", Tag.TAG_COMPOUND);
        for (int i = 0; i < assignments.size(); i++) {
            CompoundTag entry = assignments.getCompound(i);
            if (!entry.hasUUID("player")) continue;
            int slot = entry.getInt("slot");
            if (slot < 0 || slot >= SLOT_COUNT || data.occupied.contains(slot)) continue;
            data.slots.put(entry.getUUID("player"), slot);
            data.occupied.add(slot);
        }
        ListTag returns = tag.getList("return_points", Tag.TAG_COMPOUND);
        for (int i = 0; i < returns.size(); i++) {
            CompoundTag entry = returns.getCompound(i);
            if (!entry.hasUUID("player") || !entry.contains("dimension", Tag.TAG_STRING)) continue;
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
            if (dimension == null) continue;
            ReturnPoint point = new ReturnPoint(dimension,
                    entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z"),
                    entry.getFloat("yaw"), entry.getFloat("pitch"));
            if (!Double.isFinite(point.x()) || !Double.isFinite(point.y()) || !Double.isFinite(point.z())
                    || !Float.isFinite(point.yaw()) || !Float.isFinite(point.pitch())) continue;
            data.returnPoints.put(entry.getUUID("player"), point);
        }
        ListTag rewards = tag.getList("reward_progress", Tag.TAG_COMPOUND);
        for (int i = 0; i < rewards.size(); i++) {
            CompoundTag entry = rewards.getCompound(i);
            if (entry.hasUUID("player")) data.rewardDrops.put(entry.getUUID("player"),
                    com.sorrowmist.useless.stretcher.compat.NativeMath.clamp(entry.getInt("drops"), 0, MAX_REWARD_DROPS));
        }
        ListTag celebrations = tag.getList("celebrations", Tag.TAG_COMPOUND);
        for (int i = 0; i < celebrations.size(); i++) {
            CompoundTag entry = celebrations.getCompound(i);
            if (entry.hasUUID("player") && entry.getLong("end") > 0) {
                data.celebrationEnds.put(entry.getUUID("player"), entry.getLong("end"));
            }
        }
        ListTag waiting = tag.getList("awaiting_entry", Tag.TAG_COMPOUND);
        for (int i = 0; i < waiting.size(); i++) {
            CompoundTag entry = waiting.getCompound(i);
            if (entry.hasUUID("player")) data.awaitingEntry.add(entry.getUUID("player"));
        }
        return data;
    }

    public record ReturnPoint(ResourceLocation dimension, double x, double y, double z, float yaw, float pitch) {
    }
}
