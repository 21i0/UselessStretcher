package com.sorrowmist.useless.stretcher.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.compat.StaffRadiationProtection;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import mekanism.api.radiation.IRadiationManager;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.lib.radiation.RadiationUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.UUID;

final class StaffRadiationChecks {
    static void run(ServerLevel level) {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "StaffRadiationTest"));
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.getInventory().clearContent();
        var cap = player.getCapability(Capabilities.RADIATION_ENTITY);
        check(cap != null, "real Mekanism radiation capability");
        double baseline = IRadiationManager.INSTANCE.baselineRadiation();
        ItemStack staff = new ItemStack(ModItems.WONDROUS_STAFF.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, staff);
        check(!StaffRadiationProtection.enabled(player), "off by default");
        cap.set(baseline);
        cap.radiate(1);
        check(cap.getRadiation() > baseline, "disabled native dose increases");
        StaffRadiationProtection.configure(player, false, true);
        check(cap.getRadiation() == baseline, "enabling clears existing dose immediately");
        check(RadiationUtil.getRadiationResistance(player) == 1, "native shield pipeline is fully resistant");
        IRadiationManager.INSTANCE.radiate(player, 100);
        cap.radiate(100);
        check(cap.getRadiation() == baseline, "manager and direct capability exposure both blocked");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.getInventory().setItem(15, staff);
        cap.radiate(100);
        check(StaffRadiationProtection.enabled(player) && cap.getRadiation() == baseline, "inventory carry retains protection");
        // A preexisting dose restored by another system is cleared before native effects run.
        cap.set(10);
        cap.update();
        check(cap.getRadiation() == baseline, "native effect update clears restored dose");
        var encoded = ItemStack.CODEC.encodeStart(level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), staff).getOrThrow();
        var restored = ItemStack.CODEC.parse(level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), encoded).getOrThrow();
        check(restored.getOrDefault(StretcherComponents.WONDROUS_STAFF_RADIATION_PROTECTION.get(), false), "switch survives item serialization");
        player.getInventory().setItem(15, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, restored);
        check(StaffRadiationProtection.enabled(player), "offhand restored item protects");
        StaffRadiationProtection.configure(player, true, false);
        cap.radiate(1);
        check(!StaffRadiationProtection.enabled(player) && cap.getRadiation() > baseline
                && RadiationUtil.getRadiationResistance(player) < 1, "disabled restores normal native exposure");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        StaffRadiationProtection.configure(player, false, true);
        check(!StaffRadiationProtection.enabled(player), "non-staff request rejected");
        player.getInventory().clearContent();
        cap.set(baseline);
        LogUtils.getLogger().info("STAFF MEKANISM RADIATION CHECKS PASSED: default off, existing dose clear, full native shielding, direct exposure guard, inventory/offhand, saved switch and disable/non-staff validation");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError("staff radiation: " + message);
    }
}
