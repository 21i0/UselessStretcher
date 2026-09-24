package com.sorrowmist.useless.stretcher.content.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Refreshes vanilla and Lootr loot containers without taking a compile-time Lootr dependency. */
public final class WondrousStaffLootRefresh {
    private static final String SOURCE_LOOT_TABLE = "useless_stretcher:source_loot_table";
    private static final String LOOTR_PROVIDER = "noobanidus.mods.lootr.common.api.data.ILootrInfoProvider";

    private static boolean lootrLookupDone;
    private static Class<?> lootrProviderClass;
    private static Method lootrRefreshMethod;

    private WondrousStaffLootRefresh() {
    }

    /** Called by mixins before vanilla discards the table after the first opening. */
    public static void remember(BlockEntity blockEntity, ResourceKey<LootTable> lootTable) {
        if (lootTable != null) remember(blockEntity.getPersistentData(), lootTable);
    }

    /** Called by mixins before a container minecart discards its table after the first opening. */
    public static void remember(Entity entity, ResourceKey<LootTable> lootTable) {
        if (lootTable != null) remember(entity.getPersistentData(), lootTable);
    }

    public static InteractionResult tryRefreshBlock(Player player, BlockPos pos) {
        Level level = player.level();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!isSupportedBlockEntity(blockEntity)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        RefreshResult result;
        if (isLootrProvider(blockEntity)) {
            result = refreshLootr(blockEntity);
        } else {
            result = refreshVanillaBlock(player, (RandomizableContainerBlockEntity) blockEntity);
            BlockPos connected = connectedChestPos(level, pos);
            if (connected != null && level.getBlockEntity(connected) instanceof RandomizableContainerBlockEntity other) {
                RefreshResult otherResult = refreshVanillaBlock(player, other);
                if (result != RefreshResult.SUCCESS && otherResult == RefreshResult.SUCCESS) {
                    result = RefreshResult.SUCCESS;
                }
            }
        }
        notifyResult(player, result, isLootrProvider(blockEntity));
        return InteractionResult.SUCCESS;
    }

    public static boolean isSupportedEntity(Entity entity) {
        return entity instanceof AbstractMinecartContainer || isLootrProvider(entity);
    }

    public static InteractionResult tryRefreshEntity(Player player, Entity entity) {
        if (!isSupportedEntity(entity)) return InteractionResult.PASS;
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;

        boolean lootr = isLootrProvider(entity);
        RefreshResult result = lootr
                ? refreshLootr(entity)
                : refreshVanillaMinecart(player, (AbstractMinecartContainer) entity);
        notifyResult(player, result, lootr);
        return InteractionResult.SUCCESS;
    }

    private static boolean isSupportedBlockEntity(BlockEntity blockEntity) {
        return blockEntity instanceof RandomizableContainerBlockEntity || isLootrProvider(blockEntity);
    }

    private static RefreshResult refreshVanillaBlock(Player player,
                                                       RandomizableContainerBlockEntity container) {
        ResourceKey<LootTable> source = sourceLootTable(container.getPersistentData(), container.getLootTable());
        if (source == null) return RefreshResult.MISSING_SOURCE;
        try {
            container.clearContent();
            container.setLootTable(source, player.getRandom().nextLong());
            container.unpackLootTable(player);
            container.setChanged();
            return RefreshResult.SUCCESS;
        } catch (RuntimeException ignored) {
            return RefreshResult.FAILED;
        }
    }

    private static RefreshResult refreshVanillaMinecart(Player player, AbstractMinecartContainer container) {
        ResourceKey<LootTable> source = sourceLootTable(container.getPersistentData(), container.getLootTable());
        if (source == null) return RefreshResult.MISSING_SOURCE;
        try {
            container.clearItemStacks();
            container.setLootTable(source, player.getRandom().nextLong());
            container.unpackChestVehicleLootTable(player);
            container.setChanged();
            return RefreshResult.SUCCESS;
        } catch (RuntimeException ignored) {
            return RefreshResult.FAILED;
        }
    }

    private static RefreshResult refreshLootr(Object provider) {
        ensureLootrApi();
        if (lootrProviderClass == null || lootrRefreshMethod == null || !lootrProviderClass.isInstance(provider)) {
            return RefreshResult.FAILED;
        }
        try {
            lootrRefreshMethod.invoke(provider);
            return RefreshResult.SUCCESS;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return RefreshResult.FAILED;
        }
    }

    private static boolean isLootrProvider(Object target) {
        if (target == null || !ModList.get().isLoaded("lootr")) return false;
        ensureLootrApi();
        return lootrProviderClass != null && lootrProviderClass.isInstance(target);
    }

    private static synchronized void ensureLootrApi() {
        if (lootrLookupDone) return;
        lootrLookupDone = true;
        if (!ModList.get().isLoaded("lootr")) return;
        try {
            lootrProviderClass = Class.forName(LOOTR_PROVIDER, false,
                    WondrousStaffLootRefresh.class.getClassLoader());
            lootrRefreshMethod = lootrProviderClass.getMethod("performRefresh");
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError ignored) {
            lootrProviderClass = null;
            lootrRefreshMethod = null;
        }
    }

    private static void remember(CompoundTag tag, ResourceKey<LootTable> lootTable) {
        tag.putString(SOURCE_LOOT_TABLE, lootTable.location().toString());
    }

    private static ResourceKey<LootTable> sourceLootTable(CompoundTag tag,
                                                           ResourceKey<LootTable> current) {
        if (current != null) remember(tag, current);
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(SOURCE_LOOT_TABLE));
        return id == null ? null : ResourceKey.create(Registries.LOOT_TABLE, id);
    }

    private static BlockPos connectedChestPos(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)
                || !state.hasProperty(ChestBlock.TYPE)
                || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return null;
        }
        return pos.relative(ChestBlock.getConnectedDirection(state));
    }

    private static void notifyResult(Player player, RefreshResult result, boolean lootr) {
        String key = switch (result) {
            case SUCCESS -> lootr
                    ? "msg.useless_stretcher.loot_refresh.lootr_success"
                    : "msg.useless_stretcher.loot_refresh.success";
            case MISSING_SOURCE -> "msg.useless_stretcher.loot_refresh.missing_source";
            case FAILED -> "msg.useless_stretcher.loot_refresh.failed";
        };
        player.displayClientMessage(Component.translatable(key), true);
    }

    private enum RefreshResult {
        SUCCESS,
        MISSING_SOURCE,
        FAILED
    }
}
