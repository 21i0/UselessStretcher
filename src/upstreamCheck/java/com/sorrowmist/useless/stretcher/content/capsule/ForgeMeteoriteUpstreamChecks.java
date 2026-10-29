package com.sorrowmist.useless.stretcher.content.capsule;

import appeng.worldgen.meteorite.MeteoriteStructurePiece;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.List;
import java.util.UUID;

/** Actual AE2 15.x pieces, including old plans whose BB moved but absolute center did not. */
public final class ForgeMeteoriteUpstreamChecks {
    private ForgeMeteoriteUpstreamChecks() { }

    public static void run(ServerLevel level) throws ReflectiveOperationException {
        var context = StructurePieceSerializationContext.fromLevel(level);
        BlockPos center = new BlockPos(-24, -62, 19);
        var tag = new CompoundTag();
        tag.putIntArray("BB", new int[]{-64, -62, -64, 79, -62, 79});
        tag.putLong("c", center.asLong());
        tag.putFloat("r", 6);
        tag.putByte("t", (byte) appeng.worldgen.meteorite.CraterType.NONE.ordinal());
        tag.putByte("f", (byte) appeng.worldgen.meteorite.fallout.FalloutMode.NONE.ordinal());
        var piece = new MeteoriteStructurePiece(tag);
        var moved = (MeteoriteStructurePiece) CapsulePieceGeometry.move(piece, 31, 20, -45, context);
        check(moved.getSettings().getPos().equals(center.offset(31, 20, -45)), "absolute center follows all three axes");
        check(moved.getBoundingBox().minY() == -50 && moved.getBoundingBox().maxY() == -34,
                "complete native body remains inside the saved envelope");
        var restored = new MeteoriteStructurePiece(moved.createTag(context));
        check(restored.getSettings().getPos().equals(moved.getSettings().getPos())
                && restored.getBoundingBox().equals(moved.getBoundingBox()), "native AE2 NBT roundtrip");
        var data = new CapsuleInstances();
        var room = data.getOrCreate(UUID.randomUUID(), "ae2:meteorite", DimensionCapsuleCatalog.CAPSULE_OVERWORLD);
        room.sourceCoordinates = true;
        room.sourceOffsetX = -31; room.sourceOffsetY = -20; room.sourceOffsetZ = 45;
        // Reproduce vanilla Piece.move's old bug: only the bounding box is translated.
        var legacy = new MeteoriteStructurePiece(tag);
        legacy.move(31, 20, -45);
        var repaired = (MeteoriteStructurePiece) CapsulePieceGeometry.migrate(legacy, room, context);
        check(repaired.getSettings().getPos().equals(moved.getSettings().getPos()), "old absolute center repaired once");
        check(repaired.getBoundingBox().equals(moved.getBoundingBox()), "old bounding box retains its horizontal location");
        check(repaired.createTag(context).getFloat("r") == tag.getFloat("r"), "native meteorite settings retained");
        room.geometryVersion = CapsulePieceGeometry.VERSION;
        check(room.save().getInt("geometry_version") == CapsulePieceGeometry.VERSION, "geometry marker saved");
        checkpointMigration(level, context);
        LogUtils.getLogger().info("FORGE METEORITE GEOMETRY PASSED: AE2 15.x relocation, negative coordinates, full bounds, legacy center/native NBT, unfinished replay and completed/finalizing restart preservation");
    }

    private static void checkpointMigration(ServerLevel level, StructurePieceSerializationContext context) throws ReflectiveOperationException {
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(new ResourceLocation("ae2:meteorite"));
        check(structure != null, "actual registered AE2 meteorite structure");
        var tag = new CompoundTag();
        tag.putIntArray("BB", new int[]{-33, 70, -109, 110, 70, 34});
        tag.putLong("c", new BlockPos(-24, 70, 19).asLong());
        tag.putFloat("r", 6);
        tag.putByte("t", (byte) appeng.worldgen.meteorite.CraterType.NONE.ordinal());
        tag.putByte("f", (byte) appeng.worldgen.meteorite.fallout.FalloutMode.NONE.ordinal());
        var plan = new StructureStart(structure, new ChunkPos(0, 0), 0,
                new PiecesContainer(List.of(new MeteoriteStructurePiece(tag)))).createTag(context, new ChunkPos(0, 0));
        var constructor = Class.forName(NativeCapsules.class.getName() + "$Job").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var loader = CapsuleInstances.class.getDeclaredMethod("load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        loader.setAccessible(true);
        for (String phase : List.of("placing", "ready", "bedrock", "trimming")) {
            var data = new CapsuleInstances();
            var room = data.getOrCreate(UUID.randomUUID(), "ae2:meteorite", DimensionCapsuleCatalog.CAPSULE_OVERWORLD);
            room.plan = plan.copy(); room.phase = phase; room.ready = phase.equals("ready");
            room.sourceCoordinates = true; room.sourceOffsetX = -31; room.sourceOffsetZ = 45;
            room.chunk = 3; room.terrainCleaned = true; room.pixels[0] = 0xFF667788;
            constructor.newInstance(data, room, level, level, structure);
            check(room.geometryVersion == CapsulePieceGeometry.VERSION, "geometry decision checkpointed: " + phase);
            if (phase.equals("placing")) {
                check(room.phase.equals("cleaning") && room.chunk == 0 && !room.terrainCleaned && room.pixels[0] == 0,
                        "only unfinished placement resets for clean replay");
                var child = new MeteoriteStructurePiece(room.plan.getList("Children", 10).getCompound(0));
                check(child.getSettings().getPos().equals(new BlockPos(7, 70, -26)) && child.getBoundingBox().minY() == 62,
                        "saved resumed plan repairs center and full vertical body");
            } else {
                check(!room.phase.equals("cleaning") && room.chunk == 3 && room.terrainCleaned && room.pixels[0] == 0xFF667788
                                && room.plan.equals(plan), "completed/finalizing legacy room is never replayed: " + phase);
                var saved = data.save(new CompoundTag());
                // Also reproduce a checkpoint made by the old foundation repair before
                // a geometry marker existed, even when its ready flag is already false.
                saved.getList("rooms", 10).getCompound(0).remove("geometry_version");
                var reloaded = (CapsuleInstances) loader.invoke(null, saved, level.registryAccess());
                var copy = reloaded.rooms.get(room.key());
                constructor.newInstance(reloaded, copy, level, level, structure);
                check(!copy.phase.equals("cleaning") && copy.chunk == 3 && copy.terrainCleaned && copy.pixels[0] == 0xFF667788
                                && copy.plan.equals(plan), "restart of legacy foundation repair preserves player-owned projection: " + phase);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
