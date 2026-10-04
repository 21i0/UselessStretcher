package com.sorrowmist.useless.stretcher.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.content.apotheosis.ApotheosisStaffSettings;
import com.sorrowmist.useless.stretcher.content.apotheosis.ApotheosisTableData;
import com.sorrowmist.useless.stretcher.content.item.WondrousStaffRightClickHandler;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.network.ApotheosisNetwork;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

/** 1.5.3 tests; this source is excluded from ordinary builds and release jars. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class ApotheosisChecks {
    @SubscribeEvent
    public static void run(ServerStartedEvent event) {
        if (!Boolean.getBoolean("useless_stretcher.regression")) return;
        try {
            var level = event.getServer().overworld();
            var dimension = level.dimension().location();
            UUID owner = UUID.randomUUID();
            BlockPos zero = new BlockPos(2, 153, 2);
            BlockPos other = new BlockPos(3, 153, 2);
            var data = new ApotheosisTableData();
            check(data.set(owner, dimension, zero, ApotheosisTableData.Boost.ZERO), "zero bonus binding created");
            check(!data.set(owner, dimension, zero, ApotheosisTableData.Boost.ZERO), "unchanged bind does not dirty again");
            data.set(owner, dimension, other, new ApotheosisTableData.Boost(40, 20, 10, 3));
            var restored = ApotheosisTableData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            check(restored.contains(dimension, zero) && owner.equals(restored.owner(dimension, zero)), "zero binding/owner survives save");
            var boost = new ApotheosisTableData.Boost(60, 50, 40, 8);
            check(restored.updateOwned(owner, boost) == 2, "both persistent tables receive a live edit");
            check(restored.updateOwned(owner, boost) == 0, "repeated value never triggers menu broadcasts");
            check(restored.ownedTables(owner, dimension, 1).size() == 1, "bounded marker lookup");
            ItemStack staff = new ItemStack(ModItems.WONDROUS_STAFF.get());
            ApotheosisStaffSettings.setBoost(staff, boost);
            ApotheosisStaffSettings.setSelectionMode(staff, true);
            var stackCopy = ItemStack.parseOptional(level.registryAccess(), (CompoundTag) staff.save(level.registryAccess()));
            check(ApotheosisStaffSettings.boost(stackCopy).equals(boost)
                    && ApotheosisStaffSettings.selectionMode(stackCopy), "preset and selection survive item roundtrip");
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            try {
                var settings = new ApotheosisNetwork.SettingsPayload(true, true, "pinnacle", boost);
                ApotheosisNetwork.SettingsPayload.CODEC.encode(buf, settings);
                check(settings.equals(ApotheosisNetwork.SettingsPayload.CODEC.decode(buf)), "settings acknowledgment packet");
                var markers = new ApotheosisNetwork.MarkersPayload(dimension, List.of(zero, other));
                ApotheosisNetwork.MarkersPayload.CODEC.encode(buf, markers);
                check(markers.equals(ApotheosisNetwork.MarkersPayload.CODEC.decode(buf)), "marker packet");
            } finally { buf.release(); }
            if (ApotheosisStaffSettings.isEnchantingAvailable()) {
                boolean config = StretcherConfig.enableApotheosisCompat();
                try {
                    StretcherConfig.SERVER_APOTHEOSIS_COMPAT.set(true);
                    var player = new RecordingPlayer(level, new GameProfile(owner, "ApothRegression"));
                    player.setItemInHand(InteractionHand.MAIN_HAND, staff);
                    player.setPos(2.5, 154, 3.5);
                    staff.set(com.sorrowmist.useless.core.component.UComponents.BeefTimeAccelerationEnabledComponent.get(), false);
                    level.setBlockAndUpdate(zero, Blocks.ENCHANTING_TABLE.defaultBlockState());
                    var interaction = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, zero,
                            new BlockHitResult(Vec3.atCenterOf(zero), Direction.UP, zero, false));
                    WondrousStaffRightClickHandler.onRightClickBlock(interaction);
                    check(interaction.isCanceled() && ApotheosisTableData.get(level, zero).equals(boost),
                            "table selection intercepts ordinary click with acceleration off");
                    check(ApotheosisStaffSettings.selectionMode(staff), "binding keeps selection mode active");
                    checkNativeEnchanting(player, zero, boost);
                    player.packets.clear();
                    check(ApotheosisStaffSettings.setTier(player, "pinnacle")
                            && ApotheosisStaffSettings.currentTier(player).equals("pinnacle"), "native world tier upgrade");
                    checkNativeTierUnlocks(player, "PINNACLE");
                    check(player.packets.stream().anyMatch(ClientboundUpdateAdvancementsPacket.class::isInstance),
                            "tier selection broadcasts native advancement unlocks");
                    check(player.packets.stream().anyMatch(ClientboundAwardStatsPacket.class::isInstance),
                            "tier selection broadcasts native tutorial statistics");
                    player.packets.clear();
                    check(ApotheosisStaffSettings.setTier(player, "haven")
                            && ApotheosisStaffSettings.currentTier(player).equals("haven"), "native world tier downgrade");
                    check(staff.getOrDefault(StretcherComponents.APOTHEOSIS_WORLD_TIER.get(), "").equals("haven"),
                            "tier preset saved on staff");
                    checkNativeTierUnlocks(player, "HAVEN");
                    player.packets.clear();
                    check(ApotheosisStaffSettings.setTier(player, "haven"), "same-tier synchronization remains valid");
                    checkNativeTierUnlocks(player, "HAVEN");
                    LogUtils.getLogger().info("REGRESSION Apotheosis native: table gatherStats, live menu refresh, all Ctrl+T unlocks and tier payload roundtrips passed");
                } finally { StretcherConfig.SERVER_APOTHEOSIS_COMPAT.set(config); }
            } else LogUtils.getLogger().info("REGRESSION Apotheosis: optional native integration skipped because Apotheosis is absent");
            LogUtils.getLogger().info("REGRESSION Apotheosis: persistent presets, zero bindings, changed-only edits and bounded packets passed");
        } catch (Throwable failure) {
            LogUtils.getLogger().error("REGRESSION APOTHEOSIS FAILED", failure);
            event.getServer().halt(false);
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void checkNativeEnchanting(ServerPlayer player, BlockPos pos,
                                              ApotheosisTableData.Boost boost) throws ReflectiveOperationException {
        var level = player.serverLevel();
        Class<?> statsClass = Class.forName("dev.shadowsoffire.apothic_enchanting.table.EnchantmentTableStats");
        boolean config = StretcherConfig.enableApotheosisCompat();
        try {
            StretcherConfig.SERVER_APOTHEOSIS_COMPAT.set(false);
            Object baseline = gatherNativeStats(statsClass, level, pos);
            StretcherConfig.SERVER_APOTHEOSIS_COMPAT.set(true);
            Object enhanced = gatherNativeStats(statsClass, level, pos);
            checkStats(baseline, enhanced, boost, "native gatherStats");

            Class<?> menuClass = Class.forName("dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentMenu");
            Class<?> handlerClass = Class.forName("dev.shadowsoffire.apothic_enchanting.table.EnchantmentTableItemHandler");
            Object handler = handlerClass.getConstructor().newInstance();
            EnchantmentMenu menu;
            try {
                menu = (EnchantmentMenu) menuClass.getConstructor(int.class, Inventory.class,
                        ContainerLevelAccess.class, handlerClass).newInstance(21, player.getInventory(),
                        ContainerLevelAccess.create(level, pos), handler);
            } catch (NoSuchMethodException newerConstructor) {
                menu = (EnchantmentMenu) menuClass.getConstructor(int.class, Inventory.class,
                        ContainerLevelAccess.class, handlerClass, BlockPos.class).newInstance(21,
                        player.getInventory(), ContainerLevelAccess.create(level, pos), handler, pos);
            }
            var statsField = menuClass.getDeclaredField("stats");
            statsField.setAccessible(true);
            var gather = menuClass.getMethod("gatherStats");
            menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
            ItemStack originalItem = menu.getSlot(0).getItem().copy();
            int originalSeed = player.getEnchantmentSeed();
            StretcherConfig.SERVER_APOTHEOSIS_COMPAT.set(false);
            gather.invoke(menu);
            Object menuBaseline = statsField.get(menu);
            StretcherConfig.SERVER_APOTHEOSIS_COMPAT.set(true);
            gather.invoke(menu);
            checkStats(menuBaseline, statsField.get(menu), boost, "native menu gatherStats");

            var changed = new ApotheosisTableData.Boost(20, 30, 10, 2);
            check(ApotheosisTableData.get(player.getServer()).updateOwned(player.getUUID(), changed) == 1,
                    "live preset updates the actual saved table binding");
            check(menu instanceof com.sorrowmist.useless.stretcher.content.apotheosis.ApotheosisMenuRefresh,
                    "native menu has the optional refresh bridge");
            ((com.sorrowmist.useless.stretcher.content.apotheosis.ApotheosisMenuRefresh) menu).uselessStretcher$refreshStats();
            checkStats(menuBaseline, statsField.get(menu), changed, "live native slot-change refresh");
            check(ItemStack.matches(originalItem, menu.getSlot(0).getItem())
                    && player.getEnchantmentSeed() == originalSeed, "live refresh preserves item and enchantment seed");
            ApotheosisTableData.get(player.getServer()).updateOwned(player.getUUID(), boost);
        } finally { StretcherConfig.SERVER_APOTHEOSIS_COMPAT.set(config); }
    }

    private static Object gatherNativeStats(Class<?> statsClass, ServerLevel level, BlockPos pos)
            throws ReflectiveOperationException {
        try {
            return statsClass.getMethod("gatherStats", LevelReader.class, BlockPos.class, int.class)
                    .invoke(null, level, pos, 20);
        } catch (NoSuchMethodException legacySignature) {
            return statsClass.getMethod("gatherStats", LevelReader.class, BlockPos.class).invoke(null, level, pos);
        }
    }

    private static void checkStats(Object baseline, Object enhanced, ApotheosisTableData.Boost boost, String label)
            throws ReflectiveOperationException {
        String[] names = {"eterna", "quanta", "arcana", "clues"};
        int[] bonuses = {boost.eterna(), boost.quanta(), boost.arcana(), boost.clues()};
        for (int i = 0; i < names.length; i++) {
            var getter = baseline.getClass().getMethod(names[i]);
            double original = ((Number) getter.invoke(baseline)).doubleValue();
            double actual = ((Number) getter.invoke(enhanced)).doubleValue();
            double expected = i == 3 ? original + bonuses[i] : Math.min(100, original + bonuses[i]);
            check(Math.abs(actual - expected) < 0.001, label + " " + names[i] + ": expected " + expected + ", got " + actual);
        }
        for (String name : List.of("blacklist", "treasure", "stable")) {
            var getter = baseline.getClass().getMethod(name);
            check(getter.invoke(baseline).equals(getter.invoke(enhanced)), label + " preserves native " + name);
        }
    }

    private static void checkNativeTierUnlocks(RecordingPlayer player, String expectedTier) throws ReflectiveOperationException {
        Class<?> tierClass = Class.forName("dev.shadowsoffire.apotheosis.tiers.WorldTier");
        Object active = tierClass.getMethod("getTier", Player.class).invoke(null, player);
        check(((Enum<?>) active).name().equals(expectedTier), "native current tier agrees with selector");
        for (Object tier : tierClass.getEnumConstants()) {
            ResourceLocation id = (ResourceLocation) tierClass.getMethod("getUnlockAdvancement").invoke(tier);
            var advancement = player.getServer().getAdvancements().get(id);
            check(advancement != null, "native tier advancement exists: " + id);
            check(player.getAdvancements().getOrStartProgress(advancement).isDone(), "native tier advancement granted: " + id);
            check((Boolean) tierClass.getMethod("isUnlocked", Player.class, tierClass).invoke(null, player, tier),
                    "Ctrl+T native unlock predicate agrees: " + tier);
        }
        check(!(Boolean) tierClass.getMethod("isTutorialActive", Player.class).invoke(null, player),
                "native tutorial no longer hides synchronized tier selection");
        Class<?> packetClass = Class.forName("dev.shadowsoffire.apotheosis.net.WorldTierPayload");
        Object packet = packetClass.getConstructor(tierClass).newInstance(active);
        check(player.packets.stream().filter(ClientboundCustomPayloadPacket.class::isInstance)
                        .map(ClientboundCustomPayloadPacket.class::cast).map(ClientboundCustomPayloadPacket::payload)
                        .anyMatch(packet::equals), "actual native tier payload sent to client");
        @SuppressWarnings("unchecked")
        StreamCodec<io.netty.buffer.ByteBuf, Object> codec =
                (StreamCodec<io.netty.buffer.ByteBuf, Object>) packetClass.getField("CODEC").get(null);
        var buffer = Unpooled.buffer();
        try {
            codec.encode(buffer, packet);
            check(packet.equals(codec.decode(buffer)), "native client tier synchronization payload roundtrip");
        } finally { buffer.release(); }
    }

    /** Retains native stat behavior and captures outbound packets without a network client. */
    private static final class RecordingPlayer extends net.minecraft.server.level.ServerPlayer {
        private final List<Packet<?>> packets = new ArrayList<>();

        private RecordingPlayer(ServerLevel level, GameProfile profile) {
            super(level.getServer(), level, profile, net.minecraft.server.level.ClientInformation.createDefault());
            Connection transport = new Connection(PacketFlow.SERVERBOUND) {
                @Override public void setListenerForServerboundHandshake(PacketListener listener) { }
            };
            connection = new ServerGamePacketListenerImpl(level.getServer(), transport, this,
                    CommonListenerCookie.createInitial(profile, false)) {
                @Override public void send(Packet<?> packet) { packets.add(packet); }
                @Override public void send(Packet<?> packet, PacketSendListener listener) { packets.add(packet); }
            };
        }

        @Override public void awardStat(Stat<?> stat, int amount) {
            getStats().increment(this, stat, amount);
        }
    }
}
