package com.sorrowmist.useless.stretcher.regression;

import com.mojang.authlib.GameProfile;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.UUID;

/** Actual vanilla inventory equipment/swap paths, not a custom packet or duplicate doll entity. */
final class TwoIDollHeadwearChecks {
    private TwoIDollHeadwearChecks() { }

    static void run(ServerLevel level) {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TwoIHeadwear"));
        player.getAbilities().instabuild = false;
        player.getInventory().selected = 0;
        var menu = player.inventoryMenu;
        ItemStack original = new ItemStack(ModItems.TWO_I_DOLL.get());
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(original, StretcherComponents.TWO_I_BOND.get(), 47);
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(original, StretcherComponents.TWO_I_INVITATION_USED.get(), true);
        original.setHoverName(Component.literal("2i headwear memory"));
        CompoundTag future = new CompoundTag();
        future.putString("future_skin", "unchanged-by-equipment");
        original.getOrCreateTag().merge(future);
        try {
            check(player.getEquipmentSlotForItem(original) == EquipmentSlot.HEAD,
                    "vanilla equipment lookup selects head slot");
            check(menu.getSlot(5).mayPlace(original) && !menu.getSlot(6).mayPlace(original)
                            && !menu.getSlot(7).mayPlace(original) && !menu.getSlot(8).mayPlace(original),
                    "ordinary survival inventory accepts doll only in helmet armor slot");
            player.setItemInHand(InteractionHand.MAIN_HAND, original.copy());
            menu.quickMoveStack(player, 36);
            unchanged(original, player.getItemBySlot(EquipmentSlot.HEAD), "shift-click from hand equips doll");
            check(player.getMainHandItem().isEmpty() && player.getInventory().countItem(ModItems.TWO_I_DOLL.get()) == 1,
                    "equipping moves one item without duplication");
            menu.clicked(5, 40, ClickType.SWAP, player);
            unchanged(original, player.getOffhandItem(), "F swap takes head doll into offhand");
            check(player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "offhand pickup clears helmet slot");
            menu.quickMoveStack(player, 45);
            unchanged(original, player.getItemBySlot(EquipmentSlot.HEAD), "offhand shift-click equips same data");
            check(player.getOffhandItem().isEmpty(), "offhand equip consumes the moved stack");
            menu.clicked(5, 0, ClickType.SWAP, player);
            unchanged(original, player.getMainHandItem(), "number-key swap takes doll into main hand");
            check(player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "mainhand pickup clears helmet slot");
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            menu.quickMoveStack(player, 36);
            check(player.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),
                    "shift-click never replaces existing helmet");
            check(player.getInventory().countItem(ModItems.TWO_I_DOLL.get()) == 1,
                    "occupied helmet still retains exactly one doll");
            boolean found = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack current = player.getInventory().getItem(i);
                if (current.is(ModItems.TWO_I_DOLL.get())) {
                    unchanged(original, current, "occupied-slot transfer preserves every component");
                    found = true;
                }
            }
            check(found, "doll remains retrievable in normal inventory");
            com.mojang.logging.LogUtils.getLogger().info("REGRESSION 2i headwear: survival helmet slot, shift-click, main/offhand swaps, occupied helmet and full component preservation passed");
        } finally {
            menu.setCarried(ItemStack.EMPTY);
            player.getInventory().clearContent();
        }
    }

    private static void unchanged(ItemStack original, ItemStack current, String message) {
        check(current.getCount() == 1 && ItemStack.isSameItemSameTags(original, current), message);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError("2i headwear: " + message);
    }
}
