package com.sorrowmist.useless.stretcher.network;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.entity.TimeFlowEntity;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSettings;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Packets for configuring, placing and managing persistent acceleration ranges. */
public final class RangeNetwork {
    private static final int MAX_HISTORY = RangeAccelerationSavedData.MAX_HISTORY_PER_OWNER;

    private RangeNetwork() {
    }

    public record SettingsPayload(boolean offhand, boolean placementMode, boolean filterMarkingMode,
                                  boolean markSleepList,
                                  int sizeX, int sizeY, int sizeZ,
                                  boolean accelerationWhitelistMode,
                                  boolean sleepWhitelistMode) implements CustomPacketPayload {
        public static final Type<SettingsPayload> TYPE = RangeNetwork.type("range_settings");
        public static final StreamCodec<RegistryFriendlyByteBuf, SettingsPayload> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBoolean(value.offhand);
                    buf.writeBoolean(value.placementMode);
                    buf.writeBoolean(value.filterMarkingMode);
                    buf.writeBoolean(value.markSleepList);
                    buf.writeVarInt(value.sizeX);
                    buf.writeVarInt(value.sizeY);
                    buf.writeVarInt(value.sizeZ);
                    buf.writeBoolean(value.accelerationWhitelistMode);
                    buf.writeBoolean(value.sleepWhitelistMode);
                },
                buf -> new SettingsPayload(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readBoolean(), buf.readBoolean()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record PlacePayload(boolean offhand, BlockPos clickedPos, Direction face)
            implements CustomPacketPayload {
        public static final Type<PlacePayload> TYPE = RangeNetwork.type("range_place");
        public static final StreamCodec<RegistryFriendlyByteBuf, PlacePayload> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBoolean(value.offhand);
                    buf.writeBlockPos(value.clickedPos);
                    buf.writeEnum(value.face);
                },
                buf -> new PlacePayload(buf.readBoolean(), buf.readBlockPos(), buf.readEnum(Direction.class)));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record FilterTogglePayload(boolean offhand, BlockPos targetPos, boolean sameType)
            implements CustomPacketPayload {
        public static final Type<FilterTogglePayload> TYPE = RangeNetwork.type("range_filter_toggle");
        public static final StreamCodec<RegistryFriendlyByteBuf, FilterTogglePayload> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBoolean(value.offhand);
                    buf.writeBlockPos(value.targetPos);
                    buf.writeBoolean(value.sameType);
                },
                buf -> new FilterTogglePayload(buf.readBoolean(), buf.readBlockPos(), buf.readBoolean()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record HistoryRequestPayload() implements CustomPacketPayload {
        public static final Type<HistoryRequestPayload> TYPE = RangeNetwork.type("range_history_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, HistoryRequestPayload> STREAM_CODEC =
                StreamCodec.of((buf, value) -> { }, buf -> new HistoryRequestPayload());

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record HistoryTogglePayload(UUID id, boolean enabled) implements CustomPacketPayload {
        public static final Type<HistoryTogglePayload> TYPE = RangeNetwork.type("range_history_toggle");
        public static final StreamCodec<RegistryFriendlyByteBuf, HistoryTogglePayload> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeUUID(value.id);
                    buf.writeBoolean(value.enabled);
                },
                buf -> new HistoryTogglePayload(buf.readUUID(), buf.readBoolean()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record HistoryReclaimPayload(UUID id) implements CustomPacketPayload {
        public static final Type<HistoryReclaimPayload> TYPE = RangeNetwork.type("range_history_reclaim");
        public static final StreamCodec<RegistryFriendlyByteBuf, HistoryReclaimPayload> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> buf.writeUUID(value.id),
                buf -> new HistoryReclaimPayload(buf.readUUID()));

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record HistoryStatePayload(List<RangeAccelerationSavedData.Summary> fields)
            implements CustomPacketPayload {
        public static final Type<HistoryStatePayload> TYPE = RangeNetwork.type("range_history_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, HistoryStatePayload> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    if (value.fields.size() > MAX_HISTORY) throw new IllegalArgumentException("Too many range fields");
                    buf.writeVarInt(value.fields.size());
                    for (RangeAccelerationSavedData.Summary field : value.fields) {
                        buf.writeUUID(field.id());
                        ResourceLocation.STREAM_CODEC.encode(buf, field.dimension());
                        buf.writeBlockPos(field.center());
                        buf.writeLong(field.createdAt());
                        buf.writeBoolean(field.enabled());
                        buf.writeVarInt(field.speed());
                        buf.writeVarInt(field.sizeX());
                        buf.writeVarInt(field.sizeY());
                        buf.writeVarInt(field.sizeZ());
                    }
                },
                buf -> {
                    int count = buf.readVarInt();
                    if (count < 0 || count > MAX_HISTORY) throw new IllegalArgumentException("Invalid range history size");
                    List<RangeAccelerationSavedData.Summary> fields = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        fields.add(new RangeAccelerationSavedData.Summary(
                                buf.readUUID(), ResourceLocation.STREAM_CODEC.decode(buf), buf.readBlockPos(),
                                buf.readLong(), buf.readBoolean(), buf.readVarInt(),
                                buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
                    }
                    return new HistoryStatePayload(fields);
                });

        public HistoryStatePayload {
            fields = List.copyOf(fields);
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(SettingsPayload.TYPE, SettingsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleSettings(payload, context)));
        registrar.playToServer(PlacePayload.TYPE, PlacePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handlePlace(payload, context)));
        registrar.playToServer(FilterTogglePayload.TYPE, FilterTogglePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleFilterToggle(payload, context)));
        registrar.playToServer(HistoryRequestPayload.TYPE, HistoryRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> sendHistory((ServerPlayer) context.player())));
        registrar.playToServer(HistoryTogglePayload.TYPE, HistoryTogglePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleHistoryToggle(payload, context)));
        registrar.playToServer(HistoryReclaimPayload.TYPE, HistoryReclaimPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleHistoryReclaim(payload, context)));
        registrar.playToClient(HistoryStatePayload.TYPE, HistoryStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientStateReceiver.handleRangeHistory(payload)));
    }

    public static void sendSettings(InteractionHand hand, boolean placementMode, boolean filterMarkingMode,
                                    boolean markSleepList,
                                    int sizeX, int sizeY, int sizeZ,
                                    boolean accelerationWhitelistMode,
                                    boolean sleepWhitelistMode) {
        PacketDistributor.sendToServer(new SettingsPayload(hand == InteractionHand.OFF_HAND,
                placementMode, filterMarkingMode, markSleepList, sizeX, sizeY, sizeZ,
                accelerationWhitelistMode, sleepWhitelistMode));
    }

    public static void place(InteractionHand hand, BlockPos clickedPos, Direction face) {
        PacketDistributor.sendToServer(new PlacePayload(hand == InteractionHand.OFF_HAND, clickedPos, face));
    }

    public static void toggleFilter(InteractionHand hand, BlockPos targetPos, boolean sameType) {
        PacketDistributor.sendToServer(new FilterTogglePayload(
                hand == InteractionHand.OFF_HAND, targetPos, sameType));
    }

    public static void requestHistory() {
        PacketDistributor.sendToServer(new HistoryRequestPayload());
    }

    public static void setHistoryEnabled(UUID id, boolean enabled) {
        PacketDistributor.sendToServer(new HistoryTogglePayload(id, enabled));
    }

    public static void reclaimHistory(UUID id) {
        PacketDistributor.sendToServer(new HistoryReclaimPayload(id));
    }

    public static BlockPos placementCenter(net.minecraft.world.level.Level level,
                                           BlockPos clickedPos, Direction face) {
        // A valid acceleration target is the intended anchor even when it has no block entity
        // (for example crops, saplings, random-ticking leaves, or lightning rods). Static blocks
        // remain a surface placement and use the adjacent cell instead.
        return WondrousStaffAcceleration.isValidTarget(level, clickedPos)
                ? clickedPos.immutable() : clickedPos.relative(face).immutable();
    }

    private static void handleSettings(SettingsPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        ItemStack staff = heldStaff(player, payload.offhand);
        if (staff.isEmpty()) return;
        RangeAccelerationSettings.set(staff, payload.placementMode, payload.filterMarkingMode,
                payload.markSleepList,
                payload.sizeX, payload.sizeY, payload.sizeZ,
                payload.accelerationWhitelistMode, payload.sleepWhitelistMode);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static void handlePlace(PlacePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        ItemStack staff = heldStaff(player, payload.offhand);
        if (staff.isEmpty() || !RangeAccelerationSettings.placementMode(staff)
                || RangeAccelerationSettings.filterMarkingMode(staff)
                || !WondrousStaffAcceleration.isEnabled(staff)
                || WondrousStaffAcceleration.getSpeed(staff) <= 0
                || !player.isShiftKeyDown()) return;

        ServerLevel level = player.serverLevel();
        if (!near(player, payload.clickedPos)) return;
        BlockPos center = placementCenter(level, payload.clickedPos, payload.face);
        if (!level.isLoaded(center) || !level.mayInteract(player, center)) return;

        RangeAccelerationSavedData data = RangeAccelerationSavedData.get(level.getServer());
        if (!data.acquirePlacement(player.getUUID(), level.getGameTime())) return;
        RangeAccelerationSavedData.PlacementResult result = data.place(level, player, center, staff);
        if (result.status() == RangeAccelerationSavedData.PlacementStatus.OCCUPIED) {
            player.displayClientMessage(Component.translatable("msg.useless_stretcher.range_occupied"), true);
            return;
        }
        if (result.status() == RangeAccelerationSavedData.PlacementStatus.LIMIT_REACHED) {
            player.displayClientMessage(Component.translatable("msg.useless_stretcher.range_limit",
                    RangeAccelerationSavedData.MAX_ACTIVE_FIELDS_PER_OWNER), true);
            return;
        }

        UUID fieldId = result.id();
        RangeAccelerationSavedData.Field field = data.getField(fieldId);
        RangeAccelerationSettings.setMarkCounts(staff,
                field.accelerationMarkCount(), field.sleepMarkCount());
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        if (level.getEntity(fieldId) instanceof TimeFlowEntity existing) {
            existing.sync(field);
        } else {
            TimeFlowEntity marker = new TimeFlowEntity(level, fieldId);
            marker.sync(field);
            if (!level.addFreshEntity(marker)
                    && result.status() == RangeAccelerationSavedData.PlacementStatus.CREATED) {
                data.remove(fieldId);
                return;
            }
        }
        player.displayClientMessage(Component.translatable(result.status()
                        == RangeAccelerationSavedData.PlacementStatus.UPDATED
                        ? "msg.useless_stretcher.range_updated"
                        : "msg.useless_stretcher.range_placed",
                center.getX(), center.getY(), center.getZ()), true);
    }

    private static void handleFilterToggle(FilterTogglePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !near(player, payload.targetPos)) return;
        ItemStack staff = heldStaff(player, payload.offhand);
        if (staff.isEmpty() || !RangeAccelerationSettings.filterMarkingMode(staff)) return;
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(payload.targetPos) || !level.mayInteract(player, payload.targetPos)) return;
        boolean sleepList = RangeAccelerationSettings.markSleepList(staff);
        boolean whitelist = sleepList
                ? RangeAccelerationSettings.sleepWhitelistMode(staff)
                : RangeAccelerationSettings.whitelistMode(staff);
        RangeAccelerationSavedData.MarkResult result = RangeAccelerationSavedData.get(level.getServer())
                .toggleMarks(level, player.getUUID(), payload.targetPos,
                        sleepList, payload.sameType, whitelist);
        if (result.status() == RangeAccelerationSavedData.MarkStatus.NO_RANGE) {
            player.displayClientMessage(Component.translatable("msg.useless_stretcher.range_mark_no_range"), true);
            return;
        }
        if (result.status() == RangeAccelerationSavedData.MarkStatus.INVALID_TARGET) {
            player.displayClientMessage(Component.translatable("msg.useless_stretcher.range_mark_invalid"), true);
            return;
        }
        if (result.status() == RangeAccelerationSavedData.MarkStatus.FULL) {
            player.displayClientMessage(Component.translatable("msg.useless_stretcher.range_filter_full"), true);
            return;
        }

        RangeAccelerationSettings.setMarkCounts(staff,
                result.field().accelerationMarkCount(), result.field().sleepMarkCount());
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        Component listName = Component.translatable(listNameKey(sleepList, whitelist));
        player.displayClientMessage(Component.translatable(result.added()
                        ? "msg.useless_stretcher.range_filter_added"
                        : "msg.useless_stretcher.range_filter_removed",
                listName, result.affected(), result.listSize()), true);
    }

    private static void handleHistoryToggle(HistoryTogglePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        RangeAccelerationSavedData data = RangeAccelerationSavedData.get(player.getServer());
        data.setEnabled(player.getServer(), player.getUUID(), payload.id, payload.enabled);
        sendHistory(player);
    }

    private static void handleHistoryReclaim(HistoryReclaimPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        RangeAccelerationSavedData data = RangeAccelerationSavedData.get(player.getServer());
        RangeAccelerationSavedData.Summary reclaimed = data.reclaim(
                player.getServer(), player.getUUID(), payload.id());
        if (reclaimed != null) {
            player.displayClientMessage(Component.translatable("msg.useless_stretcher.range_reclaimed",
                    reclaimed.center().getX(), reclaimed.center().getY(), reclaimed.center().getZ()), true);
        }
        sendHistory(player);
    }

    private static void sendHistory(ServerPlayer player) {
        List<RangeAccelerationSavedData.Summary> history =
                RangeAccelerationSavedData.get(player.getServer()).history(player.getUUID());
        if (history.size() > MAX_HISTORY) history = history.subList(0, MAX_HISTORY);
        PacketDistributor.sendToPlayer(player, new HistoryStatePayload(history));
    }

    private static ItemStack heldStaff(ServerPlayer player, boolean offhand) {
        ItemStack stack = player.getItemInHand(offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        return stack.is(ModItems.WONDROUS_STAFF.get()) ? stack : ItemStack.EMPTY;
    }

    private static boolean near(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= 100.0D;
    }

    private static String listNameKey(boolean sleepList, boolean whitelist) {
        if (sleepList) return whitelist
                ? "gui.useless_stretcher.range.sleep_whitelist"
                : "gui.useless_stretcher.range.sleep_blacklist";
        return whitelist
                ? "gui.useless_stretcher.range.acceleration_whitelist"
                : "gui.useless_stretcher.range.acceleration_blacklist";
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, path));
    }

}
