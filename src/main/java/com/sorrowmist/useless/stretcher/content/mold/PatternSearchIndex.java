package com.sorrowmist.useless.stretcher.content.mold;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Cached encoded metadata only: no recipe lookup, decoding, or full-stack serialization. */
public final class PatternSearchIndex {
    private static final java.util.concurrent.atomic.AtomicLong REVISIONS = new java.util.concurrent.atomic.AtomicLong();
    private final long revision = REVISIONS.incrementAndGet();
    private final List<AEItemKey> keys;
    private final List<Metadata> metadata = new ArrayList<>();
    private final Map<AEKey, Integer> primaryCounts = new HashMap<>();
    private final Map<AEKey, Winners> primaryWinners = new HashMap<>();
    private int validOutputs;
    private final Map<Filter, List<AEItemKey>> results = new LinkedHashMap<>(8, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Filter, List<AEItemKey>> eldest) { return size() > 8; }
    };

    public PatternSearchIndex(Collection<AEItemKey> keys) { this.keys = List.copyOf(keys); }
    public boolean ready() { return metadata.size() == keys.size(); }
    public String progress() { return metadata.size() + "/" + keys.size(); }
    public List<AEItemKey> cached(Filter filter) { return results.get(filter); }
    public int size() { return keys.size(); }
    public long revision() { return revision; }
    public enum Keep { MOST, LEAST }

    public record DuplicatePage(long revision, int count, byte[] keepMost, byte[] keepLeast) {
        public static final DuplicatePage EMPTY = new DuplicatePage(0, 0, new byte[0], new byte[0]);
    }

    public boolean discard(AEItemKey key, Keep keep) {
        var primary = PatternOutputs.primary(key);
        var winners = primary == null ? null : primaryWinners.get(primary.what());
        return winners != null && !key.equals(keep == Keep.MOST ? winners.most : winners.least);
    }

    public DuplicatePage duplicateSelection(List<AEItemKey> page) {
        if (!ready()) return DuplicatePage.EMPTY;
        BitSet most = new BitSet(), least = new BitSet();
        for (int i = 0; i < page.size(); i++) {
            if (discard(page.get(i), Keep.MOST)) most.set(i);
            if (discard(page.get(i), Keep.LEAST)) least.set(i);
        }
        return new DuplicatePage(revision, validOutputs - primaryWinners.size(), most.toByteArray(), least.toByteArray());
    }

    private static final class Winners {
        private AEItemKey most, least;
        private long maximum, minimum;
        Winners(AEItemKey key, long amount) { most = least = key; maximum = minimum = amount; }
        void add(AEItemKey key, long amount) {
            // Equal quantities retain the earliest pattern in stable library order.
            if (amount > maximum) { most = key; maximum = amount; }
            if (amount < minimum) { least = key; minimum = amount; }
        }
    }

    public byte[] duplicateFlags(List<AEItemKey> page) {
        BitSet flags = new BitSet(page.size());
        for (int i = 0; i < page.size(); i++) {
            var primary = PatternOutputs.primary(page.get(i));
            if (primary != null && primaryCounts.getOrDefault(primary.what(), 0) > 1) flags.set(i);
        }
        return flags.toByteArray();
    }

    public void advance(long deadline) {
        int steps = 0;
        while (!ready() && steps++ < 128 && System.nanoTime() < deadline) {
            AEItemKey key = keys.get(metadata.size());
            Metadata entry = read(key);
            var primary = PatternOutputs.primary(key);
            if (primary != null) {
                primaryCounts.merge(primary.what(), 1, Integer::sum);
                primaryWinners.computeIfAbsent(primary.what(), ignored -> new Winners(key, primary.amount()))
                        .add(key, primary.amount());
                validOutputs++;
            }
            metadata.add(entry);
        }
    }

    public Search search(Filter filter) { return new Search(filter); }

    public final class Search {
        private final Filter filter;
        private final List<AEItemKey> matches = new ArrayList<>();
        private int cursor;
        private List<AEItemKey> result;
        private Search(Filter filter) { this.filter = filter; }
        public String progress() { return cursor + "/" + keys.size(); }
        public List<AEItemKey> result() { return result; }
        public boolean advance(long deadline) {
            if (!ready()) return false;
            int steps = 0;
            while (cursor < metadata.size() && steps++ < 512 && System.nanoTime() < deadline) {
                if (metadata.get(cursor).matches(filter)) matches.add(keys.get(cursor));
                cursor++;
            }
            if (cursor < metadata.size()) return false;
            result = List.copyOf(matches);
            results.put(filter, result);
            return true;
        }
    }

    public enum Mode {
        OUTPUT, INPUT, MOLD, ALL;
        public Mode next() { return values()[(ordinal() + 1) % 3]; }
    }

    public record Filter(String mod, List<String> terms, BitSet items, BitSet fluids, Mode mode) {
        public static Filter of(String query, byte[] itemMask, byte[] fluidMask) {
            return of(query, itemMask, fluidMask, Mode.ALL);
        }

        public static Filter of(String query, byte[] itemMask, byte[] fluidMask, Mode mode) {
            String normalized = java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFKC)
                    .trim().toLowerCase(Locale.ROOT);
            String mod = "";
            if (normalized.startsWith("@")) {
                int split = normalized.indexOf(' ');
                mod = normalized.substring(1, split < 0 ? normalized.length() : split);
                normalized = split < 0 ? "" : normalized.substring(split + 1).trim();
            }
            return new Filter(mod, normalized.isEmpty() ? List.of() : List.of(normalized.split("\\s+")),
                    BitSet.valueOf(itemMask), BitSet.valueOf(fluidMask), mode);
        }
    }

    private record Metadata(String source, SearchTerms inputs, SearchTerms outputs, SearchTerms molds, SearchTerms all) {
        boolean matches(Filter filter) {
            SearchTerms selected = switch (filter.mode()) {
                case OUTPUT -> outputs;
                case INPUT -> inputs;
                case MOLD -> molds;
                case ALL -> all;
            };
            return (filter.mod().isEmpty() || source.contains(filter.mod())
                    || selected.text.contains(filter.mod())) && selected.matches(filter);
        }
    }

    private static Metadata read(AEItemKey pattern) {
        Terms inputs = new Terms(), outputs = new Terms(), molds = new Terms(), all = new Terms();
        all.key(pattern);
        String source = "";
        var processing = pattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (processing != null) {
            processing.sparseInputs().forEach(inputs::generic);
            processing.sparseOutputs().forEach(outputs::generic);
        }
        var crafting = pattern.get(AEComponents.ENCODED_CRAFTING_PATTERN);
        if (crafting != null) {
            crafting.inputs().forEach(inputs::stack);
            outputs.stack(crafting.result()); all.id(crafting.recipeId());
        }
        var stonecutting = pattern.get(AEComponents.ENCODED_STONECUTTING_PATTERN);
        if (stonecutting != null) {
            inputs.stack(stonecutting.input()); outputs.stack(stonecutting.output()); all.id(stonecutting.recipeId());
        }
        var smithing = pattern.get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN);
        if (smithing != null) {
            inputs.stack(smithing.template()); inputs.stack(smithing.base()); inputs.stack(smithing.addition());
            outputs.stack(smithing.resultItem()); all.id(smithing.recipeId());
        }
        var myriad = pattern.get(UComponents.OMNIVERSAL_PATTERN_DATA.get());
        if (myriad != null) {
            all.id(myriad.recipeId());
            source = (myriad.sourceId() + " " + myriad.recipeId().getNamespace()).toLowerCase(Locale.ROOT);
            all.text.append(' ').append(source);
            myriad.displayMold().ifPresent(molds::key);
            myriad.displayMolds().forEach(molds::key);
        }
        all.merge(inputs); all.merge(outputs); all.merge(molds);
        return new Metadata(source, inputs.freeze(), outputs.freeze(), molds.freeze(), all.freeze());
    }

    private record SearchTerms(String text, int[] items, int[] fluids) {
        private boolean matches(Filter filter) {
            if (filter.terms().stream().allMatch(text::contains)) return true;
            for (int id : items) if (filter.items().get(id)) return true;
            for (int id : fluids) if (filter.fluids().get(id)) return true;
            return false;
        }
    }

    private static final class Terms {
        private final StringBuilder text = new StringBuilder();
        private final Set<Integer> items = new LinkedHashSet<>(), fluids = new LinkedHashSet<>();
        private void merge(Terms terms) {
            text.append(' ').append(terms.text); items.addAll(terms.items); fluids.addAll(terms.fluids);
        }
        private SearchTerms freeze() {
            return new SearchTerms(text.toString().toLowerCase(Locale.ROOT),
                    items.stream().mapToInt(Integer::intValue).toArray(),
                    fluids.stream().mapToInt(Integer::intValue).toArray());
        }
        private void id(ResourceLocation id) {
            if (id != null) text.append(' ').append(id).append(' ').append(id.getPath().replace('_', ' '));
        }
        private void stack(ItemStack stack) { if (stack != null && !stack.isEmpty()) key(AEItemKey.of(stack)); }
        private void generic(GenericStack stack) { if (stack != null) key(stack.what()); }
        private void key(AEKey key) {
            if (key == null) return;
            id(key.getId());
            if (key instanceof AEItemKey item) {
                int id = BuiltInRegistries.ITEM.getId(item.getItem());
                if (id >= 0) items.add(id);
            }
            if (key instanceof AEFluidKey fluid) {
                int id = BuiltInRegistries.FLUID.getId(fluid.getFluid());
                if (id >= 0) fluids.add(id);
            }
        }
    }
}
