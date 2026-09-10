package com.sorrowmist.useless.stretcher.dimension.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.world.dimension.DimensionGenerationConfig;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.GenerationStep.Carving;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.jetbrains.annotations.NotNull;

/**
 * 四联区块维度生成器：每一片塑料平台由 2x2 个区块（32x32 方块）组成（chunk_size = 4）。
 *
 * 布局按 cellSize x cellSize 的「区块单元」在世界平面无限平铺（与前置单区块平台的铺法一致，
 * 保证 AbstractDimensionTeleporter.findSafeExit 在任意落点附近都能找到安全落脚点）。
 *
 * 本类（以及 NineChunkDimGen）实现的是仿前置 uselessdim2「偶数维度」的默认布局（周界环 +
 * 中央偶数格中心，见 {@link #layoutState}）；「奇数中心」子类（QuadChunkOddDimGen /
 * NineChunkOddDimGen）会覆写 {@link #layoutState}，改回前置 uselessdim「奇数维度」那种
 * 只有前缘单线边界、单元正中恰好一格「奇点」的布局。
 *
 * 默认（偶数格）布局各角色的方块来自玩家在 DimensionConfigMenu 里选的预设：
 *  - 中心方块（center）：单元中央 centerSize x centerSize（四联 32x32 默认 4x4 时局部坐标 [14,17] x [14,17]）
 *  - 边界方块（border） ：单元外圈 1 格宽的周界（局部坐标 0 或 cellSize-1）
 *  - 地板方块（fill）   ：单元内其余全部
 * 预设地板（default_* 映射到前置 DimensionGenerationConfig 的默认块）：
 *  - default_boundary -> useless_mod:aqua_glow_plastic（= 前置 DEFAULT_BORDER_BLOCK）
 *  - default_floor    -> useless_mod:white_glow_plastic（= 前置 DEFAULT_FILL_BLOCK）
 *  - default_center   -> useless_mod:light_gray_glow_plastic（= 前置 DEFAULT_CENTER_BLOCK）
 *
 * 独立实现而非继承前置 AbstractPlasticPlatformGenerator 的原因：该类的构造器是包私有
 * （package-private），本模组包无法调用，故自建 ChunkGenerator；配置下发由
 * UselessDimensionConfigManagerMixin 在 apply(ServerLevel) 尾部补上。
 *
 * 单元边长按构造参数 cellSize 参数化：2x2 区块 = 32，九联（3x3）区块 = 48。
 * NineChunkDimGen 通过受保护的带边长构造器复用本类全部逻辑。
 */
public class QuadChunkDimGen extends ChunkGenerator {
    public static final MapCodec<QuadChunkDimGen> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource))
                    .apply(instance, QuadChunkDimGen::new)
    );

    /** 每个平台单元 = 2x2 区块 = 32 格边长；子类（九联 3x3 区块 = 48）改传更大值。 */
    protected final int cellSize;
    /** 默认布局（偶数格）单元中央中心方块区域的边长：默认 4（4x4）；奇数中心子类恒为 1。 */
    private final int centerSize;
    /** 默认布局（偶数格）单元中央中心方块的局部坐标范围（含）：cellSize/2 居中 centerSize x centerSize。 */
    private final int centerMin;
    private final int centerMax;

    private volatile DimensionGenerationConfig configuration = DimensionGenerationConfig.defaults();

    public QuadChunkDimGen(BiomeSource biomeSource) {
        this(biomeSource, 32, 4);
    }

    /** 子类（如九联 3x3 区块，cellSize=48）复用时传入更大边长（中心保持 4x4）。 */
    protected QuadChunkDimGen(BiomeSource biomeSource, int cellSize) {
        this(biomeSource, cellSize, 4);
    }

    /**
     * centerSize = 默认布局（偶数格）中心方块区域边长（奇数中心子类传 1，但其实际布局由
     * {@link #layoutState} 覆写决定，与本字段无关）。
     */
    protected QuadChunkDimGen(BiomeSource biomeSource, int cellSize, int centerSize) {
        super(biomeSource);
        this.cellSize = cellSize;
        this.centerSize = centerSize;
        this.centerMin = cellSize / 2 - centerSize / 2;
        this.centerMax = cellSize / 2 + (centerSize - 1) / 2;
    }

    @NotNull
    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    public void setConfiguration(DimensionGenerationConfig configuration) {
        this.configuration = filterAllowed(configuration.normalized());
    }

    /** Applies this addon's own dimension-floor blacklist/whitelist, falling back to defaults. */
    private DimensionGenerationConfig filterAllowed(DimensionGenerationConfig configuration) {
        return rebuild(
                allowed(configuration.borderBlockId(), DimensionGenerationConfig.DEFAULT_BORDER_BLOCK),
                allowed(configuration.fillBlockId(), DimensionGenerationConfig.DEFAULT_FILL_BLOCK),
                allowed(configuration.centerBlockId(), DimensionGenerationConfig.DEFAULT_CENTER_BLOCK),
                configuration);
    }

    /**
     * 用本模组过滤后的三个方块 ID 重建前置的配置 record。
     *
     * <p>无用之物 2.3.6 给这个 record 新增了第 9 个分量 {@code features}（边界/道路/中心标记等
     * 表面特征配置）。旧的 7 参构造器仍然保留，但它会把 {@code features} 重置为默认值——直接用它
     * 会把玩家在维度 GUI 里配好的道路、边界、中心标记全部悄悄清掉。所以这里优先用反射拿到
     * 带 {@code features} 的构造器并原样带过去；在更旧的无用之物上（没有 {@code features()}）
     * 自动退回 7 参构造器。
     */
    private static DimensionGenerationConfig rebuild(ResourceLocation border, ResourceLocation fill,
                                                     ResourceLocation center,
                                                     DimensionGenerationConfig source) {
        try {
            Object features = DimensionGenerationConfig.class.getMethod("features").invoke(source);
            Constructor<DimensionGenerationConfig> constructor = DimensionGenerationConfig.class.getConstructor(
                    ResourceLocation.class, ResourceLocation.class, ResourceLocation.class,
                    int.class, int.class, boolean.class, boolean.class, features.getClass());
            return constructor.newInstance(border, fill, center,
                    source.platformLayers(), source.platformStartY(),
                    source.generateBedrock(), source.bedrockAtBottom(), features);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return new DimensionGenerationConfig(border, fill, center,
                    source.platformLayers(), source.platformStartY(),
                    source.generateBedrock(), source.bedrockAtBottom());
        }
    }

    private static ResourceLocation allowed(ResourceLocation id, ResourceLocation fallback) {
        return StretcherConfig.isDimensionFloorBlockAllowed(id) ? id : fallback;
    }

    private BlockState platformStateFor(DimensionGenerationConfig configuration, int x, int z) {
        int lx = Math.floorMod(x, cellSize);
        int lz = Math.floorMod(z, cellSize);
        return layoutState(configuration, lx, lz);
    }

    /**
     * 单个平台单元内的方块角色判定，参数为单元局部坐标 [0, cellSize)。
     *
     * <p>默认实现 = 仿前置 uselessdim2「偶数维度」的偶数格布局：中心 centerSize x centerSize
     * （默认 4x4，居中于 cellSize/2）为中心方块；单元外圈周界（lx==0 或 lx==cellSize-1 或
     * lz==0 或 lz==cellSize-1）为边界方块；其余全部为地板方块。
     *
     * <p>「奇数中心」子类必须覆写此方法，改回前置 uselessdim「奇数维度」的布局：
     * 前缘 lx==0/lz==0 单线边界 + 单元正中（cellSize/2）恰好一格的中心方块，其余全为地板。
     */
    protected BlockState layoutState(DimensionGenerationConfig configuration, int lx, int lz) {
        if (lx >= centerMin && lx <= centerMax && lz >= centerMin && lz <= centerMax) {
            return configuration.centerBlock().defaultBlockState();
        } else if (lx == 0 || lx == cellSize - 1 || lz == 0 || lz == cellSize - 1) {
            return configuration.borderBlock().defaultBlockState();
        }
        return configuration.fillBlock().defaultBlockState();
    }

    private int getTopY(DimensionGenerationConfig configuration) {
        return configuration.platformStartY() + configuration.platformLayers();
    }

    private int getBottomY(DimensionGenerationConfig configuration) {
        return configuration.platformStartY();
    }

    private boolean shouldGenerateBedrock(DimensionGenerationConfig configuration) {
        return configuration.generateBedrock();
    }

    private int getBedrockY(DimensionGenerationConfig configuration, int minY, int maxY) {
        return configuration.bedrockAtBottom() ? clampY(minY, minY, maxY) : clampY(getBottomY(configuration), minY, maxY);
    }

    private static int clampY(int y, int minY, int maxY) {
        return Math.max(minY, Math.min(maxY - 1, y));
    }

    @Override
    public void applyCarvers(
            @NotNull WorldGenRegion region,
            long seed,
            @NotNull RandomState randomState,
            @NotNull BiomeManager biomeManager,
            @NotNull StructureManager structureManager,
            @NotNull ChunkAccess chunk,
            @NotNull Carving step
    ) {
    }

    @Override
    public void buildSurface(
            @NotNull WorldGenRegion region, @NotNull StructureManager structureManager, @NotNull RandomState randomState, @NotNull ChunkAccess chunk
    ) {
        MutableBlockPos pos = new MutableBlockPos();
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Types.WORLD_SURFACE_WG);
        Heightmap motionBlocking = chunk.getOrCreateHeightmapUnprimed(Types.MOTION_BLOCKING);
        Heightmap motionBlockingNoLeaves = chunk.getOrCreateHeightmapUnprimed(Types.MOTION_BLOCKING_NO_LEAVES);
        int minY = region.getMinBuildHeight();
        int maxY = region.getMaxBuildHeight();
        DimensionGenerationConfig configuration = this.configuration;
        int bottomY = clampY(getBottomY(configuration), minY, maxY);
        int topY = Math.max(bottomY, clampY(getTopY(configuration), minY, maxY));
        int bedrockY = getBedrockY(configuration, minY, maxY);
        int chunkMinX = chunk.getPos().getMinBlockX();
        int chunkMinZ = chunk.getPos().getMinBlockZ();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int gx = chunkMinX + x;
                int gz = chunkMinZ + z;
                if (shouldGenerateBedrock(configuration)) {
                    chunk.setBlockState(pos.set(x, bedrockY, z), Blocks.BEDROCK.defaultBlockState(), false);
                }
                for (int y = bottomY + 1; y <= topY; y++) {
                    chunk.setBlockState(pos.set(x, y, z), platformStateFor(configuration, gx, gz), false);
                }
                BlockState surfaceState = configuration.fillBlock().defaultBlockState();
                worldSurface.update(x, topY, z, surfaceState);
                oceanFloor.update(x, topY, z, surfaceState);
                motionBlocking.update(x, topY, z, surfaceState);
                motionBlockingNoLeaves.update(x, topY, z, surfaceState);
                for (int y = topY + 1; y < maxY; y++) {
                    chunk.setBlockState(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), false);
                }
            }
        }
    }

    @Override
    public void spawnOriginalMobs(@NotNull WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @NotNull
    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            @NotNull Blender blender,
            @NotNull RandomState randomState,
            @NotNull StructureManager structureManager,
            @NotNull ChunkAccess chunk
    ) {
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(int x, int z, @NotNull Types heightmap, @NotNull LevelHeightAccessor level, @NotNull RandomState randomState) {
        DimensionGenerationConfig configuration = this.configuration;
        return Math.min(level.getMaxBuildHeight(), clampY(getTopY(configuration), level.getMinBuildHeight(), level.getMaxBuildHeight()) + 1);
    }

    @NotNull
    @Override
    public NoiseColumn getBaseColumn(int x, int z, @NotNull LevelHeightAccessor level, @NotNull RandomState randomState) {
        BlockState[] column = new BlockState[level.getHeight()];
        int minBuild = level.getMinBuildHeight();
        int maxBuild = level.getMaxBuildHeight();
        DimensionGenerationConfig configuration = this.configuration;
        int bottomY = clampY(getBottomY(configuration), minBuild, maxBuild);
        int topY = Math.max(bottomY, clampY(getTopY(configuration), minBuild, maxBuild));
        int bedrockY = getBedrockY(configuration, minBuild, maxBuild);
        for (int y = minBuild; y < level.getMaxBuildHeight(); y++) {
            int idx = y - minBuild;
            if (y == bedrockY && shouldGenerateBedrock(configuration)) {
                column[idx] = Blocks.BEDROCK.defaultBlockState();
            } else if (y > bottomY && y <= topY) {
                column[idx] = platformStateFor(configuration, x, z);
            } else {
                column[idx] = Blocks.AIR.defaultBlockState();
            }
        }
        return new NoiseColumn(minBuild, column);
    }

    /** 布局描述，供 F3 调试屏显示；「奇数中心」子类会覆写。 */
    protected String layoutDescription() {
        return "偶数格布局: 周界环 1 格 + 中心 " + centerSize + "x" + centerSize;
    }

    @Override
    public void addDebugScreenInfo(List<String> info, @NotNull RandomState randomState, @NotNull BlockPos pos) {
        DimensionGenerationConfig configuration = this.configuration;
        info.add("Plastic Platform Dimension - " + (cellSize / 16) + "x" + (cellSize / 16) + " chunk (" + cellSize + "x" + cellSize + ") platform cells");
        info.add(layoutDescription());
        info.add("平台高度: Y=" + getBottomY(configuration) + " ~ " + getTopY(configuration));
        info.add("边框方块: " + configuration.borderBlockId());
        info.add("地板方块: " + configuration.fillBlockId());
        info.add("中心方块: " + configuration.centerBlockId());
    }
}
