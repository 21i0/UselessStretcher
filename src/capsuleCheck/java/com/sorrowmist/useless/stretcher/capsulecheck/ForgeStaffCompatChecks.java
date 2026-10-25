package com.sorrowmist.useless.stretcher.capsulecheck;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.content.items.BeefToolVariants;
import com.sorrowmist.useless.stretcher.compat.StaffRadiationProtection;
import com.sorrowmist.useless.stretcher.init.ModItems;
import mekanism.api.radiation.IRadiationManager;
import mekanism.api.radiation.capability.IRadiationEntity;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.lib.radiation.RadiationManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

/** Real Forge/Mekanism methods and transformed ItemStack; never shipped. */
@Mod.EventBusSubscriber(modid = "useless_stretcher")
public final class ForgeStaffCompatChecks {
    private static int ticks;
    private static boolean done;

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("stretcher.staff.compatCheck") || done
                || event.phase != TickEvent.Phase.END || ++ticks < 40) return;
        var server = event.getServer();
        try {
            if (Boolean.getBoolean("stretcher.staff.packCheck") && !checkPackCatalog(server.overworld())) {
                check(ticks < 640, "native pack mold catalog completes without blocking the server");
                return;
            }
            done = true;
            check(ModList.get().isLoaded("mekanism"), "Mekanism is actually loaded");
            ServerPlayer player = new ServerPlayer(server, server.overworld(),
                    new GameProfile(UUID.randomUUID(), "StaffCompat"));
            Connection transport = new Connection(PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(transport);
            player.connection = new ServerGamePacketListenerImpl(server, transport, player) {
                @Override public void send(Packet<?> packet) { }
                @Override public void send(Packet<?> packet, PacketSendListener listener) { }
            };
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            player.setPos(8, 100, 8);
            check(!player.isCreative() && !player.isSpectator(), "survival-mode exposure control");
            checkRadiation(player);
            checkTags(player);
            LogUtils.getLogger().info("FORGE STAFF COMPAT CHECKS PASSED: Forge={} Mekanism={} base={} survival player, native radiation and nine dynamic wrench aliases",
                    ModList.get().getModContainerById("forge").orElseThrow().getModInfo().getVersion(),
                    ModList.get().getModContainerById("mekanism").orElseThrow().getModInfo().getVersion(),
                    ModList.get().getModContainerById("useless_mod").orElseThrow().getModInfo().getVersion());
        } catch (Throwable failure) {
            done = true;
            LogUtils.getLogger().error("FORGE STAFF COMPAT CHECKS FAILED", failure);
        }
        server.halt(false);
    }

    private static boolean checkPackCatalog(net.minecraft.server.level.ServerLevel level) {
        for (String id : new String[]{"mekanism", "mekanismgenerators", "appmek"})
            check(ModList.get().isLoaded(id), "actual pack dependency loaded: " + id);
        var catalog = com.sorrowmist.useless.stretcher.content.mold.MoldCatalog.start(level);
        if (!catalog.advance()) return false;
        var counts = new java.util.TreeMap<String, Integer>();
        for (String id : new String[]{"mekanism", "mekanismgenerators", "appmek"}) {
            var entries = catalog.result().get(id);
            check(entries != null && !entries.isEmpty(), "native pack recipe molds available: " + id);
            counts.put(id, entries.size());
        }
        LogUtils.getLogger().info("FORGE STAFF PACK CATALOG PASSED: three actual dependencies, native recipe adapters and incremental mold enumeration {}", counts);
        return true;
    }

    private static void checkRadiation(ServerPlayer player) throws Exception {
        IRadiationEntity dose = player.getCapability(Capabilities.RADIATION_ENTITY).orElseThrow(
                () -> new AssertionError("Native Mekanism player radiation capability"));
        double baseline = RadiationManager.BASELINE;
        var resistance = RadiationManager.class.getDeclaredMethod("getRadiationResistance", net.minecraft.world.entity.LivingEntity.class);
        resistance.setAccessible(true);
        var manager = RadiationManager.get();
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WONDROUS_STAFF.get()));
        check(!StaffRadiationProtection.enabled(player), "radiation protection defaults off");
        check((double) resistance.invoke(manager, player) == 0D, "unarmored disabled player has no native resistance");
        dose.set(baseline);
        IRadiationManager.INSTANCE.radiate(player, 100D);
        check(dose.getRadiation() > baseline, "disabled control receives native exposure");
        StaffRadiationProtection.configure(player, false, true);
        check(dose.getRadiation() == baseline, "enabling immediately clears existing dose");
        check((double) resistance.invoke(manager, player) == 1D, "actual instance-method shielding is complete");
        IRadiationManager.INSTANCE.radiate(player, 100D);
        check(dose.getRadiation() == baseline, "manager exposure blocked while enabled");
        float health = player.getHealth();
        float exhaustion = player.getFoodData().getExhaustionLevel();
        for (int n = 0; n < 128; n++) {
            // Capability writes and saved doses have no owner parameter in this MEK
            // version. The native update(entity) must clear them before effects.
            dose.set(1.0E9);
            dose.update(player);
            check(dose.getRadiation() == baseline && player.getHealth() == health
                    && player.getFoodData().getExhaustionLevel() == exhaustion,
                    "restored dose cleared before health/food effects");
        }
        ItemStack staff = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.getInventory().setItem(15, staff);
        check(StaffRadiationProtection.enabled(player), "inventory carry protects");
        IRadiationManager.INSTANCE.radiate(player, 100D);
        check(dose.getRadiation() == baseline, "inventory native exposure blocked");
        dose.radiate(100D);
        dose.update(player);
        check(dose.getRadiation() == baseline, "direct capability writes cleared before effects");
        ItemStack restored = ItemStack.of(staff.save(new CompoundTag()));
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.OFF_HAND, restored);
        check(StaffRadiationProtection.enabled(player), "saved offhand switch protects");
        dose.set(100D);
        dose.update(player);
        check(dose.getRadiation() == baseline, "saved offhand dose cleared");
        StaffRadiationProtection.configure(player, true, false);
        check(!StaffRadiationProtection.enabled(player) && (double) resistance.invoke(manager, player) == 0D,
                "disabling restores native resistance");
        IRadiationManager.INSTANCE.radiate(player, 100D);
        check(dose.getRadiation() > baseline, "disabled native exposure returns");
        dose.update(player);
        check(dose.getRadiation() > baseline, "disabled native update does not erase dose");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        StaffRadiationProtection.configure(player, false, true);
        check(!StaffRadiationProtection.enabled(player), "non-staff configure rejected");
        player.getInventory().clearContent();
        dose.set(baseline);
        LogUtils.getLogger().info("FORGE STAFF RADIATION PASSED: disabled control, immediate clear, native manager shielding, 128 pre-effect restored-dose checks, inventory/offhand, NBT and disable");
    }

    private static void checkTags(ServerPlayer player) {
        String[] ids = {"forge:tools/wrench", "forge:tools/wrenches", "forge:wrenches",
                "c:tools/wrench", "c:tools/wrenches", "c:wrenches",
                "gtceu:crafting_tools/wrench", "gtceu:tools/crafting_wrenches", "mekanism:configurators"};
        ItemStack staff = new ItemStack(ModItems.WONDROUS_STAFF.get());
        for (boolean enabled : new boolean[]{false, true, false}) {
            staff = BeefToolVariants.withWrenchTag(staff, enabled);
            check(staff.is(ModItems.WONDROUS_STAFF.get()), "G-menu preserves staff identity");
            for (String id : ids) {
                var tag = TagKey.create(Registries.ITEM, new ResourceLocation(id));
                check(staff.is(tag) == enabled, "dynamic wrench tag follows switch: " + id + " " + enabled);
                var stick = new ItemStack(Items.STICK);
                check(stick.is(tag) == Items.STICK.builtInRegistryHolder().is(tag), "unrelated item tag untouched");
                check(ItemStack.of(staff.save(new CompoundTag())).is(tag) == enabled, "saved wrench switch: " + id);
            }
        }
        player.getInventory().clearContent();
        LogUtils.getLogger().info("FORGE STAFF WRENCH TAGS PASSED: nine aliases x off/on/off, staff identity, item NBT, unrelated items");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
