package com.sorrowmist.useless.stretcher.dimension;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.dimension.init.ModBlocks;
import com.sorrowmist.useless.stretcher.dimension.init.ModItems;
import com.sorrowmist.useless.stretcher.dimension.init.ModPOIs;
import com.sorrowmist.useless.stretcher.dimension.world.NineChunkDimensions;
import com.sorrowmist.useless.stretcher.dimension.world.NineChunkOddDimensions;
import com.sorrowmist.useless.stretcher.dimension.world.QuadChunkDimensions;
import com.sorrowmist.useless.stretcher.dimension.world.QuadChunkOddDimensions;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;

/** Registers the merged dimension-teleporter content under the stretcher mod's namespace. */
public final class DimensionCompat {
    public static final String MODID = UselessStretcherMod.MODID;

    private DimensionCompat() {
    }

    public static void init(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModPOIs.POI_TYPES.register(modEventBus);
        QuadChunkDimensions.init(modEventBus);
        NineChunkDimensions.init(modEventBus);
        QuadChunkOddDimensions.init(modEventBus);
        NineChunkOddDimensions.init(modEventBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
