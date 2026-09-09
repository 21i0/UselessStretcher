package com.sorrowmist.useless.stretcher.content.ae;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * External, server-side storage for AE node bindings. Items and blocks only carry a small
 * {@link UUID} reference, keeping their NBT minimal (same pattern as the omniversal pattern store).
 */
public final class AeBindingStore extends SavedData {
    private static final String NAME = "useless_stretcher_ae_bindings";

    private final Map<UUID, BlockPos> bindings = new HashMap<>();

    public static AeBindingStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AeBindingStore::new, AeBindingStore::load), NAME);
    }

    public BlockPos get(UUID id) {
        return id == null ? null : bindings.get(id);
    }

    public UUID put(BlockPos pos) {
        UUID id = UUID.randomUUID();
        bindings.put(id, pos.immutable());
        setDirty();
        return id;
    }

    public void remove(UUID id) {
        if (id != null && bindings.remove(id) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, BlockPos> entry : bindings.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("id", entry.getKey().toString());
            entryTag.putLong("pos", entry.getValue().asLong());
            list.add(entryTag);
        }
        tag.put("bindings", list);
        return tag;
    }

    public static AeBindingStore load(CompoundTag tag, HolderLookup.Provider registries) {
        AeBindingStore store = new AeBindingStore();
        ListTag list = tag.getList("bindings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            try {
                UUID id = UUID.fromString(entryTag.getString("id"));
                BlockPos pos = BlockPos.of(entryTag.getLong("pos"));
                store.bindings.put(id, pos);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return store;
    }
}
