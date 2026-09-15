package com.sorrowmist.useless.stretcher.content.leaf;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** World-scoped ownership and delivery state for the one-time leaf reward. */
public final class StaffLeafDropData extends SavedData {
    private static final String NAME = "useless_stretcher_staff_leaf_drops";
    private final Set<UUID> triggeredPlayers = new HashSet<>();
    private final Set<UUID> pendingPlayers = new HashSet<>();

    public static StaffLeafDropData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(StaffLeafDropData::new, StaffLeafDropData::load), NAME);
    }

    public boolean hasTriggered(UUID playerId) {
        return triggeredPlayers.contains(playerId);
    }

    public boolean isPending(UUID playerId) {
        return pendingPlayers.contains(playerId);
    }

    /** Atomically reserves this save's only reward for the player. */
    public boolean beginDelivery(UUID playerId) {
        if (!triggeredPlayers.add(playerId)) return false;
        pendingPlayers.add(playerId);
        setDirty();
        return true;
    }

    public void completeDelivery(UUID playerId) {
        if (pendingPlayers.remove(playerId)) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("triggered_players", saveUuidSet(triggeredPlayers));
        tag.put("pending_players", saveUuidSet(pendingPlayers));
        return tag;
    }

    private static StaffLeafDropData load(CompoundTag tag, HolderLookup.Provider registries) {
        StaffLeafDropData data = new StaffLeafDropData();
        loadUuidSet(tag.getList("triggered_players", Tag.TAG_COMPOUND), data.triggeredPlayers);
        loadUuidSet(tag.getList("pending_players", Tag.TAG_COMPOUND), data.pendingPlayers);

        // A pending entry is also a consumed roll, including saves written by interrupted delivery.
        data.triggeredPlayers.addAll(data.pendingPlayers);
        return data;
    }

    private static ListTag saveUuidSet(Set<UUID> values) {
        ListTag list = new ListTag();
        for (UUID value : values) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", value);
            list.add(entry);
        }
        return list;
    }

    private static void loadUuidSet(ListTag list, Set<UUID> destination) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("player")) continue;
            try {
                destination.add(entry.getUUID("player"));
            } catch (IllegalArgumentException ignored) {
                // Skip one damaged entry without preventing the world from loading.
            }
        }
    }
}
