package com.sorrowmist.useless.stretcher.content.capsule;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/** Source terrain queries during native callbacks; physical capsule chunks stay empty. */
public final class CapsuleGenerationContext implements AutoCloseable {
    private static final ThreadLocal<CapsuleGenerationContext> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<CapsuleGravitySupport.IndexBlocked> LAST_FAILURE = new ThreadLocal<>();
    private CapsuleGravitySupport.IndexBlocked gravityFailure;
    private final CapsuleGenerationContext previous;
    private final ServerLevel target, source;
    private final int dx, dy, dz;
    private final boolean sourceTerrain;
    private final boolean nativeMeteoriteGravity;
    private final java.util.Map<String, Integer> heights = new java.util.HashMap<>();

    CapsuleGenerationContext(ServerLevel target, ServerLevel source, CapsuleInstances.Instance room) {
        this(target, source, room, false);
    }

    CapsuleGenerationContext(ServerLevel target, ServerLevel source, CapsuleInstances.Instance room, boolean sourceTerrain) {
        this(target, source, room, sourceTerrain, null);
    }

    CapsuleGenerationContext(ServerLevel target, ServerLevel source, CapsuleInstances.Instance room,
                             boolean sourceTerrain, net.minecraft.world.level.ChunkPos callbackChunk) {
        previous = CURRENT.get();
        if (previous == null) LAST_FAILURE.remove();
        this.target = target; this.source = source;
        this.sourceTerrain = sourceTerrain;
        nativeMeteoriteGravity = "ae2:meteorite".equals(room.structure)
                && CapsuleDimensions.isCapsule(target.dimension().location());
        // This runs before CURRENT is changed, so a rejected callback cannot leak a context.
        if (nativeMeteoriteGravity && callbackChunk != null) CapsuleGravitySupport.preflightCallback(target, callbackChunk);
        dx = room.sourceOffsetX; dy = room.sourceOffsetY; dz = room.sourceOffsetZ;
        CURRENT.set(this);
    }

    public static Integer sourceHeight(Level level, Heightmap.Types type, int x, int z) {
        var context = CURRENT.get();
        if (context == null || !context.sourceTerrain || context.target != level) return null;
        String key = type.name() + ":" + x + ":" + z;
        return context.heights.computeIfAbsent(key, ignored -> context.source.getChunkSource().getGenerator()
                .getBaseHeight(x + context.dx, z + context.dz, type, context.source,
                        context.source.getChunkSource().randomState()) - context.dy);
    }

    public static boolean activeFor(Object level) {
        var context = CURRENT.get();
        return context != null && context.target == level;
    }

    /** Both native Piece.postProcess and Structure.afterPlace use this same context. */
    public static boolean capturesGravityFor(ServerLevel level) {
        var context = CURRENT.get();
        return context != null && context.target == level && context.nativeMeteoriteGravity;
    }

    /** Any enclosing capsule callback may already have world side effects. */
    public static boolean hasActiveCallback() {
        return CURRENT.get() != null;
    }

    public static void recordGravityFailure(CapsuleGravitySupport.IndexBlocked failure) {
        // Failure safety spans every nested context; capture permissions stay local.
        for (var context = CURRENT.get(); context != null; context = context.previous)
            if (context.gravityFailure == null) context.gravityFailure = failure;
    }

    public static CapsuleGravitySupport.IndexBlocked takeGravityFailure() {
        var failure = LAST_FAILURE.get();
        LAST_FAILURE.remove();
        return failure;
    }

    @Override public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        if (gravityFailure != null) {
            // A neighbor may wrap an exception; a mod may even swallow it. Do
            // not checkpoint/replay a callback whose earlier writes are unknown.
            recordGravityFailure(gravityFailure);
            LAST_FAILURE.set(gravityFailure);
            throw gravityFailure.copyForClose();
        }
    }
}
