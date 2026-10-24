package com.sorrowmist.useless.stretcher.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.content.entity.StaffLeafRewardEntity;
import com.sorrowmist.useless.stretcher.content.leaf.StaffLeafDropData;
import com.sorrowmist.useless.stretcher.event.WondrousStaffLeafDropHandler;
import com.sorrowmist.useless.stretcher.init.ModEntities;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real drop events, inventory delivery, carrier lifecycle and saved-data migrations. */
final class LeafRewardChecks {
    private static final UUID RESTART_PENDING = UUID.fromString("2bef29d0-94c5-4910-afd5-23d2de665980");
    private static final UUID RESTART_CLAIMED = UUID.fromString("2bef29d0-94c5-4910-afd5-23d2de665981");

    static void run(ServerLevel level) throws Exception {
        migration(level);
        var data = StaffLeafDropData.get(level.getServer());
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "LeafRewards"));
        player.setPos(12, 160, 12);
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        boolean staffEnabled = StretcherConfig.enableStaffLeafDrop();
        boolean dollEnabled = StretcherConfig.enableDollLeafDrop();
        double staffChance = StretcherConfig.staffLeafDropProbability();
        double dollChance = StretcherConfig.dollLeafDropProbability();
        check(StretcherConfig.SERVER_STAFF_LEAF_DROP_PROBABILITY.getDefault() == 0.00001D,
                "staff default remains one in 100000");
        check(StretcherConfig.SERVER_DOLL_LEAF_DROP_PROBABILITY.getDefault() == 0.0001D,
                "doll default is one in 10000");
        var playersField = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
        playersField.setAccessible(true);
        @SuppressWarnings("unchecked") var players = (List<ServerPlayer>) playersField.get(level.getServer().getPlayerList());
        try {
            StretcherConfig.SERVER_STAFF_LEAF_DROP.set(true);
            StretcherConfig.SERVER_DOLL_LEAF_DROP.set(true);
            StretcherConfig.SERVER_STAFF_LEAF_DROP_PROBABILITY.set(1D);
            StretcherConfig.SERVER_DOLL_LEAF_DROP_PROBABILITY.set(1D);
            var cancel = new CancelBreak();
            MinecraftForge.EVENT_BUS.register(cancel);
            try { drop(level, player, Blocks.OAK_LEAVES.defaultBlockState()); }
            finally { MinecraftForge.EVENT_BUS.unregister(cancel); }
            check(level.getBlockState(DROP_POS).is(Blocks.OAK_LEAVES), "cancelled break keeps leaves");
            drop(level, null, Blocks.OAK_LEAVES.defaultBlockState());
            drop(level, player, Blocks.STONE.defaultBlockState());
            player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            drop(level, player, Blocks.OAK_LEAVES.defaultBlockState());
            player.gameMode.changeGameModeForPlayer(GameType.SPECTATOR);
            drop(level, player, Blocks.OAK_LEAVES.defaultBlockState());
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            check(!data.hasRolledStaff(player.getUUID()) && !data.hasRolledDoll(player.getUUID()),
                    "cancelled/non-player/non-leaf/creative/spectator events do not roll");

            StretcherConfig.SERVER_STAFF_LEAF_DROP.set(false);
            drop(level, player, Blocks.OAK_LEAVES.defaultBlockState());
            check(data.hasClaimedDoll(player.getUUID()) && !data.hasRolledStaff(player.getUUID()),
                    "doll can roll and deliver while staff is disabled");
            StretcherConfig.SERVER_STAFF_LEAF_DROP.set(true);
            for (int i = 0; i < 8; i++) drop(level, player, Blocks.OAK_LEAVES.defaultBlockState());
            check(player.getInventory().countItem(ModItems.TWO_I_DOLL.get()) == 1
                            && player.getInventory().countItem(ModItems.WONDROUS_STAFF.get()) == 1,
                    "doll-first order independently awards staff and never repeats either reward");

            var staffFirst = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "LeafStaffFirst"));
            StretcherConfig.SERVER_DOLL_LEAF_DROP.set(false);
            drop(level, staffFirst, Blocks.OAK_LEAVES.defaultBlockState());
            check(data.hasClaimedStaff(staffFirst.getUUID()) && !data.hasRolledDoll(staffFirst.getUUID()),
                    "staff can roll while doll is disabled");
            StretcherConfig.SERVER_DOLL_LEAF_DROP.set(true);
            drop(level, staffFirst, Blocks.OAK_LEAVES.defaultBlockState());
            check(data.hasClaimedDoll(staffFirst.getUUID()), "claimed staff does not consume doll roll");

            var full = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "LeafFullInventory"));
            full.setPos(12, 160, 12);
            fill(full);
            drop(level, full, Blocks.OAK_LEAVES.defaultBlockState());
            UUID id = full.getUUID();
            check(data.hasPendingStaff(id) && data.hasPendingDoll(id), "both rewards remain pending with full inventory");
            check(carriers(level, id).size() == 2, "one visual carrier per pending reward");
            for (var carrier : carriers(level, id)) {
                check(carrier.getDisplayStack().is(carrier.isDollReward() ? ModItems.TWO_I_DOLL.get() : ModItems.WONDROUS_STAFF.get()),
                        "carrier display matches its reward");
                check(!carrier.hurt(level.damageSources().lava(), 1000) && carrier.fireImmune(), "carrier resists lava and damage");
                var duplicate = new StaffLeafRewardEntity(level, id, carrier.isDollReward());
                check(!level.addFreshEntity(duplicate), "duplicate reward carrier rejected");
                var encoded = carrier.saveWithoutId(new CompoundTag());
                carrier.discard();
                var restored = ModEntities.STAFF_LEAF_REWARD.get().create(level);
                restored.load(encoded);
                check(level.addFreshEntity(restored) && restored.isDollReward() == encoded.getBoolean("doll"),
                        "saved carrier restores owner and reward type");
            }
            var invalid = ModEntities.STAFF_LEAF_REWARD.get().create(level);
            check(!level.addFreshEntity(invalid), "ownerless carrier rejected without null lookup");
            check(!level.addFreshEntity(new StaffLeafRewardEntity(level, player.getUUID(), true)),
                    "already claimed carrier rejected");

            // Replace the player object, as reconnecting does, and retry through the real tick handler.
            var reconnect = new FakePlayer(level, full.getGameProfile());
            reconnect.setPos(12, 160, 12);
            fill(reconnect);
            players.add(reconnect);
            StretcherConfig.SERVER_STAFF_LEAF_DROP.set(false);
            StretcherConfig.SERVER_DOLL_LEAF_DROP.set(false);
            try {
                WondrousStaffLeafDropHandler.onServerTick(new ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END, () -> true, level.getServer()));
                check(data.hasPendingStaff(id) && data.hasPendingDoll(id) && carriers(level, id).size() == 2,
                        "reconnect with full inventory keeps two pending rewards");
                reconnect.getInventory().setItem(0, ItemStack.EMPTY);
                WondrousStaffLeafDropHandler.onServerTick(new ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END, () -> true, level.getServer()));
                check(data.hasClaimedStaff(id) && data.hasPendingDoll(id) && carriers(level, id).size() == 1,
                        "one free slot claims only one reward without erasing the other");
                reconnect.getInventory().setItem(1, ItemStack.EMPTY);
                WondrousStaffLeafDropHandler.onServerTick(new ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END, () -> true, level.getServer()));
                for (int i = 0; i < 4; i++) WondrousStaffLeafDropHandler.onServerTick(new ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END, () -> true, level.getServer()));
                check(data.hasClaimedDoll(id) && !data.isPending(id) && carriers(level, id).isEmpty(),
                        "pending delivery survives disabled rolls and removes visual carriers");
                check(reconnect.getInventory().countItem(ModItems.WONDROUS_STAFF.get()) == 1
                                && reconnect.getInventory().countItem(ModItems.TWO_I_DOLL.get()) == 1,
                        "repeated retry ticks deliver exactly one of each");
                var saved = reload(data.save(new CompoundTag()), level);
                check(saved.hasClaimedStaff(id) && saved.hasClaimedDoll(id)
                                && !saved.beginStaffDelivery(id) && !saved.beginDollDelivery(id),
                        "claimed records survive reload and block new rolls");
            } finally {
                players.remove(reconnect);
            }
            restartFixture(data);
            LogUtils.getLogger().info("LEAF REWARDS PASSED: independent one-time rolls, event guards, legacy migration, inventory retry, reconnect, carriers and saved claims");
        } finally {
            StretcherConfig.SERVER_STAFF_LEAF_DROP.set(staffEnabled);
            StretcherConfig.SERVER_DOLL_LEAF_DROP.set(dollEnabled);
            StretcherConfig.SERVER_STAFF_LEAF_DROP_PROBABILITY.set(staffChance);
            StretcherConfig.SERVER_DOLL_LEAF_DROP_PROBABILITY.set(dollChance);
        }
    }

    private static void migration(ServerLevel level) throws Exception {
        UUID pending = UUID.randomUUID(), claimed = UUID.randomUUID();
        CompoundTag original = new CompoundTag();
        original.put("triggered_players", entries(pending, claimed));
        original.put("pending_players", entries(pending));
        var loaded = reload(original, level);
        check(loaded.hasPendingStaff(pending) && loaded.hasClaimedStaff(claimed), "staff-only legacy keeps pending and claimed distinct");
        check(!loaded.hasRolledDoll(pending) && !loaded.hasRolledDoll(claimed), "staff-only history does not consume or award dolls");
        CompoundTag bundled = original.copy();
        ListTag masks = new ListTag();
        CompoundTag mask = new CompoundTag();
        mask.putUUID("player", claimed); mask.putInt("mask", 3); masks.add(mask);
        bundled.put("delivered_rewards", masks);
        loaded = reload(bundled, level);
        check(loaded.hasPendingStaff(pending) && loaded.hasPendingDoll(pending), "bundled unclaimed reward keeps both promises");
        check(loaded.hasClaimedStaff(claimed) && loaded.hasClaimedDoll(claimed), "bundled delivered bits remain claimed");
        mask = new CompoundTag(); mask.putUUID("player", pending); mask.putInt("mask", 1); masks.add(mask);
        loaded = reload(bundled, level);
        check(loaded.hasClaimedStaff(pending) && loaded.hasPendingDoll(pending), "partial bundled delivery does not repeat staff");
        mask.putInt("mask", 2);
        loaded = reload(bundled, level);
        check(loaded.hasPendingStaff(pending) && loaded.hasClaimedDoll(pending), "partial bundled delivery does not repeat doll");
        CompoundTag interim = bundled.copy();
        interim.putBoolean("doll_rewards_migrated", true);
        ListTag queue = entries(pending); queue.getCompound(0).putInt("count", 7);
        interim.put("pending_dolls", queue);
        loaded = reload(interim, level);
        check(loaded.hasClaimedDoll(pending) && !loaded.hasPendingDoll(pending),
                "historical claimed bit wins over leftover repeatable doll queue");
        CompoundTag queueOnly = new CompoundTag();
        queueOnly.putBoolean("doll_rewards_migrated", true);
        queueOnly.put("pending_dolls", queue);
        loaded = reload(queueOnly, level);
        check(loaded.hasPendingDoll(pending), "interim queue without a claimed record keeps one pending doll");
        loaded.markDollDelivered(pending);
        loaded = reload(loaded.save(new CompoundTag()), level);
        check(loaded.hasClaimedDoll(pending) && !loaded.hasPendingDoll(pending) && !loaded.beginDollDelivery(pending),
                "interim count is limited to one lifetime reward");
        check(!new StaffLeafDropData().hasRolledStaff(pending), "another save starts with independent player records");
        LogUtils.getLogger().info("LEAF MIGRATION PASSED: staff-only pending/completed, bundled partial masks, interim queue and normalized roundtrip");
    }

    private static void restartFixture(StaffLeafDropData data) {
        if (data.hasRolledStaff(RESTART_PENDING)) {
            check(data.hasPendingStaff(RESTART_PENDING) && data.hasPendingDoll(RESTART_PENDING), "pending fixture survived real server restart");
            check(data.hasClaimedStaff(RESTART_CLAIMED) && data.hasClaimedDoll(RESTART_CLAIMED), "claimed fixture survived real server restart");
            check(!data.beginStaffDelivery(RESTART_CLAIMED) && !data.beginDollDelivery(RESTART_CLAIMED), "restart cannot reroll claims");
            LogUtils.getLogger().info("LEAF RESTART PASSED: pending and claimed fixtures restored from previous server process");
        } else {
            data.beginStaffDelivery(RESTART_PENDING); data.beginDollDelivery(RESTART_PENDING);
            data.beginStaffDelivery(RESTART_CLAIMED); data.beginDollDelivery(RESTART_CLAIMED);
            data.markStaffDelivered(RESTART_CLAIMED); data.markDollDelivered(RESTART_CLAIMED);
            LogUtils.getLogger().info("LEAF RESTART FIXTURE SAVED: run server again to check persisted pending and claimed states");
        }
    }

    private static StaffLeafDropData reload(CompoundTag tag, ServerLevel level) throws Exception {
        var method = StaffLeafDropData.class.getDeclaredMethod("load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        method.setAccessible(true);
        return (StaffLeafDropData) method.invoke(null, tag, level.registryAccess());
    }

    private static ListTag entries(UUID... players) {
        ListTag list = new ListTag();
        for (UUID player : players) { CompoundTag tag = new CompoundTag(); tag.putUUID("player", player); list.add(tag); }
        return list;
    }

    private static final BlockPos DROP_POS = new BlockPos(12, 160, 12);
    public static final class CancelBreak {
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void cancel(BlockEvent.BreakEvent event) { event.setCanceled(true); }
    }
    private static void drop(ServerLevel level, Entity player, BlockState state) {
        level.getChunkAt(DROP_POS);
        level.setBlock(DROP_POS, state, 3);
        if (player instanceof ServerPlayer serverPlayer) {
            // Keep punching with an ordinary slot after rewards enter slot zero;
            // the staff's own bulk-mining handler is a different break path.
            serverPlayer.getInventory().selected = 8;
            boolean broken = serverPlayer.gameMode.destroyBlock(DROP_POS);
            LogUtils.getLogger().info("LEAF BREAK fixture: player={} mode={} broken={} remaining={}",
                    serverPlayer.getScoreboardName(), serverPlayer.gameMode.getGameModeForPlayer(), broken, level.getBlockState(DROP_POS));
        } else {
            net.minecraft.world.level.block.Block.dropResources(state, level, DROP_POS, null, player, ItemStack.EMPTY);
        }
    }

    private static List<StaffLeafRewardEntity> carriers(ServerLevel level, UUID owner) {
        List<StaffLeafRewardEntity> result = new ArrayList<>();
        for (var entity : level.getAllEntities()) {
            if (entity instanceof StaffLeafRewardEntity reward && !reward.isRemoved() && owner.equals(reward.getOwnerId())) result.add(reward);
        }
        return result;
    }

    private static void fill(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().items.size(); i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError("leaf rewards: " + message);
    }
}
