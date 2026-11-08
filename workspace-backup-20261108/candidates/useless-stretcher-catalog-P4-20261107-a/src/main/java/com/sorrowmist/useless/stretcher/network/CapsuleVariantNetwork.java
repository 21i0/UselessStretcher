package com.sorrowmist.useless.stretcher.network;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.capsule.CapsuleVariants;
import com.sorrowmist.useless.stretcher.content.item.DimensionCapsuleItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

/** Candidate tokens refer to server-owned plans. No client-supplied seed or NBT is accepted. */
public final class CapsuleVariantNetwork {
    public static final int QUERY = 0, CONTINUE = 1, PAUSE = 2, GENERATE = 3;
    public static final int MAX_CANDIDATES = 32;
    private CapsuleVariantNetwork() { }

    public record Entry(String candidateId, String family, String template, int sx, int sy, int sz, int pieces) { }

    public record Request(String id, String dimension, int action, String candidateId, boolean offhand,
                          int parentInstanceId, int side) implements CustomPacketPayload {
        static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "capsule_variant_request"));
        static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeUtf(p.id, 256); buf.writeUtf(p.dimension, 256); buf.writeVarInt(p.action);
            buf.writeUtf(p.candidateId, 64); buf.writeBoolean(p.offhand);
            buf.writeVarInt(p.parentInstanceId); buf.writeVarInt(p.side);
        }, buf -> new Request(buf.readUtf(256), buf.readUtf(256), buf.readVarInt(), buf.readUtf(64),
                buf.readBoolean(), buf.readVarInt(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record State(String id, String dimension, String phase, int checked, int roundStart, int roundLimit,
                        List<Entry> entries, String selectedCandidateId, int[] preview,
                        int generatedInstanceId) implements CustomPacketPayload {
        public State {
            if (entries.size() > MAX_CANDIDATES) throw new IllegalArgumentException("Too many capsule candidates");
            if (preview.length != 0 && preview.length != 4096) throw new IllegalArgumentException("Invalid candidate preview size");
            entries = List.copyOf(entries);
            preview = preview.clone();
        }
        static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "capsule_variant_state"));
        static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeUtf(p.id, 256); buf.writeUtf(p.dimension, 256); buf.writeUtf(p.phase, 32);
            buf.writeVarInt(p.checked); buf.writeVarInt(p.roundStart); buf.writeVarInt(p.roundLimit);
            buf.writeVarInt(p.entries.size());
            for (Entry e : p.entries) {
                buf.writeUtf(e.candidateId, 64); buf.writeUtf(e.family, 256); buf.writeUtf(e.template, 256);
                buf.writeVarInt(e.sx); buf.writeVarInt(e.sy); buf.writeVarInt(e.sz); buf.writeVarInt(e.pieces);
            }
            buf.writeUtf(p.selectedCandidateId, 64);
            buf.writeVarInt(p.preview.length); for (int color : p.preview) buf.writeInt(color);
            buf.writeVarInt(p.generatedInstanceId);
        }, buf -> {
            String id = buf.readUtf(256), dimension = buf.readUtf(256), phase = buf.readUtf(32);
            int checked = buf.readVarInt(), roundStart = buf.readVarInt(), roundLimit = buf.readVarInt();
            if (checked < 0 || roundStart < 0 || roundStart > checked || roundLimit < 0 || roundLimit > 256)
                throw new IllegalArgumentException("Invalid candidate search progress");
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_CANDIDATES) throw new IllegalArgumentException("Invalid candidate count");
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String token = buf.readUtf(64), family = buf.readUtf(256), template = buf.readUtf(256);
                int sx = buf.readVarInt(), sy = buf.readVarInt(), sz = buf.readVarInt(), pieces = buf.readVarInt();
                if (!validToken(token) || sx < 0 || sy < 0 || sz < 0 || sx > 65536 || sy > 65536 || sz > 65536
                        || pieces < 0 || pieces > 250000) throw new IllegalArgumentException("Invalid candidate metadata");
                entries.add(new Entry(token, family, template, sx, sy, sz, pieces));
            }
            String selected = buf.readUtf(64);
            if (!selected.isEmpty() && !validToken(selected)) throw new IllegalArgumentException("Invalid selected candidate");
            int pixels = buf.readVarInt();
            if (pixels != 0 && pixels != 4096) throw new IllegalArgumentException("Invalid candidate preview size");
            int[] preview = new int[pixels]; for (int i = 0; i < pixels; i++) preview[i] = buf.readInt();
            int instance = buf.readVarInt();
            if (instance < -1) throw new IllegalArgumentException("Invalid generated instance");
            return new State(id, dimension, phase, checked, roundStart, roundLimit, entries, selected, preview, instance);
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static boolean validToken(String token) {
        if (token.length() != 64) return false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f')) return false;
        }
        return true;
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Request.TYPE, Request.CODEC, (p, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || p.action < QUERY || p.action > GENERATE
                    || p.side < 0 || p.side > 3 || p.parentInstanceId < 0
                    || !p.candidateId.isEmpty() && !validToken(p.candidateId)
                    || p.action == GENERATE && !validToken(p.candidateId)) return;
            if (ResourceLocation.tryParse(p.id) == null || ResourceLocation.tryParse(p.dimension) == null) return;
            if (!DimensionCapsuleItem.is(player.getItemInHand(p.offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND))) return;
            long now = player.getServer().getTickCount();
            // A preview query must not swallow an immediately following pause or generation click.
            String limitKey = "capsule_variant_request_tick_" + p.action;
            long previous = player.getPersistentData().getLong(limitKey);
            if (player.getPersistentData().contains(limitKey) && now >= previous && now - previous < 4) return;
            player.getPersistentData().putLong(limitKey, now);
            try {
                CapsuleVariants.request(player, p.id, p.dimension, p.action, p.candidateId, p.offhand, p.parentInstanceId, p.side);
            } catch (RuntimeException failure) {
                com.mojang.logging.LogUtils.getLogger().error("Capsule variant request failed for {}", p.id, failure);
                state(player, new State(p.id, p.dimension, "failed", 0, 0, 256, List.of(), "", new int[0], -1));
            }
        }));
        registrar.playToClient(State.TYPE, State.CODEC, (p, context) -> context.enqueueWork(() -> ClientStateReceiver.handleCapsuleVariants(p)));
    }

    public static void request(String id, String dimension, int action, String candidateId, boolean offhand, int parentInstanceId, int side) {
        PacketDistributor.sendToServer(new Request(id, dimension, action, candidateId, offhand, parentInstanceId, side));
    }

    public static void state(ServerPlayer player, State state) { PacketDistributor.sendToPlayer(player, state); }
}
