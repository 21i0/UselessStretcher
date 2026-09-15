package com.sorrowmist.useless.stretcher.init;

import com.mojang.serialization.Codec;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** Data components for the wondrous staff's time-acceleration control. */
public final class StretcherComponents {
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, UselessStretcherMod.MODID);

    /** Selected acceleration multiplier. 0 = off; valid presets range from 2 to 1024. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WONDROUS_STAFF_SPEED =
            register("wondrous_staff_speed", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(StreamCodec.of(FriendlyByteBuf::writeVarInt, FriendlyByteBuf::readVarInt)));

    /** When true, the acceleration never expires. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> WONDROUS_STAFF_PERMANENT =
            register("wondrous_staff_permanent", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(StreamCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean)));

    /**
     * Staff acceleration mode: 0 = normal, 1 = permanent, 2 = permanent without idle throttling.
     * The separate legacy boolean above is retained so pre-mode stacks can still be read.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WONDROUS_STAFF_MODE =
            register("wondrous_staff_mode", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(StreamCodec.of(FriendlyByteBuf::writeVarInt, FriendlyByteBuf::readVarInt)));

    /** Whether Shift+right-click places an independent range instead of accelerating one target. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> RANGE_PLACEMENT_MODE =
            register("range_placement_mode", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(StreamCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean)));

    /** While true, right-clicking a machine only toggles its type in the range filter list. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> RANGE_FILTER_MARKING_MODE =
            register("range_filter_marking_mode", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(StreamCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean)));

    /** False marks the acceleration list; true marks the idle-sleep list. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> RANGE_MARK_SLEEP_LIST =
            register("range_mark_sleep_list", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(StreamCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean)));

    /** Counts from the last placed or edited range, shown beside the two list controls. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RANGE_ACCELERATION_MARK_COUNT =
            integerComponent("range_acceleration_mark_count");
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RANGE_SLEEP_MARK_COUNT =
            integerComponent("range_sleep_mark_count");

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RANGE_SIZE_X =
            integerComponent("range_size_x");
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RANGE_SIZE_Y =
            integerComponent("range_size_y");
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RANGE_SIZE_Z =
            integerComponent("range_size_z");

    /** Legacy-compatible storage: true now means the sleep list is a whitelist. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> RANGE_IDLE_THROTTLE_DISABLED =
            register("range_idle_throttle_disabled", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(StreamCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean)));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> RANGE_WHITELIST_MODE =
            register("range_whitelist_mode", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(StreamCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean)));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ResourceLocation>>> RANGE_FILTERS =
            register("range_filters", builder -> builder
                    .persistent(ResourceLocation.CODEC.listOf().xmap(List::copyOf, List::copyOf))
                    .networkSynchronized(StreamCodec.of(
                            (buffer, values) -> {
                                buffer.writeVarInt(values.size());
                                for (ResourceLocation value : values) ResourceLocation.STREAM_CODEC.encode(buffer, value);
                            },
                            buffer -> {
                                int size = buffer.readVarInt();
                                if (size < 0 || size > 128) throw new IllegalArgumentException("Invalid range filter count: " + size);
                                List<ResourceLocation> values = new ArrayList<>(size);
                                for (int i = 0; i < size; i++) values.add(ResourceLocation.STREAM_CODEC.decode(buffer));
                                return List.copyOf(values);
                            })));

    /** Small server-store reference replacing the pattern assembly's potentially multi-megabyte item data. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> PATTERN_ASSEMBLY_REF =
            register("pattern_assembly_ref", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(StreamCodec.of(FriendlyByteBuf::writeUtf, FriendlyByteBuf::readUtf)));

    private StretcherComponents() {
    }

    public static void init(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(
            String name, UnaryOperator<DataComponentType.Builder<T>> operator) {
        return DATA_COMPONENTS.register(name, () -> operator.apply(DataComponentType.builder()).build());
    }

    private static DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> integerComponent(String name) {
        return register(name, builder -> builder
                .persistent(Codec.INT)
                .networkSynchronized(StreamCodec.of(FriendlyByteBuf::writeVarInt, FriendlyByteBuf::readVarInt)));
    }
}
