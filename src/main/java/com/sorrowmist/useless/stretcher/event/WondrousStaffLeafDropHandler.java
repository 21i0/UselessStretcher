package com.sorrowmist.useless.stretcher.event;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.content.entity.StaffLeafRewardEntity;
import com.sorrowmist.useless.stretcher.content.leaf.StaffLeafDropData;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.api.enums.tool.ToolTypeMode;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Rolls the one-time staff and 2i doll independently for tree leaves. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class WondrousStaffLeafDropHandler {
    private static final ResourceLocation ADVANCEMENT_ID =
            ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "staff_leaf_drop");
    private static final String ADVANCEMENT_CRITERION = "main";
    private static final Map<UUID, Map<Boolean, UUID>> VISIBLE_REWARDS = new HashMap<>();

    private WondrousStaffLeafDropHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockDrops(BlockDropsEvent event) {
        if (event.isCanceled()
                || !(event.getBreaker() instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator() || !player.isAlive() || player.isRemoved()
                || !isLeafBlock(event.getState())) return;

        boolean staffEnabled = StretcherConfig.enableStaffLeafDrop();
        boolean dollEnabled = StretcherConfig.enableDollLeafDrop();
        if (!staffEnabled && !dollEnabled) return;

        StaffLeafDropData data = StaffLeafDropData.get(player.getServer());
        UUID playerId = player.getUUID();
        boolean awarded = false;
        if (staffEnabled && !data.hasRolledStaff(playerId)
                && event.getLevel().getRandom().nextDouble() < StretcherConfig.staffLeafDropProbability()
                && data.beginStaffDelivery(playerId)) {
            awardAdvancement(player);
            awarded = true;
        }
        if (dollEnabled && !data.hasRolledDoll(playerId)
                && event.getLevel().getRandom().nextDouble() < StretcherConfig.dollLeafDropProbability()
                && data.beginDollDelivery(playerId)) {
            awarded = true;
        }
        if (!awarded && !data.isPending(playerId)) return;

        deliverAvailable(player, data);
        ensurePendingRewards(player.getServer(), player, data);
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

            deliverAvailable(player, data);
            ensurePendingRewards(server, player, data);
        }
    }

    @SubscribeEvent
    public static void onRewardEntityLoaded(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof StaffLeafRewardEntity reward)) {
            return;
        }

        UUID owner = reward.getOwnerId();
        StaffLeafDropData data = StaffLeafDropData.get(level.getServer());
        if (owner == null || (reward.isDollReward()
                ? !data.hasPendingDoll(owner) : !data.hasPendingStaff(owner))) {
            event.setCanceled(true);
            return;
        }

        StaffLeafRewardEntity existing = findTrackedReward(level.getServer(), owner, reward.isDollReward());
        if (existing != null && existing != reward && !existing.isRemoved()) {
            event.setCanceled(true);
            return;
        }

        VISIBLE_REWARDS.computeIfAbsent(owner, ignored -> new HashMap<>())
                .put(reward.isDollReward(), reward.getUUID());
    }

    @SubscribeEvent
    public static void onRewardEntityUnloaded(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        if (!(event.getEntity() instanceof StaffLeafRewardEntity reward)) return;
        UUID owner = reward.getOwnerId();
        if (owner != null) {
            Map<Boolean, UUID> rewards = VISIBLE_REWARDS.get(owner);
            if (rewards != null) {
                rewards.remove(reward.isDollReward(), reward.getUUID());
                if (rewards.isEmpty()) VISIBLE_REWARDS.remove(owner);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        VISIBLE_REWARDS.clear();
    }

    private static void deliverAvailable(ServerPlayer player, StaffLeafDropData data) {
        UUID id = player.getUUID();
        boolean changed = false;
        if (data.hasPendingStaff(id) && tryInsert(player, createLeafStaff(player))) {
            data.markStaffDelivered(id);
            discardRewardEntity(player.getServer(), id, false);
            changed = true;
        }
        ItemStack doll = new ItemStack(ModItems.TWO_I_DOLL.get());
        doll.set(com.sorrowmist.useless.stretcher.init.StretcherComponents.TWO_I_OWNER.get(), id);
        com.sorrowmist.useless.stretcher.content.item.TwoIDollItem.ensureIdentity(doll);
        ItemStack acquiredDoll = doll.copy();
        if (data.hasPendingDoll(id) && tryInsert(player, doll)) {
            data.markDollDelivered(id);
            discardRewardEntity(player.getServer(), id, true);
            com.sorrowmist.useless.stretcher.content.item.TwoIDollAcquisition.notifyAcquired(player, acquiredDoll);
            changed = true;
        }
        if (changed) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }

    private static void ensurePendingRewards(MinecraftServer server, ServerPlayer player, StaffLeafDropData data) {
        if (data.hasPendingStaff(player.getUUID())) ensureRewardEntity(server, player, false);
        if (data.hasPendingDoll(player.getUUID())) ensureRewardEntity(server, player, true);
    }

    private static boolean tryInsert(ServerPlayer player, ItemStack stack) {
        if (player.getInventory().getSlotWithRemainingSpace(stack) < 0
                && player.getInventory().getFreeSlot() < 0) return false;
        return player.getInventory().add(stack) && stack.isEmpty();
    }

    /** Builds the same fully initialized precise-mode tool as a newly obtained base staff. */
    private static ItemStack createLeafStaff(ServerPlayer player) {
        ItemStack staff = new ItemStack(ModItems.WONDROUS_STAFF.get());
        staff.set(UComponents.EnchantModeComponent.get(), EnchantMode.SILK_TOUCH);
        staff.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
        var registry = player.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var silk = registry.getHolderOrThrow(Enchantments.SILK_TOUCH);
        var fortune = registry.getHolderOrThrow(Enchantments.FORTUNE);
        EnchantmentHelper.updateEnchantments(staff, enchantments -> {
            enchantments.set(silk, 1);
            enchantments.set(fortune, 0);
        });
        staff.set(UComponents.CurrentToolTypeComponent.get(), ToolTypeMode.NONE_MODE);
        staff.set(UComponents.BeefCaptureEnabledComponent.get(), false);
        staff.set(UComponents.BeefInvulnerabilityEnabledComponent.get(), true);
        staff.remove(UComponents.EnhancedChainMiningComponent.get());
        staff.remove(UComponents.ForceMiningComponent.get());
        staff.remove(UComponents.ForceKillEnabledComponent.get());
        staff.set(StretcherComponents.WONDROUS_STAFF_MODE.get(), 0);
        staff.set(StretcherComponents.WONDROUS_STAFF_PERMANENT.get(), false);
        staff.set(StretcherComponents.WONDROUS_STAFF_SUMMON_ENABLED.get(), false);
        staff.set(StretcherComponents.WONDROUS_STAFF_LOOT_REFRESH.get(), false);
        return staff;
    }


    private static void ensureRewardEntity(MinecraftServer server, ServerPlayer player, boolean doll) {
        UUID owner = player.getUUID();
        StaffLeafRewardEntity reward = findTrackedReward(server, owner, doll);
        if (reward != null && reward.level() != player.level()) {
            reward.discard();
            reward = null;
        }

        if (reward == null) {
            ServerLevel level = player.serverLevel();
            reward = new StaffLeafRewardEntity(level, owner, doll);
            reward.setPos(player.getX(), player.getY() + 0.15D, player.getZ());
            if (!level.addFreshEntity(reward)) return;
            VISIBLE_REWARDS.computeIfAbsent(owner, ignored -> new HashMap<>()).put(doll, reward.getUUID());
        }

        reward.setPos(player.getX(), player.getY() + 0.15D, player.getZ());
    }

    private static StaffLeafRewardEntity findTrackedReward(MinecraftServer server, UUID owner, boolean doll) {
        Map<Boolean, UUID> rewards = VISIBLE_REWARDS.get(owner);
        UUID entityId = rewards == null ? null : rewards.get(doll);
        if (entityId == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof StaffLeafRewardEntity reward
                    && !reward.isRemoved() && reward.isDollReward() == doll
                    && owner.equals(reward.getOwnerId())) {
                return reward;
            }
        }
        if (rewards != null) {
            rewards.remove(doll, entityId);
            if (rewards.isEmpty()) VISIBLE_REWARDS.remove(owner);
        }
        return null;
    }

    private static void discardRewardEntity(MinecraftServer server, UUID owner, boolean doll) {
        StaffLeafRewardEntity reward = findTrackedReward(server, owner, doll);
        if (reward != null) reward.discard();
    }

    private static void awardAdvancement(ServerPlayer player) {
        AdvancementHolder advancement = player.getServer().getAdvancements().get(ADVANCEMENT_ID);
        if (advancement != null) {
            player.getAdvancements().award(advancement, ADVANCEMENT_CRITERION);
        }
    }
}
