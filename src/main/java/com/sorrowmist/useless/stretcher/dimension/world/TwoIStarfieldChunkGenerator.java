package com.sorrowmist.useless.stretcher.dimension.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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

/** A deterministic, mob-free superflat platform with a fixed, safe arrival height. */
public final class TwoIStarfieldChunkGenerator extends ChunkGenerator {
    public static final int SURFACE_Y = 64;
    private static final int BEDROCK_Y = SURFACE_Y - 4;
    public static final Codec<TwoIStarfieldChunkGenerator> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
                            Codec.BOOL.optionalFieldOf("platform", true).forGetter(g -> g.platform))
                    .apply(instance, TwoIStarfieldChunkGenerator::new));

    private volatile BlockState floorState = Blocks.WHITE_CONCRETE.defaultBlockState();
    private final boolean platform;

    public TwoIStarfieldChunkGenerator(BiomeSource biomeSource) {
        this(biomeSource, true);
    }

    public TwoIStarfieldChunkGenerator(BiomeSource biomeSource, boolean platform) {
        super(biomeSource);
        this.platform = platform;
        resolveFloorState();
    }

    /** Preserve the original white glowing floor without retaining the retired dimension subsystem. */
    public void resolveFloorState() {
        setFloorState(BuiltInRegistries.BLOCK.get(new ResourceLocation(
                "useless_mod", "white_glow_plastic")).defaultBlockState());
    }

    public void setFloorState(BlockState floorState) {
        if (floorState != null && !floorState.isAir() && floorState.getFluidState().isEmpty()
                && floorState.blocksMotion()) {
            this.floorState = floorState;
        }
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void applyCarvers(@NotNull WorldGenRegion region, long seed, @NotNull RandomState randomState,
                             @NotNull BiomeManager biomeManager, @NotNull StructureManager structureManager,
                             @NotNull ChunkAccess chunk, @NotNull Carving step) {
    }

    @Override
    public void buildSurface(@NotNull WorldGenRegion region, @NotNull StructureManager structureManager,
                             @NotNull RandomState randomState, @NotNull ChunkAccess chunk) {
        if (!platform) return;
        MutableBlockPos pos = new MutableBlockPos();
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Types.WORLD_SURFACE_WG);
        Heightmap motionBlocking = chunk.getOrCreateHeightmapUnprimed(Types.MOTION_BLOCKING);
        Heightmap motionBlockingNoLeaves = chunk.getOrCreateHeightmapUnprimed(Types.MOTION_BLOCKING_NO_LEAVES);
        BlockState floor = floorState;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunk.setBlockState(pos.set(x, BEDROCK_Y, z), Blocks.BEDROCK.defaultBlockState(), false);
                for (int y = BEDROCK_Y + 1; y < SURFACE_Y; y++) {
                    chunk.setBlockState(pos.set(x, y, z), floor, false);
                }
                int dx = TwoIStarfieldCourtyard.relative(chunk.getPos().getMinBlockX() + x);
                int dz = TwoIStarfieldCourtyard.relative(chunk.getPos().getMinBlockZ() + z);
                boolean garden = Math.abs(dx) <= 13 && dz >= -13 && dz <= 19;
                for (int dy = -1; garden && dy <= TwoIStarfieldCourtyard.MAX_HEIGHT; dy++) {
                    BlockState decor = TwoIStarfieldCourtyard.stateAt(dx, dy, dz);
                    if (decor != null) chunk.setBlockState(pos.set(x, SURFACE_Y + dy, z), decor, false);
                }
                int top = garden ? SURFACE_Y + TwoIStarfieldCourtyard.MAX_HEIGHT : SURFACE_Y - 1;
                for (int y = SURFACE_Y - 1; y <= top; y++) {
                    BlockState state = chunk.getBlockState(pos.set(x, y, z));
                    worldSurface.update(x, y, z, state);
                    oceanFloor.update(x, y, z, state);
                    motionBlocking.update(x, y, z, state);
                    motionBlockingNoLeaves.update(x, y, z, state);
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

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(java.util.concurrent.Executor executor, @NotNull Blender blender, @NotNull RandomState randomState,
                                                          @NotNull StructureManager structureManager,
                                                          @NotNull ChunkAccess chunk) {
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
    public int getBaseHeight(int x, int z, @NotNull Types heightmap, @NotNull LevelHeightAccessor level,
                             @NotNull RandomState randomState) {
        if (!platform) return level.getMinBuildHeight();
        int dx = TwoIStarfieldCourtyard.relative(x);
        int dz = TwoIStarfieldCourtyard.relative(z);
        for (int dy = TwoIStarfieldCourtyard.MAX_HEIGHT; dy >= 0; dy--) {
            BlockState decor = TwoIStarfieldCourtyard.stateAt(dx, dy, dz);
            if (decor != null && heightmap.isOpaque().test(decor)) return SURFACE_Y + dy + 1;
        }
        return SURFACE_Y;
    }

    @Override
    public @NotNull NoiseColumn getBaseColumn(int x, int z, @NotNull LevelHeightAccessor level,
                                               @NotNull RandomState randomState) {
        BlockState[] column = new BlockState[level.getHeight()];
        int minY = level.getMinBuildHeight();
        int dx = TwoIStarfieldCourtyard.relative(x);
        int dz = TwoIStarfieldCourtyard.relative(z);
        for (int y = minY; y < level.getMaxBuildHeight(); y++) {
            BlockState state = platform && y == BEDROCK_Y ? Blocks.BEDROCK.defaultBlockState()
                    : y > BEDROCK_Y && y < SURFACE_Y ? floorState : Blocks.AIR.defaultBlockState();
            if (!platform) state = Blocks.AIR.defaultBlockState();
            BlockState decor = platform ? TwoIStarfieldCourtyard.stateAt(dx, y - SURFACE_Y, dz) : null;
            column[y - minY] = decor == null ? state : decor;
        }
        return new NoiseColumn(minY, column);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, @NotNull RandomState randomState, @NotNull BlockPos pos) {
        info.add("2i Starfield Superflat - arrival Y=" + SURFACE_Y);
    }
}
