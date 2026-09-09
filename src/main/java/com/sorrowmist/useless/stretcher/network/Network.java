package com.sorrowmist.useless.stretcher.network;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.blockentity.OmniversalMyriadBlockEntity;
import com.sorrowmist.useless.stretcher.content.mold.PatternFetcher;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class Network {
    private Network() {
    }

    public static final int ACTION_REQUEST_STATE = 0;
    public static final int ACTION_SET_ENABLED = 1;
    public static final int ACTION_TOGGLE_PATTERNS = 2;
    public static final int ACTION_CLEAR_PATTERNS = 3;
    public static final int ACTION_TOGGLE_MOD_PATTERNS = 4;

    public record MyriadStatePayload(BlockPos pos, List<String> enabledMolds, List<String> patternMolds,
                                     int patternCount, boolean aeBound) implements CustomPacketPayload {
        public static final Type<MyriadStatePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "myriad_state"));

        public static final StreamCodec<RegistryFriendlyByteBuf, MyriadStatePayload> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, MyriadStatePayload::pos,
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), MyriadStatePayload::enabledMolds,
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), MyriadStatePayload::patternMolds,
                ByteBufCodecs.VAR_INT, MyriadStatePayload::patternCount,
                ByteBufCodecs.BOOL, MyriadStatePayload::aeBound,
                MyriadStatePayload::new);

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

    public record WondrousStaffSpeedPayload(int speed, boolean permanent) implements CustomPacketPayload {
        public static final Type<WondrousStaffSpeedPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "wondrous_staff_speed"));

        public static final StreamCodec<RegistryFriendlyByteBuf, WondrousStaffSpeedPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, WondrousStaffSpeedPayload::speed,
                        ByteBufCodecs.BOOL, WondrousStaffSpeedPayload::permanent,
                        WondrousStaffSpeedPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(UselessStretcherMod.MODID);

        registrar.playToServer(MyriadActionPayload.TYPE, MyriadActionPayload.STREAM_CODEC, Network::handleAction);
        registrar.playToClient(MyriadStatePayload.TYPE, MyriadStatePayload.STREAM_CODEC,
                (payload, context) -> ClientStateReceiver.accept(payload));
        registrar.playToClient(FullSlotsPayload.TYPE, FullSlotsPayload.STREAM_CODEC,
                (payload, context) -> ClientStateReceiver.handleFullSlots(payload));
        registrar.playToServer(WondrousStaffSpeedPayload.TYPE, WondrousStaffSpeedPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleWondrousStaffSpeed(payload, context)));
    }

    private static void handleWondrousStaffSpeed(WondrousStaffSpeedPayload payload,
                                                 net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        ItemStack held = player.getMainHandItem();
        if (held.getItem() != com.sorrowmist.useless.stretcher.init.ModItems.WONDROUS_STAFF.get()) return;
        held.set(com.sorrowmist.useless.stretcher.init.StretcherComponents.WONDROUS_STAFF_SPEED.get(), payload.speed());
        held.set(com.sorrowmist.useless.stretcher.init.StretcherComponents.WONDROUS_STAFF_PERMANENT.get(), payload.permanent());
    }

    public static void sendWondrousStaffSpeed(int speed, boolean permanent) {
        PacketDistributor.sendToServer(new WondrousStaffSpeedPayload(speed, permanent));
    }

    private static void handleAction(MyriadActionPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player.level().getBlockEntity(payload.pos()) instanceof OmniversalMyriadBlockEntity be) {
                switch (payload.action()) {
                    case ACTION_SET_ENABLED -> {
                        List<ResourceLocation> enabled = payload.enabledMolds().stream()
                                .map(ResourceLocation::tryParse)
                                .filter(java.util.Objects::nonNull)
                                .toList();
                        be.setEnabledMolds(enabled);
                    }
                    case ACTION_TOGGLE_PATTERNS -> {
                        ResourceLocation moldId = ResourceLocation.tryParse(payload.moldId());
                        if (moldId != null) {
                            if (be.getPatternMolds().contains(moldId)) {
                                be.setMoldPatterns(moldId, List.of());
                            } else {
                                List<ItemStack> fetched = PatternFetcher.fetchForMold(player.level(), payload.pos(), be.getAeNodePos(), moldId.toString());
                                be.setMoldPatterns(moldId, fetched);
                            }
                        }
                    }
                    case ACTION_CLEAR_PATTERNS -> be.clearPatterns();
                    case ACTION_TOGGLE_MOD_PATTERNS -> {
                        List<ResourceLocation> ids = payload.enabledMolds().stream()
                                .map(ResourceLocation::tryParse)
                                .filter(java.util.Objects::nonNull)
                                .toList();
                        Set<ResourceLocation> current = be.getPatternMolds();
                        boolean all = !ids.isEmpty() && ids.stream().allMatch(current::contains);
                        for (ResourceLocation id : ids) {
                            if (all) {
                                be.setMoldPatterns(id, List.of());
                            } else {
                                be.setMoldPatterns(id, PatternFetcher.fetchForMold(player.level(), payload.pos(), be.getAeNodePos(), id.toString()));
                            }
                        }
                    }
                    case ACTION_REQUEST_STATE -> {
                        // fall through to reply below
                    }
                }
                replyState(player, be);
            }
        });
    }

    private static void replyState(ServerPlayer player, OmniversalMyriadBlockEntity be) {
        List<String> enabled = be.getEnabledMolds().stream().map(ResourceLocation::toString).toList();
        List<String> patternMolds = be.getPatternMolds().stream().map(ResourceLocation::toString).toList();
        int patternCount = be.getPatterns().size();
        PacketDistributor.sendToPlayer(player,
                new MyriadStatePayload(be.getBlockPos(), enabled, patternMolds, patternCount, be.getAeRef() != null));
    }

    public static void requestState(BlockPos pos) {
        PacketDistributor.sendToServer(new MyriadActionPayload(pos, ACTION_REQUEST_STATE, "", List.of()));
    }

    public static void setEnabled(BlockPos pos, List<String> enabled) {
        PacketDistributor.sendToServer(new MyriadActionPayload(pos, ACTION_SET_ENABLED, "", enabled));
    }

    public static void togglePatterns(BlockPos pos, String moldId) {
        PacketDistributor.sendToServer(new MyriadActionPayload(pos, ACTION_TOGGLE_PATTERNS, moldId, List.of()));
    }

    public static void toggleModPatterns(BlockPos pos, List<String> moldIds) {
        PacketDistributor.sendToServer(new MyriadActionPayload(pos, ACTION_TOGGLE_MOD_PATTERNS, "", moldIds));
    }

    public static void clearPatterns(BlockPos pos) {
        PacketDistributor.sendToServer(new MyriadActionPayload(pos, ACTION_CLEAR_PATTERNS, "", List.of()));
    }

    public static void sendFullSlots(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new FullSlotsPayload(true));
    }
}
