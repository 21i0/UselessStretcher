package com.sorrowmist.useless.stretcher.content.mold;

import appeng.api.stacks.AEItemKey;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.sorrowmist.useless.stretcher.content.ae.InstalledPatternHost;

/** World-owned pattern libraries. Items keep their existing UUID, never the library contents. */
public final class MyriadPatternStore extends SavedData {
    private static final String NAME = "useless_stretcher_patterns";
    private static final String PRESERVED_STATE = "useless_stretcher_preserved_state";
    private static final String PRESERVED_FORMAT = "useless_stretcher_preserved_format";
    private static final String PRESERVED_ENTRIES = "useless_stretcher_preserved_entries";
    private static final String PRESERVED_STATES = "useless_stretcher_preserved_states";
    private final Map<UUID, Library> entries = new LinkedHashMap<>();
    private final Map<UUID, Set<BlockEntity>> readers = new HashMap<>();
    private CompoundTag opaqueRoot;
    private ListTag preservedEntries = new ListTag();
    private ListTag preservedStates = new ListTag();

    public boolean contains(UUID id) { return opaqueRoot != null || entries.containsKey(id); }

    /** Missing records are valid empty sources; retained but undecodable records are unavailable. */
    public boolean isResolved(UUID id) {
        Library library = entries.get(id);
        return opaqueRoot == null && (library == null || library.unresolved == null);
    }

    private void requireKnownFormat() {
        if (opaqueRoot != null) {
            throw new IllegalStateException("Pattern store has an unsupported or damaged format; original data is preserved");
        }
    }

    private void requireResolved(UUID id, Library library) {
        requireKnownFormat();
        if (library != null && library.unresolved != null) {
            throw new IllegalStateException("Pattern library " + id + " cannot be decoded; original data is preserved");
        }
    }

    /** Capture the current contents without copying a potentially very large palette. */
    public UUID fork(UUID sourceId) {
        if (sourceId == null) return null;
        requireKnownFormat();
        Library library = entries.get(sourceId);
        requireResolved(sourceId, library);
        if (library == null) library = new Library();
        library.shared = true;
        UUID id;
        do { id = UUID.randomUUID(); } while (id.equals(sourceId) || entries.containsKey(id));
        entries.put(id, library);
        setDirty();
        return id;
    }

    private Library writable(UUID id) {
        Library library = entries.get(id);
        requireResolved(id, library);
        if (library == null) {
            library = new Library();
            entries.put(id, library);
        } else if (library.shared) {
            library = new Library(library);
            entries.put(id, library);
        }
        return library;
    }

    /** Immutable, shared per revision; no per-machine copies of a large library. */
    public List<AEItemKey> snapshot(UUID id) {
        Library library = entries.get(id);
        if (library == null) return List.of();
        if (library.snapshot == null) library.snapshot = List.copyOf(library.references.keySet());
        return library.snapshot;
    }

    public void watch(UUID id, BlockEntity reader) {
        if (id == null || !(reader instanceof InstalledPatternHost)) return;
        readers.computeIfAbsent(id, ignored -> Collections.newSetFromMap(new WeakHashMap<>())).add(reader);
    }

    private void changed(UUID id) {
        setDirty();
        Set<BlockEntity> subscribed = readers.get(id);
        if (subscribed == null) return;
        subscribed.removeIf(BlockEntity::isRemoved);
        for (BlockEntity reader : List.copyOf(subscribed)) {
            ((InstalledPatternHost) reader).uselessStretcher$refreshInstalledPatterns();
        }
        if (subscribed.isEmpty()) readers.remove(id);
    }

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

    public PatternSearchIndex searchIndex(UUID id) {
        Library library = entries.get(id);
        if (library == null || library.unresolved != null) return null;
        if (library.searchIndex == null) library.searchIndex = new PatternSearchIndex(library.references.keySet());
        return library.searchIndex;
    }

    public List<ItemStack> flatten(UUID id) {
        return keys(id).stream().map(key -> key.toStack(1)).toList();
    }

    /** Removes selected pattern keys from every mold group in this library. */
    public int removeKeys(UUID id, Collection<AEItemKey> keys) {
        Library library = entries.get(id);
        requireResolved(id, library);
        if (library == null || keys == null || keys.isEmpty()) return 0;
        if (keys.stream().noneMatch(library.references::containsKey)) return 0;
        int removed = writable(id).removeKeys(keys);
        if (removed > 0) changed(id);
        return removed;
    }

    public void replace(UUID id, ResourceLocation mold, Set<AEItemKey> patterns) {
        if (id == null || mold == null) return;
        Library library = writable(id);
        library.replace(mold, patterns);
        changed(id);
    }

    public void clear(UUID id) {
        requireResolved(id, entries.get(id));
        if (id != null && entries.remove(id) != null) changed(id);
    }

    public Rewrite stripByproducts(UUID id) {
        Library source = entries.get(id);
        requireResolved(id, source);
        return source == null ? null : new Rewrite(id, source, PatternOutputs::withoutByproducts, "trim:");
    }

    public Rewrite removeDuplicates(UUID id, PatternSearchIndex index, PatternSearchIndex.Keep keep) {
        Library source = entries.get(id);
        requireResolved(id, source);
        if (source == null || index == null || source.searchIndex != index || !index.ready()) return null;
        return new Rewrite(id, source, key -> index.discard(key, keep) ? null : key, "dedup:");
    }

    /** Build a replacement under the query budget; publish it only if its source is unchanged. */
    public final class Rewrite {
        private final UUID id;
        private final Library source, replacement = new Library();
        private final long revision;
        private final Iterator<Map.Entry<ResourceLocation, Set<AEItemKey>>> groups;
        private final Iterator<AEItemKey> orderedKeys;
        private final Map<AEItemKey, AEItemKey> rewritten = new HashMap<>();
        private final java.util.function.UnaryOperator<AEItemKey> transform;
        private final String progressPrefix;
        private Iterator<AEItemKey> current = Collections.emptyIterator();
        private Set<AEItemKey> target;
        private ResourceLocation currentMold;
        private int changed, scanned;

        private Rewrite(UUID id, Library source, java.util.function.UnaryOperator<AEItemKey> transform, String progressPrefix) {
            this.id = id; this.source = source; revision = source.revision;
            this.transform = transform; this.progressPrefix = progressPrefix;
            groups = source.groups.entrySet().iterator();
            orderedKeys = source.references.keySet().iterator();
        }
        public boolean valid() { return entries.get(id) == source && source.revision == revision; }
        public int changed() { return changed; }
        public String progress() { return progressPrefix + scanned; }
        public boolean advance(long deadline) {
            if (!valid()) throw new IllegalStateException("Pattern library changed during rewrite");
            int steps = 0;
            while (steps++ < 128 && System.nanoTime() < deadline) {
                if (orderedKeys.hasNext()) {
                    AEItemKey original = orderedKeys.next();
                    AEItemKey key = transform.apply(original);
                    if (!original.equals(key)) changed++;
                    rewritten.put(original, key);
                    // Seed the old global order before rebuilding cross-mold memberships.
                    if (key != null) replacement.references.putIfAbsent(key, 0);
                    scanned++;
                    continue;
                }
                if (!current.hasNext()) {
                    if (target != null && target.isEmpty()) replacement.groups.remove(currentMold);
                    if (!groups.hasNext()) {
                        if (changed > 0) { entries.put(id, replacement); MyriadPatternStore.this.changed(id); }
                        return true;
                    }
                    var group = groups.next();
                    currentMold = group.getKey();
                    current = group.getValue().iterator();
                    target = new LinkedHashSet<>();
                    replacement.groups.put(group.getKey(), target);
                    continue;
                }
                AEItemKey original = current.next();
                AEItemKey key = rewritten.get(original);
                if (key != null && target.add(key)) replacement.references.merge(key, 1, Integer::sum);
            }
            return false;
        }
    }

    /** Hash equality includes every component. A hash collision never merges distinct patterns. */
    public static final class Library {
        private final Map<ResourceLocation, Set<AEItemKey>> groups = new LinkedHashMap<>();
        private final Map<AEItemKey, Integer> references = new LinkedHashMap<>();
        private PatternSearchIndex searchIndex;
        private List<AEItemKey> snapshot;
        private long revision;
        private boolean shared;
        private CompoundTag unresolved;
        private int unresolvedFormat;

        public Library() {}

        private Library(CompoundTag original, int format) {
            unresolved = original.copy();
            unresolvedFormat = format;
        }

        private Library(Library source) {
            // Group sets are replaced, never edited in place, so only their index needs copying.
            groups.putAll(source.groups);
            references.putAll(source.references);
            searchIndex = source.searchIndex;
            snapshot = source.snapshot;
            revision = source.revision;
            unresolved = source.unresolved;
            unresolvedFormat = source.unresolvedFormat;
        }

        public void replace(ResourceLocation mold, Set<AEItemKey> patterns) {
            if (unresolved != null) throw new IllegalStateException("Cannot replace an undecodable pattern library");
            revision++;
            searchIndex = null;
            snapshot = null;
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

        private int removeKeys(Collection<AEItemKey> selected) {
            Set<AEItemKey> selectedKeys = new HashSet<>(selected);
            int removed = 0;
            for (var iterator = groups.entrySet().iterator(); iterator.hasNext();) {
                var entry = iterator.next();
                Set<AEItemKey> replacement = null;
                for (AEItemKey key : entry.getValue()) {
                    if (selectedKeys.contains(key)) {
                        if (replacement == null) replacement = new LinkedHashSet<>(entry.getValue());
                        replacement.remove(key);
                        removed++;
                        references.computeIfPresent(key, (ignored, count) -> count <= 1 ? null : count - 1);
                    }
                }
                if (replacement != null) {
                    if (replacement.isEmpty()) iterator.remove();
                    else entry.setValue(replacement);
                }
            }
            if (removed > 0) { searchIndex = null; snapshot = null; revision++; }
            return removed;
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (opaqueRoot != null) return opaqueRoot.copy();
        ListTag list = new ListTag(), states = new ListTag();
        Map<Library, Integer> stateIndices = new IdentityHashMap<>();
        for (var entry : entries.entrySet()) {
            Library library = entry.getValue();
            Integer state = stateIndices.get(library);
            if (state == null) {
                state = states.size();
                stateIndices.put(library, state);
                states.add(saveLibrary(library, registries));
            }
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("id", entry.getKey().toString());
            entryTag.putInt("state", state);
            list.add(entryTag);
        }
        tag.putInt("format", 3);
        tag.put("states", states);
        tag.put("entries", list);
        if (!preservedEntries.isEmpty()) tag.put(PRESERVED_ENTRIES, preservedEntries.copy());
        else tag.remove(PRESERVED_ENTRIES);
        if (!preservedStates.isEmpty()) tag.put(PRESERVED_STATES, preservedStates.copy());
        else tag.remove(PRESERVED_STATES);
        return tag;
    }

    private static CompoundTag saveLibrary(Library library, HolderLookup.Provider registries) {
        CompoundTag state = new CompoundTag();
        if (library.unresolved != null) {
            state.put(PRESERVED_STATE, library.unresolved.copy());
            state.putInt(PRESERVED_FORMAT, library.unresolvedFormat);
            return state;
        }
        // Shared UUIDs write their palette once, while preserving the original global order.
        Map<AEItemKey, Integer> indices = new HashMap<>();
        ListTag palette = new ListTag();
        for (AEItemKey key : library.references.keySet()) {
            indices.put(key, palette.size());
            palette.add(key.toStack(1).saveOptional(registries));
        }
        state.put("palette", palette);
        ListTag groups = new ListTag();
        for (var group : library.groups.entrySet()) {
            CompoundTag groupTag = new CompoundTag();
            groupTag.putString("mold", group.getKey().toString());
            groupTag.putIntArray("indices", group.getValue().stream().mapToInt(indices::get).toArray());
            groups.add(groupTag);
        }
        state.put("groups", groups);
        return state;
    }

    public static MyriadPatternStore load(CompoundTag tag, HolderLookup.Provider registries) {
        MyriadPatternStore store = new MyriadPatternStore();
        int format = tag.getInt("format");
        if (format > 3 || format < 0 || (tag.contains("format") && !tag.contains("format", Tag.TAG_ANY_NUMERIC))
                || (tag.contains("entries") && !compoundList(tag.get("entries")))
                || (format == 3 && (!compoundList(tag.get("entries")) || !compoundList(tag.get("states"))))) {
            store.opaqueRoot = tag.copy();
            return store;
        }
        if (compoundList(tag.get(PRESERVED_ENTRIES))) {
            store.preservedEntries = tag.getList(PRESERVED_ENTRIES, Tag.TAG_COMPOUND).copy();
        }
        if (compoundList(tag.get(PRESERVED_STATES))) {
            store.preservedStates = tag.getList(PRESERVED_STATES, Tag.TAG_COMPOUND).copy();
        }
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        if (format == 3) {
            ListTag stateTags = tag.getList("states", Tag.TAG_COMPOUND);
            List<Library> states = new ArrayList<>(stateTags.size());
            for (int i = 0; i < stateTags.size(); i++) {
                CompoundTag state = stateTags.getCompound(i);
                if (state.contains(PRESERVED_STATE, Tag.TAG_COMPOUND)
                        && state.contains(PRESERVED_FORMAT, Tag.TAG_INT)) {
                    int preservedFormat = state.getInt(PRESERVED_FORMAT);
                    CompoundTag original = state.getCompound(PRESERVED_STATE);
                    states.add(preservedFormat == 2 || preservedFormat == 3
                            ? loadLibrary(original, registries, preservedFormat == 3)
                            : new Library(original, preservedFormat));
                } else {
                    states.add(loadLibrary(state, registries, true));
                }
            }
            int[] owners = new int[states.size()];
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entryTag = list.getCompound(i);
                UUID id = entryId(entryTag);
                if (id == null || store.entries.containsKey(id)) {
                    store.preservedEntries.add(entryTag.copy());
                    continue;
                }
                int state = entryTag.getInt("state");
                if (!entryTag.contains("state", Tag.TAG_INT) || state < 0 || state >= states.size()) {
                    // Keep the UUID occupied so legacy resolution cannot fall back to another dimension.
                    store.entries.put(id, new Library(entryTag, 0));
                    continue;
                }
                Library library = states.get(state);
                store.entries.put(id, library);
                if (++owners[state] > 1) library.shared = true;
            }
            for (int state = 0; state < owners.length; state++) {
                if (owners[state] == 0) store.preservedStates.add(stateTags.getCompound(state).copy());
            }
            return store;
        }
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            UUID id = entryId(entryTag);
            if (id == null || store.entries.containsKey(id)) {
                store.preservedEntries.add(entryTag.copy());
                continue;
            }
            Library library = loadLibrary(entryTag, registries, false);
            store.entries.put(id, library);
        }
        return store;
    }

    private static boolean compoundList(Tag tag) {
        return tag instanceof ListTag list && (list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND);
    }

    private static UUID entryId(CompoundTag tag) {
        try {
            return UUID.fromString(tag.getString("id"));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Library loadLibrary(CompoundTag tag, HolderLookup.Provider registries, boolean strict) {
        try {
            if (!compoundList(tag.get("groups")) || (strict && !compoundList(tag.get("palette")))
                    || (tag.contains("palette") && !compoundList(tag.get("palette")))) {
                return new Library(tag, strict ? 3 : 2);
            }
            List<AEItemKey> palette = new ArrayList<>();
            ListTag paletteTags = tag.getList("palette", Tag.TAG_COMPOUND);
            for (int j = 0; j < paletteTags.size(); j++) {
                palette.add(decodeKey(paletteTags.getCompound(j), registries));
            }
            Library library = new Library();
            ListTag groups = tag.getList("groups", Tag.TAG_COMPOUND);
            for (int j = 0; j < groups.size(); j++) {
                CompoundTag group = groups.getCompound(j);
                ResourceLocation mold = ResourceLocation.tryParse(group.getString("mold"));
                if (mold == null) return new Library(tag, strict ? 3 : 2);
                Set<AEItemKey> patterns = new LinkedHashSet<>();
                if (group.contains("indices", Tag.TAG_INT_ARRAY)) {
                    for (int index : group.getIntArray("indices")) {
                        if (index >= 0 && index < palette.size() && palette.get(index) != null) {
                            patterns.add(palette.get(index));
                        } else return new Library(tag, strict ? 3 : 2);
                    }
                } else {
                    if (strict || !compoundList(group.get("patterns"))) return new Library(tag, strict ? 3 : 2);
                    // Pre-palette saves retain their UUID and all groups, without truncation.
                    ListTag stacks = group.getList("patterns", Tag.TAG_COMPOUND);
                    for (int k = 0; k < stacks.size(); k++) {
                        patterns.add(decodeKey(stacks.getCompound(k), registries));
                    }
                }
                library.replace(mold, patterns);
            }
            if (!palette.isEmpty()) {
                Map<AEItemKey, Integer> ordered = new LinkedHashMap<>();
                for (AEItemKey key : palette) {
                    Integer count = library.references.get(key);
                    if (count != null) ordered.put(key, count);
                }
                ordered.putAll(library.references);
                library.references.clear();
                library.references.putAll(ordered);
            }
            return library;
        } catch (RuntimeException invalid) {
            // Retain exact item/component NBT so restoring the missing codec can recover the whole state.
            return new Library(tag, strict ? 3 : 2);
        }
    }

    private static AEItemKey decodeKey(CompoundTag tag, HolderLookup.Provider registries) {
        ItemStack stack = ItemStack.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, registries), tag)
                .result().orElseThrow(() -> new IllegalArgumentException("Cannot decode pattern item without data loss"));
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) throw new IllegalArgumentException("Cannot decode an empty pattern item");
        return key;
    }
}
