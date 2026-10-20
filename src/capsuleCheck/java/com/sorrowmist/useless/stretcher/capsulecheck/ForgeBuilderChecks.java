package com.sorrowmist.useless.stretcher.capsulecheck;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.content.acceleration.AccelerationExecutionBudget;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.mixin.BuilderProgressAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.UUID;
final class ForgeBuilderChecks {
    static void run(ServerLevel level) throws Exception {
        for (boolean range : new boolean[]{false,true})
            for (boolean building : new boolean[]{false,true}) quarry(level, range, building);
    }

        static void quarry(ServerLevel level, boolean range, boolean building) throws Exception {
            BlockPos pos = new BlockPos(64, 150, 96);
            level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(new ResourceLocation("rftoolsbuilder:builder")).defaultBlockState());
            level.setBlockAndUpdate(pos.above(), Blocks.CHEST.defaultBlockState());
            var builder = (mcjty.rftoolsbuilder.modules.builder.blocks.BuilderTileEntity) level.getBlockEntity(pos);
            builder.setRSMode(mcjty.lib.varia.RedstoneMode.REDSTONE_IGNORED);
            var card = new ItemStack(building ? mcjty.rftoolsbuilder.modules.builder.BuilderModule.SHAPE_CARD_DEF.get()
                    : mcjty.rftoolsbuilder.modules.builder.BuilderModule.SHAPE_CARD_QUARRY_CLEAR.get());
            mcjty.rftoolsbuilder.modules.builder.items.ShapeCardItem.setShape(card, mcjty.rftoolsbuilder.shapes.Shape.SHAPE_BOX, true);
            mcjty.rftoolsbuilder.modules.builder.items.ShapeCardItem.setDimension(card, 6, 1, 6);
            mcjty.rftoolsbuilder.modules.builder.items.ShapeCardItem.setOffset(card, 8, 0, 0);
            if (building) mcjty.rftoolsbuilder.modules.builder.items.ShapeCardItem.setGhostMaterial(card.getOrCreateTag(), new ItemStack(Items.COBBLESTONE));
            var inventory = (IItemHandlerModifiable) builder.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
            check(inventory != null, "builder exposes card capability");
            inventory.setStackInSlot(0, card);
            BlockPos min = mcjty.rftoolsbuilder.modules.builder.items.ShapeCardItem.getMinCorner(pos, new BlockPos(6, 1, 6), new BlockPos(8, 0, 0));
            BlockPos max = mcjty.rftoolsbuilder.modules.builder.items.ShapeCardItem.getMaxCorner(pos, new BlockPos(6, 1, 6), new BlockPos(8, 0, 0));
            for (BlockPos p : BlockPos.betweenClosed(min, max)) level.setBlockAndUpdate(p,
                    (building ? Blocks.AIR : Blocks.STONE).defaultBlockState());
            var chest = (ChestBlockEntity) level.getBlockEntity(pos.above());
            if (building) for (int i = 0; i < 4; i++) chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            int initialMaterials = chestCount(chest);
            var restart = builder.getClass().getDeclaredMethod("restartScan");
            restart.setAccessible(true);
            restart.invoke(builder);
            check(((BuilderProgressAccessor)builder).uselessStretcher$getScan() != null, "native quarry scan starts");
            var energy = builder.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElse(null);
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
                for (int i = 0; i < 100 && ((BuilderProgressAccessor)builder).uselessStretcher$getScan() != null; i++) {
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
                        driver.route(), building, ordinary, accelerated, removed, consumedEnergy, driver.batches, "native");
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
    private static ItemStack staff(int size, int speed) {
        ItemStack staff = new ItemStack(ModItems.WONDROUS_STAFF.get());
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(staff, StretcherComponents.WONDROUS_STAFF_SPEED.get(), speed);
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(staff, StretcherComponents.RANGE_SIZE_X.get(), size);
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(staff, StretcherComponents.RANGE_SIZE_Y.get(), size);
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(staff, StretcherComponents.RANGE_SIZE_Z.get(), size);
        return staff;
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

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
