package com.sorrowmist.useless.stretcher.event;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.content.entity.StaffLeafRewardEntity;
import com.sorrowmist.useless.stretcher.content.leaf.StaffLeafDropData;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Adds the very rare wondrous staff easter egg to all Minecraft leaf blocks. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class WondrousStaffLeafDropHandler {
    private static final ResourceLocation ADVANCEMENT_ID =
            ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "staff_leaf_drop");
    private static final String ADVANCEMENT_CRITERION = "main";
    private static final Map<UUID, UUID> VISIBLE_REWARDS = new HashMap<>();

    private WondrousStaffLeafDropHandler() {
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (event.isCanceled()
                || !(event.getBreaker() instanceof ServerPlayer player)
                || player.isCreative()
                || !isLeafBlock(event.getState())
                || !StretcherConfig.enableStaffLeafDrop()) {
            return;
        }

        StaffLeafDropData data = StaffLeafDropData.get(player.getServer());
        UUID playerId = player.getUUID();
        if (data.hasTriggered(playerId)
                || event.getLevel().getRandom().nextDouble() >= StretcherConfig.staffLeafDropProbability()
                || !data.beginDelivery(playerId)) {
            return;
        }

        awardAdvancement(player);
        if (tryDeliver(player)) {
            data.completeDelivery(playerId);
        } else {
            ensureRewardEntity(player.getServer(), player);
        }
    }

    private static boolean isLeafBlock(BlockState state) {
        if (state.is(BlockTags.LEAVES) || state.getBlock() instanceof LeavesBlock) return true;
        if (state.hasProperty(BlockStateProperties.DISTANCE)
                && state.hasProperty(BlockStateProperties.PERSISTENT)) return true;

        // A few modded trees omit the shared tag and use a custom implementation without
        // vanilla leaf properties. Keep those usable without treating generic "leaf" decor
        // such as leaf litter as a full tree-leaf block.
        return state.getBlockHolder().unwrapKey()
                .map(key -> key.location().getPath().contains("leaves"))
                .orElse(false);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        StaffLeafDropData data = StaffLeafDropData.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            if (!data.isPending(playerId) || !player.isAlive() || player.isRemoved()) continue;

            if (tryDeliver(player)) {
                data.completeDelivery(playerId);
                discardRewardEntities(server, playerId);
            } else {
                ensureRewardEntity(server, player);
            }
        }
    }

    @SubscribeEvent
    public static void onRewardEntityLoaded(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof StaffLeafRewardEntity reward)) {
            return;
        }

        UUID owner = reward.getOwnerId();
        if (owner == null
                || !StaffLeafDropData.get(level.getServer()).isPending(owner)) {
            event.setCanceled(true);
            return;
        }

        StaffLeafRewardEntity existing = findTrackedReward(level.getServer(), owner);
        if (existing != null && existing != reward && !existing.isRemoved()) {
            event.setCanceled(true);
            return;
        }

        VISIBLE_REWARDS.put(owner, reward.getUUID());
    }

    @SubscribeEvent
    public static void onRewardEntityUnloaded(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof StaffLeafRewardEntity reward)) return;
        UUID owner = reward.getOwnerId();
        if (owner != null) VISIBLE_REWARDS.remove(owner, reward.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        VISIBLE_REWARDS.clear();
    }

    private static boolean tryDeliver(ServerPlayer player) {
        ItemStack staff = new ItemStack(ModItems.WONDROUS_STAFF.get());
        if (player.getInventory().getSlotWithRemainingSpace(staff) < 0
                && player.getInventory().getFreeSlot() < 0) {
            return false;
        }
        boolean inserted = player.getInventory().add(staff);
        if (inserted && staff.isEmpty()) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            return true;
        }
        return false;
    }

    private static void ensureRewardEntity(MinecraftServer server, ServerPlayer player) {
        UUID owner = player.getUUID();
        StaffLeafRewardEntity reward = findTrackedReward(server, owner);
        if (reward != null && reward.level() != player.level()) {
            reward.discard();
            reward = null;
        }

        if (reward == null) {
            ServerLevel level = player.serverLevel();
            reward = new StaffLeafRewardEntity(level, owner);
            reward.setPos(player.getX(), player.getY() + 0.15D, player.getZ());
            if (!level.addFreshEntity(reward)) return;
            VISIBLE_REWARDS.put(owner, reward.getUUID());
        }

        reward.setPos(player.getX(), player.getY() + 0.15D, player.getZ());
    }

    private static StaffLeafRewardEntity findTrackedReward(MinecraftServer server, UUID owner) {
        UUID entityId = VISIBLE_REWARDS.get(owner);
        if (entityId == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof StaffLeafRewardEntity reward
                    && owner.equals(reward.getOwnerId())) {
                return reward;
            }
        }
        VISIBLE_REWARDS.remove(owner, entityId);
        return null;
    }

    private static void discardRewardEntities(MinecraftServer server, UUID owner) {
        VISIBLE_REWARDS.remove(owner);
        List<StaffLeafRewardEntity> rewards = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof StaffLeafRewardEntity reward
                        && owner.equals(reward.getOwnerId())) {
                    rewards.add(reward);
                }
            }
        }
        rewards.forEach(Entity::discard);
    }

    private static void awardAdvancement(ServerPlayer player) {
        AdvancementHolder advancement = player.getServer().getAdvancements().get(ADVANCEMENT_ID);
        if (advancement != null) {
            player.getAdvancements().award(advancement, ADVANCEMENT_CRITERION);
        }
    }
}
