package com.sorrowmist.useless.stretcher.content.capsule;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CapsuleInstancesTest {
    @Test void actualPlanMustRetainCleanupClearanceEvenWhenItsBoxesDoNotOverlap() {
        var data = new CapsuleInstances();
        var owner = UUID.randomUUID();
        var first = data.getOrCreate(owner, "minecraft:pillager_outpost", DimensionCapsuleCatalog.CAPSULE_OVERWORLD);
        first.boundsKnown = true;
        first.minX = first.x(); first.maxX = first.x() + 63;
        first.minZ = first.z(); first.maxZ = first.z() + 63;
        var next = data.getOrCreateAdjacent(owner, first.structure, first.dimension, 0);
        next.boundsKnown = true;
        next.minX = first.maxX + 2; next.maxX = next.minX + 63;
        next.minZ = first.minZ; next.maxZ = first.maxZ;
        assertFalse(CapsuleInstances.boundsOverlap(first, next));
        int[] shift = data.snapBesideExisting(next);
        assertTrue(shift[0] != 0 || shift[1] != 0);
        assertTrue(CapsuleInstances.adjacent(first, next));
        assertTrue(next.minX - first.maxX > CapsuleInstances.reservedMargin()
                || first.minX - next.maxX > CapsuleInstances.reservedMargin()
                || next.minZ - first.maxZ > CapsuleInstances.reservedMargin()
                || first.minZ - next.maxZ > CapsuleInstances.reservedMargin());
    }
    @Test void checkpointDoesNotRequireGlobalRunningServer() throws Exception {
        var owner = UUID.randomUUID();
        var data = new CapsuleInstances();
        var room = data.getOrCreate(owner, "minecraft:desert_pyramid", DimensionCapsuleCatalog.CAPSULE_OVERWORLD);
        room.plan.putString("test_plan", "exact native checkpoint");
        room.sourceDimension = "minecraft:overworld";
        room.sourceSeed = 928173L;
        room.generating = true;
        room.phase = "placing";
        room.chunk = 3;
        room.piece = 2;
        room.progress = 42;
        var saved = data.save(new CompoundTag());
        var restored = reload(saved);
        var copy = restored.rooms.get(room.key());
        assertNotNull(copy);
        assertTrue(copy.generating);
        assertEquals(room.plan, copy.plan);
        assertEquals(3, copy.chunk);
        assertEquals(2, copy.piece);
        assertEquals(42, copy.progress);
        assertEquals(room.sourceDimension, copy.sourceDimension);
        assertEquals(room.sourceSeed, copy.sourceSeed);
        assertSame(copy, restored.getOrCreate(owner, room.structure, room.dimension));
    }

    @Test void legacyRoomAndAdjacentRequestsRemainStableAfterReload() throws Exception {
        var owner = UUID.randomUUID();
        var data = new CapsuleInstances();
        var room = data.getOrCreate(owner, "minecraft:fortress", "minecraft:the_nether");
        var next = data.getOrCreateAdjacent(owner, room.structure, room.dimension, 0);
        assertSame(next, data.getOrCreateAdjacent(owner, room.structure, room.dimension, 0));
        var saved = data.save(new CompoundTag());
        var first = saved.getList("rooms", 10).getCompound(0);
        first.remove("instance_id");
        first.remove("completion_notified");
        first.putBoolean("ready", true);
        var migrated = reload(saved);
        var original = migrated.rooms.get(CapsuleInstances.key(owner, room.structure, "useless_stretcher:capsule_nether"));
        assertNotNull(original);
        assertTrue(original.ready);
        assertTrue(original.completionNotified);
        assertEquals(2, migrated.instanceCount(owner, room.structure, original.dimension));
        var reloaded = reload(migrated.save(new CompoundTag()));
        assertEquals(migrated.rooms.keySet(), reloaded.rooms.keySet());
        assertEquals(2, reloaded.instanceCount(owner, room.structure, original.dimension));
    }

    private static CapsuleInstances reload(CompoundTag saved) throws Exception {
        var method = CapsuleInstances.class.getDeclaredMethod("load", CompoundTag.class,
                net.minecraft.core.HolderLookup.Provider.class);
        method.setAccessible(true);
        return (CapsuleInstances) method.invoke(null, saved, null);
    }
}
