package com.sorrowmist.useless.stretcher.content.capsule;

import com.mojang.logging.LogUtils;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/** Same-epoch, write-before-only recovery. Saved records never recreate authority. */
public final class CapsuleGravityRecovery {
    private static final Map<MinecraftServer, Epoch> EPOCHS = new WeakHashMap<>();
    private static final int FORMAT = 1;
    private CapsuleGravityRecovery() {}

    private static final class Epoch {
        final String id = UUID.randomUUID().toString();
        final Map<String, Proof> proofs = new HashMap<>();
    }
    private static final class Proof {
        final String pause;
        final WeakReference<Object> resources;
        final WeakReference<ServerLevel> source, target;
        final Witness witness;
        String recordHash;
        Proof(String pause, ServerLevel source, ServerLevel target, Witness witness) {
            this.pause = pause; this.resources = new WeakReference<>(source.getServer().getResourceManager());
            this.source = new WeakReference<>(source); this.target = new WeakReference<>(target);
            this.witness = witness;
        }
    }
    static final class ResumeGuard {
        final Proof proof;
        final String epoch, roomHash, claimHash;
        ResumeGuard(Proof proof, String epoch, String roomHash, String claimHash) {
            this.proof = proof; this.epoch = epoch; this.roomHash = roomHash; this.claimHash = claimHash;
        }
    }
    /** Weak identity and exact attachment content; never keeps historical chunks loaded. */
    static final class Witness {
        final String dimension;
        final ChunkPos pos;
        final WeakReference<LevelChunk> chunk;
        final WeakReference<CapsuleChunkGravityData> data;
        final boolean present;
        final String digest;
        Witness(ServerLevel level, LevelChunk chunk, CapsuleChunkGravityData data) {
            dimension = level.dimension().location().toString(); pos = chunk.getPos();
            this.chunk = new WeakReference<>(chunk); this.data = new WeakReference<>(data);
            present = data != null;
            digest = data == null ? "absent" : CapsuleGravityFingerprint.hash(data.saveTag());
        }
        boolean matches(ServerLevel level, ChunkPos expected) {
            if (!dimension.equals(level.dimension().location().toString()) || !pos.equals(expected)) return false;
            var current = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (current == null || current != chunk.get()) return false;
            var attachment = current.getExistingDataOrNull(CapsuleChunkGravityAttachments.TYPE);
            if (!present) return attachment == null;
            return attachment != null && attachment == data.get() && !attachment.isBlocked()
                    && digest.equals(CapsuleGravityFingerprint.hash(attachment.saveTag()));
        }
        CompoundTag describe() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", dimension); tag.putInt("x", pos.x); tag.putInt("z", pos.z);
            tag.putBoolean("attachment_present", present); tag.putString("attachment_hash", digest);
            return tag;
        }
    }
    static Witness observe(ServerLevel level, ChunkPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk == null) return null;
        var data = chunk.getExistingDataOrNull(CapsuleChunkGravityAttachments.TYPE);
        return data != null && data.isBlocked() ? null : new Witness(level, chunk, data);
    }
    static void started(MinecraftServer server) { EPOCHS.put(server, new Epoch()); }
    static void stopped(MinecraftServer server) { EPOCHS.remove(server); }
    private static Epoch epoch(MinecraftServer server) { return EPOCHS.computeIfAbsent(server, ignored -> new Epoch()); }
    private static boolean supported(CompoundTag record, String key) {
        return record != null && record.contains("format", Tag.TAG_INT) && record.getInt("format") == FORMAT
                && record.contains("key", Tag.TAG_STRING) && key.equals(record.getString("key"));
    }
    static boolean paused(MinecraftServer server, String key) {
        var data = CapsuleGravityPauseData.get(server);
        return pausedMetadata(key, !data.mutationBlockedReason(key).isEmpty(), data.find(key));
    }
    static boolean pausedMetadata(String key, boolean readOnly, CompoundTag record) {
        return readOnly || record != null && (!supported(record, key) || !"resumed".equals(record.getString("status")));
    }

    /** Entry to a completed room never grants authority to resume or rewrite its plan. */
    static boolean mayEnterReady(MinecraftServer server, CapsuleInstances.Instance room) {
        var data = CapsuleGravityPauseData.get(server);
        return mayEnterReadyMetadata(room.ready, room.generating, room.error, room.key(),
                data.isReadOnly(room.key()), data.blockedOnlyByLegacy(room.key()), data.find(room.key()));
    }

    /** Selecting a ready parent does not overwrite its terminal journal record. */
    static boolean mayUseReadySource(MinecraftServer server, CapsuleInstances.Instance room) {
        var data = CapsuleGravityPauseData.get(server);
        return mayEnterReadyMetadata(room.ready, room.generating, room.error, room.key(),
                data.isReadOnly(room.key()), false, data.find(room.key()));
    }

    static boolean mayEnterReadyMetadata(boolean ready, boolean generating, String error, String key,
                                         boolean readOnly, boolean legacyOnly, CompoundTag record) {
        if (!ready || generating || error == null || !error.isEmpty()) return false;
        // An unreadable legacy aggregate is not proof that an unrelated saved
        // ready room needs regeneration. A bad room-specific shard is different:
        // it may contain a claim, so it never receives this entry-only exception.
        if (readOnly) return legacyOnly && record == null;
        return record == null || supported(record, key) && "resumed".equals(record.getString("status"));
    }

    /** Called after room has been marked paused, before any cleanup attempt. */
    static void begin(MinecraftServer server, CapsuleInstances.Instance room, ServerLevel source, ServerLevel target,
                      CapsuleGravitySupport.IndexBlocked failure, boolean provenBefore, String callback,
                      Witness witness, CompoundTag cleanup) {
        var data = CapsuleGravityPauseData.get(server);
        var old = data.find(room.key());
        if (supported(old, room.key()) && "claimed".equals(old.getString("status"))) {
            // Keep the original evidence and permanently block a failed claimed job.
            old.putString("status", "aborted"); old.putString("category", room.error);
            old.putString("retry_failure", failure.reason);
            old.put("retry_attachment", failure.attachment.copy());
            old.put("cleanup", cleanup.copy()); data.put(room.key(), old);
            return;
        }
        if (old != null && (!supported(old, room.key()) || !"resumed".equals(old.getString("status"))))
            throw new IllegalStateException("Existing pause record must not be overwritten");
        var epoch = epoch(server);
        CompoundTag record = new CompoundTag();
        String pause = UUID.randomUUID().toString();
        record.putInt("format", FORMAT); record.putString("key", room.key());
        record.putString("pause", pause); record.putString("epoch", epoch.id);
        record.putString("status", "paused"); record.putString("category", room.error);
        record.putBoolean("proven_before", provenBefore); record.putString("callback", callback);
        record.putString("reason", failure.reason); record.putString("failure_dimension", failure.dimension);
        record.putInt("failure_x", failure.chunk.x); record.putInt("failure_z", failure.chunk.z);
        record.put("failure_attachment", failure.attachment.copy());
        record.put("cleanup", cleanup.copy());
        if (witness != null) record.put("witness", witness.describe());
        try {
            CompoundTag snapshot = CapsuleGravityFingerprint.room(room);
            record.putString("room_hash", CapsuleGravityFingerprint.hash(snapshot));
            record.putString("plan_hash", CapsuleGravityFingerprint.hash(room.plan));
            snapshot.remove("plan"); record.put("state", snapshot);
            record.putString("environment_hash", CapsuleGravityFingerprint.environment(source, target));
            record.putBoolean("fingerprint_complete", true);
        } catch (RuntimeException cannotFingerprint) {
            record.putBoolean("fingerprint_complete", false);
            record.putString("fingerprint_failure", cannotFingerprint.getClass().getName());
            LogUtils.getLogger().error("Cannot prove capsule pause fingerprint {}; recovery refused", room.key(), cannotFingerprint);
        }
        data.put(room.key(), record);
        Proof proof = new Proof(pause, source, target, witness);
        proof.recordHash = CapsuleGravityFingerprint.hash(record);
        epoch.proofs.put(room.key(), proof);
    }
    static void finishCleanup(MinecraftServer server, String key, CompoundTag cleanup) {
        var data = CapsuleGravityPauseData.get(server);
        var record = data.find(key);
        var proof = epoch(server).proofs.get(key);
        if (supported(record, key) && "aborted".equals(record.getString("status"))) {
            record.put("cleanup", cleanup.copy()); data.put(key, record); return;
        }
        if (record == null || proof == null || !proof.pause.equals(record.getString("pause"))
                || !proof.recordHash.equals(CapsuleGravityFingerprint.hash(record))) return;
        record.put("cleanup", cleanup.copy());
        data.put(key, record);
        proof.recordHash = CapsuleGravityFingerprint.hash(record);
    }

    /** Loads only the small pause journal/room metadata. Never resolves or loads a chunk. */
    public static CompoundTag inspect(MinecraftServer server, String key) {
        CompoundTag result = new CompoundTag();
        result.putString("key", key);
        var data = CapsuleGravityPauseData.get(server);
        result.putBoolean("journal_read_only", data.isReadOnly(key));
        if (data.isReadOnly(key)) result.putString("journal_error", data.blockedReason(key));
        result.putBoolean("legacy_journal_unresolved", data.hasUnresolvedLegacy());
        result.putString("journal_mutation_error", data.mutationBlockedReason(key));
        var record = data.find(key);
        if (record != null) result.put("pause", record);
        else result.putString("pause_status", "missing_fingerprint_or_no_pause");
        var room = CapsuleInstances.get(server).rooms.get(key);
        if (room != null) {
            result.putString("room_error", room.error); result.putString("phase", room.phase);
            result.putInt("chunk", room.chunk); result.putInt("piece", room.piece);
            result.putBoolean("ready", room.ready); result.putBoolean("generating", room.generating);
        }
        var epoch = EPOCHS.get(server);
        var proof = epoch == null ? null : epoch.proofs.get(key);
        result.putBoolean("same_epoch_authority", proof != null && record != null
                && epoch.id.equals(record.getString("epoch")) && proof.pause.equals(record.getString("pause")));
        result.putBoolean("runtime_busy", NativeCapsules.gravityRecoveryBusy(key));
        return result;
    }

    /** No chunk loading, bad-payload repair, cursor reset, clean or native replay shortcut. */
    public static String resumePreflight(MinecraftServer server, String key, String pauseId) {
        if (!server.isSameThread()) return "server_thread_required";
        try { if (!UUID.fromString(pauseId).toString().equals(pauseId)) return "invalid_pause_id"; }
        catch (IllegalArgumentException invalid) { return "invalid_pause_id"; }
        try {
            var data = CapsuleGravityPauseData.get(server);
            if (!data.mutationBlockedReason(key).isEmpty()) return "pause_journal_not_writable";
            var record = data.find(key);
            if (!supported(record, key)) return "missing_or_unsupported_fingerprint";
            if (!pauseId.equals(record.getString("pause"))) return "pause_id_changed";
            if (!"paused".equals(record.getString("status"))) return "already_consumed_or_unknown_status";
            if (!"gravity_index_preflight_blocked".equals(record.getString("category"))
                    || !record.getBoolean("proven_before")) return "partial_or_unproven_callback";
            if (!record.getBoolean("fingerprint_complete")) return "incomplete_fingerprint";
            if (!"prepared_chunk_unavailable".equals(record.getString("reason"))) return "damaged_payload_repair_not_supported";
            var epoch = EPOCHS.get(server);
            var proof = epoch == null ? null : epoch.proofs.get(key);
            if (proof == null || !epoch.id.equals(record.getString("epoch")) || !pauseId.equals(proof.pause))
                return "different_epoch_or_missing_runtime_proof";
            if (!proof.recordHash.equals(CapsuleGravityFingerprint.hash(record))) return "pause_fingerprint_changed";
            if (!record.contains("cleanup", Tag.TAG_COMPOUND) || !record.getCompound("cleanup").getBoolean("complete"))
                return "cleanup_not_confirmed";
            var room = CapsuleInstances.get(server).rooms.get(key);
            if (room == null || room.ready || room.generating || !room.error.equals(record.getString("category")))
                return "room_state_changed";
            if (NativeCapsules.gravityRecoveryBusy(key)) return "job_resume_or_entry_pending";
            if (!record.getString("room_hash").equals(CapsuleGravityFingerprint.roomHash(room))) return "room_fingerprint_changed";
            var sourceId = ResourceLocation.tryParse(room.sourceDimension);
            var targetId = ResourceLocation.tryParse(room.dimension);
            if (sourceId == null || targetId == null) return "invalid_dimension_identity";
            var source = server.getLevel(ResourceKey.create(Registries.DIMENSION, sourceId));
            var target = server.getLevel(ResourceKey.create(Registries.DIMENSION, targetId));
            if (source == null || target == null || source != proof.source.get() || target != proof.target.get()
                    || source.getSeed() != room.sourceSeed) return "source_or_target_changed";
            if (proof.resources.get() != server.getResourceManager()) return "resources_reloaded";
            if (!record.getString("environment_hash").equals(CapsuleGravityFingerprint.environment(source, target)))
                return "environment_fingerprint_changed";
            ChunkPos failedChunk = new ChunkPos(record.getInt("failure_x"), record.getInt("failure_z"));
            if (!record.getString("failure_dimension").equals(room.dimension)
                    || proof.witness == null || !proof.witness.matches(target, failedChunk))
                return "missing_changed_or_unloaded_attachment_witness";
            CapsuleGravitySupport.preflightCallback(target, failedChunk);
            // Construction is read-only and uses a dedicated path, never legacy migration.
            return NativeCapsules.resumeGravityPreflight(server, room, record, proof.recordHash);
        } catch (RuntimeException refused) {
            LogUtils.getLogger().error("Capsule preflight recovery refused for {}", key, refused);
            return "preflight_validation_failed";
        }
    }
    /** Called immediately before enqueue on the same server thread. One token, one job. */
    static ResumeGuard consume(MinecraftServer server, String key, CompoundTag expected, String expectedHash) {
        var data = CapsuleGravityPauseData.get(server);
        var current = data.find(key);
        if (current == null || !expectedHash.equals(CapsuleGravityFingerprint.hash(current))
                || !"paused".equals(current.getString("status")) || !current.equals(expected))
            throw new IllegalStateException("Pause changed during strict job reconstruction");
        var epoch = epoch(server);
        var proof = epoch.proofs.get(key);
        if (proof == null || !proof.pause.equals(current.getString("pause")))
            throw new IllegalStateException("Runtime authority disappeared");
        current.putString("status", "claimed");
        data.put(key, current);
        epoch.proofs.remove(key);
        return new ResumeGuard(proof, epoch.id, current.getString("room_hash"), CapsuleGravityFingerprint.hash(current));
    }
    static void validateClaim(MinecraftServer server, CapsuleInstances.Instance room, ServerLevel target,
                              ChunkPos chunk, ResumeGuard guard) {
        var epoch = EPOCHS.get(server);
        var record = CapsuleGravityPauseData.get(server).find(room.key());
        var source = guard.proof.source.get();
        if (epoch == null || !epoch.id.equals(guard.epoch) || record == null
                || !"claimed".equals(record.getString("status"))
                || !guard.claimHash.equals(CapsuleGravityFingerprint.hash(record))
                || guard.proof.resources.get() != server.getResourceManager()
                || guard.proof.target.get() != target || source == null
                || server.getLevel(target.dimension()) != target || server.getLevel(source.dimension()) != source
                || !guard.roomHash.equals(CapsuleGravityFingerprint.roomHash(room))
                || guard.proof.witness == null || !guard.proof.witness.matches(target, chunk))
            throw new CapsuleGravitySupport.IndexBlocked(target, chunk, true, "resume_guard_changed");
    }
    static void completeClaim(MinecraftServer server, String key, ResumeGuard guard) {
        var data = CapsuleGravityPauseData.get(server);
        var record = data.find(key);
        if (record == null || !"claimed".equals(record.getString("status"))
                || !guard.claimHash.equals(CapsuleGravityFingerprint.hash(record))
                || guard.proof.resources.get() != server.getResourceManager())
            throw new IllegalStateException("Claim changed before callback checkpoint completed");
        record.putString("status", "resumed"); data.put(key, record);
    }
}
