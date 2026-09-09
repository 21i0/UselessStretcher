package com.sorrowmist.useless.stretcher.dimension.world;

import com.sorrowmist.useless.stretcher.dimension.init.ModBlocks;
import com.sorrowmist.useless.stretcher.dimension.init.ModPOIs;
import com.sorrowmist.useless.world.teleport.AbstractDimensionTeleporter;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * 四联区块维度的传送器，复用前置 AbstractDimensionTeleporter 的完整传送链路：
 * 找落点、POI 检索、传送、配置界面（DimensionConfigMenu）触发逻辑全部继承自前置。
 */
public class QuadChunkDimTeleporter extends AbstractDimensionTeleporter {
    @Override
    protected ResourceKey<Level> getDimensionKey() {
        return QuadChunkDimensions.QUAD_CHUNK_KEY;
    }

    @Override
    protected Supplier<Block> getTeleportBlock() {
        return ModBlocks.QUAD_CHUNK_DIMENSION_BLOCK;
    }

    @Override
    protected Holder<PoiType> getPOI() {
        return ModPOIs.QUAD_CHUNK_TELEPORT_POI;
    }
}
