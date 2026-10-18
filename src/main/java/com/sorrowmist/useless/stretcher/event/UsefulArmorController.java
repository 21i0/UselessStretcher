package com.sorrowmist.useless.stretcher.event;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-authoritative full-set creative lock, independent of the crafting recipe switch. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class UsefulArmorController {
    private static final String FORCED = "useless_stretcher_useful_armor_forced";
    private static final String PREVIOUS_MODE = "useless_stretcher_useful_armor_previous_mode";

    private UsefulArmorController() { }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag data = player.getPersistentData();
        boolean forced = data.getBoolean(FORCED);
        boolean complete = hasCompleteSet(player);
        if (complete) {
            if (!forced) {
                data.putBoolean(FORCED, true);
                data.putInt(PREVIOUS_MODE, player.gameMode.getGameModeForPlayer().getId());
            }
            if (!player.gameMode.getGameModeForPlayer().isCreative()) {
                changeMode(player, GameType.CREATIVE);
            }
            return;
        }
        if (forced) restore(player, data);
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        CompoundTag source = event.getOriginal().getPersistentData();
        CompoundTag target = event.getEntity().getPersistentData();
        if (source.getBoolean(FORCED)) {
            target.putBoolean(FORCED, true);
            target.putInt(PREVIOUS_MODE, source.getInt(PREVIOUS_MODE));
        }
    }

    private static void restore(ServerPlayer player, CompoundTag data) {
        int previous = data.getInt(PREVIOUS_MODE);
        GameType mode = GameType.byNullableId(previous);
        if (mode == null) mode = GameType.SURVIVAL;
        data.remove(FORCED);
        data.remove(PREVIOUS_MODE);
        if (player.gameMode.getGameModeForPlayer() != mode) changeMode(player, mode);
    }

    private static void changeMode(ServerPlayer player, GameType mode) {
        player.setGameMode(mode);
        // A command-level game-mode event may deny the change. The suit is authoritative
        // and also guards the lower-level controller used directly by other mods.
        if (player.gameMode.getGameModeForPlayer() != mode && player.gameMode.changeGameModeForPlayer(mode)) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                    net.minecraft.network.protocol.game.ClientboundGameEventPacket.CHANGE_GAME_MODE, mode.getId()));
            if (mode != GameType.SPECTATOR) player.setCamera(player);
        }
    }

    public static boolean hasCompleteSet(ServerPlayer player) {
        return isPiece(player.getItemBySlot(EquipmentSlot.HEAD), ModItems.USEFUL_ARMOR_HELMET.get())
                && isPiece(player.getItemBySlot(EquipmentSlot.CHEST), ModItems.USEFUL_ARMOR_CHESTPLATE.get())
                && isPiece(player.getItemBySlot(EquipmentSlot.LEGS), ModItems.USEFUL_ARMOR_LEGGINGS.get())
                && isPiece(player.getItemBySlot(EquipmentSlot.FEET), ModItems.USEFUL_ARMOR_BOOTS.get());
    }

    private static boolean isPiece(ItemStack stack, net.minecraft.world.item.Item item) {
        return stack.is(item);
    }
}
