package com.sorrowmist.useless.stretcher.content.capsule;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/** Source terrain queries during native callbacks; physical capsule chunks stay empty. */
public final class CapsuleGenerationContext implements AutoCloseable {
    private static final ThreadLocal<CapsuleGenerationContext> CURRENT = new ThreadLocal<>();
    private final CapsuleGenerationContext previous;
    private final ServerLevel target, source;
    private final int dx, dy, dz;
    private final boolean sourceTerrain;
    private final java.util.Map<String, Integer> heights = new java.util.HashMap<>();

    CapsuleGenerationContext(ServerLevel target, ServerLevel source, CapsuleInstances.Instance room) {
        this(target, source, room, false);
    }

    CapsuleGenerationContext(ServerLevel target, ServerLevel source, CapsuleInstances.Instance room, boolean sourceTerrain) {
        previous = CURRENT.get();
        this.target = target; this.source = source;
        this.sourceTerrain = sourceTerrain;
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

    @Override public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
