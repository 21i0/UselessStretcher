package com.sorrowmist.useless.stretcher.regression;

import appeng.api.stacks.AEItemKey;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.MoldMatcher;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.item.StaffMiningContext;
import com.sorrowmist.useless.stretcher.content.item.WondrousStaffItem;
import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData;
import com.sorrowmist.useless.stretcher.content.mold.MyriadPatternStore;
import com.sorrowmist.useless.stretcher.content.mold.MoldMatch;
import com.sorrowmist.useless.stretcher.content.blockentity.OmniversalMyriadBlockEntity;
import com.sorrowmist.useless.stretcher.content.acceleration.AccelerationExecutionBudget;
import com.sorrowmist.useless.stretcher.content.entity.TimeFlowEntity;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.mold.PatternFetcher;
import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldStore;
import com.sorrowmist.useless.stretcher.content.mold.MoldRecipeIndex;
import com.sorrowmist.useless.stretcher.event.MyriadWorkQueue;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.core.component.UComponents;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import com.sorrowmist.useless.stretcher.menu.OmniversalMyriadMenu;
import com.sorrowmist.useless.stretcher.network.MyriadSelectionBatches;
import com.sorrowmist.useless.stretcher.network.Network;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.init.ModBlocks;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.utils.mining.MiningUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ItemAbilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Opt-in tests, compiled only with -PregressionTests. Never included in the delivered jar. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class RegressionChecks {
    private static final ResourceLocation A = ResourceLocation.parse("minecraft:furnace");
    private static final ResourceLocation B = ResourceLocation.parse("minecraft:crafting_table");
    private static OmniversalMyriadBlockEntity queued;
    private static MoldRecipeIndex sessionIndex;
    private static List<AEItemKey> firstFetch;
    private static int ticks;

    @SubscribeEvent
    public static void start(ServerStartedEvent event) {
        if (!Boolean.getBoolean("useless_stretcher.regression")) return;
        try {
            ServerLevel level = event.getServer().overworld();
            storage(level);
            molds(level);
            drops();
            acceleration(level);
            toolCompatibility();
            largeMoldDrop(level);
            indexedRecipes(level);
            legacyDimensions(level);
            guideRecipeRemoved(level);
            BlockPos pos = new BlockPos(0, 100, 0);
            level.setBlockAndUpdate(pos, ModBlocks.OMNIVERSAL_MYRIAD.get().defaultBlockState());
            queued = (OmniversalMyriadBlockEntity) level.getBlockEntity(pos);
            failedRefresh(level, queued);
            queued.requestPatterns(List.of(A, B));
            check(!queued.getFetchProgress().isEmpty(), "fetch must enqueue without completing inline");
            queued.clearPatterns();
            check(queued.getRequestedPatternMolds().isEmpty(), "clear cancels pending fetch");
            queued.requestPatterns(List.of(A, B));
            queued.removePatterns(List.of(B));
            check(!queued.getRequestedPatternMolds().contains(B), "remove cancels only selected group");
            sessionIndex = MoldRecipeIndex.get(level);
            LogUtils.getLogger().info("REGRESSION: synchronous checks passed; awaiting queued fetch");
        } catch (Throwable failure) {
            LogUtils.getLogger().error("REGRESSION FAILED", failure);
            event.getServer().halt(false);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (queued == null) return;
        try {
            if (++ticks > 1200) throw new AssertionError("queued fetch timed out");
            if (!queued.getFetchProgress().isEmpty()) return;
            check(queued.getPatternCount() > 0, "real furnace recipes encoded");
            check(!queued.getPatternMolds().contains(B), "cancelled group not restored by completion");
            int size = queued.getPatternCount();
            var keys = queued.getPatterns().stream().map(AEItemKey::of).toList();
            var level = event.getServer().overworld();
            check(MoldRecipeIndex.get(level) == sessionIndex, "completed fetch retains session index");
            queued.clearPatterns();
            check(queued.getPatternCount() == 0, "clear removes all shared references");
            check(MoldRecipeIndex.get(level) == sessionIndex, "clearing stored patterns retains recipe cache");
            if (firstFetch == null) {
                firstFetch = keys;
                BlockPos pos = queued.getBlockPos();
                queued.requestPatterns(List.of(A));
                level.removeBlock(pos, false);
                check(MoldRecipeIndex.get(level) == sessionIndex, "block removal and cancellation retain recipe cache");
                level.setBlockAndUpdate(pos, ModBlocks.OMNIVERSAL_MYRIAD.get().defaultBlockState());
                queued = (OmniversalMyriadBlockEntity) level.getBlockEntity(pos);
                queued.requestPatterns(List.of(A));
                return;
            }
            check(keys.equals(firstFetch), "replacement block warm fetch returns identical ordered patterns");
            LogUtils.getLogger().info("REGRESSION ALL PASSED: encoded {} actual patterns twice over {} ticks; session cache retained", size, ticks);
            queued = null;
            event.getServer().halt(false);
        } catch (Throwable failure) {
            LogUtils.getLogger().error("REGRESSION FAILED", failure);
            queued = null;
            event.getServer().halt(false);
        }
    }

    private static ItemStack sample(int id) {
        ItemStack stack = new ItemStack(Items.PAPER);
        CompoundTag tag = new CompoundTag();
        tag.putInt("recipe", id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static void storage(ServerLevel level) {
        var store = new MyriadPatternStore();
        UUID id = UUID.randomUUID();
        Set<AEItemKey> first = new LinkedHashSet<>();
        Set<AEItemKey> second = new LinkedHashSet<>();
        for (int i = 0; i < 12000; i++) first.add(AEItemKey.of(sample(i)));
        for (int i = 6000; i < 18000; i++) second.add(AEItemKey.of(sample(i)));
        long started = System.nanoTime();
        store.replace(id, A, first);
        store.replace(id, B, second);
        check(store.count(id) == 18000, "dedup union must be 18000, no 4096 truncation");
        long elapsed = System.nanoTime() - started;
        var saved = store.save(new CompoundTag(), level.registryAccess());
        var entries = saved.getList("entries", 10);
        check(entries.getCompound(0).getList("palette", 10).size() == 18000, "one palette stack per key");
        var loaded = MyriadPatternStore.load(saved, level.registryAccess());
        check(loaded.count(id) == 18000 && loaded.molds(id).equals(Set.of(A, B)), "palette roundtrip and UUID");
        loaded.replace(id, A, Set.of());
        check(loaded.count(id) == 12000, "shared references survive removal of first mold");
        loaded.replace(id, B, Set.of());
        check(loaded.count(id) == 0, "last owner releases shared patterns");

        CompoundTag legacy = new CompoundTag();
        CompoundTag entry = new CompoundTag();
        entry.putString("id", id.toString());
        ListTag groups = new ListTag();
        for (ResourceLocation mold : List.of(A, B)) {
            CompoundTag group = new CompoundTag();
            group.putString("mold", mold.toString());
            ListTag patterns = new ListTag();
            for (int i = 0; i < 5000; i++) patterns.add(sample(i).saveOptional(level.registryAccess()));
            group.put("patterns", patterns);
            groups.add(group);
        }
        entry.put("groups", groups);
        ListTag legacyEntries = new ListTag();
        legacyEntries.add(entry);
        legacy.put("entries", legacyEntries);
        var migrated = MyriadPatternStore.load(legacy, level.registryAccess());
        check(migrated.count(id) == 5000, "legacy >4096 preserved");
        migrated.replace(id, A, Set.of());
        check(migrated.count(id) == 5000, "legacy shared ownership preserved");
        LogUtils.getLogger().info("REGRESSION storage: 24000 memberships / 18000 unique, index updates {} ms", elapsed / 1_000_000.0);
    }

    private static void molds(ServerLevel level) {
        ItemStack wildcard = new ItemStack(ModItems.OMNIVERSAL_MYRIAD.get());
        Ingredient iron = Ingredient.of(Items.IRON_INGOT);
        check(!AlloyFurnaceRecipeCatalog.isKnownMold(level, wildcard), "empty wildcard skipped by catalog");
        check(!AdapterUtils.matchesMold(iron, wildcard), "empty wildcard cannot match");
        var empty = MoldMatcher.prepare(Map.of(0, wildcard));
        check(!empty.matches(List.of(iron)), "empty wildcard cannot satisfy furnace requirement");
        check(empty.matches(null) && empty.matches(List.of()), "null/empty requirements handled");
        var mixed = MoldMatcher.prepare(Map.of(0, wildcard, 1, new ItemStack(Items.IRON_INGOT)));
        check(mixed.matches(List.of(iron)), "ordinary mold remains usable beside empty wildcard");
        MyriadMoldData.writeEnabledMolds(wildcard, Set.of(ResourceLocation.parse("minecraft:iron_ingot")), level);
        var enabled = MoldMatcher.prepare(Map.of(0, wildcard));
        check(enabled.matches(List.of(iron, iron, iron)), "wildcard has unlimited mold semantics");
        check(!empty.matches(List.of(iron)), "prepared snapshot unaffected by later item edit");
        MyriadMoldData.writeEnabledMolds(wildcard, Set.of(), level);
        check(MyriadMoldData.readEnabledMolds(wildcard).isEmpty(), "component cache invalidates on edit");
        check(MoldMatch.matchingIds(iron, Set.of(ResourceLocation.parse("minecraft:iron_ingot"))).size() == 1,
                "indexed ordinary mold match");
    }

    private static void drops() {
        List<ItemStack> input = new ArrayList<>();
        for (int i = 0; i < 12000; i++) {
            ItemStack stack = sample(i % 300);
            stack.setCount(1 + i % 80);
            input.add(stack);
        }
        List<ItemStack> expected = MiningUtils.mergeItemStacks(copy(input));
        StaffMiningContext.run(() -> {
            List<ItemStack> actual = MiningUtils.mergeItemStacks(copy(input));
            check(actual.size() == expected.size(), "drop merger stack count");
            for (int i = 0; i < actual.size(); i++) {
                check(ItemStack.matches(expected.get(i), actual.get(i)), "drop merger order/components/count at " + i);
            }
        });
        check(!StaffMiningContext.active(), "mining context released");
    }

    @SuppressWarnings("unchecked")
    private static void failedRefresh(ServerLevel level, OmniversalMyriadBlockEntity block) throws Exception {
        block.replacePatternKeys(A, Set.of(AEItemKey.of(sample(42))));
        UUID ref = block.getPatternRef();
        PatternFetcher fetch = new PatternFetcher(List.of(A));
        var index = MoldRecipeIndex.get(level);
        while (!index.ready()) index.advance();
        var query = index.query(List.of(A));
        while (!query.prepared()) query.prepareStep();
        while (query.hasNext()) query.next();
        field(PatternFetcher.class, "index").set(fetch, index);
        field(PatternFetcher.class, "query").set(fetch, query);
        ((Set<ResourceLocation>) field(PatternFetcher.class, "failedMolds").get(fetch)).add(A);
        check(fetch.step(level, block), "failed group finishes without committing");
        check(block.getPatternCount() == 1 && ref.equals(block.getPatternRef()),
                "failed refresh preserves existing patterns and UUID");
        block.clearPatterns();
    }

    @SuppressWarnings("unchecked")
    private static void acceleration(ServerLevel level) throws Exception {
        var server = level.getServer();
        AccelerationExecutionBudget.beginTick(server);
        Object slow = new Object();
        Object fast = new Object();
        AccelerationExecutionBudget.prioritize(server, slow, false);
        AccelerationExecutionBudget.prioritize(server, fast, false);
        check(AccelerationExecutionBudget.batchSize(1024) == 4,
                "target execution is limited to small batches");
        check(AccelerationExecutionBudget.take(server, slow, 1024) > 0,
                "first target receives work allowance");
        check(AccelerationExecutionBudget.take(server, fast, 1024) > 0,
                "one target cannot consume all count allowance before another");
        Object ordinary = new Object();
        Object permanent = new Object();
        AccelerationExecutionBudget.prioritize(server, ordinary, true);
        AccelerationExecutionBudget.prioritize(server, permanent, false);
        int ordinaryGrant = AccelerationExecutionBudget.take(server, ordinary, 1024);
        int permanentGrant = AccelerationExecutionBudget.take(server, permanent, 1024);
        check(ordinaryGrant > 0 && ordinaryGrant >= permanentGrant,
                "ordinary mode gets first priority while permanent mode keeps a bounded share");
        AccelerationExecutionBudget.recordWork(server, System.nanoTime() - 10_000_000L);
        check(AccelerationExecutionBudget.take(server, ordinary, 1024) == 0,
                "elapsed budget prevents further grants");
        check(WondrousStaffAcceleration.tickTarget(level, BlockPos.ZERO, 1024) == 0,
                "elapsed budget prevents block callbacks");
        AccelerationExecutionBudget.removeServer(server);
        AccelerationExecutionBudget.beginTick(server);
        check(AccelerationExecutionBudget.take(server, new Object(), 1024) == 1024,
                "server budget cleanup allows a fresh tick");

        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID unloadedId = UUID.randomUUID();
        BlockPos center = new BlockPos(4, 100, 4);
        BlockPos unloaded = new BlockPos(10_000_000, 100, 10_000_000);
        check(!level.hasChunkAt(unloaded), "test location is not loaded");
        level.setBlockAndUpdate(center, Blocks.FURNACE.defaultBlockState());
        CompoundTag root = new CompoundTag();
        ListTag fields = new ListTag();
        fields.add(rangeTag(owner, id, center));
        fields.add(rangeTag(owner, unloadedId, unloaded));
        root.put("fields", fields);
        var load = RangeAccelerationSavedData.class.getDeclaredMethod("load", CompoundTag.class,
                net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        var ranges = (RangeAccelerationSavedData) load.invoke(null, root, level.registryAccess());
        var runtime = (Map<UUID, Object>) field(RangeAccelerationSavedData.class, "runtime").get(ranges);
        runtime.put(unloadedId, new Object());
        ranges.tick(server);
        check(runtime.containsKey(id) && !runtime.containsKey(unloadedId),
                "loaded field owns runtime; unloaded field releases runtime");
        var marker = (TimeFlowEntity) level.getEntity(id);
        check(marker != null, "range creates time-flow marker");
        var unchanged = marker.getAccelerationMarks();
        marker.sync(ranges.getField(id));
        check(marker.getAccelerationMarks() == unchanged, "unchanged marker reuses list cache");
        var marked = ranges.toggleMarks(level, owner, center, false, false, false);
        check(marked.affected() == 1 && marker.getAccelerationMarks().contains(center.asLong()),
                "marker revision publishes changed marks");
        ranges.tick(server);
        check(runtime.containsKey(id), "range runtime rebuilt after mark change");
        check(ranges.setEnabled(server, owner, id, false) && !runtime.containsKey(id)
                        && !marker.isEnabled(), "pause releases runtime and synchronizes marker");
        ranges.tick(server);
        check(!runtime.containsKey(id), "paused field does not rebuild runtime");
        ranges.setEnabled(server, owner, id, true);
        ranges.tick(server);
        check(runtime.containsKey(id), "resuming recreates local runtime");
        check(ranges.reclaim(server, owner, id) != null && !runtime.containsKey(id)
                        && marker.isRemoved(), "reclaim removes marker and runtime");
        check(!level.hasChunkAt(unloaded), "range cleanup never force-loads remote chunks");
        ranges.remove(unloadedId);
        level.removeBlock(center, false);
        AccelerationExecutionBudget.removeServer(server);
        LogUtils.getLogger().info("REGRESSION acceleration: budget, revisions, pause, reclaim and unload passed");
    }

    private static void indexedRecipes(ServerLevel level) {
        check(MyriadWorkQueue.sliceBudget(5_000_000L, 50_000_000L) == 8_000_000L, "healthy fetch budget");
        check(MyriadWorkQueue.sliceBudget(42_000_000L, 50_000_000L) == 2_000_000L, "reduced fetch budget");
        check(MyriadWorkQueue.sliceBudget(60_000_000L, 50_000_000L) == 1_000_000L, "overloaded fetch budget");
        check(MyriadWorkQueue.sliceBudget(0L, 4_000_000L) == 1_000_000L, "tick-rate-aware fetch budget");
        var actual = AlloyFurnaceRecipeCatalog.entries(level);
        var shared = MoldRecipeIndex.get(level);
        while (!shared.ready()) shared.advance();
        check(shared == MoldRecipeIndex.get(level), "one shared index across fetches");
        check(!shared.isFor(new ArrayList<>(actual)), "new catalog identity invalidates index even at same size");
        verifyQuery(shared, actual, Set.of(A));
        verifyQuery(shared, actual, Set.of(A, B));
        verifyQuery(shared, actual, Set.of(ResourceLocation.parse("regression:absent")));
        AlloyFurnaceRecipeCatalog.invalidate();
        check(MoldRecipeIndex.get(level) != shared, "real upstream invalidation replaces cached index");

        var base = actual.stream().filter(entry -> entry.recipe() != null).findFirst().orElseThrow();
        var custom = new HiddenMold();
        ItemStack componentStack = new ItemStack(Items.IRON_INGOT);
        componentStack.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("component mold"));
        var decorated = DataComponentIngredient.of(true, componentStack);
        var fixtures = List.of(
                withMolds(base, 0, List.of(Ingredient.of(Items.FURNACE, Items.CRAFTING_TABLE))),
                withMolds(base, 1, List.of(Ingredient.of(Items.FURNACE), Ingredient.of(Items.FURNACE))),
                withMolds(base, 2, List.of(decorated)),
                withMolds(base, 3, List.of(custom.toVanilla())),
                withMolds(base, 4, List.of()));
        var fixtureIndex = new MoldRecipeIndex(fixtures);
        while (!fixtureIndex.ready()) fixtureIndex.advance();
        var selected = Set.of(A, B, ResourceLocation.parse("minecraft:iron_ingot"));
        verifyQuery(fixtureIndex, fixtures, selected);
        var cancelled = fixtureIndex.query(Set.of(A, B));
        while (!cancelled.prepared()) cancelled.prepareStep();
        cancelled.cancel(B);
        int remaining = 0;
        while (cancelled.hasNext()) {
            var match = cancelled.next();
            check(match.molds().equals(Set.of(A)), "cancelled mold removed from merged recipients");
            remaining++;
        }
        check(remaining == 3 && cancelled.consumedReferences() == cancelled.totalReferences(),
                "cancelled posting lists skipped without scanning their recipes");
        var earlyCancel = fixtureIndex.query(Set.of(A));
        earlyCancel.cancel(A);
        check(consume(earlyCancel) == 0, "cancellation during query preparation");
        custom.tests = 0;
        consume(fixtureIndex.query(selected));
        check(custom.tests == 0, "warm custom predicate results reused across requests");

        List<AlloyFurnaceRecipeCatalog.Entry> synthetic = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            // Separate Ingredient instances expose accidental per-ingredient heap expansion.
            synthetic.add(withMolds(base, i, List.of(Ingredient.of(i % 997 == 0 ? Items.FURNACE : Items.CRAFTING_TABLE))));
        }
        long started = System.nanoTime();
        var largeIndex = new MoldRecipeIndex(synthetic);
        while (!largeIndex.ready()) largeIndex.advance();
        long buildNanos = System.nanoTime() - started;
        verifyQuery(largeIndex, synthetic, Set.of(A));
        long queryStarted = System.nanoTime();
        int matched = 0;
        for (int i = 0; i < 100; i++) matched = consume(largeIndex.query(Set.of(A)));
        long queryNanos = (System.nanoTime() - queryStarted) / 100;
        check(matched == 101, "100k catalog visits only 101 selected recipes");
        check(((Map<?, ?>) readField(largeIndex, "byMold")).size() == 2,
                "100k distinct ordinary ingredients collapse to two machine postings");
        verifyQuery(largeIndex, synthetic, Set.of(A, B));
        LogUtils.getLogger().info("REGRESSION index: 100000 entries -> {} candidates; build {} ms; warm query average {} ms",
                matched, buildNanos / 1_000_000.0, queryNanos / 1_000_000.0);
        var beforeStop = MoldRecipeIndex.get(level);
        MoldRecipeIndex.removeServer(level.getServer());
        check(MoldRecipeIndex.get(level) != beforeStop, "server cleanup drops index cache");
    }

    private static void legacyDimensions(ServerLevel level) {
        var tab = com.sorrowmist.useless.stretcher.init.ModCreativeTabs.MAIN.get();
        tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(
                level.enabledFeatures(), true, level.registryAccess()));
        var hidden = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                ResourceLocation.parse("c:hidden_from_recipe_viewers"));
        for (String name : List.of("quad_chunk", "nine_chunk", "quad_chunk_odd", "nine_chunk_odd")) {
            var id = ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, name + "_dimension_block");
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id);
            check(item != Items.AIR && block != Blocks.AIR, "legacy dimension block registry retained");
            var stack = new ItemStack(item);
            check(stack.is(hidden), "legacy dimension block hidden from recipe viewers");
            check(tab.getDisplayItems().stream().noneMatch(s -> s.is(item)), "legacy dimension block not in creative tab");
            check(level.getRecipeManager().byKey(id).isEmpty(), "retired dimension recipe removed");
            check(ItemStack.parseOptional(level.registryAccess(), (CompoundTag) stack.save(level.registryAccess())).is(item),
                    "legacy dimension item roundtrip");
            var dimension = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, name));
            check(level.getServer().getLevel(dimension) != null, "legacy dimension remains loaded and reachable");
        }
        LogUtils.getLogger().info("REGRESSION dimensions: four legacy IDs/worlds retained, recipes/creative entries removed");
    }

    private static void guideRecipeRemoved(ServerLevel level) {
        var id = ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "guide");
        check(level.getRecipeManager().byKey(id).isEmpty(), "standalone guide recipe is removed");
        LogUtils.getLogger().info("REGRESSION guide: standalone guide recipe absent; pages are merged into AE2 guide");
    }

    private static void toolCompatibility() {
        ItemStack staff = new ItemStack(ModItems.WONDROUS_STAFF.get());
        check(staff.getItem() instanceof WondrousStaffItem, "staff keeps its item identity");
        check(staff.getItem().canPerformAction(staff, ItemAbilities.SHEARS_DIG),
                "shears capability enabled by default");
        check(staff.getItem().canPerformAction(staff, ItemAbilities.FIRESTARTER_LIGHT),
                "flint capability enabled by default");
        staff.set(UComponents.BeefShearsComponent.get(), false);
        staff.set(UComponents.BeefFlintAndSteelComponent.get(), false);
        check(!staff.getItem().canPerformAction(staff, ItemAbilities.SHEARS_DIG),
                "shears capability follows its G-menu switch");
        check(!staff.getItem().canPerformAction(staff, ItemAbilities.FIRESTARTER_LIGHT),
                "flint capability follows its G-menu switch");
        var wrenchTag = net.minecraft.tags.TagKey.create(Registries.ITEM,
                ResourceLocation.parse("c:tools/wrench"));
        var wrenchesTag = net.minecraft.tags.TagKey.create(Registries.ITEM,
                ResourceLocation.parse("c:wrenches"));
        var mekanismConfiguratorTag = net.minecraft.tags.TagKey.create(Registries.ITEM,
                ResourceLocation.parse("mekanism:configurators"));
        staff.set(UComponents.WrenchTagEnabledComponent.get(), false);
        check(!staff.is(wrenchTag), "disabled wrench tag is not advertised");
        check(!staff.is(wrenchesTag), "disabled aggregate wrench tag is not advertised");
        check(!staff.is(mekanismConfiguratorTag), "disabled Mekanism configurator tag is not advertised");
        for (String abilityName : List.of("wrench_dismantle", "wrench_rotate", "wrench_empty", "wrench_configure",
                "wrench_configure_chemicals", "wrench_configure_energy", "wrench_configure_fluids",
                "wrench_configure_heat", "wrench_configure_items")) {
            check(!staff.getItem().canPerformAction(staff, net.neoforged.neoforge.common.ItemAbility.get(abilityName)),
                    "disabled wrench ability is blocked: " + abilityName);
        }
        staff.set(UComponents.WrenchTagEnabledComponent.get(), true);
        check(staff.is(wrenchTag), "enabled wrench tag is advertised");
        check(staff.is(wrenchesTag), "enabled aggregate wrench tag is advertised");
        check(staff.is(mekanismConfiguratorTag), "enabled Mekanism configurator tag is advertised");
        LogUtils.getLogger().info("REGRESSION tools: wrench, shears and flint switches synchronized");
    }

    private static Object readField(Object object, String name) {
        try { return field(object.getClass(), name).get(object); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }

    private static AlloyFurnaceRecipeCatalog.Entry withMolds(AlloyFurnaceRecipeCatalog.Entry template, int ordinal,
                                                             List<Ingredient> molds) {
        var recipe = template.recipe();
        var replacement = new AdvancedAlloyFurnaceRecipe(ResourceLocation.parse("regression:recipe_" + ordinal),
                recipe.inputs(), recipe.inputFluids(), recipe.keyInputs(), recipe.outputs(), recipe.outputFluids(),
                recipe.keyOutputs(), recipe.energy(), recipe.processTime(), recipe.catalyst(), recipe.catalystUses(),
                molds, recipe.mode(), recipe.tier());
        return new AlloyFurnaceRecipeCatalog.Entry(template.identity(), replacement, template.sourceId());
    }

    private static int consume(MoldRecipeIndex.Query query) {
        while (!query.prepared()) query.prepareStep();
        int count = 0;
        while (query.hasNext()) { query.next(); count++; }
        return count;
    }

    private static void verifyQuery(MoldRecipeIndex index, List<AlloyFurnaceRecipeCatalog.Entry> entries,
                                    Set<ResourceLocation> selected) {
        Map<Integer, Set<ResourceLocation>> expected = new java.util.LinkedHashMap<>();
        for (int ordinal = 0; ordinal < entries.size(); ordinal++) {
            var recipe = entries.get(ordinal).recipe();
            if (recipe == null) continue;
            Set<ResourceLocation> matches = new LinkedHashSet<>();
            for (Ingredient mold : recipe.molds()) matches.addAll(MoldMatch.matchingIds(mold, selected));
            if (!matches.isEmpty()) expected.put(ordinal, matches);
        }
        var query = index.query(selected);
        while (!query.prepared()) query.prepareStep();
        var old = expected.entrySet().iterator();
        while (query.hasNext()) {
            var result = query.next();
            check(old.hasNext(), "indexed query cannot add unrelated recipes");
            var entry = old.next();
            check(result.ordinal() == entry.getKey() && result.molds().equals(entry.getValue()),
                    "indexed recipe order and per-mold membership match full scan");
        }
        check(!old.hasNext(), "indexed query cannot omit valid recipes");
        check(query.consumedReferences() == query.totalReferences(), "indexed progress completes");
    }

    private static final class HiddenMold implements ICustomIngredient {
        private int tests;
        public boolean test(ItemStack stack) { tests++; return stack.is(Items.FURNACE); }
        public java.util.stream.Stream<ItemStack> getItems() { return java.util.stream.Stream.of(new ItemStack(Items.PAPER)); }
        public boolean isSimple() { return false; }
        public IngredientType<?> getType() { return null; }
    }

    private static CompoundTag rangeTag(UUID owner, UUID id, BlockPos center) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("owner", owner);
        tag.putString("dimension", "minecraft:overworld");
        tag.putLong("center", center.asLong());
        tag.putBoolean("enabled", true);
        tag.putBoolean("positional_lists", true);
        tag.putInt("speed", 1);
        tag.putInt("size_x", 1);
        tag.putInt("size_y", 1);
        tag.putInt("size_z", 1);
        return tag;
    }

    private static java.lang.reflect.Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void largeMoldDrop(ServerLevel level) throws Exception {
        Set<ResourceLocation> selected = new LinkedHashSet<>();
        for (int i = 0; i < 50_000; i++) {
            selected.add(ResourceLocation.parse("regression:" + "m".repeat(120) + i));
        }
        selected.add(ResourceLocation.parse("minecraft:iron_ingot"));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "MoldRegression"));
        BlockPos pos = new BlockPos(8, 100, 8);
        BlockPos restoredPos = new BlockPos(9, 100, 8);
        var block = ModBlocks.OMNIVERSAL_MYRIAD.get();
        var state = block.defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        var entity = (OmniversalMyriadBlockEntity) level.getBlockEntity(pos);
        var menu = new OmniversalMyriadMenu(0, player.getInventory(), pos);
        var batches = MyriadSelectionBatches.split(selected.stream().map(ResourceLocation::toString).toList());
        check(batches.size() > 1, "large selection is split into bounded packets");
        for (int i = 0; i < batches.size(); i++) {
            String phase = i == 0 ? "begin" : i == batches.size() - 1 ? "end" : "append";
            var payload = new Network.MyriadActionPayload(pos, Network.ACTION_SET_ENABLED, phase, batches.get(i));
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            Network.MyriadActionPayload decoded;
            try {
                Network.MyriadActionPayload.STREAM_CODEC.encode(buffer, payload);
                check(buffer.readableBytes() < 16_384, "selection packet stays below network limit");
                decoded = Network.MyriadActionPayload.STREAM_CODEC.decode(buffer);
            } finally { buffer.release(); }
            Set<ResourceLocation> completed = menu.receiveSelection(decoded.moldId(), decoded.enabledMolds());
            if (i + 1 < batches.size()) check(completed == null, "partial selection not applied");
            else {
                check(selected.equals(completed), "all selection packets reconstruct exact IDs");
                entity.setEnabledMolds(completed);
            }
            var statePacket = new Network.MyriadStatePayload(pos, batches.get(i), batches.get(i), 18000,
                    true, "0", i, batches.size());
            var stateBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            try {
                Network.MyriadStatePayload.STREAM_CODEC.encode(stateBuffer, statePacket);
                check(stateBuffer.readableBytes() < 20_000, "state packet size bounded");
                check(statePacket.equals(Network.MyriadStatePayload.STREAM_CODEC.decode(stateBuffer)),
                        "state packet roundtrip");
            } finally { stateBuffer.release(); }
        }
        check(menu.needsSelections(1) && !menu.needsSelections(1) && menu.needsSelections(2),
                "unchanged selection is not repeatedly transmitted with progress");
        check(menu.receiveSelection(Network.ACTION_FETCH_PATTERNS, "begin", List.of(A.toString())) == null,
                "batch fetch waits until complete");
        check(menu.receiveSelection(Network.ACTION_REMOVE_PATTERNS, "end", List.of(B.toString())) == null,
                "different actions cannot splice pending batches");
        check(menu.receiveSelection(Network.ACTION_FETCH_PATTERNS, "end", List.of(B.toString())).equals(Set.of(A, B)),
                "batch fetch receives full selection atomically");
        entity.replacePatternKeys(A, Set.of(AEItemKey.of(sample(1))));
        UUID patternRef = entity.getPatternRef();
        UUID aeRef = UUID.randomUUID();
        entity.setAeRef(aeRef);
        ItemStack tool = new ItemStack(ModItems.WONDROUS_STAFF.get());
        var directDrops = Block.getDrops(state, level, pos, entity, player, tool);
        check(directDrops.size() == 1, "direct mining API returns exactly one configured block");
        ItemStack drop = directDrops.getFirst();
        check(MyriadMoldData.readEnabledMolds(drop).equals(selected), "large mold set survives drop");
        check(!drop.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains("enabled_molds"),
                "drop never contains inline selection");
        check(MyriadMoldData.readPatternRef(drop).equals(patternRef)
                && MyriadMoldData.readAeRef(drop).equals(aeRef), "other UUID bindings unchanged");
        UUID moldRef = MyriadMoldData.readMoldRef(drop);
        int bytes;
        var itemBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(itemBuffer, drop);
            bytes = itemBuffer.readableBytes();
            check(bytes < 512, "50k molds serialize as a compact item");
            drop = ItemStack.STREAM_CODEC.decode(itemBuffer);
        } finally { itemBuffer.release(); }
        var store = MyriadMoldStore.get(level.getServer());
        var loaded = MyriadMoldStore.load(store.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        check(loaded.resolve(moldRef).equals(selected), "large mold snapshot survives save/load");
        check(store.snapshot(selected).equals(moldRef), "unchanged drops reuse immutable snapshot");
        CompoundTag blockData = entity.saveWithFullMetadata(level.registryAccess());
        check(blockData.hasUUID("MoldRef") && !blockData.contains("EnabledMolds"),
                "chunk and control-pick block data contain only mold reference");
        var reloadedBlock = (OmniversalMyriadBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                pos, state, blockData, level.registryAccess());
        check(reloadedBlock != null, "saved block reloads");
        reloadedBlock.setLevel(level);
        check(reloadedBlock.getEnabledMolds().equals(selected), "block reload resolves all external molds");
        check(AdapterUtils.matchesMold(Ingredient.of(Items.IRON_INGOT), drop), "external mold usable in furnace");
        check(MoldMatcher.prepare(Map.of(0, drop)).matches(List.of(Ingredient.of(Items.IRON_INGOT))),
                "external mold usable in hub");
        level.setBlockAndUpdate(restoredPos, state);
        block.setPlacedBy(level, restoredPos, state, player, drop);
        var restored = (OmniversalMyriadBlockEntity) level.getBlockEntity(restoredPos);
        check(restored.getEnabledMolds().equals(selected) && patternRef.equals(restored.getPatternRef()),
                "replacement restores selections and pattern UUID");
        restored.setEnabledMolds(Set.of(A));
        ItemStack changed = Block.getDrops(state, level, restoredPos, restored, player, tool).getFirst();
        check(MyriadMoldData.readEnabledMolds(changed).equals(Set.of(A))
                && MyriadMoldData.readEnabledMolds(drop).equals(selected), "copied items edit independently");

        var area = new AABB(pos).inflate(2);
        level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);
        block.playerWillDestroy(level, pos, state, player);
        level.removeBlock(pos, false);
        block.playerDestroy(level, player, pos, state, entity, tool);
        var spawned = level.getEntitiesOfClass(ItemEntity.class, area);
        check(spawned.size() == 1 && MyriadMoldData.readEnabledMolds(spawned.getFirst().getItem()).equals(selected),
                "normal mining spawns one complete externalized drop");
        spawned.forEach(ItemEntity::discard);

        ItemStack legacy = new ItemStack(ModItems.OMNIVERSAL_MYRIAD.get());
        ListTag inline = new ListTag();
        selected.forEach(id -> inline.add(net.minecraft.nbt.StringTag.valueOf(id.toString())));
        CustomData.update(DataComponents.CUSTOM_DATA, legacy, tag -> tag.put("enabled_molds", inline));
        player.getInventory().setItem(0, legacy);
        player.inventoryMenu.broadcastChanges();
        check(MyriadMoldData.readMoldRef(legacy) != null
                && MyriadMoldData.readEnabledMolds(legacy).equals(selected), "legacy inventory migrated before menu sync");
        check(!legacy.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains("enabled_molds"),
                "legacy inventory payload no longer inline");
        player.getInventory().clearContent();
        level.removeBlock(restoredPos, false);
        LogUtils.getLogger().info("REGRESSION large molds: {} selections, {} item bytes, {} bounded packets; drop/restore/migration passed",
                selected.size(), bytes, batches.size());
    }

    private static List<ItemStack> copy(List<ItemStack> stacks) {
        return new ArrayList<>(stacks.stream().map(ItemStack::copy).toList());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
