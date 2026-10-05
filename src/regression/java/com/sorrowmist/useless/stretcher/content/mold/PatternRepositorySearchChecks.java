package com.sorrowmist.useless.stretcher.content.mold;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.network.Network;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class PatternRepositorySearchChecks {
    @SubscribeEvent
    public static void start(ServerStartedEvent event) {
        if (!Boolean.getBoolean("useless_stretcher.regression")) return;
        try {
            var keys = new LinkedHashSet<AEItemKey>();
            for (int i = 1; i <= 130; i++) keys.add(AEItemKey.of(PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), i)),
                    List.of(new GenericStack(AEItemKey.of(Items.STONE), i)))));
            var target = PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEFluidKey.of(Fluids.WATER), 1000)),
                    List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)));
            target.set(UComponents.OMNIVERSAL_PATTERN_DATA.get(), new OmniversalPatternData(
                    OmniversalPatternData.CURRENT_VERSION, ResourceLocation.parse("regression:remote_match"),
                    "search-fixture", "regression", true, Optional.of(AEItemKey.of(Items.BUCKET)),
                    List.of(AEItemKey.of(Items.BUCKET)), List.of(), List.of(), List.of(), List.of(), List.of()));
            AEItemKey targetKey = AEItemKey.of(target);
            keys.add(targetKey);
            var store = new MyriadPatternStore();
            UUID ref = UUID.randomUUID();
            var mold = ResourceLocation.parse("minecraft:bucket");
            store.replace(ref, mold, keys);
            var index = store.searchIndex(ref);
            check(index == store.searchIndex(ref), "unchanged libraries share one metadata index");
            while (!index.ready()) index.advance(Long.MAX_VALUE);
            var byId = PatternSearchIndex.Filter.of("diamond", new byte[0], new byte[0]);
            check(run(index, byId).equals(List.of(targetKey)), "search covers patterns beyond the first two pages");
            check(index.cached(byId).equals(List.of(targetKey)), "repeated queries reuse filtered results");
            check(run(index, PatternSearchIndex.Filter.of("regression:remote_match", new byte[0], new byte[0]))
                    .equals(List.of(targetKey)), "stored recipe ids are searchable without recipe resolution");
            BitSet itemMask = new BitSet();
            itemMask.set(BuiltInRegistries.ITEM.getId(Items.BUCKET));
            check(run(index, PatternSearchIndex.Filter.of("\u6876", itemMask.toByteArray(), new byte[0]))
                    .equals(List.of(targetKey)), "localized mold registry masks search encoded mold metadata");
            BitSet fluidMask = new BitSet();
            fluidMask.set(BuiltInRegistries.FLUID.getId(Fluids.WATER));
            check(run(index, PatternSearchIndex.Filter.of("shui", new byte[0], fluidMask.toByteArray()))
                    .equals(List.of(targetKey)), "localized fluid masks search encoded inputs");
            check(run(index, PatternSearchIndex.Filter.of("@regression diamond", new byte[0], new byte[0]))
                    .equals(List.of(targetKey)), "source filters combine with item names");
            for (var mode : List.of(PatternSearchIndex.Mode.OUTPUT, PatternSearchIndex.Mode.INPUT, PatternSearchIndex.Mode.MOLD)) {
                check(run(index, PatternSearchIndex.Filter.of("diamond", new byte[0], new byte[0], mode)).size()
                        == (mode == PatternSearchIndex.Mode.OUTPUT ? 1 : 0), "output stays in its search mode");
                check(run(index, PatternSearchIndex.Filter.of("shui", new byte[0], fluidMask.toByteArray(), mode)).size()
                        == (mode == PatternSearchIndex.Mode.INPUT ? 1 : 0), "localized fluid input stays in its mode");
                check(run(index, PatternSearchIndex.Filter.of("tong", itemMask.toByteArray(), new byte[0], mode)).size()
                        == (mode == PatternSearchIndex.Mode.MOLD ? 1 : 0), "localized mold stays in its mode");
            }
            var flags = BitSet.valueOf(index.duplicateFlags(List.copyOf(keys)));
            check(flags.cardinality() == 130 && flags.get(0) && flags.get(129) && !flags.get(130),
                    "duplicate marks cover the whole library and ignore primary output quantities");
            var duplicatePage = index.duplicateSelection(List.copyOf(keys));
            var keepMost = BitSet.valueOf(duplicatePage.keepMost());
            var keepLeast = BitSet.valueOf(duplicatePage.keepLeast());
            check(duplicatePage.count() == 129 && keepMost.get(0) && !keepMost.get(129)
                            && !keepLeast.get(0) && keepLeast.get(129) && !keepMost.get(130)
                            && !keepLeast.get(130),
                    "duplicate selection keeps the highest or lowest output and leaves unique components alone");
            var dedupStore = new MyriadPatternStore();
            UUID dedupId = UUID.randomUUID();
            dedupStore.replace(dedupId, mold, keys);
            dedupStore.replace(dedupId, ResourceLocation.parse("minecraft:furnace"),
                    new LinkedHashSet<>(List.of(List.copyOf(keys).get(0), List.copyOf(keys).get(130))));
            var dedupIndex = dedupStore.searchIndex(dedupId);
            while (!dedupIndex.ready()) dedupIndex.advance(Long.MAX_VALUE);
            var dedupRewrite = dedupStore.removeDuplicates(dedupId, dedupIndex, PatternSearchIndex.Keep.MOST);
            check(dedupRewrite != null, "whole-library deduplication accepts a current index");
            while (!dedupRewrite.advance(Long.MAX_VALUE)) { }
            check(dedupStore.count(dedupId) == 2 && dedupStore.molds(dedupId).size() == 2,
                    "whole-library deduplication keeps one winner and all mold memberships");
            rewriteChecks(event.getServer().registryAccess());
            store.removeKeys(ref, List.of(targetKey));
            check(store.searchIndex(ref) != index, "deletions invalidate the search cache");

            var packet = new Network.PatternPageRequestPayload(BlockPos.ZERO, 2, true, false,
                    42, "\u6876", itemMask.toByteArray(), fluidMask.toByteArray(), 128, PatternSearchIndex.Mode.MOLD);
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), event.getServer().registryAccess());
            try {
                Network.PatternPageRequestPayload.STREAM_CODEC.encode(buffer, packet);
                var decoded = Network.PatternPageRequestPayload.STREAM_CODEC.decode(buffer);
                check(decoded.requestId() == 42 && decoded.page() == 2 && decoded.query().equals(packet.query()),
                        "search packets preserve query sequence, page and localized text");
                check(Arrays.equals(decoded.matchingItems(), packet.matchingItems())
                        && Arrays.equals(decoded.matchingFluids(), packet.matchingFluids()), "registry masks round-trip");
                check(decoded.mode() == PatternSearchIndex.Mode.MOLD && decoded.pageSize() == 128,
                        "mode and current window page size round-trip");
                buffer.clear();
                Network.PatternTrimPayload.STREAM_CODEC.encode(buffer, new Network.PatternTrimPayload(packet));
                var trim = Network.PatternTrimPayload.STREAM_CODEC.decode(buffer);
                check(trim.request().requestId() == 42 && trim.request().mode() == PatternSearchIndex.Mode.MOLD,
                        "trim command retains tagged request");
            } finally { buffer.release(); }
            LogUtils.getLogger().info("REGRESSION: full-library pattern search, metadata caching and packet checks passed");
        } catch (Throwable failure) {
            LogUtils.getLogger().error("REGRESSION FAILED: pattern repository search", failure);
            event.getServer().halt(false);
        }
    }

    private static void rewriteChecks(HolderLookup.Provider registries) {
        var originals = new LinkedHashSet<AEItemKey>();
        for (int i = 1; i <= 300; i++) {
            var encoded = PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), i)),
                    List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), i),
                            new GenericStack(AEItemKey.of(Items.STICK), 1)));
            encoded.set(UComponents.OMNIVERSAL_PATTERN_DATA.get(), new OmniversalPatternData(
                    OmniversalPatternData.CURRENT_VERSION, ResourceLocation.parse("regression:trim_" + i),
                    "identity-" + i, "regression", true, Optional.of(AEItemKey.of(Items.BUCKET)),
                    List.of(AEItemKey.of(Items.BUCKET)), List.of(), List.of(), List.of(), List.of(0), List.of(0, 1)));
            var actual = new net.minecraft.world.item.ItemStack(com.sorrowmist.useless.init.ModItems.OMNIVERSAL_PATTERN.get());
            actual.applyComponents(encoded.getComponentsPatch());
            originals.add(AEItemKey.of(actual));
        }
        var store = new MyriadPatternStore();
        var id = UUID.randomUUID();
        var first = ResourceLocation.parse("minecraft:bucket");
        var second = ResourceLocation.parse("minecraft:furnace");
        store.replace(id, first, originals);
        store.replace(id, second, new LinkedHashSet<>(originals.stream().limit(100).toList()));
        store.replace(id, first, originals);
        var before = List.copyOf(store.keys(id));
        var rewrite = store.stripByproducts(id);
        check(!rewrite.advance(Long.MAX_VALUE), "large trim yields after a bounded slice");
        check(List.copyOf(store.keys(id)).equals(before), "incomplete rewrite does not modify live library");
        int slices = 1;
        while (!rewrite.advance(Long.MAX_VALUE)) check(++slices < 50, "bounded rewrite completes");
        check(rewrite.changed() == 300, "one update per unique pattern, not per mold membership");
        var after = List.copyOf(store.keys(id));
        check(after.equals(before.stream().map(PatternOutputs::withoutByproducts).toList()),
                "trim keeps global order even when mold-group order differs");
        for (int i = 0; i < before.size(); i++) {
            var old = before.get(i); var replacement = after.get(i);
            var data = old.get(UComponents.OMNIVERSAL_PATTERN_DATA.get());
            var updated = replacement.get(UComponents.OMNIVERSAL_PATTERN_DATA.get());
            check(replacement.getItem() == com.sorrowmist.useless.init.ModItems.OMNIVERSAL_PATTERN.get(),
                    "trim never converts an omniversal pattern into a processing item");
            check(updated.recipeId().equals(data.recipeId()) && updated.recipeFingerprint().equals(data.recipeFingerprint())
                    && updated.displayMolds().equals(data.displayMolds()) && updated.itemIdInputSlots().equals(List.of(0))
                    && updated.itemIdOutputSlots().equals(List.of(0)), "identity, molds and valid dynamic flags retained");
            check(replacement.get(AEComponents.ENCODED_PROCESSING_PATTERN).sparseOutputs().size() == 1
                    && replacement.get(AEComponents.ENCODED_PROCESSING_PATTERN).sparseInputs()
                    .equals(old.get(AEComponents.ENCODED_PROCESSING_PATTERN).sparseInputs()), "inputs preserved, byproduct removed");
            new com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPatternDetails(
                    new appeng.crafting.pattern.AEProcessingPattern(replacement), updated.itemIdInputSlots(),
                    updated.itemIdOutputSlots(), registries);
        }
        var loaded = MyriadPatternStore.load(store.save(new CompoundTag(), registries), registries);
        check(List.copyOf(loaded.keys(id)).equals(after) && loaded.molds(id).equals(store.molds(id)),
                "save/reload preserves UUID, mold memberships, order and rewritten data");
        var cancelled = loaded.stripByproducts(id);
        cancelled.advance(Long.MAX_VALUE);
        loaded.removeKeys(id, List.of(after.getFirst()));
        check(!cancelled.valid(), "concurrent edits invalidate staged rewrite");
        try { cancelled.advance(Long.MAX_VALUE); throw new AssertionError("stale rewrite committed"); }
        catch (IllegalStateException expected) { }
        check(loaded.count(id) == 299, "cancelled rewrite cannot restore a deleted pattern");
        var sparse = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 2), new GenericStack(AEItemKey.of(Items.STICK), 1),
                        new GenericStack(AEItemKey.of(Items.DIAMOND), 3)));
        check(PatternOutputs.primary(AEItemKey.of(sparse)).amount() == 5, "primary counts match AE condensation");
    }

    private static List<AEItemKey> run(PatternSearchIndex index, PatternSearchIndex.Filter filter) {
        var search = index.search(filter);
        while (!search.advance(Long.MAX_VALUE)) { }
        return search.result();
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
