package com.sorrowmist.useless.stretcher.dimension.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sorrowmist.useless.world.dimension.DimensionGenerationConfig;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

/**
 * 四联区块·奇数中心维度生成器（仿前置 useless_mod 的「奇数维度」uselessdim，即 2x2 区块尺码）。
 *
 * <p>前置「奇数维度」UselessDimGen 的单元是 16x16 区块内按局部坐标判定：只有 x==0/z==0 的前缘
 * 是边界线、x==8 && z==8 恰好一格是中心方块、其余全是地板；平铺后整个平面是「每 16 格一条单线
 * 边界 + 每条边界之间的 15 格填充带正中央恰好一格中心」。本类把同一个图样按 2x2 区块（cellSize
 * =32）放样：单元前缘单线边界，两条边界之间的填充带为 2*16-1 = 31 格（奇数格，如 31x31），
 * 正中央 cellSize/2 = (16,16) 恰好一格中心方块——就像 5 个苹果正中间是第 3 个一样，填充带是
 * 奇数格、中间必然只有一格「奇点」。反例：偶数格周界环方案的中心点在两条边界之间对不齐，这是
 * 之前版本被判定「完全不对」的原因。
 *
 * <p>复用 QuadChunkDimGen 的全部区块填充/基岩/高度逻辑，仅覆写 layoutState。配置下发仍由
 * UselessDimensionConfigManagerMixin 命中（本类是其子类）。
 */
public class QuadChunkOddDimGen extends QuadChunkDimGen {
    public static final MapCodec<QuadChunkOddDimGen> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource))
                    .apply(instance, QuadChunkOddDimGen::new)
    );

    public QuadChunkOddDimGen(BiomeSource biomeSource) {
        this(biomeSource, 32);
    }

    /** 九联奇数中心子类（cellSize=48）经由此构造器复用本类全部布局逻辑。 */
    protected QuadChunkOddDimGen(BiomeSource biomeSource, int cellSize) {
        super(biomeSource, cellSize, 1);
    }

    @NotNull
    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /**
     * 仿前置 UselessDimGen「奇数维度」的单元图样（仅前缘 lx==0/lz==0 单线边界，无对侧周界环），
     * 中心为单元正中央 cellSize/2 的单独一格（四联 (16,16)）。
     */
    @Override
    protected BlockState layoutState(DimensionGenerationConfig configuration, int lx, int lz) {
        if (lx == cellSize / 2 && lz == cellSize / 2) {
            return configuration.centerBlock().defaultBlockState();
        } else if (lx == 0 || lz == 0) {
            return configuration.borderBlock().defaultBlockState();
        }
        return configuration.fillBlock().defaultBlockState();
    }

    @Override
    protected String layoutDescription() {
        return "奇数布局(奇点维度): 前缘单线边界 + 填充带 " + (cellSize - 1) + "x" + (cellSize - 1) + " + 正中单格中心";
    }
}
