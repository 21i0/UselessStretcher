package com.sorrowmist.useless.stretcher.dimension.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

/**
 * 九联区块·奇数中心维度生成器（仿前置 useless_mod「奇数维度」uselessdim，按 3x3 区块 = cellSize
 * 48 放样）：继承 QuadChunkOddDimGen 的奇数布局（前缘单线边界 + 填充带 cellSize-1 = 47 格 +
 * 正中央 cellSize/2 = (24,24) 恰好一格中心），仅把单元边长换成 48。
 */
public class NineChunkOddDimGen extends QuadChunkOddDimGen {
    public static final MapCodec<NineChunkOddDimGen> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource))
                    .apply(instance, NineChunkOddDimGen::new)
    );

    public NineChunkOddDimGen(BiomeSource biomeSource) {
        super(biomeSource, 48);
    }

    @NotNull
    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }
}
