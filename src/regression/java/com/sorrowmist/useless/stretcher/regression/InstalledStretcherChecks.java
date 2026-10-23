package com.sorrowmist.useless.stretcher.regression;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import com.sorrowmist.useless.content.blocks.multiblock.OmniversalAlloyFurnaceStructure;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PassivePatternInputTransaction;
import com.sorrowmist.useless.content.menus.AdvancedAlloyFurnaceMenu;
import com.sorrowmist.useless.content.menus.MePatternAssemblyMenu;
import com.sorrowmist.useless.content.menus.PassiveCraftingHatchMenu;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.ae.InstalledStretcherPatterns;
import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData;
import com.sorrowmist.useless.stretcher.content.mold.MyriadPatternStore;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Actual upstream inventories, formed furnace, powered AE grid, native tasks and saved libraries. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class InstalledStretcherChecks {
    private static final BlockPos CORE = new BlockPos(86, 150, 86), SMALL = new BlockPos(78, 150, 86);
    private static final UUID LIBRARY = UUID.fromString("b6288aef-0812-4c7c-801e-7b05bba3a2a1");
    private static final ResourceLocation GROUP = ResourceLocation.parse("minecraft:furnace");
    private static AdvancedAlloyFurnaceBlockEntity small;
    private static MultiblockAlloyFurnaceCoreBlockEntity core;
    private static MePatternAssemblyBlockEntity assembly;
    private static PassiveCraftingHatchBlockEntity hatch;
    private static List<AEItemKey> patterns;
    private static int ticks, phase;
    private static final ItemStore storage = new ItemStore();
    private static AEItemKey smallOutput, coreOutput;
    private static long expectedSmall, expectedCore;

    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if (!Boolean.getBoolean("useless_stretcher.installed_patterns_only")) return;
        try {
            ServerLevel level = event.getServer().overworld();
            for (int x = 4; x <= 5; x++) for (int z = 5; z <= 5; z++) level.setChunkForced(x, z, true);
            if (level.getBlockEntity(CORE.south(2)) instanceof PassiveCraftingHatchBlockEntity savedHatch
                    && InstalledStretcherPatterns.isStretcher(savedHatch.getPatterns().getStackInSlot(0))) {
                check(InstalledStretcherPatterns.keys(level, savedHatch.getPatterns().getStackInSlot(0), null).size() == 2,
                        "installed item and complete library survived actual process restart");
                check(!savedHatch.saveWithFullMetadata(level.registryAccess()).getList("StretcherPatternCursors", 10).isEmpty(),
                        "installed passive cursor loaded from world NBT");
                LogUtils.getLogger().info("INSTALLED STRETCHER MACHINE RESTART PASSED: existing physical slot, source dimension, library and passive cursor restored before setup");
                savedHatch.cancelAllTasks();
                savedHatch.getPatterns().setStackInSlot(0, ItemStack.EMPTY);
            }
            var blocks = com.sorrowmist.useless.init.ModBlocks.class;
            for (var entry : OmniversalAlloyFurnaceStructure.entries()) {
                var block = switch (entry.part()) {
                    case CORE -> com.sorrowmist.useless.init.ModBlocks.MULTIBLOCK_ALLOY_FURNACE_CORE.get();
                    case AIR -> Blocks.AIR;
                    case CASING -> BuiltInRegistries.BLOCK.get(ResourceLocation.parse("useless_mod:useless_casing"));
                    case COIL -> BuiltInRegistries.BLOCK.stream()
                            .filter(b -> b instanceof com.sorrowmist.useless.content.blocks.multiblock.UselessCoilBlock)
                            .findFirst().orElseThrow();
                };
                // Discover a real casing from the active tag rather than assuming an item id.
                if (entry.part() == OmniversalAlloyFurnaceStructure.Part.CASING)
                    block = BuiltInRegistries.BLOCK.getTag(com.sorrowmist.useless.init.ModTags.OMNIVERSAL_FURNACE_CASINGS)
                            .orElseThrow().iterator().next().value();
                level.setBlockAndUpdate(entry.worldPos(CORE, Direction.NORTH), block.defaultBlockState());
            }
            BlockPos ap = CORE.west(), hp = CORE.east(), pp = CORE.south(2);
            level.setBlockAndUpdate(ap, com.sorrowmist.useless.init.ModBlocks.ME_PATTERN_ASSEMBLY.get().defaultBlockState());
            level.setBlockAndUpdate(hp, com.sorrowmist.useless.init.ModBlocks.OMNIVERSAL_MOLD_HUB.get().defaultBlockState());
            level.setBlockAndUpdate(pp, com.sorrowmist.useless.init.ModBlocks.PASSIVE_CRAFTING_HATCH.get().defaultBlockState());
            level.setBlockAndUpdate(SMALL, com.sorrowmist.useless.init.ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get().defaultBlockState());
            small = (AdvancedAlloyFurnaceBlockEntity) level.getBlockEntity(SMALL);
            core = (MultiblockAlloyFurnaceCoreBlockEntity) level.getBlockEntity(CORE);
            assembly = (MePatternAssemblyBlockEntity) level.getBlockEntity(ap);
            hatch = (PassiveCraftingHatchBlockEntity) level.getBlockEntity(pp);
            // A continuous line of creative cells powers and joins both actual machine nodes.
            for (int x = SMALL.getX(); x <= ap.getX(); x++)
                level.setBlockAndUpdate(new BlockPos(x, 150, 85), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            core.requestStructureValidation();
        } catch (Throwable failure) { fail(event.getServer(), failure); }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("useless_stretcher.installed_patterns_only")) return;
        try {
            ServerLevel level = event.getServer().overworld();
            check(++ticks < 1800, "fixture timed out in phase " + phase);
            if (ticks < 60 || !AlloyFurnaceRecipeCatalog.isReady(level)) return;
            small.getEnergyManager().setEnergyStored(small.getEnergyManager().getMaxEnergyStoredLong());
            core.getEnergyManager().setEnergyStored(core.getEnergyManager().getMaxEnergyStoredLong());
            if (phase == 0) {
                check(small instanceof com.sorrowmist.useless.stretcher.content.ae.InstalledPatternHost
                        && (Object) core instanceof com.sorrowmist.useless.stretcher.content.ae.InstalledPatternHost,
                        "both actual machines received the required integration mixin");
                check(core.isFormed() && hatch.getController() == core && assembly.getController() == core, "real multiblock links both parts");
                check(small.getMainNode().isOnline() && assembly.getMainNode().isOnline(), "both real AE providers online");
                patterns = realPatterns(level);
                small.getItemHandler().setStackInSlot(small.getMoldSlot(), new ItemStack(Items.FURNACE));
                ((com.sorrowmist.useless.content.blockentities.multiblock.OmniversalMoldHubBlockEntity)
                        level.getBlockEntity(CORE.east())).getMolds().setStackInSlot(0, new ItemStack(Items.FURNACE));
                var store = MyriadPatternStore.get(level);
                if (store.contains(LIBRARY)) {
                    check(store.count(LIBRARY) >= 2, "library survived an actual server restart");
                    LogUtils.getLogger().info("INSTALLED STRETCHER REAL RESTART PASSED: saved library resolves before fixture replacement");
                }
                store.replace(LIBRARY, GROUP, new LinkedHashSet<>(patterns));
                ItemStack item = stretcher(level);
                var fake = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "PatternLibraryCheck"));
                fake.setPos(SMALL.getX(), SMALL.getY(), SMALL.getZ());
                var smallMenu = new AdvancedAlloyFurnaceMenu(1, fake.getInventory(), small);
                check(smallMenu.slots.get(20).mayPlace(item), "small furnace actual UI pattern slot accepts stretcher");
                fake.getInventory().setItem(9, item.copy());
                smallMenu.quickMoveStack(fake, 128);
                check(small.getTerminalPatternInventory().getStackInSlot(0).is(ModItems.USELESS_STRETCHER.get()), "shift-click routes to pattern slot, not material input");
                check(small.getTerminalPatternInventory().isItemValid(0, item), "AE terminal insertion accepts stretcher");
                var assemblyMenu = new MePatternAssemblyMenu(2, fake.getInventory(), assembly.getBlockPos());
                check(assemblyMenu.slots.getFirst().mayPlace(item), "assembly actual UI accepts stretcher");
                check(assembly.getPatterns().insertItem(0, item.copy(), false).isEmpty(), "assembly actual handler inserts stretcher");
                var hatchMenu = new PassiveCraftingHatchMenu(3, fake.getInventory(), hatch.getBlockPos());
                check(hatchMenu.slots.getFirst().mayPlace(item), "passive UI accepts stretcher");
                check(hatch.getPatterns().insertItem(0, item.copy(), false).isEmpty(), "passive actual handler inserts stretcher");
                check(!assembly.getPatterns().isItemValid(0, new ItemStack(Items.DIRT)), "unrelated items remain rejected");
                check(!assembly.getPatterns().isItemValid(assembly.getPatterns().getActiveSlots(), item), "recovery-only slots remain insertion blocked");
                var gridStorage = assembly.getMainNode().getGrid().getStorageService();
                gridStorage.addGlobalStorageProvider(mounts -> mounts.mount(storage, 0));
                phase = 1;
                return;
            }
            if (phase == 1 && small.getAvailablePatterns().size() >= 2 && core.getAvailablePatterns().size() >= 2) {
                var grid = assembly.getMainNode().getGrid();
                check(small.getAvailablePatterns().size() == 2 && core.getAvailablePatterns().size() == 2, "one stretcher publishes both native patterns");
                for (var pattern : small.getAvailablePatterns())
                    check(grid.getCraftingService().getCraftingFor(pattern.getPrimaryOutput().what()).contains(pattern), "AE actually indexes small provider");
                for (var pattern : core.getAvailablePatterns())
                    check(grid.getCraftingService().getCraftingFor(pattern.getPrimaryOutput().what()).contains(pattern), "AE actually indexes assembly provider");
                // Duplicate linked stretchers do not publish the same library twice.
                small.getTerminalPatternInventory().setItemDirect(1, stretcher(level));
                phase = 2;
                return;
            }
            if (phase == 2) {
                check(small.getAvailablePatterns().size() == 2, "same library deduplicated across physical slots");
                small.getTerminalPatternInventory().setItemDirect(1, ItemStack.EMPTY);
                var p0 = small.getAvailablePatterns().get(0);
                var p1 = core.getAvailablePatterns().get(1);
                smallOutput = (AEItemKey) p0.getPrimaryOutput().what();
                coreOutput = (AEItemKey) p1.getPrimaryOutput().what();
                seedInputs(p0, 16); seedInputs(p1, 16);
                expectedSmall = storage.amount(smallOutput) + p0.getPrimaryOutput().amount();
                expectedCore = storage.amount(coreOutput) + p1.getPrimaryOutput().amount();
                var a = extract(level, p0);
                var b = extract(level, p1);
                check(a.successful() && b.successful(), "native input transactions supplied actual machine requests");
                check(small.pushPattern(p0, a.inputs()) && core.pushPattern(p1, b.inputs()), "both native machines accept actual decoded library patterns");
                hatch.applySettings(20, 1);
                phase = 3;
                return;
            }
            if (phase == 3 && storage.amount(smallOutput) >= expectedSmall && storage.amount(coreOutput) >= expectedCore) {
                LogUtils.getLogger().info("INSTALLED STRETCHER NATIVE EXECUTION PASSED: small furnace and assembly returned real outputs to AE");
                // Remove first recipe through the shared library; both live providers must drop it.
                MyriadPatternStore.get(level).removeKeys(LIBRARY, List.of(patterns.getFirst()));
                phase = 4; return;
            }
            if (phase == 4 && small.getAvailablePatterns().size() == 1 && core.getAvailablePatterns().size() == 1) {
                check(!core.getAvailablePatterns().getFirst().getDefinition().equals(patterns.getFirst()), "deleted pattern is unpublished");
                MyriadPatternStore.get(level).replace(LIBRARY, GROUP, new LinkedHashSet<>(patterns));
                // Stop ordinary providers while testing autonomous passive cycles.
                small.getTerminalPatternInventory().setItemDirect(0, ItemStack.EMPTY);
                assembly.getPatterns().setStackInSlot(0, ItemStack.EMPTY);
                hatch.cancelAllTasks();
                storage.values.clear();
                for (var pattern : patterns) {
                    var decoded = com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnacePatternResolver.decode(pattern.toStack(1), level);
                    seedInputs(decoded, 16);
                }
                phase = 5; return;
            }
            if (phase == 5 && storage.amount(smallOutput) > 0 && storage.amount(coreOutput) > 0) {
                LogUtils.getLogger().info("INSTALLED STRETCHER PASSIVE EXECUTION PASSED: one physical slot autonomously executes both library recipes");
                CompoundTag saved = hatch.saveWithFullMetadata(level.registryAccess());
                check(!saved.getList("StretcherPatternCursors", 10).isEmpty(), "passive selection cursor persisted with native task save");
                var other = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
                check(other != null && InstalledStretcherPatterns.keys(other, stretcher(level), null).size() == 2, "cross-dimension source resolves without moving the library");
                ItemStack legacy = stretcher(level); net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, legacy, t -> t.remove("pattern_dimension"));
                check(InstalledStretcherPatterns.keys(other, legacy, null).size() == 2, "old UUID-only stretcher resolves its original world library");
                ItemStack missingDimension = stretcher(level);
                net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        missingDimension, t -> t.putString("pattern_dimension", "missing:removed_dimension"));
                check(InstalledStretcherPatterns.keys(level, missingDimension, null).isEmpty(), "removed source dimension cannot rebind a different local library");
                var restored = ItemStack.parse(level.registryAccess(), stretcher(level).save(level.registryAccess())).orElseThrow();
                check(InstalledStretcherPatterns.keys(level, restored, null).size() == 2, "item NBT round trip preserves compact library link");
                check(stretcher(level).save(level.registryAccess()).toString().length() < 1024, "item never embeds the library");
                check(MyriadPatternStore.get(level).snapshot(LIBRARY) == MyriadPatternStore.get(level).snapshot(LIBRARY), "unchanged library shares one immutable snapshot");
                LogUtils.getLogger().info("INSTALLED STRETCHER CHECKS PASSED: three real GUIs/handlers, powered AE publication and requests, passive rotation, live deletion, legacy/cross-dimension and NBT");
                event.getServer().halt(false);
            }
        } catch (Throwable failure) { fail(event.getServer(), failure); }
    }

    private static ItemStack stretcher(ServerLevel level) {
        ItemStack item = new ItemStack(ModItems.USELESS_STRETCHER.get());
        MyriadMoldData.writePatternRef(item, LIBRARY); InstalledStretcherPatterns.bindDimension(item, level);
        return item;
    }
    private static List<AEItemKey> realPatterns(ServerLevel level) {
        List<AEItemKey> result = new ArrayList<>();
        var outputs = new java.util.HashSet<AEItemKey>();
        for (var entry : AlloyFurnaceRecipeCatalog.entries(level)) {
            if (entry.recipe().molds().size() != 1 || !entry.recipe().molds().getFirst().test(new ItemStack(Items.FURNACE))) continue;
            var recipe = entry.recipe();
            if (recipe.inputs().size() != 1 || !recipe.inputFluids().isEmpty() || !recipe.keyInputs().isEmpty() || recipe.outputs().size() != 1) continue;
            ItemStack out = recipe.outputs().getFirst();
            if (!out.is(Items.IRON_INGOT) && !out.is(Items.GOLD_INGOT)) continue;
            if (outputs.contains(AEItemKey.of(out))) continue;
            ItemStack processing = OmniversalPatternEncoding.createProcessingPattern(recipe);
            if (processing.isEmpty()) continue;
            result.add(AEItemKey.of(OmniversalPatternEncoding.encode(processing, entry, level)));
            outputs.add(AEItemKey.of(out));
            if (result.size() == 2) return result;
        }
        throw new AssertionError("Need two real vanilla smelting patterns");
    }
    private static void seedInputs(IPatternDetails pattern, long times) {
        for (var input : pattern.getInputs()) {
            var possible = input.getPossibleInputs()[0];
            storage.values.merge(possible.what(), possible.amount() * input.getMultiplier() * times, Long::sum);
        }
    }
    private static PassivePatternInputTransaction.Result extract(ServerLevel level, IPatternDetails pattern) {
        return PassivePatternInputTransaction.extract(pattern, 1, level, storage, storage::getAvailableStacks,
                IActionSource.empty(), (key, amount) -> { throw new AssertionError("unexpected rollback"); });
    }
    private static void fail(net.minecraft.server.MinecraftServer server, Throwable failure) {
        LogUtils.getLogger().error("INSTALLED STRETCHER CHECKS FAILED", failure); server.halt(false);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class ItemStore implements MEStorage {
        final Map<AEKey, Long> values = new LinkedHashMap<>();
        long amount(AEKey key) { return values.getOrDefault(key, 0L); }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE) values.merge(key, amount, Long::sum); return amount;
        }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long found = Math.min(amount, amount(key));
            if (mode == Actionable.MODULATE) values.put(key, amount(key) - found); return found;
        }
        @Override public void getAvailableStacks(KeyCounter out) { values.forEach(out::add); }
        @Override public net.minecraft.network.chat.Component getDescription() { return net.minecraft.network.chat.Component.literal("Pattern test ME storage"); }
    }
}
