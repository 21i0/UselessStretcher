package com.sorrowmist.useless.stretcher.content.mold;

import appeng.api.stacks.AEItemKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** World-owned pattern libraries. Items keep their existing UUID, never the library contents. */
public final class MyriadPatternStore extends SavedData {
    private static final String NAME = "useless_stretcher_patterns";
    private final Map<UUID, Library> entries = new HashMap<>();

    public static MyriadPatternStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MyriadPatternStore::new, MyriadPatternStore::load), NAME);
    }

    public Set<ResourceLocation> molds(UUID id) {
        Library library = entries.get(id);
        return library == null ? Set.of() : Collections.unmodifiableSet(library.groups.keySet());
    }

    public int count(UUID id) {
        Library library = entries.get(id);
        return library == null ? 0 : library.references.size();
    }

    /** Read-only immutable keys; materialize stacks only for slots that can receive them. */
    public Collection<AEItemKey> keys(UUID id) {
        Library library = entries.get(id);
        return library == null ? List.of() : Collections.unmodifiableSet(library.references.keySet());
    }

    public List<ItemStack> flatten(UUID id) {
        return keys(id).stream().map(key -> key.toStack(1)).toList();
    }

    public void replace(UUID id, ResourceLocation mold, Set<AEItemKey> patterns) {
        if (id == null || mold == null) return;
        Library library = entries.computeIfAbsent(id, ignored -> new Library());
        library.replace(mold, patterns);
        if (library.groups.isEmpty()) entries.remove(id);
        setDirty();
    }

    public void clear(UUID id) {
        if (id != null && entries.remove(id) != null) setDirty();
    }

    /** Hash equality includes every component. A hash collision never merges distinct patterns. */
    public static final class Library {
        private final Map<ResourceLocation, Set<AEItemKey>> groups = new LinkedHashMap<>();
        private final Map<AEItemKey, Integer> references = new LinkedHashMap<>();

        public void replace(ResourceLocation mold, Set<AEItemKey> patterns) {
            Set<AEItemKey> replacement = new LinkedHashSet<>(patterns);
            Set<AEItemKey> old = groups.remove(mold);
            if (old != null) {
                for (AEItemKey key : old) {
                    references.computeIfPresent(key, (ignored, count) -> count == 1 ? null : count - 1);
                }
            }
            if (!replacement.isEmpty()) {
                groups.put(mold, replacement);
                for (AEItemKey key : replacement) references.merge(key, 1, Integer::sum);
            }
        }

        public int size() { return references.size(); }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var entry : entries.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("id", entry.getKey().toString());
            // One stack per distinct pattern; mold groups store compact palette indices.
            Map<AEItemKey, Integer> indices = new HashMap<>();
            ListTag palette = new ListTag();
            for (AEItemKey key : entry.getValue().references.keySet()) {
                indices.put(key, palette.size());
                palette.add(key.toStack(1).saveOptional(registries));
            }
            entryTag.put("palette", palette);
            ListTag groups = new ListTag();
            for (var group : entry.getValue().groups.entrySet()) {
                CompoundTag groupTag = new CompoundTag();
                groupTag.putString("mold", group.getKey().toString());
                groupTag.putIntArray("indices", group.getValue().stream().mapToInt(indices::get).toArray());
                groups.add(groupTag);
            }
            entryTag.put("groups", groups);
            list.add(entryTag);
        }
        tag.putInt("format", 2);
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
            List<AEItemKey> palette = new ArrayList<>();
            ListTag paletteTags = entryTag.getList("palette", Tag.TAG_COMPOUND);
            for (int j = 0; j < paletteTags.size(); j++) {
                palette.add(AEItemKey.of(ItemStack.parse(registries, paletteTags.getCompound(j))
                        .orElse(ItemStack.EMPTY)));
            }
            Library library = new Library();
            ListTag groups = entryTag.getList("groups", Tag.TAG_COMPOUND);
            for (int j = 0; j < groups.size(); j++) {
                CompoundTag group = groups.getCompound(j);
                ResourceLocation mold = ResourceLocation.tryParse(group.getString("mold"));
                if (mold == null) continue;
                Set<AEItemKey> patterns = new LinkedHashSet<>();
                if (group.contains("indices", Tag.TAG_INT_ARRAY)) {
                    for (int index : group.getIntArray("indices")) {
                        if (index >= 0 && index < palette.size() && palette.get(index) != null) {
                            patterns.add(palette.get(index));
                        }
                    }
                } else {
                    // Pre-palette saves retain their UUID and all groups, without truncation.
                    ListTag stacks = group.getList("patterns", Tag.TAG_COMPOUND);
                    for (int k = 0; k < stacks.size(); k++) {
                        AEItemKey key = AEItemKey.of(ItemStack.parse(registries, stacks.getCompound(k))
                                .orElse(ItemStack.EMPTY));
                        if (key != null) patterns.add(key);
                    }
                }
                library.replace(mold, patterns);
            }
            if (library.size() > 0) store.entries.put(id, library);
        }
        return store;
    }
}
