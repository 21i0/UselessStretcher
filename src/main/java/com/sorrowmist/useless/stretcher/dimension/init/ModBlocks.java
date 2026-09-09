package com.sorrowmist.useless.stretcher.dimension.init;

import com.sorrowmist.useless.stretcher.dimension.DimensionCompat;
import com.sorrowmist.useless.stretcher.dimension.world.NineChunkDimTeleporter;
import com.sorrowmist.useless.stretcher.dimension.world.NineChunkOddDimTeleporter;
import com.sorrowmist.useless.stretcher.dimension.world.QuadChunkDimTeleporter;
import com.sorrowmist.useless.stretcher.dimension.world.QuadChunkOddDimTeleporter;
import com.sorrowmist.useless.content.blocks.TeleportPadBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(DimensionCompat.MODID);

    /**
     * 四联区块维度传送方块。
     * 直接复用前置 mod 的 TeleportPadBlock：它的 useItemOn/useWithoutItem 逻辑与
     * 维度传送方块完全一致（左键传送、潜行打开 DimensionConfigMenu 配置界面），
     * 因此「GUI 界面与维度传送方块完全一致」由复用前置类天然保证。
     * 属性按规格：硬度 3.5、爆炸抗性 6.0、需要镐（requiresCorrectToolForDrops +
     * mineable/pickaxe 标签由 datagen 补上）、光照 0（不发光）。
     */
    public static final DeferredBlock<Block> QUAD_CHUNK_DIMENSION_BLOCK = BLOCKS.register(
            "quad_chunk_dimension_block",
            () -> new TeleportPadBlock(
                    QuadChunkDimTeleporter::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(3.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    /**
     * 九联区块维度传送方块（3x3 区块平台）。
     * 与四联区块方块一样直接复用前置 TeleportPadBlock，GUI/传送逻辑完全一致，
     * 仅维度 key 与 POI 指向九联区块维度。
     */
    public static final DeferredBlock<Block> NINE_CHUNK_DIMENSION_BLOCK = BLOCKS.register(
            "nine_chunk_dimension_block",
            () -> new TeleportPadBlock(
                    NineChunkDimTeleporter::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(3.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    /**
     * 四联区块·奇数中心维度传送方块：与 QUAD_CHUNK_DIMENSION_BLOCK 完全相同的属性与前置
     * TeleportPadBlock 逻辑，仅目标维度指向中心为单格（奇数中心）的 quad_chunk_odd 维度，
     * 仿前置 useless_mod 的「奇数维度」（uselessdim，中心为单块）。
     */
    public static final DeferredBlock<Block> QUAD_CHUNK_ODD_DIMENSION_BLOCK = BLOCKS.register(
            "quad_chunk_odd_dimension_block",
            () -> new TeleportPadBlock(
                    QuadChunkOddDimTeleporter::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(3.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    /**
     * 九联区块·奇数中心维度传送方块：与 NINE_CHUNK_DIMENSION_BLOCK 完全相同的属性与前置
     * TeleportPadBlock 逻辑，仅目标维度指向中心为单格的 nine_chunk_odd 维度。
     */
    public static final DeferredBlock<Block> NINE_CHUNK_ODD_DIMENSION_BLOCK = BLOCKS.register(
            "nine_chunk_odd_dimension_block",
            () -> new TeleportPadBlock(
                    NineChunkOddDimTeleporter::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .strength(3.5F, 6.0F)
                            .requiresCorrectToolForDrops()
            )
    );

    private ModBlocks() {
    }
}
