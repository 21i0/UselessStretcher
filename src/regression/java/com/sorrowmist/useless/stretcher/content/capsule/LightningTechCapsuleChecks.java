package com.sorrowmist.useless.stretcher.content.capsule;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.fml.ModList;

import java.util.UUID;
import java.lang.reflect.Method;

/** Native regression for AE2 Lightning Tech's custom End structure. */
public final class LightningTechCapsuleChecks {
    private static final String STRUCTURE = "ae2lt:firmament_starship";
    private static final String DIMENSION = DimensionCapsuleCatalog.CAPSULE_END;
    private static int ticks;
    private static boolean started;
    private static ServerPlayer player;
    private static CapsuleInstances.Instance room;

    private LightningTechCapsuleChecks() { }

    public static boolean tick(ServerLevel fallback) {
        if (!ModList.get().isLoaded("ae2lt")) {
            LogUtils.getLogger().info("CAPSULE AE2LT CHECKS SKIPPED: AE2 Lightning Tech is not loaded");
            return true;
        }
        if (++ticks > 60_000) throw new AssertionError("AE2LT capsule check timed out");
        if (!started) {
            var registry = fallback.registryAccess().registryOrThrow(Registries.STRUCTURE);
            var structure = registry.get(ResourceLocation.parse(STRUCTURE));
            check(structure != null, "AE2LT firmament structure is registered");
            var level = fallback.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                    Registries.DIMENSION, ResourceLocation.parse(DIMENSION)));
            check(level != null, "AE2LT End dimension is loaded");
            player = newPlayer(level, "AE2LTFirmament");
            check(DimensionCapsuleCatalog.dimensions(player, structure).contains(DIMENSION),
                    "AE2LT firmament structure is offered in the End");
            room = CapsuleInstances.get(fallback.getServer()).getOrCreate(player.getUUID(), STRUCTURE, DIMENSION);
            var source = DimensionCapsuleCatalog.sourceLevel(player, structure, DIMENSION);
            check(source != null, "AE2LT firmament has a compatible source world");
            moveOrigin(room, source);
            NativeCapsules.request(player, STRUCTURE, DIMENSION, 1, 0);
            started = true;
            return false;
        }
        check(room != null, "AE2LT room allocated");
        if (!room.error.isEmpty()) throw new AssertionError("AE2LT native generation failed: " + room.error);
        if (!room.ready) return false;
        var level = fallback.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.parse(DIMENSION)));
        var start = StructureStart.loadStaticStart(StructurePieceSerializationContext.fromLevel(level), room.plan,
                level.getSeed());
        check(start != null && start.isValid(), "AE2LT plan restores after generation");
        check(start.getPieces().size() > 0, "AE2LT firmament structure has a native Piece");
        int nonAir = 0;
        int ae2Blocks = 0;
        int blockEntities = 0;
        var bounds = start.getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            var state = level.getBlockState(pos);
            if (!state.isAir()) {
                nonAir++;
                ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
                if (id != null && id.getNamespace().equals("ae2lt")) ae2Blocks++;
            }
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity != null) blockEntities++;
        }
        check(nonAir >= 32, "AE2LT firmament places real structure blocks");
        check(ae2Blocks > 0, "AE2LT firmament keeps its mod blocks");
        check(blockEntities > 0, "AE2LT firmament initializes native block entities and loot carriers");
        verifySavedCache(fallback, room.key());
        LogUtils.getLogger().info("CAPSULE AE2LT CHECKS PASSED: firmament starship in End, {} pieces, {} blocks, {} AE2LT blocks, {} block entities, {} ticks",
                start.getPieces().size(), nonAir, ae2Blocks, blockEntities, ticks);
        room = null;
        return true;
    }

    private static void verifySavedCache(ServerLevel fallback, String key) {
        var data = CapsuleInstances.get(fallback.getServer());
        CompoundTag saved = data.save(new CompoundTag(), fallback.registryAccess());
        try {
            Method load = CapsuleInstances.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
            load.setAccessible(true);
            var restored = (CapsuleInstances) load.invoke(null, saved, fallback.registryAccess());
            var room = restored.rooms.get(key);
            check(room != null && room.ready && !room.plan.isEmpty(),
                    "AE2LT native plan cache is written to and restored from SavedData");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot restore capsule SavedData cache", failure);
        }
    }

    private static void moveOrigin(CapsuleInstances.Instance room, ServerLevel level) {
        // Keep the test room in an End biome accepted by the active structure tag.
        var source = level.getChunkSource().getGenerator().getBiomeSource();
        var sampler = level.getChunkSource().randomState().sampler();
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.parse(STRUCTURE));
        var pair = source.findClosestBiome3d(new BlockPos(room.x(), 80, room.z()), 4096, 64, 64,
                structure.biomes()::contains, sampler, level);
        if (pair == null) throw new AssertionError("No compatible End biome for AE2LT firmament");
        try {
            var x = room.getClass().getDeclaredField("originX");
            var z = room.getClass().getDeclaredField("originZ");
            x.setAccessible(true);
            z.setAccessible(true);
            x.setInt(room, pair.getFirst().getX());
            z.setInt(room, pair.getFirst().getZ());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot position AE2LT regression room", failure);
        }
    }

    private static ServerPlayer newPlayer(ServerLevel level, String name) {
        var profile = new GameProfile(UUID.randomUUID(), name);
        var result = new ServerPlayer(level.getServer(), level, profile,
                net.minecraft.server.level.ClientInformation.createDefault());
        var transport = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
            @Override public void setListenerForServerboundHandshake(net.minecraft.network.PacketListener listener) { }
        };
        result.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(), transport,
                result, net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,
                                       net.minecraft.network.PacketSendListener listener) { }
        };
        return result;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
