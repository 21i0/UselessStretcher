package com.sorrowmist.useless.stretcher.network;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.capsule.NativeCapsules;
import com.sorrowmist.useless.stretcher.content.item.DimensionCapsuleItem;
import net.minecraft.network.FriendlyByteBuf;
import com.sorrowmist.useless.compat.forge1201.network.StreamCodec;
import com.sorrowmist.useless.compat.forge1201.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import com.sorrowmist.useless.compat.forge1201.network.PayloadRegistrar;

public final class CapsuleNetwork {
    private CapsuleNetwork() { }
    public record Complete(String id, String dimension, int instanceId) implements CustomPacketPayload {
        static final Type<Complete> TYPE = new Type<>(new ResourceLocation(UselessStretcherMod.MODID, "capsule_complete"));
        static final StreamCodec<FriendlyByteBuf, Complete> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeUtf(p.id, 256); buf.writeUtf(p.dimension, 256); buf.writeVarInt(p.instanceId);
        }, buf -> new Complete(buf.readUtf(256), buf.readUtf(256), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Request(String id, String dimension, int action, int side, boolean offhand, int instanceId) implements CustomPacketPayload {
        public Request(String id, String dimension, int action, int side, boolean offhand) {
            this(id, dimension, action, side, offhand, 0);
        }
        static final Type<Request> TYPE = new Type<>(new ResourceLocation(UselessStretcherMod.MODID, "native_capsule_request"));
        static final StreamCodec<FriendlyByteBuf, Request> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeUtf(p.id, 256); buf.writeUtf(p.dimension, 256); buf.writeVarInt(p.action); buf.writeVarInt(p.side); buf.writeBoolean(p.offhand); buf.writeVarInt(p.instanceId);
        }, buf -> new Request(buf.readUtf(256), buf.readUtf(256), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record State(String id, String dimension, int instanceId, int instanceCount, int originX, int originZ,
                        String phase, int progress, int[] pixels, int sx, int sy, int sz) implements CustomPacketPayload {
        public State(String id, String dimension, String phase, int progress, int[] pixels, int sx, int sy, int sz) {
            this(id, dimension, 0, 0, 0, 0, phase, progress, pixels, sx, sy, sz);
        }
        static final Type<State> TYPE = new Type<>(new ResourceLocation(UselessStretcherMod.MODID, "native_capsule_state"));
        static final StreamCodec<FriendlyByteBuf, State> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeUtf(p.id, 256); buf.writeUtf(p.dimension, 256); buf.writeVarInt(p.instanceId);
            buf.writeVarInt(p.instanceCount); buf.writeVarInt(p.originX); buf.writeVarInt(p.originZ);
            buf.writeUtf(p.phase, 32); buf.writeVarInt(p.progress);
            buf.writeVarInt(p.pixels.length); for (int color : p.pixels) buf.writeInt(color);
            buf.writeVarInt(p.sx); buf.writeVarInt(p.sy); buf.writeVarInt(p.sz);
        }, buf -> {
            String id = buf.readUtf(256), dim = buf.readUtf(256); int instanceId = buf.readVarInt();
            int instanceCount = buf.readVarInt(), originX = buf.readVarInt(), originZ = buf.readVarInt();
            String phase = buf.readUtf(32); int progress = buf.readVarInt(), count = buf.readVarInt();
            if (count != 0 && count != 4096) throw new IllegalArgumentException("Invalid capsule map size");
            int[] pixels = new int[count]; for (int i = 0; i < count; i++) pixels[i] = buf.readInt();
            return new State(id, dim, instanceId, instanceCount, originX, originZ, phase, progress, pixels,
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Complete.TYPE, Complete.CODEC, (p, context) -> context.enqueueWork(() ->
                ClientStateReceiver.handleCapsuleReady(p)));
        registrar.playToServer(Request.TYPE, Request.CODEC, (p, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || p.action < 0 || p.action > 3 || p.side < 0 || p.side > 3 || p.instanceId < 0) return;
            if (!DimensionCapsuleItem.is(player.getItemInHand(p.offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND))) return;
            long now = player.getServer().getTickCount();
            // Progress polling must not consume the interaction budget: a query and
            // a button click can arrive in the same tick, especially on a local server.
            String throttleKey = p.action == 0 ? "capsule_query_tick" : "capsule_action_tick";
            var persistent = player.getPersistentData();
            long previous = persistent.getLong(throttleKey);
            if (persistent.contains(throttleKey) && now >= previous && now - previous < 4) return;
            persistent.putLong(throttleKey, now);
            if (ResourceLocation.tryParse(p.id) == null || ResourceLocation.tryParse(p.dimension) == null) return;
            try { NativeCapsules.request(player, p.id, p.dimension, p.action, p.side, p.instanceId); }
            catch (RuntimeException failure) {
                com.sorrowmist.useless.stretcher.util.RateLimitedLogger.error(
                        com.mojang.logging.LogUtils.getLogger(), "Capsule request failed for {}", p.id, failure);
                state(player, p.id, p.dimension, p.instanceId, "failed", 0, new int[0], 0, 0, 0);
            }
        }));
        registrar.playToClient(State.TYPE, State.CODEC, (p, context) -> context.enqueueWork(() -> ClientStateReceiver.handleNativeCapsule(p)));
    }
    public static void request(String id, String dimension, int action, int side, boolean offhand) {
        PacketDistributor.sendToServer(new Request(id, dimension, action, side, offhand));
    }
    public static void request(String id, String dimension, int action, int side, boolean offhand, int instanceId) {
        PacketDistributor.sendToServer(new Request(id, dimension, action, side, offhand, instanceId));
    }
    public static void state(ServerPlayer player, String id, String dimension, String phase, int progress, int[] pixels, int sx, int sy, int sz) {
        state(player, id, dimension, 0, phase, progress, pixels, sx, sy, sz);
    }
    public static void complete(ServerPlayer player, String id, String dimension, int instanceId) {
        PacketDistributor.sendToPlayer(player, new Complete(id, dimension, instanceId));
    }
    public static void state(ServerPlayer player, String id, String dimension, int instanceId, String phase, int progress, int[] pixels, int sx, int sy, int sz) {
        state(player, id, dimension, instanceId, 0, 0, 0, phase, progress, pixels, sx, sy, sz);
    }
    public static void state(ServerPlayer player, String id, String dimension, int instanceId, int instanceCount,
                             int originX, int originZ, String phase, int progress, int[] pixels, int sx, int sy, int sz) {
        PacketDistributor.sendToPlayer(player, new State(id, dimension, instanceId, instanceCount, originX, originZ,
                phase, progress, pixels.clone(), sx, sy, sz));
    }
}
