package com.sorrowmist.useless.stretcher.content.mold;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * External, server-side storage for fetched omniversal patterns. Items and blocks only carry a
 * small {@link UUID} reference, so inventories never serialize hundreds of encoded patterns.
 */
public final class MyriadPatternStore extends SavedData {
    private static final String NAME = "useless_stretcher_patterns";

    private final Map<UUID, Map<ResourceLocation, List<ItemStack>>> entries = new HashMap<>();

    public static MyriadPatternStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MyriadPatternStore::new, MyriadPatternStore::load), NAME);
    }

    public Map<ResourceLocation, List<ItemStack>> get(UUID id) {
        return id == null ? Map.of() : entries.getOrDefault(id, Map.of());
    }

    public Map<ResourceLocation, List<ItemStack>> getOrCreate(UUID id) {
        return entries.computeIfAbsent(id, k -> new LinkedHashMap<>());
    }

    public void put(UUID id, Map<ResourceLocation, List<ItemStack>> groups) {
        Map<ResourceLocation, List<ItemStack>> copy = new LinkedHashMap<>();
        if (groups != null) {
            for (Map.Entry<ResourceLocation, List<ItemStack>> entry : groups.entrySet()) {
                List<ItemStack> stacks = new ArrayList<>();
                for (ItemStack stack : entry.getValue()) {
                    if (stack != null && !stack.isEmpty()) stacks.add(stack.copy());
                }
                if (!stacks.isEmpty()) copy.put(entry.getKey(), stacks);
            }
        }
        if (copy.isEmpty()) entries.remove(id);
        else entries.put(id, copy);
        setDirty();
    }

    public void remove(UUID id) {
        if (id != null && entries.remove(id) != null) setDirty();
    }

    public void setMold(UUID id, ResourceLocation mold, List<ItemStack> patterns) {
        if (id == null || mold == null) return;
        Map<ResourceLocation, List<ItemStack>> groups = new LinkedHashMap<>(getOrCreate(id));
        if (patterns == null || patterns.isEmpty()) {
            groups.remove(mold);
        } else {
            List<ItemStack> copies = new ArrayList<>();
            for (ItemStack stack : patterns) {
                if (stack != null && !stack.isEmpty()) copies.add(stack.copy());
            }
            if (copies.isEmpty()) groups.remove(mold);
            else groups.put(mold, copies);
        }
        put(id, groups);
    }

    public void clear(UUID id) {
        if (id != null) put(id, Map.of());
    }

    public List<ItemStack> flatten(UUID id) {
        List<ItemStack> result = new ArrayList<>();
        for (List<ItemStack> group : get(id).values()) {
            for (ItemStack stack : group) {
                if (stack == null || stack.isEmpty()) continue;
                boolean duplicate = false;
                for (ItemStack existing : result) {
                    if (ItemStack.isSameItemSameComponents(existing, stack)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) result.add(stack.copy());
            }
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Map<ResourceLocation, List<ItemStack>>> entry : entries.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("id", entry.getKey().toString());

            ListTag groups = new ListTag();
            for (Map.Entry<ResourceLocation, List<ItemStack>> group : entry.getValue().entrySet()) {
                CompoundTag groupTag = new CompoundTag();
                groupTag.putString("mold", group.getKey().toString());
                ListTag stacks = new ListTag();
                for (ItemStack stack : group.getValue()) {
                    if (stack != null && !stack.isEmpty()) stacks.add(stack.saveOptional(registries));
                }
                groupTag.put("patterns", stacks);
                groups.add(groupTag);
            }
            entryTag.put("groups", groups);
            list.add(entryTag);
        }
        tag.put("entries", list);
        return tag;
    }

    public static MyriadPatternStore load(CompoundTag tag, HolderLookup.Provider registries) {
        MyriadPatternStore store = new MyriadPatternStore();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            UUID id;
            try {
                id = UUID.fromString(entryTag.getString("id"));
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            Map<ResourceLocation, List<ItemStack>> groups = new LinkedHashMap<>();
            ListTag groupList = entryTag.getList("groups", Tag.TAG_COMPOUND);
            for (int j = 0; j < groupList.size(); j++) {
                CompoundTag groupTag = groupList.getCompound(j);
                ResourceLocation mold = ResourceLocation.tryParse(groupTag.getString("mold"));
                if (mold == null) continue;

                ListTag stacks = groupTag.getList("patterns", Tag.TAG_COMPOUND);
                List<ItemStack> patterns = new ArrayList<>();
                for (int k = 0; k < stacks.size(); k++) {
                    ItemStack stack = ItemStack.parse(registries, stacks.getCompound(k)).orElse(ItemStack.EMPTY);
                    if (!stack.isEmpty()) patterns.add(stack);
                }
                if (!patterns.isEmpty()) groups.put(mold, patterns);
            }
            if (!groups.isEmpty()) store.entries.put(id, groups);
        }
        return store;
    }
}
