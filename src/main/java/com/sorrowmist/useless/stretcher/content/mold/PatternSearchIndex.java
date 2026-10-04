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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Cached encoded metadata only: no recipe lookup, decoding, or full-stack serialization. */
public final class PatternSearchIndex {
    private final List<AEItemKey> keys;
    private final List<Metadata> metadata = new ArrayList<>();
    private final Map<Filter, List<AEItemKey>> results = new LinkedHashMap<>(8, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Filter, List<AEItemKey>> eldest) { return size() > 8; }
    };

    public PatternSearchIndex(Collection<AEItemKey> keys) { this.keys = List.copyOf(keys); }
    public boolean ready() { return metadata.size() == keys.size(); }
    public String progress() { return metadata.size() + "/" + keys.size(); }
    public List<AEItemKey> cached(Filter filter) { return results.get(filter); }
    public int size() { return keys.size(); }

    public void advance(long deadline) {
        int steps = 0;
        while (!ready() && steps++ < 128 && System.nanoTime() < deadline) {
            metadata.add(read(keys.get(metadata.size())));
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

    public record Filter(String mod, List<String> terms, BitSet items, BitSet fluids) {
        public static Filter of(String query, byte[] itemMask, byte[] fluidMask) {
            String normalized = java.text.Normalizer.normalize(query, java.text.Normalizer.Form.NFKC)
                    .trim().toLowerCase(Locale.ROOT);
            String mod = "";
            if (normalized.startsWith("@")) {
                int split = normalized.indexOf(' ');
                mod = normalized.substring(1, split < 0 ? normalized.length() : split);
                normalized = split < 0 ? "" : normalized.substring(split + 1).trim();
            }
            return new Filter(mod, normalized.isEmpty() ? List.of() : List.of(normalized.split("\\s+")),
                    BitSet.valueOf(itemMask), BitSet.valueOf(fluidMask));
        }
    }

    private record Metadata(String text, int[] items, int[] fluids) {
        boolean matches(Filter filter) {
            if (!text.contains(filter.mod())) return false;
            if (filter.terms().stream().allMatch(text::contains)) return true;
            for (int id : items) if (filter.items().get(id)) return true;
            for (int id : fluids) if (filter.fluids().get(id)) return true;
            return false;
        }
    }

    private static Metadata read(AEItemKey pattern) {
        Terms terms = new Terms();
        terms.key(pattern);
        var processing = pattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (processing != null) {
            processing.sparseInputs().forEach(terms::generic);
            processing.sparseOutputs().forEach(terms::generic);
        }
        var crafting = pattern.get(AEComponents.ENCODED_CRAFTING_PATTERN);
        if (crafting != null) {
            crafting.inputs().forEach(terms::stack);
            terms.stack(crafting.result()); terms.id(crafting.recipeId());
        }
        var stonecutting = pattern.get(AEComponents.ENCODED_STONECUTTING_PATTERN);
        if (stonecutting != null) {
            terms.stack(stonecutting.input()); terms.stack(stonecutting.output()); terms.id(stonecutting.recipeId());
        }
        var smithing = pattern.get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN);
        if (smithing != null) {
            terms.stack(smithing.template()); terms.stack(smithing.base()); terms.stack(smithing.addition());
            terms.stack(smithing.resultItem()); terms.id(smithing.recipeId());
        }
        var myriad = pattern.get(UComponents.OMNIVERSAL_PATTERN_DATA.get());
        if (myriad != null) {
            terms.id(myriad.recipeId());
            terms.text.append(' ').append(myriad.sourceId());
            myriad.displayMold().ifPresent(terms::key);
            myriad.displayMolds().forEach(terms::key);
        }
        return new Metadata(terms.text.toString().toLowerCase(Locale.ROOT),
                terms.items.stream().mapToInt(Integer::intValue).toArray(),
                terms.fluids.stream().mapToInt(Integer::intValue).toArray());
    }

    private static final class Terms {
        private final StringBuilder text = new StringBuilder();
        private final Set<Integer> items = new LinkedHashSet<>(), fluids = new LinkedHashSet<>();
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
