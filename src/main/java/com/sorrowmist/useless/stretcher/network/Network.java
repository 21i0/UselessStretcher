package com.sorrowmist.useless.stretcher.network;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.blockentity.OmniversalMyriadBlockEntity;
import com.sorrowmist.useless.stretcher.menu.OmniversalMyriadMenu;
import com.sorrowmist.useless.stretcher.content.item.StaffTutorialData;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffSummoning;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.config.ServerConfigSync;
import com.sorrowmist.useless.stretcher.content.acceleration.PermanentAccelerationHistory;
import com.sorrowmist.useless.stretcher.content.mold.MoldCatalogService;
import com.sorrowmist.useless.stretcher.content.apotheosis.ApotheosisStaffSettings;
import com.sorrowmist.useless.stretcher.content.apotheosis.ApotheosisTableData;
import com.sorrowmist.useless.stretcher.content.entity.TimeStopManager;
import com.sorrowmist.useless.stretcher.content.mold.MyriadPatternStore;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
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
    public static final int ACTION_REQUEST_CATALOG = 7;

    public record MoldCatalogPayload(BlockPos pos, int part, int parts, String status, String progress,
                                     List<String> sourceIds, List<String> itemIds)
            implements CustomPacketPayload {
        private static final int MAX_PART_ENTRIES = 256;
        public static final Type<MoldCatalogPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "mold_catalog"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MoldCatalogPayload> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeBlockPos(value.pos());
                    buffer.writeVarInt(value.part());
                    buffer.writeVarInt(value.parts());
                    buffer.writeUtf(value.status(), 16);
                    buffer.writeUtf(value.progress(), 32);
                    int size = Math.min(MAX_PART_ENTRIES, Math.min(value.sourceIds().size(), value.itemIds().size()));
                    buffer.writeVarInt(size);
                    for (int i = 0; i < size; i++) {
                        buffer.writeUtf(value.sourceIds().get(i), 128);
                        buffer.writeUtf(value.itemIds().get(i), 256);
                    }
                }, buffer -> {
                    BlockPos pos = buffer.readBlockPos();
                    int part = buffer.readVarInt();
                    int parts = buffer.readVarInt();
                    String status = buffer.readUtf(16);
                    String progress = buffer.readUtf(32);
                    int size = buffer.readVarInt();
                    if (size < 0 || size > MAX_PART_ENTRIES || parts < 0 || parts > 4096
                            || (parts > 0 && (part < 0 || part >= parts))) {
                        throw new IllegalArgumentException("Invalid mold catalog batch");
                    }
                    List<String> sources = new ArrayList<>(size);
                    List<String> ids = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        sources.add(buffer.readUtf(128));
                        ids.add(buffer.readUtf(256));
                    }
                    return new MoldCatalogPayload(pos, part, parts, status, progress, sources, ids);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

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

    /** A bounded page of stored AE patterns. Keys keep their full component data. */
    public record PatternPagePayload(BlockPos pos, int page, int pages, boolean aeBound,
                                     boolean item, boolean offhand, List<AEItemKey> patterns,
                                     int requestId, String progress) implements CustomPacketPayload {
        public PatternPagePayload(BlockPos pos, int page, int pages, boolean aeBound,
                                  boolean item, boolean offhand, List<AEItemKey> patterns) {
            this(pos, page, pages, aeBound, item, offhand, patterns, 0, "");
        }
        public static final Type<PatternPagePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "myriad_pattern_page"));
        public static final int MAX_PAGE = 64;
        public static final StreamCodec<RegistryFriendlyByteBuf, PatternPagePayload> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBlockPos(value.pos());
                    buf.writeVarInt(value.page());
                    buf.writeVarInt(value.pages());
                    buf.writeBoolean(value.aeBound());
                    buf.writeBoolean(value.item());
                    buf.writeBoolean(value.offhand());
                    buf.writeVarInt(value.requestId());
                    buf.writeUtf(value.progress(), 64);
                    int size = Math.min(MAX_PAGE, value.patterns().size());
                    buf.writeVarInt(size);
                    for (int i = 0; i < size; i++) AEKey.writeKey(buf, value.patterns().get(i));
                }, buf -> {
                    BlockPos pos = buf.readBlockPos();
                    int page = buf.readVarInt();
                    int pages = buf.readVarInt();
                    boolean bound = buf.readBoolean();
                    boolean item = buf.readBoolean();
                    boolean offhand = buf.readBoolean();
                    int requestId = buf.readVarInt();
                    String progress = buf.readUtf(64);
                    int size = buf.readVarInt();
                    if (page < 0 || pages < 0 || (pages > 0 && page >= pages) || size < 0 || size > MAX_PAGE) {
                        throw new IllegalArgumentException("Invalid pattern page");
                    }
                    List<AEItemKey> values = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        var key = AEKey.readKey(buf);
                        if (key instanceof AEItemKey itemKey) values.add(itemKey);
                    }
                    return new PatternPagePayload(pos, page, pages, bound, item, offhand, values, requestId, progress);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record PatternPageRequestPayload(BlockPos pos, int page, boolean item, boolean offhand,
                                            int requestId, String query, byte[] matchingItems,
                                            byte[] matchingFluids) implements CustomPacketPayload {
        public PatternPageRequestPayload(BlockPos pos, int page, boolean item, boolean offhand) {
            this(pos, page, item, offhand, 0, "", new byte[0], new byte[0]);
        }
        public static final Type<PatternPageRequestPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "myriad_pattern_page_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PatternPageRequestPayload> STREAM_CODEC =
                StreamCodec.of((buf, value) -> {
                    buf.writeBlockPos(value.pos()); buf.writeVarInt(value.page());
                    buf.writeBoolean(value.item()); buf.writeBoolean(value.offhand());
                    buf.writeVarInt(value.requestId()); buf.writeUtf(value.query(), 128);
                    buf.writeByteArray(value.matchingItems()); buf.writeByteArray(value.matchingFluids());
                }, buf -> new PatternPageRequestPayload(buf.readBlockPos(), buf.readVarInt(),
                        buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readUtf(128),
                        buf.readByteArray(131072), buf.readByteArray(131072)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record PatternDeletePayload(BlockPos pos, boolean item, boolean offhand, List<AEItemKey> patterns) implements CustomPacketPayload {
        public static final Type<PatternDeletePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "myriad_pattern_delete"));
        private static final int MAX_DELETE = 64;
        public static final StreamCodec<RegistryFriendlyByteBuf, PatternDeletePayload> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBlockPos(value.pos());
                    buf.writeBoolean(value.item());
                    buf.writeBoolean(value.offhand());
                    int size = Math.min(MAX_DELETE, value.patterns().size());
                    buf.writeVarInt(size);
                    for (int i = 0; i < size; i++) AEKey.writeKey(buf, value.patterns().get(i));
                }, buf -> {
                    BlockPos pos = buf.readBlockPos();
                    boolean item = buf.readBoolean();
                    boolean offhand = buf.readBoolean();
                    int size = buf.readVarInt();
                    if (size < 0 || size > MAX_DELETE) throw new IllegalArgumentException("Invalid pattern delete");
                    List<AEItemKey> values = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        var key = AEKey.readKey(buf);
                        if (key instanceof AEItemKey itemKey) values.add(itemKey);
                    }
                    return new PatternDeletePayload(pos, item, offhand, values);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
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

    /** One-shot key action; the server owns the 12-second lifetime and ignores repeats while active. */
    public record StaffTimeStopPayload(boolean offhand) implements CustomPacketPayload {
        public static final Type<StaffTimeStopPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "staff_time_stop"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StaffTimeStopPayload> STREAM_CODEC =
                StreamCodec.of((buf, value) -> buf.writeBoolean(value.offhand()),
                        buf -> new StaffTimeStopPayload(buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record EntityTimerSettingsPayload(boolean timers, int speed, boolean offhand) implements CustomPacketPayload {
        public static final Type<EntityTimerSettingsPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "entity_timer_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, EntityTimerSettingsPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, EntityTimerSettingsPayload::timers,
                        ByteBufCodecs.VAR_INT, EntityTimerSettingsPayload::speed,
                        ByteBufCodecs.BOOL, EntityTimerSettingsPayload::offhand, EntityTimerSettingsPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void sendEntityTimerSettings(boolean timers, int speed, InteractionHand hand) {
        PacketDistributor.sendToServer(new EntityTimerSettingsPayload(timers, speed, hand == InteractionHand.OFF_HAND));
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

    public record ApotheosisTierPayload(String tier) implements CustomPacketPayload {
        public static final Type<ApotheosisTierPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "apotheosis_tier"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ApotheosisTierPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ApotheosisTierPayload::tier,
                        ApotheosisTierPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ApotheosisOptionsPayload(boolean selectionMode, int eterna, int quanta, int arcana,
                                           int clues, boolean offhand) implements CustomPacketPayload {
        public static final Type<ApotheosisOptionsPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "apotheosis_options"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ApotheosisOptionsPayload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, ApotheosisOptionsPayload::selectionMode,
                        ByteBufCodecs.VAR_INT, ApotheosisOptionsPayload::eterna,
                        ByteBufCodecs.VAR_INT, ApotheosisOptionsPayload::quanta,
                        ByteBufCodecs.VAR_INT, ApotheosisOptionsPayload::arcana,
                        ByteBufCodecs.VAR_INT, ApotheosisOptionsPayload::clues,
                        ByteBufCodecs.BOOL, ApotheosisOptionsPayload::offhand,
                        ApotheosisOptionsPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ApotheosisTablePayload(BlockPos pos, boolean offhand) implements CustomPacketPayload {
        public static final Type<ApotheosisTablePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "apotheosis_table"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ApotheosisTablePayload> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, ApotheosisTablePayload::pos,
                        ByteBufCodecs.BOOL, ApotheosisTablePayload::offhand, ApotheosisTablePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
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

    public record ReclaimerStatePayload(List<ReclaimerEntry> entries, boolean personal) implements CustomPacketPayload {
        public static final Type<ReclaimerStatePayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "reclaimer_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReclaimerStatePayload> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBoolean(value.personal());
                    int count = Math.min(MAX_RECLAIM_ENTRIES, value.entries().size()); buf.writeVarInt(count);
                    for (int i = 0; i < count; i++) ReclaimerEntry.STREAM_CODEC.encode(buf, value.entries().get(i));
                }, buf -> {
                    boolean personal = buf.readBoolean();
                    int count = buf.readVarInt();
                    if (count < 0 || count > MAX_RECLAIM_ENTRIES) throw new IllegalArgumentException("Invalid reclaim count");
                    List<ReclaimerEntry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) entries.add(ReclaimerEntry.STREAM_CODEC.decode(buf));
                    return new ReclaimerStatePayload(entries, personal);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ReclaimerRequestPayload(boolean personal) implements CustomPacketPayload {
        public static final Type<ReclaimerRequestPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "reclaimer_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReclaimerRequestPayload> STREAM_CODEC =
                StreamCodec.of((buf, value) -> buf.writeBoolean(value.personal()),
                        buf -> new ReclaimerRequestPayload(buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ReclaimerActionPayload(boolean range, UUID id, boolean personal) implements CustomPacketPayload {
        public static final Type<ReclaimerActionPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "reclaimer_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReclaimerActionPayload> STREAM_CODEC =
                StreamCodec.of((buf, value) -> {
                    buf.writeBoolean(value.range()); buf.writeUUID(value.id()); buf.writeBoolean(value.personal());
                }, buf -> new ReclaimerActionPayload(buf.readBoolean(), buf.readUUID(), buf.readBoolean()));
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
        PayloadRegistrar registrar = event.registrar("7");
        registrar.playToClient(ServerConfigSync.Payload.TYPE, ServerConfigSync.Payload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.sorrowmist.useless.stretcher.client.StretcherConfigScreen.accept(payload)));

        registrar.playToServer(MyriadActionPayload.TYPE, MyriadActionPayload.STREAM_CODEC, Network::handleAction);
        registrar.playToClient(MyriadStatePayload.TYPE, MyriadStatePayload.STREAM_CODEC,
                (payload, context) -> ClientStateReceiver.accept(payload));
        registrar.playToClient(PatternPagePayload.TYPE, PatternPagePayload.STREAM_CODEC,
                (payload, context) -> ClientStateReceiver.acceptPatternPage(payload));
        registrar.playToServer(PatternPageRequestPayload.TYPE, PatternPageRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handlePatternPageRequest(payload, context)));
        registrar.playToServer(PatternDeletePayload.TYPE, PatternDeletePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handlePatternDelete(payload, context)));
        registrar.playToClient(MoldCatalogPayload.TYPE, MoldCatalogPayload.STREAM_CODEC,
                (payload, context) -> ClientStateReceiver.acceptMoldCatalog(payload));
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
        registrar.playToServer(StaffTimeStopPayload.TYPE, StaffTimeStopPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleStaffTimeStop(payload, context)));
        registrar.playToServer(EntityTimerSettingsPayload.TYPE, EntityTimerSettingsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer player) || !StretcherConfig.serverStaffAcceleration()) return;
                    ItemStack held = player.getItemInHand(payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                    if (!held.is(com.sorrowmist.useless.stretcher.init.ModItems.WONDROUS_STAFF.get())) return;
                    held.set(StretcherComponents.ENTITY_TIMER_MODE.get(), payload.timers());
                    held.set(StretcherComponents.ENTITY_TIMER_SPEED.get(),
                            com.sorrowmist.useless.stretcher.content.entity.EntityTimerAcceleration.normalizeSpeed(payload.speed()));
                }));
        registrar.playToServer(WondrousStaffFeaturesPayload.TYPE, WondrousStaffFeaturesPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleWondrousStaffFeatures(payload, context)));
        registrar.playToServer(ApotheosisTierPayload.TYPE, ApotheosisTierPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleApotheosisTier(payload, context)));
        registrar.playToServer(ApotheosisOptionsPayload.TYPE, ApotheosisOptionsPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleApotheosisOptions(payload, context)));
        registrar.playToServer(ApotheosisTablePayload.TYPE, ApotheosisTablePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleApotheosisTable(payload, context)));
        ApotheosisNetwork.register(registrar);
        registrar.playToServer(WondrousStaffSummonPayload.TYPE, WondrousStaffSummonPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleWondrousStaffSummon(payload, context)));
        registrar.playToServer(WondrousStaffRecallPayload.TYPE, WondrousStaffRecallPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleWondrousStaffRecall(payload, context)));
        registrar.playToServer(ReclaimerRequestPayload.TYPE, ReclaimerRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        sendReclaimerState((ServerPlayer) context.player(), payload.personal())));
        registrar.playToServer(ReclaimerActionPayload.TYPE, ReclaimerActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleReclaimerAction(payload, context)));
        registrar.playToClient(ReclaimerStatePayload.TYPE, ReclaimerStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientStateReceiver.handleReclaimer(payload)));
        RangeNetwork.register(registrar);
    }

    private static void handleWondrousStaffSpeed(WondrousStaffSpeedPayload payload,
                                                 net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!StretcherConfig.serverStaffAcceleration()) return;
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

    private static void handleStaffTimeStop(StaffTimeStopPayload payload,
                                            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !StretcherConfig.serverTimeStop()) return;
        InteractionHand hand = payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(com.sorrowmist.useless.stretcher.init.ModItems.WONDROUS_STAFF.get())) return;
        TimeStopManager.start(player);
    }

    public static void sendStaffTimeStop(InteractionHand hand) {
        PacketDistributor.sendToServer(new StaffTimeStopPayload(hand == InteractionHand.OFF_HAND));
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

    private static boolean apotheosisAllowed() {
        return StretcherConfig.enableApotheosisCompat() && ApotheosisStaffSettings.isAvailable();
    }

    private static void handleApotheosisTier(ApotheosisTierPayload payload,
            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !StretcherConfig.enableApotheosisCompat()
                || !ApotheosisStaffSettings.isApotheosisLoaded()) return;
        if (!(player.getMainHandItem().getItem() instanceof com.sorrowmist.useless.stretcher.content.item.WondrousStaffItem)
                && !(player.getOffhandItem().getItem() instanceof com.sorrowmist.useless.stretcher.content.item.WondrousStaffItem)) return;
        String tier = payload.tier().toLowerCase(java.util.Locale.ROOT);
        if (!Set.of("haven", "frontier", "ascent", "summit", "pinnacle").contains(tier)) return;
        if (!ApotheosisStaffSettings.setTier(player, tier)) {
            player.displayClientMessage(Component.translatable("msg.useless_stretcher.apotheosis.failed"), true);
        } else {
            player.displayClientMessage(Component.translatable("msg.useless_stretcher.apotheosis.tier_set", tier), true);
        }
        InteractionHand hand = player.getMainHandItem().getItem() instanceof
                com.sorrowmist.useless.stretcher.content.item.WondrousStaffItem ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ApotheosisNetwork.sendSettings(player, hand);
    }

    private static void handleApotheosisOptions(ApotheosisOptionsPayload payload,
            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !apotheosisAllowed()
                || !ApotheosisStaffSettings.isAvailable()) return;
        InteractionHand hand = payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(com.sorrowmist.useless.stretcher.init.ModItems.WONDROUS_STAFF.get())) return;
        var boost = new ApotheosisTableData.Boost(
                payload.eterna(), payload.quanta(), payload.arcana(), payload.clues()).normalized();
        ApotheosisStaffSettings.setBoost(held, boost);
        ApotheosisStaffSettings.setSelectionMode(held, payload.selectionMode());
        if (payload.selectionMode()) {
            held.set(StretcherComponents.RANGE_FILTER_MARKING_MODE.get(), false);
            held.set(StretcherComponents.RANGE_PLACEMENT_MODE.get(), false);
        }
        ApotheosisTableData data = ApotheosisTableData.get(player.getServer());
        if (data.updateOwned(player.getUUID(), boost) > 0) {
            ApotheosisTableData.refreshOpenMenus(player.getServer());
        }
        player.inventoryMenu.broadcastChanges();
        ApotheosisNetwork.sendSettings(player, hand);
    }

    private static void handleApotheosisTable(ApotheosisTablePayload payload,
            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        InteractionHand hand = payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ApotheosisStaffSettings.markTable(player, hand, payload.pos());
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

    private static boolean canManage(ServerPlayer player, boolean personal) {
        if (player == null || (!personal && !StretcherConfig.serverRemoteReclaimer())) return false;
        var item = personal ? com.sorrowmist.useless.stretcher.init.ModItems.WONDROUS_STAFF.get()
                : com.sorrowmist.useless.stretcher.init.ModItems.RANGE_RECLAIMER.get();
        return player.getMainHandItem().is(item) || player.getOffhandItem().is(item);
    }

    private static void sendReclaimerState(ServerPlayer player, boolean personal) {
        if (!canManage(player, personal)) return;
        List<ReclaimerEntry> result = new ArrayList<>();
        RangeAccelerationSavedData data = RangeAccelerationSavedData.get(player.getServer());
        for (RangeAccelerationSavedData.Field field : data.operatorFields()) {
            if (personal && !field.owner().equals(player.getUUID())) continue;
            if (result.size() >= MAX_RECLAIM_ENTRIES) break;
            result.add(new ReclaimerEntry(true, field.id(), field.owner(), ownerName(player, field.owner()),
                    field.dimension(), field.center(), (field.name().isBlank() ? "时间流逝" : field.name())
                            + " · x" + field.speed()));
        }
        for (var entry : PermanentAccelerationHistory.get(player.getServer())
                .activeEntries(personal ? player.getUUID() : null)) {
            if (result.size() >= MAX_RECLAIM_ENTRIES) break;
            result.add(new ReclaimerEntry(false, entry.id(), entry.owner(), ownerName(player, entry.owner()),
                    entry.dimension(), entry.pos(), entry.label()));
        }
        result.sort(java.util.Comparator.comparing(ReclaimerEntry::ownerName)
                .thenComparing(entry -> entry.owner().toString()).thenComparing(ReclaimerEntry::label));
        PacketDistributor.sendToPlayer(player, new ReclaimerStatePayload(result, personal));
    }

    private static String ownerName(ServerPlayer viewer, UUID owner) {
        ServerPlayer online = viewer.getServer().getPlayerList().getPlayer(owner);
        if (online != null) return online.getGameProfile().getName();
        var cache = viewer.getServer().getProfileCache();
        return cache == null ? owner.toString()
                : cache.get(owner).map(com.mojang.authlib.GameProfile::getName).orElse(owner.toString());
    }

    private static void handleReclaimerAction(ReclaimerActionPayload payload,
                                               net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!canManage(player, payload.personal())) return;
        boolean removed = false;
        if (payload.range()) {
            var ranges = RangeAccelerationSavedData.get(player.getServer());
            removed = payload.personal()
                    ? ranges.reclaim(player.getServer(), player.getUUID(), payload.id()) != null
                    : ranges.reclaimByOperator(player.getServer(), payload.id()) != null;
        } else {
            removed = PermanentAccelerationHistory.get(player.getServer())
                    .reclaim(player.getServer(), payload.id(), payload.personal() ? player.getUUID() : null);
        }
        player.displayClientMessage(Component.translatable(removed
                ? "msg.useless_stretcher.reclaimer.removed"
                : "msg.useless_stretcher.reclaimer.missing"), true);
        sendReclaimerState(player, payload.personal());
    }

    public static void sendWondrousStaffFeatures(boolean summonEnabled, boolean lootRefresh,
                                                 InteractionHand hand) {
        PacketDistributor.sendToServer(new WondrousStaffFeaturesPayload(
                summonEnabled, lootRefresh, hand == InteractionHand.OFF_HAND));
    }

    public static void setApotheosisTier(String tier) {
        PacketDistributor.sendToServer(new ApotheosisTierPayload(tier));
    }

    public static void sendApotheosisOptions(boolean selectionMode, ApotheosisTableData.Boost boost,
                                             InteractionHand hand) {
        PacketDistributor.sendToServer(new ApotheosisOptionsPayload(selectionMode, boost.eterna(),
                boost.quanta(), boost.arcana(), boost.clues(), hand == InteractionHand.OFF_HAND));
    }

    public static void markApotheosisTable(BlockPos pos, InteractionHand hand) {
        PacketDistributor.sendToServer(new ApotheosisTablePayload(pos, hand == InteractionHand.OFF_HAND));
    }

    public static void sendWondrousStaffSummon(List<String> entityIds, InteractionHand hand) {
        PacketDistributor.sendToServer(new WondrousStaffSummonPayload(
                List.copyOf(entityIds), hand == InteractionHand.OFF_HAND));
    }

    public static void recallWondrousStaff(InteractionHand hand) {
        PacketDistributor.sendToServer(new WondrousStaffRecallPayload(hand == InteractionHand.OFF_HAND));
    }

    public static void requestReclaimer() {
        PacketDistributor.sendToServer(new ReclaimerRequestPayload(false));
    }

    public static void requestPersonalReclaimer() {
        PacketDistributor.sendToServer(new ReclaimerRequestPayload(true));
    }

    public static void requestReclaimerState(ServerPlayer player) {
        sendReclaimerState(player, false);
    }

    public static void reclaimTarget(boolean range, UUID id, boolean personal) {
        PacketDistributor.sendToServer(new ReclaimerActionPayload(range, id, personal));
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
                    case ACTION_REQUEST_CATALOG -> MoldCatalogService.request(player, payload.pos());
                }
                replyState(player, be);
            }
        });
    }

    private static boolean canViewPatterns(ServerPlayer player, BlockPos pos) {
        return player != null && player.level().hasChunkAt(pos)
                && player.distanceToSqr(pos.getCenter()) <= 64.0D
                && player.level().getBlockEntity(pos) instanceof OmniversalMyriadBlockEntity;
    }

    private static void handlePatternPageRequest(PatternPageRequestPayload payload,
                                                   net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || payload.page() < 0) return;
        if (payload.item()) {
            ItemStack held = player.getItemInHand(payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (!held.is(com.sorrowmist.useless.stretcher.init.ModItems.USELESS_STRETCHER.get())) return;
            sendPatternPage(player, BlockPos.ZERO, payload.page(), true, payload.offhand(), payload);
        } else if (canViewPatterns(player, payload.pos())) {
            sendPatternPage(player, payload.pos(), payload.page(), false, payload.offhand(), payload);
        }
    }

    private static void handlePatternDelete(PatternDeletePayload payload,
                                              net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || payload.patterns().isEmpty()) return;
        UUID ref = null;
        OmniversalMyriadBlockEntity be = null;
        if (payload.item()) {
            ItemStack held = player.getItemInHand(payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (!held.is(com.sorrowmist.useless.stretcher.init.ModItems.USELESS_STRETCHER.get())) return;
            ref = com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData.readPatternRef(held);
        } else if (canViewPatterns(player, payload.pos())
                && player.level().getBlockEntity(payload.pos()) instanceof OmniversalMyriadBlockEntity myriad) {
            be = myriad;
            ref = be.getPatternRef();
        }
        if (ref == null) return;
        MyriadPatternStore.get((ServerLevel) player.level()).removeKeys(ref, payload.patterns());
        if (be != null) { be.markPatternsChanged(); replyState(player, be); }
        // The item screen follows deletion with its current tagged search request. An untagged
        // response here could replace that filter or reopen an already closed screen.
        if (!payload.item()) sendPatternPage(player, payload.pos(), 0, false, payload.offhand());
    }

    private static void sendPatternPage(ServerPlayer player, BlockPos pos, int requestedPage,
                                        boolean item, boolean offhand) {
        sendPatternPage(player, pos, requestedPage, item, offhand,
                new PatternPageRequestPayload(pos, requestedPage, item, offhand));
    }

    private static void sendPatternPage(ServerPlayer player, BlockPos pos, int requestedPage,
                                        boolean item, boolean offhand, PatternPageRequestPayload request) {
        UUID ref;
        boolean aeBound;
        if (item) {
            ItemStack held = player.getItemInHand(offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            if (!held.is(com.sorrowmist.useless.stretcher.init.ModItems.USELESS_STRETCHER.get())) return;
            ref = com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData.readPatternRef(held);
            UUID aeRef = com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData.readAeRef(held);
            var serverLevel = (ServerLevel) player.level();
            BlockPos nodePos = com.sorrowmist.useless.stretcher.content.ae.AeBindingStore.get(serverLevel).get(aeRef);
            aeBound = isConnectedAeNode(serverLevel, nodePos);
        } else {
            if (!canViewPatterns(player, pos)) return;
            OmniversalMyriadBlockEntity be = (OmniversalMyriadBlockEntity) player.level().getBlockEntity(pos);
            if (be == null) return;
            ref = be.getPatternRef();
            aeBound = isConnectedAeNode((ServerLevel) player.level(), be.getAeNodePos());
        }
        var store = MyriadPatternStore.get((ServerLevel) player.level());
        com.sorrowmist.useless.stretcher.content.mold.PatternRepositorySearch.cancel(player);
        if (!request.query().isBlank() && ref != null) {
            com.sorrowmist.useless.stretcher.content.mold.PatternRepositorySearch.request(
                    player, ref, store, request, aeBound);
            return;
        }
        var all = store.keys(ref);
        int pages = Math.max(1, (all.size() + PatternPagePayload.MAX_PAGE - 1) / PatternPagePayload.MAX_PAGE);
        int page = Math.min(requestedPage, pages - 1);
        int from = page * PatternPagePayload.MAX_PAGE;
        List<AEItemKey> pageKeys = all.stream().skip(from).limit(PatternPagePayload.MAX_PAGE).toList();
        PacketDistributor.sendToPlayer(player, new PatternPagePayload(pos, page, pages, aeBound, item, offhand,
                pageKeys, request.requestId(), ""));
    }

    private static boolean isConnectedAeNode(ServerLevel level, BlockPos pos) {
        if (pos == null || !level.hasChunkAt(pos)
                || !(level.getBlockEntity(pos) instanceof appeng.api.networking.IInWorldGridNodeHost host)) return false;
        for (var side : net.minecraft.core.Direction.values()) {
            var node = host.getGridNode(side);
            if (node != null && node.isActive()) return true;
        }
        return false;
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

    public static void requestMoldCatalog(BlockPos pos) {
        PacketDistributor.sendToServer(new MyriadActionPayload(pos, ACTION_REQUEST_CATALOG, "", List.of()));
    }

    public static void requestPatternPage(BlockPos pos, int page) {
        PacketDistributor.sendToServer(new PatternPageRequestPayload(pos, Math.max(0, page), false, false));
    }

    public static void requestStretcherPatternPage(InteractionHand hand, int page) {
        PacketDistributor.sendToServer(new PatternPageRequestPayload(BlockPos.ZERO, Math.max(0, page), true,
                hand == InteractionHand.OFF_HAND));
    }

    public static void requestStretcherPatternPage(InteractionHand hand, int page, int requestId,
                                                  String query, byte[] items, byte[] fluids) {
        PacketDistributor.sendToServer(new PatternPageRequestPayload(BlockPos.ZERO, Math.max(0, page), true,
                hand == InteractionHand.OFF_HAND, requestId, query, items, fluids));
    }

    public static void deletePatterns(BlockPos pos, List<AEItemKey> patterns) {
        if (patterns == null || patterns.isEmpty()) return;
        PacketDistributor.sendToServer(new PatternDeletePayload(pos, false, false, List.copyOf(patterns)));
    }

    public static void deleteStretcherPatterns(InteractionHand hand, List<AEItemKey> patterns) {
        if (patterns == null || patterns.isEmpty()) return;
        PacketDistributor.sendToServer(new PatternDeletePayload(BlockPos.ZERO, true,
                hand == InteractionHand.OFF_HAND, List.copyOf(patterns)));
    }

    public static void sendMoldCatalogStatus(ServerPlayer player, BlockPos pos, String status, String progress) {
        PacketDistributor.sendToPlayer(player,
                new MoldCatalogPayload(pos, -1, 0, status, progress, List.of(), List.of()));
    }

    public static void sendMoldCatalog(ServerPlayer player, BlockPos pos,
                                       Map<String, List<ResourceLocation>> bySource) {
        List<String> sources = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        bySource.forEach((source, sourceIds) -> sourceIds.forEach(id -> {
            sources.add(source);
            ids.add(id.toString());
        }));
        int parts = Math.max(1, (ids.size() + 255) / 256);
        for (int part = 0; part < parts; part++) {
            int from = part * 256;
            int to = Math.min(ids.size(), from + 256);
            PacketDistributor.sendToPlayer(player, new MoldCatalogPayload(pos, part, parts, "done", "",
                    List.copyOf(sources.subList(from, to)), List.copyOf(ids.subList(from, to))));
        }
    }

    public static void sendFullSlots(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new FullSlotsPayload(true));
    }
}
