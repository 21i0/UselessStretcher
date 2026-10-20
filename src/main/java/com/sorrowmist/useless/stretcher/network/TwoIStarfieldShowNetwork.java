package com.sorrowmist.useless.stretcher.network;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.sorrowmist.useless.compat.forge1201.network.StreamCodec;
import com.sorrowmist.useless.compat.forge1201.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import com.sorrowmist.useless.compat.forge1201.network.RegisterPayloadHandlersEvent;

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
        static final Type<Show> TYPE = new Type<>(new ResourceLocation(
                UselessStretcherMod.MODID, "two_i_starfield_show"));
        static final StreamCodec<FriendlyByteBuf, Show> CODEC = new StreamCodec<>() {
            @Override
            public Show decode(FriendlyByteBuf buffer) {
                return new Show(buffer.readBlockPos(), buffer.readLong());
            }

            @Override
            public void encode(FriendlyByteBuf buffer, Show value) {
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
