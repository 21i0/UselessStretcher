package com.sorrowmist.useless.stretcher.storytest;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollMemory;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.ForgeHooksClient;

import java.util.UUID;

/** Exercises the real MinecraftForge hook used by ItemInHandRenderer after inventory synchronization. */
public final class TwoIHandStabilityChecks {
    private TwoIHandStabilityChecks() { }

    public static void run() throws Exception {
        var selectedSlot = ForgeHooksClient.class.getDeclaredField("slotMainHand");
        selectedSlot.setAccessible(true);
        int originalSlot = selectedSlot.getInt(null);
        try {
            selectedSlot.setInt(null, 0);
            for (int slot : new int[]{0, -1}) {
                ItemStack displayed = new ItemStack(ModItems.TWO_I_DOLL.get());
                com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(displayed, StretcherComponents.TWO_I_DOLL_ID.get(), new UUID(0, slot == -1 ? 9 : 8));
                for (int second = 1; second <= 600; second++) {
                    // Container synchronization replaces the stack object even while the player holds still.
                    ItemStack synced = displayed.copy();
                    com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(synced, StretcherComponents.TWO_I_OWNER.get(), new UUID(0, 7));
                    com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(synced, StretcherComponents.TWO_I_MEMORY.get(), new TwoIDollMemory(
                            new TwoIDollMemory.Cooldowns(0, 0, 0, 0, 0, 0, -1, false),
                            new TwoIDollMemory.Interaction(0, 0, 0, 0, Math.min(2, second / 300),
                                    second % 300 * 20, -1, "", 0, 0), second * 20L));
                    com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(synced, StretcherComponents.TWO_I_BOND.get(), Math.min(2, second / 300));
                    com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(synced, StretcherComponents.TWO_I_INVITATION_USED.get(), second > 500);
                    com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(synced, StretcherComponents.TWO_I_ROTATION.get(), second % 16);
                    check(!ForgeHooksClient.shouldCauseReequipAnimation(displayed, synced, slot),
                            "stationary carried doll must not reequip after companion/owner/bond updates in slot " + slot);
                    displayed = synced;
                }
                check(!ForgeHooksClient.shouldCauseReequipAnimation(displayed, displayed.copy(), slot),
                        "identical item resync must not move the hand");
                check(ForgeHooksClient.shouldCauseReequipAnimation(displayed, ItemStack.EMPTY, slot),
                        "putting the doll away still animates");
                check(ForgeHooksClient.shouldCauseReequipAnimation(ItemStack.EMPTY, displayed, slot),
                        "taking the doll out still animates");
                check(ForgeHooksClient.shouldCauseReequipAnimation(displayed, new ItemStack(Items.STONE), slot),
                        "switching to another item still animates");
            }
            ItemStack doll = new ItemStack(ModItems.TWO_I_DOLL.get());
            com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(doll, StretcherComponents.TWO_I_DOLL_ID.get(), new UUID(0, 8));
            ItemStack otherDoll = doll.copy();
            com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(otherDoll, StretcherComponents.TWO_I_DOLL_ID.get(), new UUID(0, 9));
            // Player action: repeatedly exchange the two otherwise identical dolls between the hands.
            selectedSlot.setInt(null, 0);
            for (int swap = 0; swap < 100; swap++) {
                check(ForgeHooksClient.shouldCauseReequipAnimation(doll, otherDoll, 0),
                        "dual-doll hand swap must still animate the main hand");
                check(ForgeHooksClient.shouldCauseReequipAnimation(otherDoll, doll, -1),
                        "dual-doll hand swap must still animate the off hand");
                ItemStack previous = doll; doll = otherDoll; otherDoll = previous;
            }
            selectedSlot.setInt(null, 0);
            check(ForgeHooksClient.shouldCauseReequipAnimation(doll, doll.copy(), 1),
                    "switching hotbar slots still animates even between dolls");
            ItemStack stone = new ItemStack(Items.STONE);
            check(ForgeHooksClient.shouldCauseReequipAnimation(stone, stone.copy(), -1),
                    "the ordinary item's hook remains unchanged");
            LogUtils.getLogger().info("2I HAND STABILITY PASSED: 1200 real sync hooks without idle reequip; 100 dual-doll hand swaps animate both hands; put-away/take-out/item/slot changes preserved");
        } finally {
            selectedSlot.setInt(null, originalSlot);
        }
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError("2i hand stability: " + message);
    }
}
