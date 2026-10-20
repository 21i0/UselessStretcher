package com.sorrowmist.useless.stretcher.content.capsule;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import com.sorrowmist.useless.compat.forge1201.registry.DeferredRegister;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** One stable, terrain-free storage world per real dimension, recreated before player login. */
public final class CapsuleDimensions {
    private static final DeferredRegister<Codec<? extends ChunkGenerator>> GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, UselessStretcherMod.MODID);
    static { GENERATORS.register("capsule_void", () -> CapsuleVoidChunkGenerator.CODEC); }
    private CapsuleDimensions() { }
    public static void init(IEventBus bus) { GENERATORS.register(bus); }

    /** Forge mods can add worlds after MinecraftServer.createLevels has returned. */
    public interface ServerAccess { void stretcher$ensureCapsuleDimensions(); }
    public static void ensure(MinecraftServer server) {
        ((ServerAccess) server).stretcher$ensureCapsuleDimensions();
    }

    public static boolean isCapsule(ResourceLocation id) {
        return id.getNamespace().equals(UselessStretcherMod.MODID) && id.getPath().startsWith("capsule_");
    }
    public static String targetFor(ResourceLocation source) {
        if (source.equals(Level.OVERWORLD.location())) return DimensionCapsuleCatalog.CAPSULE_OVERWORLD;
        if (source.equals(Level.NETHER.location())) return DimensionCapsuleCatalog.CAPSULE_NETHER;
        if (source.equals(Level.END.location())) return DimensionCapsuleCatalog.CAPSULE_END;
        return UselessStretcherMod.MODID + ":capsule_mirror/" + source.getNamespace() + "/" + source.getPath();
    }
    public static void bootstrap(MinecraftServer server, Executor executor,
            LevelStorageSource.LevelStorageAccess storage, ChunkProgressListener listener) {
        List<ServerLevel> sources = new ArrayList<>();
        server.getAllLevels().forEach(level -> { if (!isCapsule(level.dimension().location())) sources.add(level); });
        for (ServerLevel source : sources) {
            ResourceKey<Level> target = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(targetFor(source.dimension().location())));
            ServerLevel mirror = server.getLevel(target);
            if (mirror == null) {
                CapsuleVoidChunkGenerator generator = new CapsuleVoidChunkGenerator(
                        source.getChunkSource().getGenerator().getBiomeSource(), source.getMinBuildHeight(),
                        source.getHeight(), source.getSeaLevel());
                generator.bindSource(source);
                mirror = new ServerLevel(server, executor, storage,
                        new DerivedLevelData(server.getWorldData(), (net.minecraft.world.level.storage.ServerLevelData) source.getLevelData()),
                        target, new LevelStem(source.dimensionTypeRegistration(), generator), listener,
                        server.getWorldData().isDebugWorld(), BiomeManager.obfuscateSeed(source.getSeed()),
                        List.of(), false, server.overworld().getRandomSequences());
                // Storage coordinates are independent of the source. A small source
                // border must not exclude the capsule allocator's reserved rooms.
                server.forgeGetWorldMap().put(target, mirror);
                server.markWorldsDirty();
                MinecraftForge.EVENT_BUS.post(new LevelEvent.Load(mirror));
                LogUtils.getLogger().info("CAPSULE ENVIRONMENT {} <- {} type={} minY={} height={}", target.location(),
                        source.dimension().location(), source.dimensionTypeRegistration().unwrapKey().orElse(null),
                        mirror.getMinBuildHeight(), mirror.getHeight());
            }
            if (mirror.getChunkSource().getGenerator() instanceof CapsuleVoidChunkGenerator generator) generator.bindSource(source);
        }
    }
}
