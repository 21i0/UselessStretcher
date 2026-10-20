package com.sorrowmist.useless.stretcher.content.apotheosis;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import net.minecraftforge.fml.ModList;
import com.sorrowmist.useless.compat.forge1201.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;

/** Optional Apotheosis bridge. Reflection keeps Apotheosis and Apothic Enchanting runtime-optional. */
public final class ApotheosisStaffSettings {
    private static final String WORLD_TIER_CLASS = "dev.shadowsoffire.apotheosis.tiers.WorldTier";

    private ApotheosisStaffSettings() { }

    public static boolean isAvailable() {
        return isWorldTierAvailable() || isEnchantingAvailable();
    }

    /** Apotheosis 7 (MC 1.20.1) contains the enchanting module itself. */
    public static boolean isEnchantingAvailable() {
        if (ModList.get().isLoaded("apothic_enchanting")) return true;
        if (!isApotheosisLoaded()) return false;
        try {
            return Class.forName("dev.shadowsoffire.apotheosis.Apotheosis", false,
                    ApotheosisStaffSettings.class.getClassLoader()).getField("enableEnch").getBoolean(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    /** World tiers were introduced after MC 1.20.1. Do not advertise absent APIs. */
    public static boolean isWorldTierAvailable() {
        if (!isApotheosisLoaded()) return false;
        try {
            Class.forName(WORLD_TIER_CLASS, false, ApotheosisStaffSettings.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    public static boolean isApotheosisLoaded() {
        return ModList.get().isLoaded("apotheosis");
    }

    public static ApotheosisTableData.Boost boost(ItemStack stack) {
        return new ApotheosisTableData.Boost(
                com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(stack, StretcherComponents.APOTHEOSIS_ETERNA.get(), 0),
                com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(stack, StretcherComponents.APOTHEOSIS_QUANTA.get(), 0),
                com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(stack, StretcherComponents.APOTHEOSIS_ARCANA.get(), 0),
                com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(stack, StretcherComponents.APOTHEOSIS_CLUES.get(), 0)).normalized();
    }

    public static void setBoost(ItemStack stack, ApotheosisTableData.Boost boost) {
        ApotheosisTableData.Boost value = boost.normalized();
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(stack, StretcherComponents.APOTHEOSIS_ETERNA.get(), value.eterna());
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(stack, StretcherComponents.APOTHEOSIS_QUANTA.get(), value.quanta());
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(stack, StretcherComponents.APOTHEOSIS_ARCANA.get(), value.arcana());
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(stack, StretcherComponents.APOTHEOSIS_CLUES.get(), value.clues());
    }

    public static boolean selectionMode(ItemStack stack) {
        return com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(stack, StretcherComponents.APOTHEOSIS_TABLE_SELECTION.get(), false);
    }

    public static void setSelectionMode(ItemStack stack, boolean enabled) {
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(stack, StretcherComponents.APOTHEOSIS_TABLE_SELECTION.get(), enabled);
    }

    public static boolean isTable(BlockState state) {
        return state.getBlock() instanceof EnchantmentTableBlock;
    }

    public static boolean markTable(ServerPlayer player, net.minecraft.world.InteractionHand hand,
                                    net.minecraft.core.BlockPos pos) {
        ItemStack held = player.getItemInHand(hand);
        if (!com.sorrowmist.useless.stretcher.config.StretcherConfig.enableApotheosisCompat()
                || !isEnchantingAvailable()
                || !(held.getItem() instanceof com.sorrowmist.useless.stretcher.content.item.WondrousStaffItem)
                || !selectionMode(held) || !player.level().hasChunkAt(pos)
                || player.distanceToSqr(pos.getCenter()) > 64.0D || !isTable(player.level().getBlockState(pos))) return false;
        var data = ApotheosisTableData.get(player.getServer());
        var dimension = player.level().dimension().location();
        var previousOwner = data.owner(dimension, pos);
        boolean changed = data.set(player.getUUID(), dimension, pos, boost(held));
        if (changed) ApotheosisTableData.refreshOpenMenus(player.getServer());
        if (changed || !player.getUUID().equals(previousOwner)) {
            com.sorrowmist.useless.stretcher.network.ApotheosisNetwork.sendMarkers(player);
            if (previousOwner != null && !previousOwner.equals(player.getUUID())) {
                var previousPlayer = player.getServer().getPlayerList().getPlayer(previousOwner);
                if (previousPlayer != null) com.sorrowmist.useless.stretcher.network.ApotheosisNetwork.sendMarkers(previousPlayer);
            }
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "msg.useless_stretcher.apotheosis.table_set"), true);
        return true;
    }

    public static boolean setTier(ServerPlayer player, String name) {
        if (!isWorldTierAvailable()) return false;
        try {
            ClassLoader loader = player.getClass().getClassLoader();
            Class<?> tierClass = Class.forName(WORLD_TIER_CLASS, true, loader);
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object tier = Enum.valueOf((Class<? extends Enum>) tierClass.asSubclass(Enum.class), name.toUpperCase(java.util.Locale.ROOT));
            // Ctrl+T determines availability from progression advancements, separately from
            // the active tier attachment. Preserve all existing unlocks when downgrading.
            for (Object candidate : tierClass.getEnumConstants()) {
                if (((Enum<?>) candidate).ordinal() > ((Enum<?>) tier).ordinal()) continue;
                ResourceLocation id = (ResourceLocation) tierClass.getMethod("getUnlockAdvancement").invoke(candidate);
                var advancement = player.getServer().getAdvancements().getAdvancement(id);
                if (advancement == null) continue;
                var progress = player.getAdvancements().getOrStartProgress(advancement);
                java.util.List<String> remaining = new java.util.ArrayList<>();
                progress.getRemainingCriteria().forEach(remaining::add);
                for (String criterion : remaining) {
                    player.getAdvancements().award(advancement, criterion);
                }
            }
            tierClass.getMethod("setTier", Player.class, tierClass).invoke(null, player, tier);
            // Native setTier may return early if already selected. Always send its native
            // payload and newly unlocked advancements so Ctrl+T immediately agrees.
            Class<?> packetClass = Class.forName("dev.shadowsoffire.apotheosis.net.WorldTierPayload", true, loader);
            Object packet = packetClass.getConstructor(tierClass).newInstance(tier);
            if (packet instanceof CustomPacketPayload payload) PacketDistributor.sendToPlayer(player, payload);
            player.getAdvancements().flushDirty(player);
            player.getStats().sendStats(player);
            for (var hand : net.minecraft.world.InteractionHand.values()) {
                var held = player.getItemInHand(hand);
                if (held.getItem() instanceof com.sorrowmist.useless.stretcher.content.item.WondrousStaffItem) {
                    com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(held, StretcherComponents.APOTHEOSIS_WORLD_TIER.get(), name.toLowerCase(java.util.Locale.ROOT));
                }
            }
            player.inventoryMenu.broadcastChanges();
            return true;
        } catch (ReflectiveOperationException | LinkageError | IllegalArgumentException exception) {
            LogUtils.getLogger().warn("Could not set Apotheosis world tier {} for {}", name, player.getGameProfile().getName(), exception);
            return false;
        }
    }

    public static String currentTier(Player player) {
        if (!isWorldTierAvailable() || player == null) return "haven";
        try {
            Class<?> tierClass = Class.forName(WORLD_TIER_CLASS, true, player.getClass().getClassLoader());
            Object tier = tierClass.getMethod("getTier", Player.class).invoke(null, player);
            return tier instanceof Enum<?> value ? value.name().toLowerCase(java.util.Locale.ROOT) : "haven";
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return "haven";
        }
    }
}
