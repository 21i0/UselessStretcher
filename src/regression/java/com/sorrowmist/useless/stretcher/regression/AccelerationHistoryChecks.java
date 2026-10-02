package com.sorrowmist.useless.stretcher.regression;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.content.acceleration.AccelerationExecutionBudget;
import com.sorrowmist.useless.stretcher.content.acceleration.PermanentAccelerationHistory;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import java.util.UUID;

final class AccelerationHistoryChecks {
    static void run(ServerLevel level) {
        BlockPos pos = new BlockPos(30, 130, 30);
        level.getChunkAt(pos);
        level.setBlockAndUpdate(pos.below(), Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(pos, Blocks.HOPPER.defaultBlockState());
        var broken = new BrokenNameHopper(pos);
        level.setBlockEntity(broken);
        broken.setItem(0, new ItemStack(Items.COBBLESTONE, 4));
        var owner = UUID.randomUUID();
        var marker = new WondrousStaffAccelerationEntity(level, pos, 4);
        marker.setOwnerUuid(owner);
        marker.setPermanent();
        marker.setIdleThrottleDisabled(true);
        check(level.addFreshEntity(marker), "permanent marker spawned");
        var history = PermanentAccelerationHistory.get(level.getServer());
        AccelerationExecutionBudget.beginTick(level.getServer());
        marker.tickCount = 1;
        marker.tick();
        for (int i = 0; i < 50; i++) history.track(level, marker);
        check(broken.nameCalls == 1, "broken addon display name is probed once, not every tick");
        check(history.activeEntries(owner).size() == 1, "broken name does not disable permanent history");
        check(history.activeEntries(owner).getFirst().label().startsWith(Blocks.HOPPER.getName().getString()),
                "broken name falls back to the block name");
        var chest = (ChestBlockEntity) level.getBlockEntity(pos.below());
        check(!chest.isEmpty(), "machine still ticks after display-name exception");
        marker.setSpeed(16);
        history.track(level, marker);
        check(history.activeEntries(owner).getFirst().label().contains("x16"), "history speed remains current");

        // A new target instance must not inherit the old instance's failed name probe.
        var replacement = new NamedHopper(pos);
        replacement.displayName = Component.literal("Renamed machine");
        level.removeBlockEntity(pos);
        level.setBlockEntity(replacement);
        history.track(level, marker);
        check(history.activeEntries(owner).getFirst().label().startsWith("Renamed machine"),
                "replacement target and custom names stay supported");
        replacement.displayName = Component.literal("Updated name");
        history.track(level, marker);
        check(history.activeEntries(owner).getFirst().label().startsWith("Updated name"), "custom rename updates");
        check(!history.reclaim(level.getServer(), marker.getUUID(), UUID.randomUUID()), "wrong owner rejected");
        check(history.reclaim(level.getServer(), marker.getUUID(), owner), "remote reclaim remains functional");
        check(marker.isRemoved(), "reclaim removes loaded marker");

        var unloaded = new WondrousStaffAccelerationEntity(level, pos, 4);
        unloaded.setOwnerUuid(owner);
        unloaded.setPermanent();
        history.track(level, unloaded);
        check(history.reclaim(level.getServer(), unloaded.getUUID(), owner), "unloaded marker can be reclaimed");
        var restored = PermanentAccelerationHistory.load(history.save(new CompoundTag(), level.registryAccess()),
                level.registryAccess());
        check(restored.isReclaimed(unloaded.getUUID()), "unloaded reclaim tombstone survives save/load");
        history.forget(unloaded.getUUID());
        history.forget(marker.getUUID());
        level.removeBlock(pos, false);
        level.removeBlock(pos.below(), false);
        LogUtils.getLogger().info("REGRESSION acceleration history: broken addon name isolated, machine ticks and remote reclaim preserved");
    }

    private static final class BrokenNameHopper extends HopperBlockEntity {
        private int nameCalls;

        private BrokenNameHopper(BlockPos pos) {
            super(pos, Blocks.HOPPER.defaultBlockState());
        }

        @Override public Component getDisplayName() {
            nameCalls++;
            throw new IllegalArgumentException("Unsupported value type (Packaged FAA name regression)");
        }
    }

    private static final class NamedHopper extends HopperBlockEntity {
        private Component displayName;

        private NamedHopper(BlockPos pos) { super(pos, Blocks.HOPPER.defaultBlockState()); }

        @Override public Component getDisplayName() { return displayName; }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
