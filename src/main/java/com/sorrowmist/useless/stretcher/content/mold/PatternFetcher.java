package com.sorrowmist.useless.stretcher.content.mold;

import appeng.api.stacks.AEItemKey;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding;
import com.sorrowmist.useless.stretcher.content.ae.AeMaterialContext;
import com.sorrowmist.useless.stretcher.content.blockentity.OmniversalMyriadBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resumable main-thread job that only encodes recipes returned by the shared mold index. */
public final class PatternFetcher {
    private final Map<ResourceLocation, Set<AEItemKey>> groups = new LinkedHashMap<>();
    private final Set<ResourceLocation> failedMolds = new LinkedHashSet<>();
    private MoldRecipeIndex index;
    private MoldRecipeIndex.Query query;
    private AeMaterialContext materials;
    private boolean materialsReady;
    private int failures;
    private Iterator<ResourceLocation> committing;

    public PatternFetcher(Collection<ResourceLocation> requested) {
        for (ResourceLocation id : requested) groups.put(id, new LinkedHashSet<>());
    }

    public boolean contains(ResourceLocation id) { return groups.containsKey(id); }
    public Set<ResourceLocation> molds() { return Set.copyOf(groups.keySet()); }
    public int failures() { return failures; }

    public void cancel(ResourceLocation id) {
        groups.remove(id);
        if (query != null) query.cancel(id);
        committing = null;
    }

    public String progress() {
        if (index == null) return "0";
        if (!index.ready()) return "index:" + index.progress();
        if (query == null || !query.prepared()) return "select";
        return query.consumedReferences() + "/" + query.totalReferences();
    }

    /** Performs one unit. The queue checks its global wall-clock budget between units. */
    public boolean step(ServerLevel level, OmniversalMyriadBlockEntity block) {
        if (groups.isEmpty()) return true;
        if (index == null) index = MoldRecipeIndex.get(level);
        if (index != MoldRecipeIndex.get(level)) {
            throw new IllegalStateException("Recipes reloaded during pattern fetch; retry the selection");
        }
        index.checkHealthy();
        if (!index.ready()) { index.advance(); return false; }
        if (query == null) query = index.query(groups.keySet());
        if (!query.prepared()) { query.prepareStep(); return false; }
        if (query.hasNext() && !materialsReady) {
            materials = AeMaterialContext.fromBoundNode(level, block.getAeNodePos());
            if (materials == null) materials = AeMaterialContext.fromNearbyGrid(level, block.getBlockPos(), 8);
            materialsReady = true;
            return false;
        }
        if (!query.hasNext()) {
            if (committing == null) committing = List.copyOf(groups.keySet()).iterator();
            if (!committing.hasNext()) return true;
            ResourceLocation id = committing.next();
            // A broken recipe must not erase an existing, usable group during a refresh.
            if (!failedMolds.contains(id)) block.replacePatternKeys(id, groups.get(id));
            return !committing.hasNext();
        }

        var match = query.next();
        var entry = match.entry();
        var recipe = entry.recipe();
        Set<ResourceLocation> matched = match.molds();
        matched.retainAll(groups.keySet());
        if (matched.isEmpty()) return false;

        AeMaterialContext.push(materials);
        try {
            ItemStack processing = OmniversalPatternEncoding.createProcessingPattern(recipe);
            if (processing.isEmpty()) return false;
            ItemStack pattern = OmniversalPatternEncoding.encode(processing, entry, level);
            AEItemKey key = AEItemKey.of(pattern);
            if (key != null) for (ResourceLocation id : matched) groups.get(id).add(key);
        } catch (RuntimeException exception) {
            failures++;
            failedMolds.addAll(matched);
            if (failures <= 5) LogUtils.getLogger().warn("Cannot encode myriad recipe {}", recipe.id(), exception);
        } finally {
            AeMaterialContext.pop();
        }
        return false;
    }
}
