package com.sorrowmist.useless.stretcher.client.search;

import appeng.api.stacks.AEFluidKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;

/** Cached client names become compact registry masks; stored pattern NBT stays on the server. */
public final class RegistrySearchCatalog {
    private static RegistrySearchCatalog cached;
    private final LocalizedSearchIndex index;
    private final Iterator<Item> items = BuiltInRegistries.ITEM.iterator();
    private final Iterator<Fluid> fluids = BuiltInRegistries.FLUID.iterator();
    private final List<Entry> entries = new ArrayList<>();

    private RegistrySearchCatalog(LocalizedSearchIndex index) { this.index = index; }
    public static RegistrySearchCatalog current() {
        var index = LocalizedSearchIndex.current();
        if (cached == null || cached.index != index) cached = new RegistrySearchCatalog(index);
        return cached;
    }
    public boolean ready() { return !items.hasNext() && !fluids.hasNext(); }
    public boolean transliterating() { return index.pending(); }

    public void advance() {
        long deadline = System.nanoTime() + 1_000_000L;
        int count = 0;
        while (!ready() && count++ < 256 && System.nanoTime() < deadline) {
            if (items.hasNext()) {
                Item item = items.next();
                var id = BuiltInRegistries.ITEM.getKey(item);
                String name;
                try { name = new ItemStack(item).getHoverName().getString(); }
                catch (RuntimeException ignored) { name = id.toString(); }
                entries.add(new Entry(BuiltInRegistries.ITEM.getId(item), false,
                        index.document(name, id.toString(), id.getNamespace())));
            } else {
                Fluid fluid = fluids.next();
                var id = BuiltInRegistries.FLUID.getKey(fluid);
                var key = AEFluidKey.of(fluid);
                if (key == null) continue;
                String name;
                try { name = key.getDisplayName().getString(); }
                catch (RuntimeException ignored) { name = id.toString(); }
                entries.add(new Entry(BuiltInRegistries.FLUID.getId(fluid), true,
                        index.document(name, id.toString(), id.getNamespace())));
            }
        }
    }

    public Masks match(String text) {
        var parsed = LocalizedSearchIndex.Query.parse(text);
        // @mod applies to the complete recipe on the server, not only the output item's namespace.
        var query = new LocalizedSearchIndex.Query("", parsed.terms());
        BitSet itemMatches = new BitSet(), fluidMatches = new BitSet();
        if (!query.empty()) for (var entry : entries) {
            if (query.matches(entry.document)) (entry.fluid ? fluidMatches : itemMatches).set(entry.id);
        }
        return new Masks(itemMatches.toByteArray(), fluidMatches.toByteArray());
    }

    public record Masks(byte[] items, byte[] fluids) { }
    private record Entry(int id, boolean fluid, LocalizedSearchIndex.Document document) { }
}
