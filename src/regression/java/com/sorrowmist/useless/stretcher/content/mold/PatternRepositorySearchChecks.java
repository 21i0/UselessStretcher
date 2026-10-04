package com.sorrowmist.useless.stretcher.content.mold;

import appeng.api.crafting.PatternDetailsHelper;
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
            store.removeKeys(ref, List.of(targetKey));
            check(store.searchIndex(ref) != index, "deletions invalidate the search cache");

            var packet = new Network.PatternPageRequestPayload(BlockPos.ZERO, 2, true, false,
                    42, "\u6876", itemMask.toByteArray(), fluidMask.toByteArray());
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), event.getServer().registryAccess());
            try {
                Network.PatternPageRequestPayload.STREAM_CODEC.encode(buffer, packet);
                var decoded = Network.PatternPageRequestPayload.STREAM_CODEC.decode(buffer);
                check(decoded.requestId() == 42 && decoded.page() == 2 && decoded.query().equals(packet.query()),
                        "search packets preserve query sequence, page and localized text");
                check(Arrays.equals(decoded.matchingItems(), packet.matchingItems())
                        && Arrays.equals(decoded.matchingFluids(), packet.matchingFluids()), "registry masks round-trip");
            } finally { buffer.release(); }
            LogUtils.getLogger().info("REGRESSION: full-library pattern search, metadata caching and packet checks passed");
        } catch (Throwable failure) {
            LogUtils.getLogger().error("REGRESSION FAILED: pattern repository search", failure);
            event.getServer().halt(false);
        }
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
