package com.sorrowmist.useless.stretcher.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.content.acceleration.AccelerationExecutionBudget;
import com.sorrowmist.useless.stretcher.content.acceleration.ExtraTickClock;
import com.sorrowmist.useless.stretcher.content.acceleration.MachineActivityProbe;
import com.sorrowmist.useless.stretcher.content.entity.EntityTimerAcceleration;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.UUID;

/** Actual mod recipes and quarry work, not replacement test tickers. Never shipped. */
final class ExternalAccelerationChecks {
    static void run(ServerLevel level) throws Exception {
        long gameTime = level.getGameTime(), dayTime = level.getDayTime();
        if (ModList.get().isLoaded("occultism")) {
            Occultism.waitingRitual(level, false);
            Occultism.waitingRitual(level, true);
        }
        for (boolean range : new boolean[]{false, true}) {
            if (ModList.get().isLoaded("occultism")) {
                Occultism.miner(level, range);
                Occultism.ritual(level, range);
                for (boolean timers : new boolean[]{true, false}) {
                    for (int processing = 0; processing < 3; processing++) Occultism.spirit(level, range, timers, processing);
                }
            }
            if (ModList.get().isLoaded("rftoolsbuilder")) {
                Rftools.quarry(level, range, false);
                Rftools.quarry(level, range, true);
            }
        }
        check(level.getGameTime() == gameTime && level.getDayTime() == dayTime, "acceleration must preserve world time");
    }

    private static final class Occultism {
        @SuppressWarnings("unchecked")
        static void waitingRitual(ServerLevel level, boolean itemUse) {
            BlockPos pos = new BlockPos(96, 150, 64);
            String path = itemUse ? "wild_random_animal_water" : "misc_wild_trim";
            var recipe = (net.minecraft.world.item.crafting.RecipeHolder<com.klikli_dev.occultism.crafting.recipe.RitualRecipe>)
                    (net.minecraft.world.item.crafting.RecipeHolder<?>) level.getRecipeManager()
                            .byKey(ResourceLocation.parse("occultism:ritual/" + path)).orElseThrow();
            recipe.value().getPentacle().place(level, pos, Rotation.NONE);
            level.setBlockAndUpdate(pos, com.klikli_dev.occultism.registry.OccultismBlocks.GOLDEN_SACRIFICIAL_BOWL.get().defaultBlockState());
            var bowl = (com.klikli_dev.occultism.common.blockentity.GoldenSacrificialBowlBlockEntity) level.getBlockEntity(pos);
            var offerings = new ArrayList<BlockPos>();
            int n = 0;
            for (var ingredient : recipe.value().getIngredients()) {
                BlockPos offering = pos.offset(-7, 0, n++ - 3);
                offerings.add(offering);
                level.setBlockAndUpdate(offering, com.klikli_dev.occultism.registry.OccultismBlocks.SACRIFICIAL_BOWL.get().defaultBlockState());
                ((com.klikli_dev.occultism.common.blockentity.SacrificialBowlBlockEntity) level.getBlockEntity(offering))
                        .itemStackHandler.setStackInSlot(0, ingredient.getItems()[0].copyWithCount(1));
            }
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "RitualWaiting"));
            player.setPos(Vec3.atCenterOf(pos));
            var activation = recipe.value().getActivationItemStack()[0].copyWithCount(1);
            bowl.itemStackHandler.setStackInSlot(0, activation);
            try {
                check(bowl.startRitual(null, activation, recipe), "native conditional ritual starts");
                var probe = new MachineActivityProbe();
                check(probe.isWorking(level, pos, bowl.getBlockState()), "new known target gets one initial observation");
                for (int i = 0; i < 25; i++) {
                    bowl.setChanged();
                    bowl.tick();
                    check(bowl.currentTime == 0 && !probe.isWorking(level, pos, bowl.getBlockState()),
                            "native waiting condition is idle despite dirty notifications");
                }
                if (itemUse) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, recipe.value().getItemToUse().getItems()[0].copy());
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                            new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND));
                    check(bowl.itemUseFulfilled(), "native item-use event fulfills the ritual condition");
                } else {
                    var llama = net.minecraft.world.entity.EntityType.LLAMA.create(level);
                    check(llama != null, "native sacrifice entity exists");
                    llama.setPos(Vec3.atCenterOf(pos));
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                            new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(llama, level.damageSources().playerAttack(player)));
                    check(bowl.sacrificeFulfilled(), "native player sacrifice event fulfills the ritual condition");
                }
                check(probe.isWorking(level, pos, bowl.getBlockState()), "native condition fulfillment wakes the same probe immediately");
                LogUtils.getLogger().info("OCCULTISM WAITING STATE PASSED: {} native waiting/progress conditions, dirty notifications ignored", path);
            } finally {
                if (bowl.getCurrentRitualRecipe() != null) bowl.stopRitual(false);
                offerings.forEach(p -> level.removeBlock(p, false));
                level.removeBlock(pos, false);
                level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(10)).forEach(ItemEntity::discard);
            }
        }

        static void miner(ServerLevel level, boolean range) {
            BlockPos pos = new BlockPos(64, 150, 64);
            level.setBlockAndUpdate(pos, com.klikli_dev.occultism.registry.OccultismBlocks.DIMENSIONAL_MINESHAFT.get().defaultBlockState());
            var machine = (com.klikli_dev.occultism.common.blockentity.DimensionalMineshaftBlockEntity) level.getBlockEntity(pos);
            var input = new ItemStack(com.klikli_dev.occultism.registry.OccultismItems.MINER_FOLIOT_UNSPECIALIZED.get());
            machine.inputHandler.setStackInSlot(0, input);
            machine.tick();
            check(machine.miningTime > 0, "real miner starts its native recipe");
            int duration = machine.miningTime;
            check(new MachineActivityProbe().isWorking(level, pos, machine.getBlockState()), "internal mining timer keeps miner awake");
            try (var driver = new Driver(level, pos, range, 64)) {
                for (int i = 0; i < 300 && count(machine.outputHandler) == 0; i++) driver.tick();
                check(count(machine.outputHandler) > 0, "real miner produces native recipe output");
                check(input.getDamageValue() > 0, "mining still consumes spirit tool durability");
                LogUtils.getLogger().info("OCCULTISM MINER PASSED: {} duration={} batches={} output={} durability={}",
                        driver.route(), duration, driver.batches, count(machine.outputHandler), input.getDamageValue());
                level.setBlockAndUpdate(pos.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
                int before = input.getDamageValue();
                driver.tick();
                check(before == input.getDamageValue() && machine.miningTime == 0, "redstone pause is respected");
            } finally {
                level.removeBlock(pos.east(), false);
                level.removeBlock(pos, false);
            }
        }

        static void ritual(ServerLevel level, boolean range) {
            BlockPos pos = new BlockPos(96, 150, 64);
            var recipe = level.getRecipeManager().byKey(ResourceLocation.parse("occultism:ritual/craft_dimensional_mineshaft"))
                    .map(r -> (net.minecraft.world.item.crafting.RecipeHolder<com.klikli_dev.occultism.crafting.recipe.RitualRecipe>) r).orElseThrow();
            recipe.value().getPentacle().place(level, pos, Rotation.NONE);
            level.setBlockAndUpdate(pos, com.klikli_dev.occultism.registry.OccultismBlocks.GOLDEN_SACRIFICIAL_BOWL.get().defaultBlockState());
            var bowl = (com.klikli_dev.occultism.common.blockentity.GoldenSacrificialBowlBlockEntity) level.getBlockEntity(pos);
            check(bowl instanceof ExtraTickClock,
                    "Occultism golden bowl receives the optional virtual ritual clock mixin");
            var offeringPositions = new ArrayList<BlockPos>();
            int n = 0;
            for (var ingredient : recipe.value().getIngredients()) {
                BlockPos offeringPos = pos.offset(-7, 0, n++ - 3);
                offeringPositions.add(offeringPos);
                level.setBlockAndUpdate(offeringPos, com.klikli_dev.occultism.registry.OccultismBlocks.SACRIFICIAL_BOWL.get().defaultBlockState());
                var offering = (com.klikli_dev.occultism.common.blockentity.SacrificialBowlBlockEntity) level.getBlockEntity(offeringPos);
                offering.itemStackHandler.setStackInSlot(0, ingredient.getItems()[0].copyWithCount(1));
            }
            var activation = recipe.value().getActivationItemStack()[0].copyWithCount(1);
            bowl.itemStackHandler.setStackInSlot(0, activation);
            check(recipe.value().getRitual().isValid(level, pos, bowl, null, activation, recipe.value().getIngredients()),
                    "real pentacle and native offerings validate");
            check(bowl.startRitual(null, activation, recipe), "real ritual starts");
            try (var driver = new RitualDriver(level, pos, range, 20)) {
                long start = level.getGameTime();
                check(start % 20 != 0, "ritual regression deliberately starts outside the world's work phase");
                for (int i = 0; i < 40; i++) bowl.tick();
                check(bowl.currentTime == 0, "repeating an ordinary ticker at a fixed clock phase reproduces the original stall");
                for (int i = 0; i < 50 && bowl.currentTime < 3; i++) driver.tick();
                check(bowl.currentTime >= 3, "ritual progresses at a fixed real-world clock phase");
                LogUtils.getLogger().info("OCCULTISM CLOCK PASSED: {} realTime={} ordinary40=0 acceleratedProgress={} staffBatches={}",
                        driver.route(), start, bowl.currentTime, driver.batches);
                check(level.getGameTime() == start, "ritual does not mutate world time");
                for (int i = 0; i < 600 && bowl.getCurrentRitualRecipe() != null; i++) driver.tick();
                check(bowl.getCurrentRitualRecipe() == null, "real native ritual finishes");
                check(bowl.itemStackHandler.getStackInSlot(0).isEmpty(), "activation consumed exactly once");
                for (BlockPos offeringPos : offeringPositions) {
                    var offering = (com.klikli_dev.occultism.common.blockentity.SacrificialBowlBlockEntity) level.getBlockEntity(offeringPos);
                    check(offering.itemStackHandler.getStackInSlot(0).isEmpty(), "every native ritual offering consumed");
                }
                int outputs = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(5)).stream()
                        .filter(e -> e.getItem().is(com.klikli_dev.occultism.registry.OccultismBlocks.DIMENSIONAL_MINESHAFT.get().asItem()))
                        .mapToInt(e -> e.getItem().getCount()).sum();
                check(outputs == 1, "native ritual produces one dimensional mineshaft");
                level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(5)).forEach(ItemEntity::discard);
                LogUtils.getLogger().info("OCCULTISM RITUAL PASSED: {} native pentacle, seven offerings, exactly one result, {} batches",
                        driver.route(), driver.batches);
            } finally {
                if (bowl.getCurrentRitualRecipe() != null) bowl.stopRitual(false);
                offeringPositions.forEach(p -> level.removeBlock(p, false));
                level.removeBlock(pos, false);
            }
        }

        static void spirit(ServerLevel level, boolean range, boolean timers, int processing) {
            BlockPos pos = new BlockPos(128, 150, 64);
            var spirit = com.klikli_dev.occultism.registry.OccultismEntities.FOLIOT_TYPE.get().create(level);
            check(spirit != null, "real foliot created");
            spirit.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            spirit.setNoGravity(true);
            spirit.setSpiritMaxAge(-1);
            com.klikli_dev.occultism.common.entity.job.SpiritJob job;
            net.minecraft.world.item.Item input, output;
            int expected;
            if (processing == 0) {
                job = new com.klikli_dev.occultism.common.entity.job.CrusherJob(spirit, () -> 1F, () -> 1F, () -> 1, () -> 1);
                job.setFactoryId(com.klikli_dev.occultism.registry.OccultismSpiritJobs.CRUSH_TIER1.getId());
                input = Items.BONE; output = Items.BONE_MEAL; expected = 4;
            } else if (processing == 1) {
                job = new com.klikli_dev.occultism.common.entity.job.SmelterJob(spirit, () -> 1F, () -> 1);
                job.setFactoryId(com.klikli_dev.occultism.registry.OccultismSpiritJobs.SMELT_TIER1.getId());
                input = Items.RAW_IRON; output = Items.IRON_INGOT; expected = 1;
            } else {
                job = new com.klikli_dev.occultism.common.entity.job.CrystallizerJob(spirit, () -> 1F, () -> 1F, () -> 1, () -> 2);
                job.setFactoryId(com.klikli_dev.occultism.registry.OccultismSpiritJobs.CRYSTAL_TIER2.getId());
                input = Items.QUARTZ_BLOCK; output = Items.QUARTZ; expected = 4;
            }
            spirit.setJob(job);
            spirit.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(input));
            level.addFreshEntity(spirit);
            int age = spirit.getSpiritAge();
            var position = spirit.position();
            RangeAccelerationSavedData ranges = range ? RangeAccelerationSavedData.get(level.getServer()) : null;
            UUID owner = UUID.randomUUID(), id = null;
            if (range) {
                var staff = staff(1, 64);
                staff.set(StretcherComponents.ENTITY_TIMER_MODE.get(), timers);
                staff.set(StretcherComponents.ENTITY_TIMER_SPEED.get(), 64);
                id = ranges.place(level, FakePlayerFactory.get(level, new GameProfile(owner, "OccultismTimer")), pos, staff).id();
            }
            var singleMarker = new com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity(level, spirit, 64);
            singleMarker.setEntityTimerMode(timers);
            try {
                for (int i = 0; i < 200 && !spirit.getMainHandItem().isEmpty(); i++) {
                    AccelerationExecutionBudget.removeServer(level.getServer());
                    AccelerationExecutionBudget.beginTick(level.getServer());
                    if (range) {
                        ranges.tick(level.getServer());
                        boolean found = false;
                        for (var entity : level.getAllEntities()) {
                            if (entity instanceof com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity marker
                                    && spirit.getUUID().equals(marker.getTargetUuid())) {
                                marker.tick();
                                found = true;
                            }
                        }
                        check(found, "range discovers the real spirit and creates its acceleration marker");
                    }
                    else singleMarker.tick();
                }
                check(spirit.getMainHandItem().isEmpty(), "real processor consumes input through entity acceleration");
                int outputs = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(5)).stream()
                        .filter(e -> e.getItem().is(output)).mapToInt(e -> e.getItem().getCount()).sum();
                check(outputs == expected, "native spirit recipe produces exactly its expected result: " + input + " -> " + outputs);
                if (timers) check(spirit.getSpiritAge() == age && spirit.position().equals(position), "timer mode preserves spirit lifetime and movement");
                LogUtils.getLogger().info("OCCULTISM SPIRIT PASSED: {} timers={} {} -> {} x{}",
                        range ? "range" : "single-target", timers, input, output, outputs);
            } finally {
                singleMarker.discard();
                if (range) ranges.reclaim(level.getServer(), owner, id);
                spirit.discard();
                level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(5)).forEach(ItemEntity::discard);
                AccelerationExecutionBudget.removeServer(level.getServer());
            }
        }
    }

    private static final class Rftools {
        static void quarry(ServerLevel level, boolean range, boolean building) throws Exception {
            BlockPos pos = new BlockPos(64, 150, 96);
            level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("rftoolsbuilder:builder")).defaultBlockState());
            level.setBlockAndUpdate(pos.above(), Blocks.CHEST.defaultBlockState());
            var builder = (mcjty.rftoolsbuilder.modules.builder.blocks.BuilderTileEntity) level.getBlockEntity(pos);
            builder.setRSMode(mcjty.lib.varia.RedstoneMode.REDSTONE_IGNORED);
            var card = new ItemStack(building ? mcjty.rftoolsbuilder.modules.builder.BuilderModule.SHAPE_CARD_DEF.get()
                    : mcjty.rftoolsbuilder.modules.builder.BuilderModule.SHAPE_CARD_QUARRY_CLEAR.get());
            mcjty.rftoolsbuilder.modules.builder.items.ShapeCardItem.setShape(card, mcjty.rftoolsbuilder.shapes.Shape.SHAPE_BOX, true);
            mcjty.rftoolsbuilder.modules.builder.items.ShapeCardItem.setDimension(card, 6, 1, 6);
            mcjty.rftoolsbuilder.modules.builder.items.ShapeCardItem.setOffset(card, 8, 0, 0);
            if (building) mcjty.rftoolsbuilder.modules.builder.items.ShapeCardItem.setGhostMaterial(card, new ItemStack(Items.COBBLESTONE));
            var inventory = (IItemHandlerModifiable) level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
            check(inventory != null, "builder exposes card capability");
            inventory.setStackInSlot(0, card);
            BlockPos min = mcjty.rftoolsbuilder.modules.builder.items.ShapeCardItem.getMinCorner(pos, new BlockPos(6, 1, 6), new BlockPos(8, 0, 0));
            BlockPos max = mcjty.rftoolsbuilder.modules.builder.items.ShapeCardItem.getMaxCorner(pos, new BlockPos(6, 1, 6), new BlockPos(8, 0, 0));
            for (BlockPos p : BlockPos.betweenClosed(min, max)) level.setBlockAndUpdate(p,
                    (building ? Blocks.AIR : Blocks.STONE).defaultBlockState());
            var chest = (ChestBlockEntity) level.getBlockEntity(pos.above());
            if (building) for (int i = 0; i < 4; i++) chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            int initialMaterials = chestCount(chest);
            var dataType = mcjty.rftoolsbuilder.modules.builder.BuilderModule.BUILDER_DATA;
            var restart = builder.getClass().getDeclaredMethod("restartScan", mcjty.rftoolsbuilder.modules.builder.data.BuilderData.class);
            restart.setAccessible(true);
            builder.setData(dataType, (mcjty.rftoolsbuilder.modules.builder.data.BuilderData)
                    restart.invoke(builder, builder.getData(dataType)));
            check(builder.getData(dataType).scan() != null, "native quarry scan starts");
            var energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, Direction.UP);
            check(energy != null, "builder exposes real FE");
            builder.tickServer();
            check(chestCount(chest) == initialMaterials, "native builder cannot work without energy");
            energy.receiveEnergy(Integer.MAX_VALUE, false);
            builder.setRSMode(mcjty.lib.varia.RedstoneMode.REDSTONE_ONREQUIRED);
            try (var blocked = new Driver(level, pos, range, 16)) { blocked.tick(); }
            check(chestCount(chest) == initialMaterials, "acceleration respects builder redstone pause");
            builder.setRSMode(mcjty.lib.varia.RedstoneMode.REDSTONE_IGNORED);
            builder.tickServer();
            int ordinary = Math.abs(chestCount(chest) - initialMaterials);
            try (var driver = new Driver(level, pos, range, 16)) {
                energy.receiveEnergy(Integer.MAX_VALUE, false);
                long consumedEnergy = 0;
                for (int i = 0; i < 100 && builder.getData(dataType).scan() != null; i++) {
                    energy.receiveEnergy(Integer.MAX_VALUE, false);
                    int beforeEnergy = energy.getEnergyStored();
                    driver.tick();
                    consumedEnergy += beforeEnergy - energy.getEnergyStored();
                }
                int accelerated = Math.abs(chestCount(chest) - initialMaterials);
                check(accelerated > ordinary && accelerated > 20, "accelerated real quarry outproduces one ordinary tick");
                check(consumedEnergy > 0, "native builder consumes FE even when its buffer is refilled between batches");
                int removed = 0;
                for (BlockPos p : BlockPos.betweenClosed(min, max)) {
                    if (building ? level.getBlockState(p).is(Blocks.COBBLESTONE) : level.getBlockState(p).isAir()) removed++;
                }
                check(removed == accelerated, "every built/quarried block conserves one material: " + removed + " vs " + accelerated);
                LogUtils.getLogger().info("RFTOOLS BUILDER PASSED: {} building={} ordinary={} accelerated={} blocks={} FE={} batches={} error={}",
                        driver.route(), building, ordinary, accelerated, removed, consumedEnergy, driver.batches, builder.getData(dataType).lastError());
            } finally {
                level.removeBlock(pos, false);
                level.removeBlock(pos.above(), false);
                for (BlockPos p : BlockPos.betweenClosed(min, max)) level.removeBlock(p, false);
            }
        }

        private static int chestCount(ChestBlockEntity chest) {
            int count = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(Items.COBBLESTONE)) count += chest.getItem(i).getCount();
            return count;
        }
    }

    private static int count(net.neoforged.neoforge.items.IItemHandler inventory) {
        int count = 0;
        for (int i = 0; i < inventory.getSlots(); i++) count += inventory.getStackInSlot(i).getCount();
        return count;
    }

    private static ItemStack staff(int size, int speed) {
        ItemStack staff = new ItemStack(ModItems.WONDROUS_STAFF.get());
        staff.set(StretcherComponents.WONDROUS_STAFF_SPEED.get(), speed);
        staff.set(StretcherComponents.RANGE_SIZE_X.get(), size);
        staff.set(StretcherComponents.RANGE_SIZE_Y.get(), size);
        staff.set(StretcherComponents.RANGE_SIZE_Z.get(), size);
        return staff;
    }

    /** Drives the real staff-created marker (single target) or placed range field. */
    private static final class RitualDriver implements AutoCloseable {
        final ServerLevel level;
        final BlockPos pos;
        final int speed;
        final RangeAccelerationSavedData ranges;
        final UUID owner = UUID.randomUUID();
        final UUID id;
        final com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity marker;
        int batches;

        RitualDriver(ServerLevel level, BlockPos pos, boolean range, int speed) {
            this.level = level;
            this.pos = pos;
            this.speed = speed;
            if (range) {
                ranges = RangeAccelerationSavedData.get(level.getServer());
                var player = FakePlayerFactory.get(level, new GameProfile(owner, "OccultismRangeStaff"));
                var stack = configuredStaff(speed);
                id = ranges.place(level, player, pos, stack).id();
                marker = null;
            } else {
                ranges = null;
                id = null;
                var player = FakePlayerFactory.get(level, new GameProfile(owner, "OccultismHandStaff"));
                var stack = configuredStaff(speed);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                player.setShiftKeyDown(true);
                var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                var result = WondrousStaffAcceleration.tryUse(new net.minecraft.world.item.context.UseOnContext(
                        player, InteractionHand.MAIN_HAND, hit));
                check(result.consumesAction(), "held enabled staff accepts Shift+right-click on golden bowl");
                marker = level.getEntitiesOfClass(
                                com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity.class,
                                new AABB(pos).inflate(1), effect -> pos.equals(effect.getTargetPos()))
                        .stream().findFirst().orElseThrow(() -> new AssertionError(
                                "actual staff interaction creates the golden-bowl acceleration marker"));
                check(marker.getSpeed() == speed, "staff marker uses the selected acceleration speed");
            }
        }

        private static ItemStack configuredStaff(int speed) {
            ItemStack stack = staff(1, speed);
            stack.set(UComponents.BeefTimeAccelerationEnabledComponent.get(), true);
            return stack;
        }

        void tick() {
            AccelerationExecutionBudget.removeServer(level.getServer());
            AccelerationExecutionBudget.beginTick(level.getServer());
            if (ranges == null) marker.tick();
            else ranges.tick(level.getServer());
            batches++;
        }

        String route() { return ranges == null ? "held-staff-marker" : "placed-range-field"; }

        @Override public void close() {
            if (marker != null) marker.discard();
            if (ranges != null) ranges.reclaim(level.getServer(), owner, id);
            AccelerationExecutionBudget.removeServer(level.getServer());
        }
    }

    private static final class Driver implements AutoCloseable {
        final ServerLevel level;
        final BlockPos pos;
        final int speed;
        final RangeAccelerationSavedData ranges;
        final UUID owner = UUID.randomUUID();
        final UUID id;
        int batches;
        int executed;
        Driver(ServerLevel level, BlockPos pos, boolean range, int speed) {
            this.level = level;
            this.pos = pos;
            this.speed = speed;
            ranges = range ? new RangeAccelerationSavedData() : null;
            id = range ? ranges.place(level, FakePlayerFactory.get(level, new GameProfile(owner, "ExternalMachines")),
                    pos, staff(1, speed)).id() : null;
        }
        void tick() {
            AccelerationExecutionBudget.removeServer(level.getServer());
            AccelerationExecutionBudget.beginTick(level.getServer());
            if (ranges == null) executed += WondrousStaffAcceleration.tickTarget(level, pos, speed, this);
            else ranges.tick(level.getServer());
            batches++;
        }
        String route() { return ranges == null ? "single-target" : "range"; }
        @Override public void close() {
            if (ranges != null) ranges.reclaim(level.getServer(), owner, id);
            AccelerationExecutionBudget.removeServer(level.getServer());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
