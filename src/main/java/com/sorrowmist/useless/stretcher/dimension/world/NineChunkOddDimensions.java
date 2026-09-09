package com.sorrowmist.useless.stretcher.dimension.world;

import com.sorrowmist.useless.stretcher.dimension.DimensionCompat;
import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 九联区块·奇数中心维度的维度 Key 与区块生成器注册。
 * 维度实际数据文件在 src/main/resources/data/<modid>/dimension|dimension_type/nine_chunk_odd.json
 */
public final class NineChunkOddDimensions {
    /** 九联区块奇数中心维度 key：some_useless_things_mod_1787656950:nine_chunk_odd（spec dimension_type = nine_chunk_odd） */
    public static final ResourceKey<Level> NINE_CHUNK_ODD_KEY = ResourceKey.create(
            Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(DimensionCompat.MODID, "nine_chunk_odd")
    );

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(
            Registries.CHUNK_GENERATOR, DimensionCompat.MODID
    );

    public static final Supplier<MapCodec<? extends ChunkGenerator>> NINE_CHUNK_ODD_GEN = CHUNK_GENERATORS.register(
            "nine_chunk_odd_gen", () -> NineChunkOddDimGen.CODEC
    );

    private NineChunkOddDimensions() {
    }

    public static void init(IEventBus modEventBus) {
        CHUNK_GENERATORS.register(modEventBus);
    }
}
