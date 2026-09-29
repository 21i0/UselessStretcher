package com.sorrowmist.useless.stretcher.network;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.blockentity.OmniversalMyriadBlockEntity;
import com.sorrowmist.useless.stretcher.menu.OmniversalMyriadMenu;
import com.sorrowmist.useless.stretcher.content.item.StaffTutorialData;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffSummoning;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Network {
    private static final int MAX_RECLAIM_ENTRIES = 512;
    private Network() {
    }

    public static final int ACTION_REQUEST_STATE = 0;
    public static final int ACTION_SET_ENABLED = 1;
    public static final int ACTION_TOGGLE_PATTERNS = 2;
    public static final int ACTION_CLEAR_PATTERNS = 3;
    public static final int ACTION_TOGGLE_MOD_PATTERNS = 4;
    public static final int ACTION_FETCH_PATTERNS = 5;
    public static final int ACTION_REMOVE_PATTERNS = 6;

    public record MyriadStatePayload(BlockPos pos, List<String> enabledMolds, List<String> patternMolds,
                                     int patternCount, boolean aeBound, String progress,
                                     int part, int parts) implements CustomPacketPayload {
        public static final Type<MyriadStatePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "myriad_state"));

        public static final StreamCodec<RegistryFriendlyByteBuf, MyriadStatePayload> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeBlockPos(value.pos());
                    buffer.writeCollection(value.enabledMolds(), (buf, id) -> buf.writeUtf(id));
                    buffer.writeCollection(value.patternMolds(), (buf, id) -> buf.writeUtf(id));
                    buffer.writeVarInt(value.patternCount());
                    buffer.writeBoolean(value.aeBound());
                    buffer.writeUtf(value.progress());
                    buffer.writeVarInt(value.part());
                    buffer.writeVarInt(value.parts());
                }, buffer -> new MyriadStatePayload(buffer.readBlockPos(),
                        buffer.readList(buf -> buf.readUtf()), buffer.readList(buf -> buf.readUtf()),
                        buffer.readVarInt(), buffer.readBoolean(), buffer.readUtf(),
                        buffer.readVarInt(), buffer.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MyriadActionPayload(BlockPos pos, int action, String moldId, List<String> enabledMolds)
            implements CustomPacketPayload {
        public static final Type<MyriadActionPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "myriad_action"));

        public static final StreamCodec<RegistryFriendlyByteBuf, MyriadActionPayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, MyriadActionPayload::pos,
                ByteBufCodecs.VAR_INT, MyriadActionPayload::action,
                ByteBufCodecs.STRING_UTF8, MyriadActionPayload::moldId,
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), MyriadActionPayload::enabledMolds,
                MyriadActionPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record FullSlotsPayload(boolean full) implements CustomPacketPayload {
        public static final Type<FullSlotsPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "full_slots"));

        public static final StreamCodec<RegistryFriendlyByteBuf, FullSlotsPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, FullSlotsPayload::full,
                FullSlotsPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record StaffTutorialPayload() implements CustomPacketPayload {
        public static final Type<StaffTutorialPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "staff_tutorial"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StaffTutorialPayload> STREAM_CODEC =
                StreamCodec.of((buf, value) -> { }, buf -> new StaffTutorialPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Sent once when the player opens the X staff configuration screen. */
    public record StaffTutorialOpenedPayload() implements CustomPacketPayload {
        public static final Type<StaffTutorialOpenedPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "staff_tutorial_opened"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StaffTutorialOpenedPayload> STREAM_CODEC =
                StreamCodec.of((buf, value) -> { }, buf -> new StaffTutorialOpenedPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record WondrousStaffSpeedPayload(int speed, int mode, boolean accelerationEnabled, boolean offhand)
            implements CustomPacketPayload {
        public static final Type<WondrousStaffSpeedPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "wondrous_staff_speed"));

        public static final StreamCodec<RegistryFriendlyByteBuf, WondrousStaffSpeedPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, WondrousStaffSpeedPayload::speed,
                        ByteBufCodecs.VAR_INT, WondrousStaffSpeedPayload::mode,
                        ByteBufCodecs.BOOL, WondrousStaffSpeedPayload::accelerationEnabled,
                        ByteBufCodecs.BOOL, WondrousStaffSpeedPayload::offhand,
                        WondrousStaffSpeedPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Staff-only feature toggles edited by the X screen. */
    public record WondrousStaffFeaturesPayload(boolean summonEnabled,
                                                boolean lootRefresh, boolean offhand)
            implements CustomPacketPayload {
        public static final Type<WondrousStaffFeaturesPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID,
                        "wondrous_staff_features"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WondrousStaffFeaturesPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, WondrousStaffFeaturesPayload::summonEnabled,
                        ByteBufCodecs.BOOL, WondrousStaffFeaturesPayload::lootRefresh,
                        ByteBufCodecs.BOOL, WondrousStaffFeaturesPayload::offhand,
                        WondrousStaffFeaturesPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Selected entity registry IDs to summon. The server applies the hard count limit. */
    public record WondrousStaffSummonPayload(List<String> entityIds, boolean offhand)
            implements CustomPacketPayload {
        public static final Type<WondrousStaffSummonPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID,
                        "wondrous_staff_summon"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WondrousStaffSummonPayload> STREAM_CODEC =
                StreamCodec.of((buffer, value) -> {
                    int size = Math.min(value.entityIds().size(), WondrousStaffSummoning.MAX_SELECTION);
                    buffer.writeVarInt(size);
                    for (int i = 0; i < size; i++) buffer.writeUtf(value.entityIds().get(i), 256);
                    buffer.writeBoolean(value.offhand());
                }, buffer -> {
                    int size = buffer.readVarInt();
                    if (size < 0 || size > WondrousStaffSummoning.MAX_SELECTION) {
                        throw new IllegalArgumentException("Invalid summon selection size: " + size);
                    }
                    List<String> ids = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) ids.add(buffer.readUtf(256));
                    return new WondrousStaffSummonPayload(ids, buffer.readBoolean());
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record WondrousStaffRecallPayload(boolean offhand) implements CustomPacketPayload {
        public static final Type<WondrousStaffRecallPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "wondrous_staff_recall"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WondrousStaffRecallPayload> STREAM_CODEC =
                StreamCodec.of((buf, value) -> buf.writeBoolean(value.offhand()),
                        buf -> new WondrousStaffRecallPayload(buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** One range or machine marker shown by the operator reclaimer. */
    public record ReclaimerEntry(boolean range, UUID id, UUID owner, String ownerName,
                                 ResourceLocation dimension, BlockPos pos, String label)
            implements CustomPacketPayload {
        public static final Type<ReclaimerEntry> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "reclaimer_entry"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReclaimerEntry> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBoolean(value.range()); buf.writeUUID(value.id()); buf.writeUUID(value.owner());
                    buf.writeUtf(value.ownerName(), 64); ResourceLocation.STREAM_CODEC.encode(buf, value.dimension());
                    buf.writeBlockPos(value.pos()); buf.writeUtf(value.label(), 128);
                }, buf -> new ReclaimerEntry(buf.readBoolean(), buf.readUUID(), buf.readUUID(), buf.readUtf(64),
                        ResourceLocation.STREAM_CODEC.decode(buf), buf.readBlockPos(), buf.readUtf(128)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ReclaimerStatePayload(List<ReclaimerEntry> entries) implements CustomPacketPayload {
        public static final Type<ReclaimerStatePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "reclaimer_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReclaimerStatePayload> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    int count = Math.min(MAX_RECLAIM_ENTRIES, value.entries().size()); buf.writeVarInt(count);
                    for (int i = 0; i < count; i++) ReclaimerEntry.STREAM_CODEC.encode(buf, value.entries().get(i));
                }, buf -> {
                    int count = Math.min(MAX_RECLAIM_ENTRIES, Math.max(0, buf.readVarInt()));
                    List<ReclaimerEntry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) entries.add(ReclaimerEntry.STREAM_CODEC.decode(buf));
                    return new ReclaimerStatePayload(entries);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ReclaimerRequestPayload() implements CustomPacketPayload {
        public static final Type<ReclaimerRequestPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "reclaimer_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReclaimerRequestPayload> STREAM_CODEC =
                StreamCodec.of((buf, value) -> {}, buf -> new ReclaimerRequestPayload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ReclaimerActionPayload(boolean range, UUID id) implements CustomPacketPayload {
        public static final Type<ReclaimerActionPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "reclaimer_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReclaimerActionPayload> STREAM_CODEC =
                StreamCodec.of((buf, value) -> { buf.writeBoolean(value.range()); buf.writeUUID(value.id()); },
                        buf -> new ReclaimerActionPayload(buf.readBoolean(), buf.readUUID()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Dimension-wide visual clock used only for client cloud movement. */
    public record TimeAccelerationStatePayload(String dimension, int speed) implements CustomPacketPayload {
        public static final Type<TimeAccelerationStatePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID,
                        "time_acceleration_state"));

        public static final StreamCodec<RegistryFriendlyByteBuf, TimeAccelerationStatePayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, TimeAccelerationStatePayload::dimension,
                        ByteBufCodecs.VAR_INT, TimeAccelerationStatePayload::speed,
                        TimeAccelerationStatePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("4");

        registrar.playToServer(MyriadActionPayload.TYPE, MyriadActionPayload.STREAM_CODEC, Network::handleAction);
        registrar.playToClient(MyriadStatePayload.TYPE, MyriadStatePayload.STREAM_CODEC,
                (payload, context) -> ClientStateReceiver.accept(payload));
        registrar.playToClient(FullSlotsPayload.TYPE, FullSlotsPayload.STREAM_CODEC,
                (payload, context) -> ClientStateReceiver.handleFullSlots(payload));
        registrar.playToClient(StaffTutorialPayload.TYPE, StaffTutorialPayload.STREAM_CODEC,
                (payload, context) -> ClientStateReceiver.handleStaffTutorial());
        registrar.playToServer(StaffTutorialOpenedPayload.TYPE, StaffTutorialOpenedPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        StaffTutorialData.get(player.getServer()).markUiOpened(player.getUUID());
                    }
                }));
        registrar.playToClient(TimeAccelerationStatePayload.TYPE, TimeAccelerationStatePayload.STREAM_CODEC,
                (payload, context) -> ClientStateReceiver.handleTimeAcceleration(payload));
        registrar.playToServer(WondrousStaffSpeedPayload.TYPE, WondrousStaffSpeedPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleWondrousStaffSpeed(payload, context)));
        registrar.playToServer(WondrousStaffFeaturesPayload.TYPE, WondrousStaffFeaturesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleWondrousStaffFeatures(payload, context)));
        registrar.playToServer(WondrousStaffSummonPayload.TYPE, WondrousStaffSummonPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleWondrousStaffSummon(payload, context)));
        registrar.playToServer(WondrousStaffRecallPayload.TYPE, WondrousStaffRecallPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleWondrousStaffRecall(payload, context)));
        registrar.playToServer(ReclaimerRequestPayload.TYPE, ReclaimerRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> sendReclaimerState((ServerPlayer) context.player())));
        registrar.playToServer(ReclaimerActionPayload.TYPE, ReclaimerActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleReclaimerAction(payload, context)));
        registrar.playToClient(ReclaimerStatePayload.TYPE, ReclaimerStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientStateReceiver.handleReclaimer(payload)));
        RangeNetwork.register(registrar);
    }

    private static void handleWondrousStaffSpeed(WondrousStaffSpeedPayload payload,
                                                 net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        InteractionHand hand = payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() != com.sorrowmist.useless.stretcher.init.ModItems.WONDROUS_STAFF.get()) return;
        int speed = switch (payload.speed()) {
            case 0, 2, 4, 16, 32, 64, 128, 256, 512, 1024 -> payload.speed();
            default -> com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration.DEFAULT_GEAR;
        };
        int mode = payload.mode() >= 0
                && payload.mode() < com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration.STAFF_MODE_COUNT
                ? payload.mode()
                : com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration.STAFF_MODE_NORMAL;
        held.set(com.sorrowmist.useless.stretcher.init.StretcherComponents.WONDROUS_STAFF_SPEED.get(), speed);
        com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration.setMode(held, mode);
        held.set(com.sorrowmist.useless.core.component.UComponents.BeefTimeAccelerationEnabledComponent.get(),
                payload.accelerationEnabled());
        if (StaffTutorialData.get(player.getServer()).markHintShown(player.getUUID(),
                StaffTutorialData.HINT_GEAR_CHANGE)) {
            sendStaffTutorial(player);
        }
    }

    public static void sendWondrousStaffSpeed(int speed, int mode, boolean accelerationEnabled,
                                              InteractionHand hand) {
        PacketDistributor.sendToServer(new WondrousStaffSpeedPayload(
                speed, mode, accelerationEnabled, hand == InteractionHand.OFF_HAND));
    }

    private static void handleWondrousStaffFeatures(WondrousStaffFeaturesPayload payload,
                                                    net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        InteractionHand hand = payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(com.sorrowmist.useless.stretcher.init.ModItems.WONDROUS_STAFF.get())) return;
        held.set(StretcherComponents.WONDROUS_STAFF_SUMMON_ENABLED.get(), payload.summonEnabled());
        held.set(StretcherComponents.WONDROUS_STAFF_LOOT_REFRESH.get(),
                payload.lootRefresh()
                        && com.sorrowmist.useless.stretcher.config.StretcherConfig.enableStaffLootRefresh());
    }

    private static void handleWondrousStaffSummon(WondrousStaffSummonPayload payload,
                                                  net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        InteractionHand hand = payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(com.sorrowmist.useless.stretcher.init.ModItems.WONDROUS_STAFF.get())) return;
        WondrousStaffSummoning.summon(player, held, payload.entityIds());
    }

    private static void handleWondrousStaffRecall(WondrousStaffRecallPayload payload,
                                                   net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        ItemStack held = player.getItemInHand(payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        WondrousStaffSummoning.recall(player, held);
    }

    private static void sendReclaimerState(ServerPlayer player) {
        if (player == null || (!player.getMainHandItem().is(com.sorrowmist.useless.stretcher.init.ModItems.RANGE_RECLAIMER.get())
                && !player.getOffhandItem().is(com.sorrowmist.useless.stretcher.init.ModItems.RANGE_RECLAIMER.get()))) return;
        List<ReclaimerEntry> result = new ArrayList<>();
        RangeAccelerationSavedData data = RangeAccelerationSavedData.get(player.getServer());
        for (RangeAccelerationSavedData.Field field : data.operatorFields()) {
            if (result.size() >= MAX_RECLAIM_ENTRIES) break;
            result.add(new ReclaimerEntry(true, field.id(), field.owner(), ownerName(player, field.owner()),
                    field.dimension(), field.center(), "时间流逝 " + field.speed() + "x"));
        }
        for (ServerLevel level : player.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof WondrousStaffAccelerationEntity marker)
                        || marker.isEntityMode() || marker.isTimeMode() || marker.getOwnerUuid() == null) continue;
                if (result.size() >= MAX_RECLAIM_ENTRIES) break;
                BlockPos pos = marker.getTargetPos();
                String label = level.getBlockState(pos).getBlock().getName().getString();
                result.add(new ReclaimerEntry(false, marker.getUUID(), marker.getOwnerUuid(),
                        ownerName(player, marker.getOwnerUuid()), level.dimension().location(), pos, label));
            }
        }
        PacketDistributor.sendToPlayer(player, new ReclaimerStatePayload(result));
    }

    private static String ownerName(ServerPlayer viewer, UUID owner) {
        ServerPlayer online = viewer.getServer().getPlayerList().getPlayer(owner);
        return online != null ? online.getGameProfile().getName() : owner.toString();
    }

    private static void handleReclaimerAction(ReclaimerActionPayload payload,
                                               net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        ItemStack tool = player.getMainHandItem().is(com.sorrowmist.useless.stretcher.init.ModItems.RANGE_RECLAIMER.get())
                ? player.getMainHandItem() : player.getOffhandItem();
        if (!tool.is(com.sorrowmist.useless.stretcher.init.ModItems.RANGE_RECLAIMER.get())) return;
        boolean removed = false;
        if (payload.range()) {
            removed = RangeAccelerationSavedData.get(player.getServer())
                    .reclaimByOperator(player.getServer(), payload.id()) != null;
        } else {
            for (ServerLevel level : player.getServer().getAllLevels()) {
                Entity entity = level.getEntity(payload.id());
                if (entity instanceof WondrousStaffAccelerationEntity marker) {
                    marker.discard();
                    removed = true;
                    break;
                }
            }
        }
        player.displayClientMessage(Component.translatable(removed
                ? "msg.useless_stretcher.reclaimer.removed"
                : "msg.useless_stretcher.reclaimer.missing"), true);
        sendReclaimerState(player);
    }

    public static void sendWondrousStaffFeatures(boolean summonEnabled, boolean lootRefresh,
                                                 InteractionHand hand) {
        PacketDistributor.sendToServer(new WondrousStaffFeaturesPayload(
                summonEnabled, lootRefresh, hand == InteractionHand.OFF_HAND));
    }

    public static void sendWondrousStaffSummon(List<String> entityIds, InteractionHand hand) {
        PacketDistributor.sendToServer(new WondrousStaffSummonPayload(
                List.copyOf(entityIds), hand == InteractionHand.OFF_HAND));
    }

    public static void recallWondrousStaff(InteractionHand hand) {
        PacketDistributor.sendToServer(new WondrousStaffRecallPayload(hand == InteractionHand.OFF_HAND));
    }

    public static void requestReclaimer() {
        PacketDistributor.sendToServer(new ReclaimerRequestPayload());
    }

    public static void reclaimTarget(boolean range, UUID id) {
        PacketDistributor.sendToServer(new ReclaimerActionPayload(range, id));
    }

    public static void sendStaffTutorial(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new StaffTutorialPayload());
    }

    public static void sendStaffTutorialOpened() {
        PacketDistributor.sendToServer(new StaffTutorialOpenedPayload());
    }

    public static void sendTimeAccelerationState(net.minecraft.server.level.ServerLevel level, int speed) {
        PacketDistributor.sendToPlayersInDimension(level, new TimeAccelerationStatePayload(
                level.dimension().location().toString(), Math.max(0, speed)));
    }

    private static void handleAction(MyriadActionPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!(player.containerMenu instanceof OmniversalMyriadMenu menu)
                    || !menu.getPos().equals(payload.pos())
                    || player.distanceToSqr(payload.pos().getCenter()) > 64.0
                    || !player.level().hasChunkAt(payload.pos())) return;
            if (player.level().getBlockEntity(payload.pos()) instanceof OmniversalMyriadBlockEntity be) {
                switch (payload.action()) {
                    case ACTION_SET_ENABLED -> {
                        Set<ResourceLocation> enabled = menu.receiveSelection(payload.moldId(), payload.enabledMolds());
                        if (enabled == null) return;
                        be.setEnabledMolds(enabled);
                    }
                    case ACTION_TOGGLE_PATTERNS -> {
                        ResourceLocation moldId = ResourceLocation.tryParse(payload.moldId());
                        if (moldId != null) {
                            if (be.getRequestedPatternMolds().contains(moldId)) {
                                be.removePatterns(List.of(moldId));
                            } else {
                                be.requestPatterns(List.of(moldId));
                            }
                        }
                    }
                    case ACTION_CLEAR_PATTERNS -> be.clearPatterns();
                    case ACTION_TOGGLE_MOD_PATTERNS -> {
                        List<ResourceLocation> ids = payload.enabledMolds().stream()
                                .map(ResourceLocation::tryParse)
                                .filter(java.util.Objects::nonNull)
                                .toList();
                        Set<ResourceLocation> current = be.getRequestedPatternMolds();
                        boolean all = !ids.isEmpty() && ids.stream().allMatch(current::contains);
                        if (all) be.removePatterns(ids);
                        else be.requestPatterns(ids);
                    }
                    case ACTION_FETCH_PATTERNS, ACTION_REMOVE_PATTERNS -> {
                        Set<ResourceLocation> ids = menu.receiveSelection(
                                payload.action(), payload.moldId(), payload.enabledMolds());
                        if (ids == null) return;
                        boolean fetch = payload.action() == ACTION_FETCH_PATTERNS;
                        if (fetch) be.requestPatterns(ids);
                        else be.removePatterns(ids);
                    }
                    case ACTION_REQUEST_STATE -> {
                        // fall through to reply below
                    }
                }
                replyState(player, be);
            }
        });
    }

    public static void replyToViewers(OmniversalMyriadBlockEntity be) {
        if (!(be.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        for (ServerPlayer player : level.players()) {
            if (player.containerMenu instanceof OmniversalMyriadMenu menu
                    && menu.getPos().equals(be.getBlockPos())) replyState(player, be);
        }
    }

    private static void replyState(ServerPlayer player, OmniversalMyriadBlockEntity be) {
        if (player.containerMenu instanceof OmniversalMyriadMenu menu
                && !menu.needsSelections(be.selectionVersion())) {
            PacketDistributor.sendToPlayer(player, new MyriadStatePayload(be.getBlockPos(), List.of(), List.of(),
                    be.getPatternCount(), be.getAeRef() != null, be.getFetchProgress(), -1, 0));
            return;
        }
        var enabled = MyriadSelectionBatches.split(be.getEnabledMolds().stream().map(ResourceLocation::toString).toList());
        var patternMolds = MyriadSelectionBatches.split(be.getRequestedPatternMolds().stream().map(ResourceLocation::toString).toList());
        int patternCount = be.getPatternCount();
        int parts = Math.max(enabled.size(), patternMolds.size());
        for (int part = 0; part < parts; part++) {
            PacketDistributor.sendToPlayer(player,
                    new MyriadStatePayload(be.getBlockPos(), part < enabled.size() ? enabled.get(part) : List.of(),
                            part < patternMolds.size() ? patternMolds.get(part) : List.of(), patternCount,
                            be.getAeRef() != null, be.getFetchProgress(), part, parts));
        }
    }

    public static void requestState(BlockPos pos) {
        PacketDistributor.sendToServer(new MyriadActionPayload(pos, ACTION_REQUEST_STATE, "", List.of()));
    }

    public static void setEnabled(BlockPos pos, List<String> enabled) {
        var batches = MyriadSelectionBatches.split(enabled);
        for (int part = 0; part < batches.size(); part++) {
            String phase = batches.size() == 1 ? "single" : part == 0 ? "begin"
                    : part == batches.size() - 1 ? "end" : "append";
            PacketDistributor.sendToServer(new MyriadActionPayload(pos, ACTION_SET_ENABLED, phase, batches.get(part)));
        }
    }

    public static void togglePatterns(BlockPos pos, String moldId) {
        PacketDistributor.sendToServer(new MyriadActionPayload(pos, ACTION_TOGGLE_PATTERNS, moldId, List.of()));
    }

    public static void toggleModPatterns(BlockPos pos, List<String> moldIds) {
        PacketDistributor.sendToServer(new MyriadActionPayload(pos, ACTION_TOGGLE_MOD_PATTERNS, "", moldIds));
    }

    public static void setPatterns(BlockPos pos, List<String> moldIds, boolean fetch) {
        var batches = MyriadSelectionBatches.split(moldIds);
        for (int part = 0; part < batches.size(); part++) {
            String phase = batches.size() == 1 ? "single" : part == 0 ? "begin"
                    : part == batches.size() - 1 ? "end" : "append";
            PacketDistributor.sendToServer(new MyriadActionPayload(pos,
                    fetch ? ACTION_FETCH_PATTERNS : ACTION_REMOVE_PATTERNS, phase, batches.get(part)));
        }
    }

    public static void clearPatterns(BlockPos pos) {
        PacketDistributor.sendToServer(new MyriadActionPayload(pos, ACTION_CLEAR_PATTERNS, "", List.of()));
    }

    public static void sendFullSlots(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new FullSlotsPayload(true));
    }
}
