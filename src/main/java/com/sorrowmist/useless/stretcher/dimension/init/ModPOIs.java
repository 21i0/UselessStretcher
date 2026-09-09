package com.sorrowmist.useless.stretcher.dimension.init;

import com.google.common.collect.ImmutableSet;
import com.sorrowmist.useless.stretcher.dimension.DimensionCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModPOIs {
    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, DimensionCompat.MODID);

    /**
     * 四联区块传送方块的 POI：让 AbstractDimensionTeleporter.findClosestTeleportBlock()
     * 能在维度内/回程时靠 POI 系统找到已放置的传送方块作为落点。
     */
    public static final DeferredHolder<PoiType, PoiType> QUAD_CHUNK_TELEPORT_POI = POI_TYPES.register(
            "quad_chunk_teleport_pad",
            () -> new PoiType(
                    ImmutableSet.copyOf(ModBlocks.QUAD_CHUNK_DIMENSION_BLOCK.get().getStateDefinition().getPossibleStates()),
                    0,
                    1
            )
    );

    /**
     * 九联区块传送方块的 POI，机制与四联区块 POI 相同：
     * 让 AbstractDimensionTeleporter.findClosestTeleportBlock() 能在维度内/回程时
     * 靠 POI 系统找到已放置的传送方块作为落点。
     */
    public static final DeferredHolder<PoiType, PoiType> NINE_CHUNK_TELEPORT_POI = POI_TYPES.register(
            "nine_chunk_teleport_pad",
            () -> new PoiType(
                    ImmutableSet.copyOf(ModBlocks.NINE_CHUNK_DIMENSION_BLOCK.get().getStateDefinition().getPossibleStates()),
                    0,
                    1
            )
    );

    /**
     * 四联区块奇数中心传送方块的 POI，机制与四联区块 POI 相同：
     * 让 AbstractDimensionTeleporter.findClosestTeleportBlock() 能在维度内/回程时
     * 靠 POI 系统找到已放置的传送方块作为落点。
     */
    public static final DeferredHolder<PoiType, PoiType> QUAD_CHUNK_ODD_TELEPORT_POI = POI_TYPES.register(
            "quad_chunk_odd_teleport_pad",
            () -> new PoiType(
                    ImmutableSet.copyOf(ModBlocks.QUAD_CHUNK_ODD_DIMENSION_BLOCK.get().getStateDefinition().getPossibleStates()),
                    0,
                    1
            )
    );

    /**
     * 九联区块奇数中心传送方块的 POI，机制与九联区块 POI 相同：
     * 让 AbstractDimensionTeleporter.findClosestTeleportBlock() 能在维度内/回程时
     * 靠 POI 系统找到已放置的传送方块作为落点。
     */
    public static final DeferredHolder<PoiType, PoiType> NINE_CHUNK_ODD_TELEPORT_POI = POI_TYPES.register(
            "nine_chunk_odd_teleport_pad",
            () -> new PoiType(
                    ImmutableSet.copyOf(ModBlocks.NINE_CHUNK_ODD_DIMENSION_BLOCK.get().getStateDefinition().getPossibleStates()),
                    0,
                    1
            )
    );

    private ModPOIs() {
    }
}
