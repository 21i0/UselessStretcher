package com.sorrowmist.useless.stretcher.content.ae;

import com.sorrowmist.useless.core.component.MultiblockPartData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Stores large pattern-assembly inventories on the server instead of synchronizing them on items. */
public final class PatternAssemblyItemStore extends SavedData {
    private static final String NAME = "useless_stretcher_pattern_assemblies";
    private final Map<UUID, StoredEntry> entries = new HashMap<>();

    public static PatternAssemblyItemStore get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(PatternAssemblyItemStore::new, PatternAssemblyItemStore::load), NAME);
    }

    public UUID put(MultiblockPartData data) {
        return release(null, data);
    }

    /** Marks a snapshot as available on an item, reusing its block-owned id when possible. */
    public UUID release(UUID id, MultiblockPartData data) {
        UUID reference = id == null ? UUID.randomUUID() : id;
        entries.put(reference, new StoredEntry(data, true));
        setDirty();
        return reference;
    }

    public MultiblockPartData get(UUID id) {
        StoredEntry entry = id == null ? null : entries.get(id);
        return entry == null ? null : entry.data();
    }

    /**
     * Claims an item snapshot for a placed block. A copied item whose id is already claimed gets
     * an independent id, preventing two assemblies from overwriting each other's future drops.
     */
    public Claim claim(UUID id) {
        StoredEntry entry = id == null ? null : entries.get(id);
        if (entry == null) return null;
        UUID claimedId = id;
        if (entry.available()) {
            entries.put(id, new StoredEntry(entry.data(), false));
        } else {
            claimedId = UUID.randomUUID();
            entries.put(claimedId, new StoredEntry(entry.data(), false));
        }
        setDirty();
        return new Claim(claimedId, entry.data());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        entries.forEach((id, stored) -> {
            MultiblockPartData data = stored.data();
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id.toString());
            entry.putInt("version", data.version());
            entry.put("inventory", data.inventory());
            entry.putInt("interval_ticks", data.intervalTicks());
            entry.putLong("multiplier", data.multiplier());
            entry.putBoolean("available", stored.available());
            list.add(entry);
        });
        tag.put("entries", list);
        return tag;
    }

    private static PatternAssemblyItemStore load(CompoundTag tag, HolderLookup.Provider registries) {
        PatternAssemblyItemStore store = new PatternAssemblyItemStore();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                UUID id = UUID.fromString(entry.getString("id"));
                MultiblockPartData data = new MultiblockPartData(
                        entry.getInt("version"), entry.getCompound("inventory"),
                        entry.getInt("interval_ticks"), entry.getLong("multiplier"));
                store.entries.put(id, new StoredEntry(data, entry.getBoolean("available")));
            } catch (IllegalArgumentException ignored) {
                // Ignore a single damaged reference without preventing the world from loading.
            }
        }
        return store;
    }

    public record Claim(UUID reference, MultiblockPartData data) {
    }

    private record StoredEntry(MultiblockPartData data, boolean available) {
    }
}
