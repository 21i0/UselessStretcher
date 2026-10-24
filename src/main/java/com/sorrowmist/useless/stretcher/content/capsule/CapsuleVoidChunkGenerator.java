package com.sorrowmist.useless.stretcher.content.capsule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Empty physical storage; only explicit native capsule callbacks may place blocks. */
public final class CapsuleVoidChunkGenerator extends ChunkGenerator {
    public static final Codec<CapsuleVoidChunkGenerator> CODEC = RecordCodecBuilder.create(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            Codec.INT.fieldOf("min_y").forGetter(g -> g.minY),
            Codec.INT.fieldOf("height").forGetter(g -> g.height),
            Codec.INT.fieldOf("sea_level").forGetter(g -> g.seaLevel)
    ).apply(i, CapsuleVoidChunkGenerator::new));
    private final int minY, height, seaLevel;
    private ServerLevel source;

    public CapsuleVoidChunkGenerator(BiomeSource biomes, int minY, int height, int seaLevel) {
        super(biomes);
        this.minY = minY; this.height = height; this.seaLevel = seaLevel;
    }
    void bindSource(ServerLevel source) { this.source = source; }
    @Override protected Codec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public int getGenDepth() { return height; }
    @Override public int getMinY() { return minY; }
    @Override public int getSeaLevel() { return seaLevel; }
    @Override public WeightedRandomList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> biome,
            StructureManager structures, MobCategory category, BlockPos pos) {
        return source == null ? super.getMobsAt(biome, structures, category, pos)
                : source.getChunkSource().getGenerator().getMobsAt(biome, structures, category, pos);
    }
    @Override public CompletableFuture<ChunkAccess> createBiomes(java.util.concurrent.Executor executor, RandomState state, Blender blender,
            StructureManager structures, ChunkAccess chunk) {
        return super.createBiomes(executor, source == null ? state : source.getChunkSource().randomState(), blender, structures, chunk);
    }
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(java.util.concurrent.Executor executor, Blender blender, RandomState state,
            StructureManager structures, ChunkAccess chunk) { return CompletableFuture.completedFuture(chunk); }
    @Override public void createStructures(RegistryAccess access, ChunkGeneratorStructureState state,
            StructureManager structures, ChunkAccess chunk, StructureTemplateManager templates) { }
    @Override public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) { }
    @Override public void applyCarvers(WorldGenRegion level, long seed, RandomState state, BiomeManager biomes,
            StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) { }
    @Override public void buildSurface(WorldGenRegion level, StructureManager structures, RandomState state, ChunkAccess chunk) { }
    @Override public void spawnOriginalMobs(WorldGenRegion level) { }
    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState state) {
        return level.getMinBuildHeight();
    }
    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState state) {
        BlockState[] blocks = new BlockState[level.getHeight()];
        Arrays.fill(blocks, Blocks.AIR.defaultBlockState());
        return new NoiseColumn(level.getMinBuildHeight(), blocks);
    }
    @Override public void addDebugScreenInfo(List<String> lines, RandomState state, BlockPos pos) {
        lines.add("Capsule: native structures, no terrain generation");
    }
}
