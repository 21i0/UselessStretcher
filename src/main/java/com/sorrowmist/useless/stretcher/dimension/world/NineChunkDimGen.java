package com.sorrowmist.useless.stretcher.dimension.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.jetbrains.annotations.NotNull;

/**
 * 九联区块维度生成器：每一片塑料平台由 3x3 个区块（48x48 方块）组成。
 *
 * 复用 QuadChunkDimGen 的全部平台逻辑，仅把单元边长改为 48：
 *  - 中心方块（center）：单元中央 4x4（局部坐标 [22,25] x [22,25]）
 *  - 边界方块（border） ：单元外圈 1 格宽的周界（局部坐标 0 或 47）
 *  - 地板方块（fill）   ：单元内其余全部
 * 同样按 48x48 的「区块单元」在世界平面无限平铺，保证前置
 * AbstractDimensionTeleporter.findSafeExit 在任意落点附近都能找到安全落脚点。
 */
public class NineChunkDimGen extends QuadChunkDimGen {
    public static final MapCodec<NineChunkDimGen> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource))
                    .apply(instance, NineChunkDimGen::new)
    );

    public NineChunkDimGen(BiomeSource biomeSource) {
        super(biomeSource, 48);
    }

    @NotNull
    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }
}
