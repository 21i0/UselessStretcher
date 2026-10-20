package com.sorrowmist.useless.stretcher.dimension.world;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.eventbus.api.IEventBus;
import com.sorrowmist.useless.compat.forge1201.registry.DeferredRegister;

/** Stable flat dimension used only for 2i's private starfield visits. */
public final class TwoIStarfieldDimensions {
    public static final ResourceKey<Level> KEY = ResourceKey.create(
            Registries.DIMENSION, new ResourceLocation(UselessStretcherMod.MODID, "two_i_starfield"));

    private static final DeferredRegister<com.mojang.serialization.Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, UselessStretcherMod.MODID);

    public static final Supplier<com.mojang.serialization.Codec<? extends ChunkGenerator>> GENERATOR = CHUNK_GENERATORS.register(
            "two_i_starfield_gen", () -> TwoIStarfieldChunkGenerator.CODEC);

    private TwoIStarfieldDimensions() {
    }

    public static void init(IEventBus modEventBus) {
        CHUNK_GENERATORS.register(modEventBus);
    }
}
