package com.sorrowmist.useless.stretcher.content.capsule;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/** SOURCE ONLY: state follows the loaded chunk, never a global room/pin map. */
@EventBusSubscriber(modid = "useless_stretcher")
public final class CapsuleGravitySupport {
    private CapsuleGravitySupport() {}

    /** A was private and never deployed. Refuse its worlds instead of losing provenance. */
    @SubscribeEvent public static void beforeStart(ServerAboutToStartEvent event) {
        var legacy = event.getServer().getWorldPath(LevelResource.ROOT).resolve("data")
                .resolve("useless_stretcher_capsule_gravity.dat");
        if (!Files.notExists(legacy, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalStateException("Private capsule gravity revision A data detected: " + legacy
                    + "; revision B requires a verified migration before this world may tick. Original file retained.");
    }

    public static boolean eligible(ServerLevel level) {
        return level != null && CapsuleDimensions.isCapsule(level.dimension().location())
                && level.getServer().isSameThread();
    }

    /** NativeCapsules calls this for its already prepared current callback chunk. */
    public static void preflightCallback(ServerLevel level, ChunkPos pos) {
        if (!eligible(level)) throw new IllegalStateException("Native gravity callback must run on its capsule server thread");
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk == null) throw blocked(level, pos, true, "prepared_chunk_unavailable");
        var data = chunk.getExistingDataOrNull(CapsuleChunkGravityAttachments.TYPE);
        if (data != null && data.isBlocked()) throw blocked(level, pos, true, data.blockedReason());
    }

    /** A late third-party write may leave earlier callback effects, so it gets a distinct permanent pause. */
    public static void beforeNativePlacement(ServerLevel level, BlockPos pos, BlockState requested) {
        if (!eligible(level) || !CapsuleGenerationContext.capturesGravityFor(level) || code(requested) == 0) return;
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) throw blocked(level, new ChunkPos(pos), false, "late_chunk_unavailable");
        var data = chunk.getExistingDataOrNull(CapsuleChunkGravityAttachments.TYPE);
        if (data != null && data.isBlocked())
            throw blocked(level, chunk.getPos(), false, data.blockedReason());
    }

    public static void afterSuccessfulPlacement(ServerLevel level, BlockPos pos, BlockState actual) {
        if (!eligible(level)) return;
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) {
            if (CapsuleGenerationContext.capturesGravityFor(level) && code(actual) != 0)
                throw blocked(level, new ChunkPos(pos), false, "chunk_unavailable_after_write");
            return;
        }
        int value = code(actual);
        if (CapsuleGenerationContext.capturesGravityFor(level) && value != 0) {
            var data = chunk.getData(CapsuleChunkGravityAttachments.TYPE);
            if (data.isBlocked()) throw blocked(level, chunk.getPos(), false, data.blockedReason());
            if (data.set(pos.getY() >> 4, local(pos), value)) chunk.setUnsaved(true);
        } else {
            var data = chunk.getExistingDataOrNull(CapsuleChunkGravityAttachments.TYPE);
            if (data != null && !data.isBlocked() && data.clear(pos.getY() >> 4, local(pos))) chunk.setUnsaved(true);
        }
    }

    public static boolean shouldKeepStatic(ServerLevel level, BlockPos pos, BlockState state) {
        if (!eligible(level)) return false;
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return false;
        var data = chunk.getExistingDataOrNull(CapsuleChunkGravityAttachments.TYPE);
        if (data == null) return false;
        // Damaged provenance cannot identify exact cells. Temporarily quarantine
        // gravity for these three materials in this chunk, including player
        // placements, rather than releasing a possible meteorite entity storm.
        // This creates no pins and does not count as repairing the attachment.
        if (data.isBlocked()) return code(state) != 0;
        int expected = data.get(pos.getY() >> 4, local(pos));
        if (expected == 0) return false;
        if (expected != code(state)) {
            if (data.clear(pos.getY() >> 4, local(pos))) chunk.setUnsaved(true);
            return false;
        }
        return true;
    }

    private static int local(BlockPos pos) {
        return ((pos.getY() & 15) << 8) | ((pos.getZ() & 15) << 4) | (pos.getX() & 15);
    }
    private static int code(BlockState state) {
        // These three vanilla blocks have no state properties. Do not silently
        // collapse a third party's augmented states to their material name.
        if (state.equals(Blocks.GRAVEL.defaultBlockState())) return CapsuleChunkGravityData.GRAVEL;
        if (state.equals(Blocks.SAND.defaultBlockState())) return CapsuleChunkGravityData.SAND;
        if (state.equals(Blocks.RED_SAND.defaultBlockState())) return CapsuleChunkGravityData.RED_SAND;
        return CapsuleChunkGravityData.NONE;
    }

    public static boolean isPausedError(String error) {
        return "gravity_index_preflight_blocked".equals(error) || "gravity_index_partial_unknown".equals(error);
    }
    private static IndexBlocked blocked(ServerLevel level, ChunkPos pos, boolean beforeCallback, String reason) {
        boolean actuallyBefore = beforeCallback && !CapsuleGenerationContext.hasActiveCallback();
        IndexBlocked failure = new IndexBlocked(level, pos, actuallyBefore, reason);
        if (!actuallyBefore) CapsuleGenerationContext.recordGravityFailure(failure);
        return failure;
    }
    public static final class IndexBlocked extends IllegalStateException {
        public final boolean beforeCallback;
        public final String dimension;
        public final ChunkPos chunk;
        public final String reason;
        public final net.minecraft.nbt.CompoundTag attachment;
        public IndexBlocked(ServerLevel level, ChunkPos pos, boolean beforeCallback, String reason) {
            super("Capsule gravity index blocked: dimension=" + level.dimension().location() + " chunk=" + pos
                    + " before_callback=" + beforeCallback + " reason=" + reason);
            this.beforeCallback = beforeCallback;
            this.dimension = level.dimension().location().toString();
            this.chunk = pos; this.reason = reason;
            this.attachment = describeAttachment(level, pos);
        }
        private IndexBlocked(String message, boolean beforeCallback, String dimension, ChunkPos chunk, String reason,
                             net.minecraft.nbt.CompoundTag attachment) {
            super(message);
            this.beforeCallback = beforeCallback;
            this.dimension = dimension; this.chunk = chunk; this.reason = reason;
            this.attachment = attachment.copy();
        }
        public IndexBlocked copyForClose() {
            // Throwing the original object again from TWR.close would cause
            // Throwable.addSuppressed to reject self-suppression.
            return new IndexBlocked(getMessage(), beforeCallback, dimension, chunk, reason, attachment);
        }
    }
    private static net.minecraft.nbt.CompoundTag describeAttachment(ServerLevel level, ChunkPos pos) {
        var result = new net.minecraft.nbt.CompoundTag();
        var chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk == null) { result.putString("status", "unavailable"); return result; }
        var data = chunk.getExistingDataOrNull(CapsuleChunkGravityAttachments.TYPE);
        if (data == null) { result.putString("status", "absent"); return result; }
        result.putString("status", data.isBlocked() ? "blocked" : "valid");
        result.putString("blocked_reason", data.blockedReason());
        try {
            var tag = data.saveTag();
            result.putByte("tag_type", tag.getId());
            result.putString("hash", CapsuleGravityFingerprint.hash(tag));
        } catch (RuntimeException cannotHash) {
            result.putString("hash_error", cannotHash.getClass().getName());
        }
        return result;
    }
}
