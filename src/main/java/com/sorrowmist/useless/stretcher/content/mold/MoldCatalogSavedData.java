package com.sorrowmist.useless.stretcher.content.mold;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Persisted mold directory shared by every Omniversal Myriad block in a world. */
public final class MoldCatalogSavedData extends SavedData {
    private String fingerprint = "";
    private Map<String, List<ResourceLocation>> moldsBySource = Map.of();

    public static MoldCatalogSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(MoldCatalogSavedData::new, MoldCatalogSavedData::load),
                "useless_stretcher_mold_catalog");
    }

    public boolean matches(String value) {
        return !fingerprint.isEmpty() && fingerprint.equals(value);
    }

    public Map<String, List<ResourceLocation>> moldsBySource() {
        return moldsBySource;
    }

    public boolean hasEntries() {
        return !moldsBySource.isEmpty();
    }

    public void replace(String value, Map<String, List<MoldCatalog.MoldEntry>> catalog) {
        Map<String, List<ResourceLocation>> copy = new TreeMap<>();
        catalog.forEach((source, entries) -> {
            List<ResourceLocation> ids = entries.stream().map(MoldCatalog.MoldEntry::id).distinct().toList();
            if (!ids.isEmpty()) copy.put(source, ids);
        });
        fingerprint = value;
        moldsBySource = Collections.unmodifiableMap(copy);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("fingerprint", fingerprint);
        ListTag sources = new ListTag();
        moldsBySource.forEach((source, ids) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("source", source);
            ListTag molds = new ListTag();
            ids.forEach(id -> molds.add(StringTag.valueOf(id.toString())));
            entry.put("molds", molds);
            sources.add(entry);
        });
        tag.put("sources", sources);
        return tag;
    }

    public static MoldCatalogSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        MoldCatalogSavedData data = new MoldCatalogSavedData();
        data.fingerprint = tag.getString("fingerprint");
        Map<String, List<ResourceLocation>> loaded = new TreeMap<>();
        ListTag sources = tag.getList("sources", 10);
        for (int i = 0; i < sources.size(); i++) {
            CompoundTag sourceTag = sources.getCompound(i);
            String source = sourceTag.getString("source");
            if (source.isBlank()) continue;
            List<ResourceLocation> ids = new ArrayList<>();
            ListTag molds = sourceTag.getList("molds", 8);
            for (int j = 0; j < molds.size(); j++) {
                ResourceLocation id = ResourceLocation.tryParse(molds.getString(j));
                if (id != null) ids.add(id);
            }
            if (!ids.isEmpty()) loaded.put(source, List.copyOf(ids));
        }
        data.moldsBySource = Collections.unmodifiableMap(loaded);
        return data;
    }
}
