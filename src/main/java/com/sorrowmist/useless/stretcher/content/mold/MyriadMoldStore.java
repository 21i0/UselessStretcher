package com.sorrowmist.useless.stretcher.content.mold;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Immutable world-wide snapshots: copied items never share mutable mold selections. */
public final class MyriadMoldStore extends SavedData {
    private final Map<UUID, Set<ResourceLocation>> entries = new HashMap<>();
    private final Map<Set<ResourceLocation>, UUID> byContents = new HashMap<>();

    public static MyriadMoldStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(MyriadMoldStore::new, MyriadMoldStore::load), "useless_stretcher_molds");
    }

    public UUID snapshot(Collection<ResourceLocation> molds) {
        Set<ResourceLocation> copy = Set.copyOf(molds);
        if (copy.isEmpty()) return null;
        UUID existing = byContents.get(copy);
        if (existing != null) return existing;
        UUID id = UUID.randomUUID();
        entries.put(id, copy);
        byContents.put(copy, id);
        setDirty();
        return id;
    }

    public Set<ResourceLocation> resolve(UUID id) {
        return entries.getOrDefault(id, Set.of());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        entries.forEach((id, molds) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            ListTag ids = new ListTag();
            molds.forEach(mold -> ids.add(StringTag.valueOf(mold.toString())));
            entry.put("molds", ids);
            list.add(entry);
        });
        tag.put("entries", list);
        return tag;
    }

    public static MyriadMoldStore load(CompoundTag tag, HolderLookup.Provider registries) {
        MyriadMoldStore store = new MyriadMoldStore();
        ListTag entries = tag.getList("entries", 10);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID("id")) continue;
            Set<ResourceLocation> molds = new LinkedHashSet<>();
            ListTag list = entry.getList("molds", 8);
            for (int j = 0; j < list.size(); j++) {
                ResourceLocation mold = ResourceLocation.tryParse(list.getString(j));
                if (mold != null) molds.add(mold);
            }
            UUID id = entry.getUUID("id");
            Set<ResourceLocation> snapshot = Set.copyOf(molds);
            store.entries.put(id, snapshot);
            store.byContents.putIfAbsent(snapshot, id);
        }
        return store;
    }
}
