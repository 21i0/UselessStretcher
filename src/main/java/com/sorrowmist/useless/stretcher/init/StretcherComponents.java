package com.sorrowmist.useless.stretcher.init;

import com.mojang.serialization.Codec;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

/** Data components for the wondrous staff's time-acceleration control. */
public final class StretcherComponents {
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, UselessStretcherMod.MODID);

    /** Selected acceleration multiplier. 0 = off, otherwise multiples of 4 up to 1024. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WONDROUS_STAFF_SPEED =
            register("wondrous_staff_speed", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(StreamCodec.of(FriendlyByteBuf::writeVarInt, FriendlyByteBuf::readVarInt)));

    /** When true, the acceleration never expires. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> WONDROUS_STAFF_PERMANENT =
            register("wondrous_staff_permanent", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(StreamCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean)));

    private StretcherComponents() {
    }

    public static void init(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(
            String name, UnaryOperator<DataComponentType.Builder<T>> operator) {
        return DATA_COMPONENTS.register(name, () -> operator.apply(DataComponentType.builder()).build());
    }
}
