package com.sorrowmist.useless.stretcher.upstreamcheck;

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
import com.sorrowmist.useless.content.blockentities.multiblock.OmniversalMoldHubBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import com.sorrowmist.useless.content.blocks.multiblock.OmniversalAlloyFurnaceStructure;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnacePatternResolver;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PassivePatternInputTransaction;
import com.sorrowmist.useless.content.menus.AdvancedAlloyFurnaceMenu;
import com.sorrowmist.useless.content.menus.MePatternAssemblyMenu;
import com.sorrowmist.useless.content.menus.PassiveCraftingHatchMenu;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.stretcher.content.ae.InstalledPatternHost;
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
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Backport of upstream's real formed-machine and live AE publication checks. */
final class ForgeInstalledUpstreamChecks {
    private static final BlockPos CORE = new BlockPos(86, 150, 86), SMALL = new BlockPos(78, 150, 86);
    private static final UUID LIBRARY = UUID.fromString("b6288aef-0812-4c7c-801e-7b05bba3a2a1");
    private static final ResourceLocation GROUP = new ResourceLocation("minecraft:furnace");
    private static AdvancedAlloyFurnaceBlockEntity small;
    private static MultiblockAlloyFurnaceCoreBlockEntity core;
    private static MePatternAssemblyBlockEntity assembly;
    private static PassiveCraftingHatchBlockEntity hatch;
    private static List<AEItemKey> patterns;
    private static int ticks, phase;
    private static final ItemStore storage = new ItemStore();
    private static AEItemKey smallOutput, coreOutput;
    private static long expectedSmall, expectedCore;

    static void start(ServerLevel level) {
        if (level.getBlockEntity(CORE.south(2)) instanceof PassiveCraftingHatchBlockEntity saved
                && InstalledStretcherPatterns.isStretcher(saved.getPatterns().getStackInSlot(0))) {
            check(InstalledStretcherPatterns.keys(level, saved.getPatterns().getStackInSlot(0), null).size() == 2,
                    "physical library link restored after actual server restart");
            check(!saved.saveWithFullMetadata().getList("StretcherPatternCursors", 10).isEmpty(),
                    "passive cursor restored after actual server restart");
            saved.cancelAllTasks(); saved.getPatterns().setStackInSlot(0, ItemStack.EMPTY);
            LogUtils.getLogger().info("FORGE INSTALLED RESTART PASSED: world-owned library, installed item and passive cursor restored");
        }
        for (int x = 4; x <= 5; x++) level.setChunkForced(x, 5, true);
        for (var entry : OmniversalAlloyFurnaceStructure.entries()) {
            var block = switch (entry.part()) {
                case CORE -> com.sorrowmist.useless.init.ModBlocks.MULTIBLOCK_ALLOY_FURNACE_CORE.get();
                case AIR -> Blocks.AIR;
                case CASING -> BuiltInRegistries.BLOCK.getTag(com.sorrowmist.useless.init.ModTags.OMNIVERSAL_FURNACE_CASINGS)
                        .orElseThrow().iterator().next().value();
                case COIL -> BuiltInRegistries.BLOCK.stream()
                        .filter(b -> b instanceof com.sorrowmist.useless.content.blocks.multiblock.UselessCoilBlock).findFirst().orElseThrow();
            };
            level.setBlockAndUpdate(entry.worldPos(CORE, Direction.NORTH), block.defaultBlockState());
        }
        level.setBlockAndUpdate(CORE.west(), com.sorrowmist.useless.init.ModBlocks.ME_PATTERN_ASSEMBLY.get().defaultBlockState());
        level.setBlockAndUpdate(CORE.east(), com.sorrowmist.useless.init.ModBlocks.OMNIVERSAL_MOLD_HUB.get().defaultBlockState());
        level.setBlockAndUpdate(CORE.south(2), com.sorrowmist.useless.init.ModBlocks.PASSIVE_CRAFTING_HATCH.get().defaultBlockState());
        level.setBlockAndUpdate(SMALL, com.sorrowmist.useless.init.ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get().defaultBlockState());
        small = (AdvancedAlloyFurnaceBlockEntity) level.getBlockEntity(SMALL);
        core = (MultiblockAlloyFurnaceCoreBlockEntity) level.getBlockEntity(CORE);
        assembly = (MePatternAssemblyBlockEntity) level.getBlockEntity(CORE.west());
        hatch = (PassiveCraftingHatchBlockEntity) level.getBlockEntity(CORE.south(2));
        for (int x = SMALL.getX(); x <= CORE.getX() - 1; x++)
            level.setBlockAndUpdate(new BlockPos(x, 150, 85), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        core.requestStructureValidation();
    }

    static boolean tick(ServerLevel level) {
        check(++ticks < 1800, "installed patterns timed out in phase " + phase);
        if (ticks < 60 || !AlloyFurnaceRecipeCatalog.isReady(level)) return false;
        small.getEnergyManager().setEnergyStored(small.getEnergyManager().getMaxEnergyStoredLong());
        core.getEnergyManager().setEnergyStored(core.getEnergyManager().getMaxEnergyStoredLong());
        if (phase == 0) {
            check(small instanceof InstalledPatternHost && (Object) core instanceof InstalledPatternHost, "required provider mixins applied");
            check(core.isFormed() && hatch.getController() == core && assembly.getController() == core, "native multiblock formed");
            check(small.getMainNode().isOnline() && assembly.getMainNode().isOnline(), "real AE providers online");
            patterns = realPatterns(level);
            small.getItemHandler().setStackInSlot(small.getMoldSlot(), new ItemStack(Items.FURNACE));
            ((OmniversalMoldHubBlockEntity) level.getBlockEntity(CORE.east())).getMolds().setStackInSlot(0, new ItemStack(Items.FURNACE));
            MyriadPatternStore.get(level).replace(LIBRARY, GROUP, new LinkedHashSet<>(patterns));
            var item = stretcher(level);
            var fake = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "InstalledCheck"));
            var menu = new AdvancedAlloyFurnaceMenu(1, fake.getInventory(), small);
            check(menu.slots.get(20).mayPlace(item), "small furnace pattern Slot.mayPlace accepts stretcher");
            fake.getInventory().setItem(9, item.copy());
            int playerSlot = java.util.stream.IntStream.range(0, menu.slots.size()).filter(i ->
                    menu.slots.get(i).container == fake.getInventory() && menu.slots.get(i).getContainerSlot() == 9).findFirst().orElseThrow();
            menu.quickMoveStack(fake, playerSlot);
            check(small.getTerminalPatternInventory().getStackInSlot(0).is(ModItems.USELESS_STRETCHER.get()), "shift-click uses pattern slot route");
            check(small.getTerminalPatternInventory().isItemValid(0, item), "AE terminal accepts stretcher");
            check(new MePatternAssemblyMenu(2, fake.getInventory(), assembly.getBlockPos()).slots.get(0).mayPlace(item), "assembly menu accepts stretcher");
            check(assembly.getPatterns().insertItem(0, item.copy(), false).isEmpty(), "assembly handler accepts stretcher");
            check(new PassiveCraftingHatchMenu(3, fake.getInventory(), hatch.getBlockPos()).slots.get(0).mayPlace(item), "passive menu accepts stretcher");
            check(hatch.getPatterns().insertItem(0, item.copy(), false).isEmpty(), "passive synthetic predicate accepts stretcher");
            check(!assembly.getPatterns().isItemValid(0, new ItemStack(Items.DIRT)), "unrelated items still rejected");
            check(!assembly.getPatterns().isItemValid(assembly.getPatterns().getActiveSlots(), item), "recovery slots still blocked");
            assembly.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(mounts -> mounts.mount(storage, 0));
            phase = 1;
        } else if (phase == 1 && small.getAvailablePatterns().size() >= 2 && core.getAvailablePatterns().size() >= 2) {
            check(small.getAvailablePatterns().size() == 2 && core.getAvailablePatterns().size() == 2, "whole library published by both native machines");
            var grid = assembly.getMainNode().getGrid();
            for (var p : core.getAvailablePatterns())
                check(grid.getCraftingService().getCraftingFor(p.getPrimaryOutput().what()).contains(p), "actual AE crafting service indexes patterns");
            small.getTerminalPatternInventory().setItemDirect(1, stretcher(level));
            phase = 2;
        } else if (phase == 2) {
            check(small.getAvailablePatterns().size() == 2, "repeated library slots deduplicated");
            small.getTerminalPatternInventory().setItemDirect(1, ItemStack.EMPTY);
            var p0 = small.getAvailablePatterns().get(0);
            var p1 = core.getAvailablePatterns().get(1);
            smallOutput = (AEItemKey) p0.getPrimaryOutput().what(); coreOutput = (AEItemKey) p1.getPrimaryOutput().what();
            seed(p0); seed(p1);
            expectedSmall = storage.amount(smallOutput) + p0.getPrimaryOutput().amount();
            expectedCore = storage.amount(coreOutput) + p1.getPrimaryOutput().amount();
            var a = extract(level, p0); var b = extract(level, p1);
            check(a.successful() && b.successful(), "native input transactions resolve library inputs");
            check(small.pushPattern(p0, a.inputs()) && core.pushPattern(p1, b.inputs()), "native machines accept library crafting requests");
            hatch.applySettings(20, 1);
            phase = 3;
        } else if (phase == 3 && storage.amount(smallOutput) >= expectedSmall && storage.amount(coreOutput) >= expectedCore) {
            MyriadPatternStore.get(level).removeKeys(LIBRARY, List.of(patterns.get(0)));
            phase = 4;
        } else if (phase == 4 && small.getAvailablePatterns().size() == 1 && core.getAvailablePatterns().size() == 1) {
            check(!core.getAvailablePatterns().get(0).getDefinition().equals(patterns.get(0)), "live deletion unpublishes removed pattern");
            MyriadPatternStore.get(level).replace(LIBRARY, GROUP, new LinkedHashSet<>(patterns));
            small.getTerminalPatternInventory().setItemDirect(0, ItemStack.EMPTY);
            assembly.getPatterns().setStackInSlot(0, ItemStack.EMPTY);
            hatch.cancelAllTasks(); storage.values.clear();
            for (var key : patterns) seed(AdvancedAlloyFurnacePatternResolver.decode(key.toStack(1), level));
            phase = 5;
        } else if (phase == 5 && storage.amount(smallOutput) > 0 && storage.amount(coreOutput) > 0) {
            check(!hatch.saveWithFullMetadata().getList("StretcherPatternCursors", 10).isEmpty(), "passive selection cursor saved with task NBT");
            var other = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            check(other != null && InstalledStretcherPatterns.keys(other, stretcher(level), null).size() == 2, "cross-dimension library reference");
            var legacy = stretcher(level); legacy.getOrCreateTag().remove("pattern_dimension");
            check(InstalledStretcherPatterns.keys(other, legacy, null).size() == 2, "legacy UUID-only library reference");
            var missing = stretcher(level); missing.getOrCreateTag().putString("pattern_dimension", "missing:removed");
            check(InstalledStretcherPatterns.keys(level, missing, null).isEmpty(), "missing dimension cannot rebind a local library");
            var restored = ItemStack.of(stretcher(level).save(new CompoundTag()));
            check(InstalledStretcherPatterns.keys(level, restored, null).size() == 2 && restored.save(new CompoundTag()).toString().length() < 1024,
                    "compact library link survives item NBT");
            check(MyriadPatternStore.get(level).snapshot(LIBRARY) == MyriadPatternStore.get(level).snapshot(LIBRARY), "unchanged snapshots shared");
            LogUtils.getLogger().info("FORGE INSTALLED PATTERNS PASSED: three native menus/handlers, shift-click, terminal, real AE publication/execution, passive rotation, live deletion, cross-dimension and NBT");
            return true;
        }
        return false;
    }

    private static ItemStack stretcher(ServerLevel level) {
        var stack = new ItemStack(ModItems.USELESS_STRETCHER.get());
        MyriadMoldData.writePatternRef(stack, LIBRARY); InstalledStretcherPatterns.bindDimension(stack, level); return stack;
    }
    private static List<AEItemKey> realPatterns(ServerLevel level) {
        List<AEItemKey> result = new ArrayList<>();
        var outputs = new java.util.HashSet<AEItemKey>();
        for (var entry : AlloyFurnaceRecipeCatalog.entries(level)) {
            var recipe = entry.recipe();
            if (recipe.molds().size() != 1 || !recipe.molds().get(0).test(new ItemStack(Items.FURNACE))
                    || recipe.inputs().size() != 1 || !recipe.inputFluids().isEmpty() || !recipe.keyInputs().isEmpty() || recipe.outputs().size() != 1) continue;
            var out = recipe.outputs().get(0);
            if ((!out.is(Items.IRON_INGOT) && !out.is(Items.GOLD_INGOT)) || outputs.contains(AEItemKey.of(out))) continue;
            var processing = OmniversalPatternEncoding.createProcessingPattern(recipe);
            if (processing.isEmpty()) continue;
            result.add(AEItemKey.of(OmniversalPatternEncoding.encode(processing, entry, level))); outputs.add(AEItemKey.of(out));
            if (result.size() == 2) return result;
        }
        throw new AssertionError("Need two native furnace patterns");
    }
    private static void seed(IPatternDetails pattern) {
        for (var input : pattern.getInputs()) {
            var possible = input.getPossibleInputs()[0];
            storage.values.merge(possible.what(), possible.amount() * input.getMultiplier() * 16, Long::sum);
        }
    }
    private static PassivePatternInputTransaction.Result extract(ServerLevel level, IPatternDetails pattern) {
        return PassivePatternInputTransaction.extract(pattern, 1, level, storage, storage::getAvailableStacks,
                IActionSource.empty(), (key, amount) -> { throw new AssertionError("Unexpected rollback"); });
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
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
        @Override public net.minecraft.network.chat.Component getDescription() { return net.minecraft.network.chat.Component.literal("Installed pattern check"); }
    }
}
