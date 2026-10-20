package com.sorrowmist.useless.stretcher.content.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Persistent once-only acquisition announcements, keyed by the doll's stable identity. */
public final class TwoIDollAcquisitionData extends SavedData {
    private final Set<UUID> announced = new HashSet<>();

    public static TwoIDollAcquisitionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                tag -> TwoIDollAcquisitionData.load(tag, net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().registryAccess()), TwoIDollAcquisitionData::new,
                "useless_stretcher_two_i_acquisitions");
    }

    public boolean markIfNew(UUID dollId) {
        if (dollId == null || !announced.add(dollId)) return false;
        setDirty();
        return true;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        return save(tag, net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().registryAccess());
    }

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag ids = new ListTag();
        for (UUID id : announced) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            ids.add(entry);
        }
        tag.put("announced", ids);
        return tag;
    }

    private static TwoIDollAcquisitionData load(CompoundTag tag, HolderLookup.Provider registries) {
        TwoIDollAcquisitionData data = new TwoIDollAcquisitionData();
        ListTag ids = tag.getList("announced", Tag.TAG_COMPOUND);
        for (int i = 0; i < ids.size(); i++) {
            CompoundTag entry = ids.getCompound(i);
            if (entry.hasUUID("id")) data.announced.add(entry.getUUID("id"));
        }
        return data;
    }
}
