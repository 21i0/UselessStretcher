package com.sorrowmist.useless.stretcher.network;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** One small packet at entry/reconnect; particle animation is entirely client-side thereafter. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class TwoIStarfieldShowNetwork {
    private TwoIStarfieldShowNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Show.TYPE, Show.CODEC, (payload, context) ->
                context.enqueueWork(() -> com.sorrowmist.useless.stretcher.client.render.TwoIStarfieldEffects
                        .startShow(payload.center(), payload.endTime())));
    }

    public static void send(ServerPlayer player, BlockPos center, long endTime) {
        PacketDistributor.sendToPlayer(player, new Show(center, endTime));
    }

    public record Show(BlockPos center, long endTime) implements CustomPacketPayload {
        static final Type<Show> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                UselessStretcherMod.MODID, "two_i_starfield_show"));
        static final StreamCodec<RegistryFriendlyByteBuf, Show> CODEC = new StreamCodec<>() {
            @Override
            public Show decode(RegistryFriendlyByteBuf buffer) {
                return new Show(buffer.readBlockPos(), buffer.readLong());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Show value) {
                buffer.writeBlockPos(value.center());
                buffer.writeLong(value.endTime());
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
