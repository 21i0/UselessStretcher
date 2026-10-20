package com.sorrowmist.useless.stretcher.content.capsule;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/** World-owned room assignments prevent overlapping players and repeated template placement. */
public final class DimensionCapsuleData extends SavedData {
    private static final int GRID_WIDTH = 48;
    private static final int MAX_ROOMS = GRID_WIDTH * GRID_WIDTH;
    private final Map<String, Room> rooms = new HashMap<>();
    private int nextSlot;

    public static DimensionCapsuleData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                tag -> DimensionCapsuleData.load(tag, net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().registryAccess()), DimensionCapsuleData::new,
                "useless_stretcher_capsule_rooms");
    }

    public Room room(String key) {
        Room existing = rooms.get(key);
        if (existing != null) return existing;
        if (nextSlot >= MAX_ROOMS) return null;
        Room room = new Room(nextSlot++, false, false);
        rooms.put(key, room);
        setDirty();
        return room;
    }

    public void markPlaced(String key) {
        Room room = rooms.get(key);
        if (room != null && !room.placed()) {
            rooms.put(key, new Room(room.slot(), true, true));
            setDirty();
        }
    }

    public void markStarted(String key) {
        Room room = rooms.get(key);
        if (room != null && !room.started()) {
            rooms.put(key, new Room(room.slot(), false, true));
            setDirty();
        }
    }

    @Override public CompoundTag save(CompoundTag tag) {
        return save(tag, net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().registryAccess());
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("next_slot", nextSlot);
        ListTag entries = new ListTag();
        rooms.forEach((key, room) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("key", key);
            entry.putInt("slot", room.slot());
            entry.putBoolean("placed", room.placed());
            entry.putBoolean("started", room.started());
            entries.add(entry);
        });
        tag.put("rooms", entries);
        return tag;
    }

    static DimensionCapsuleData load(CompoundTag tag, HolderLookup.Provider registries) {
        DimensionCapsuleData data = new DimensionCapsuleData();
        data.nextSlot = com.sorrowmist.useless.stretcher.compat.NativeMath.clamp(tag.getInt("next_slot"), 0, MAX_ROOMS);
        ListTag entries = tag.getList("rooms", Tag.TAG_COMPOUND);
        java.util.Set<Integer> occupied = new java.util.HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            String key = entry.getString("key");
            int slot = entry.getInt("slot");
            if (key.isEmpty() || slot < 0 || slot >= MAX_ROOMS
                    || data.rooms.containsKey(key) || !occupied.add(slot)) continue;
            data.rooms.put(key, new Room(slot, entry.getBoolean("placed"),
                    entry.getBoolean("started") || entry.getBoolean("placed")));
            data.nextSlot = Math.max(data.nextSlot, slot + 1);
        }
        return data;
    }

    public record Room(int slot, boolean placed, boolean started) {
        public BlockPos origin(int y) {
            return new BlockPos(-12_000 + (slot % GRID_WIDTH) * 512, y,
                    -12_000 + (slot / GRID_WIDTH) * 512);
        }
    }
}
