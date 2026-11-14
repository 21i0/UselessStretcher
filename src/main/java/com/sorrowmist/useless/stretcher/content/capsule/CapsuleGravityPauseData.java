package com.sorrowmist.useless.stretcher.content.capsule;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Room-local, synchronously persisted pause records. The server facade contains
 * paths, never a server/provider/world reference, and is not installed in global
 * DimensionDataStorage. The original in-memory format-1 API remains available
 * for its data regression checks; production uses get(server) and per-key IO.
 */
public final class CapsuleGravityPauseData extends SavedData {
    public static final String NAME = "useless_stretcher_capsule_gravity_pauses";
    public static final int FORMAT = 1;
    private static final int SHARD_FORMAT = 2;
    public static final long MAX_SHARD_FILE_BYTES = 2L * 1024L * 1024L;
    public static final long MAX_SHARD_NBT_BYTES = 16L * 1024L * 1024L;
    public static final int MAX_MEMORY_RECORDS = 256;
    public static final int MAX_ARCHIVES_PER_ROOM = 32;
    public static final long MAX_ARCHIVE_BYTES_PER_ROOM = 32L * 1024L * 1024L;
    private static final long MIN_FREE_BYTES = 16L * 1024L * 1024L;
    private static final long MAX_LEGACY_FILE_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_LEGACY_NBT_BYTES = 256L * 1024L * 1024L;
    private static final Set<String> ROOT_KEYS = Set.of("format", "records");
    private static final Set<String> SHARD_ENVELOPE_KEYS = Set.of("data");
    private static final Set<String> SHARD_KEYS = Set.of("format", "key", "record", "record_hash");
    private static final Set<String> MUTABLE_RECORD_KEYS = Set.of("status", "category", "cleanup");
    private static final Set<String> CLEANUP_KEYS = Set.of("complete", "dimension", "tickets", "planner");
    private static final Set<String> TICKET_KEYS = Set.of("type", "x", "z", "radius", "released", "error");
    private static final Set<String> STATUSES = Set.of("paused", "claimed", "aborted", "resumed");
    private static final Map<MinecraftServer, CapsuleGravityPauseData> CACHE = new WeakHashMap<>();

    private final Path directory;
    private final Map<String, CompoundTag> records = new TreeMap<>();
    private String blockedReason = "";
    private CompoundTag opaquePayload;
    private CompoundTag opaqueEnvelope;
    private LegacyStamp legacyStamp;
    private WeakReference<CompoundTag> legacyRoot = new WeakReference<>(null);
    private String legacyReason = "";
    private boolean legacyAbsent;

    /** Original in-memory data model; never used by the production server facade. */
    public CapsuleGravityPauseData() { directory = null; }

    private CapsuleGravityPauseData(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
    }

    public static synchronized CapsuleGravityPauseData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return CACHE.computeIfAbsent(server, ignored -> new CapsuleGravityPauseData(
                server.getWorldPath(LevelResource.ROOT).resolve("data")));
    }

    /** Real filesystem adapter for independent storage tests; creates nothing. */
    static CapsuleGravityPauseData openDirectory(Path directory) {
        return new CapsuleGravityPauseData(directory);
    }

    public synchronized boolean isReadOnly() {
        return directory == null ? !blockedReason.isEmpty() : !legacy().reason.isEmpty();
    }
    public synchronized String blockedReason() {
        return directory == null ? blockedReason : legacy().reason;
    }
    public synchronized boolean hasUnresolvedLegacy() {
        return directory != null && !legacy().reason.isEmpty();
    }
    public synchronized boolean hasShard(String key) {
        Objects.requireNonNull(key, "key");
        return directory != null && !Files.notExists(shardPath(key), LinkOption.NOFOLLOW_LINKS);
    }
    /** Only this case may be considered for an already-ready read-only entry. */
    public synchronized boolean blockedOnlyByLegacy(String key) {
        if (directory == null || !Files.notExists(shardPath(key), LinkOption.NOFOLLOW_LINKS)
                || !safeDirectoryForRead()) return false;
        return !legacy().reason.isEmpty();
    }
    public synchronized boolean isReadOnly(String key) { return !blockedReason(key).isEmpty(); }
    public synchronized String blockedReason(String key) {
        Objects.requireNonNull(key, "key");
        if (directory == null) return blockedReason;
        RoomRead room = readRoom(key);
        return room.reason;
    }

    /**
     * Pure preflight for starting world work. Read-only ready-room entry must use
     * blockedReason(key), not this write-capacity decision. This does not create
     * directories, lock files, archives or a placeholder pause record.
     */
    public synchronized String mutationBlockedReason(String key) {
        Objects.requireNonNull(key, "key");
        if (directory == null) return blockedReason;
        RoomRead room = readRoom(key);
        if (!room.reason.isEmpty()) return room.reason;
        try {
            writablePath(directory, true);
            writablePath(shardPath(key), false);
            writablePath(directory.resolve(baseName(key) + ".lock"), false);
            if (room.record != null && "resumed".equals(room.record.getString("status"))) {
                byte[] encoded = encodeChecked(envelope(key, room.record));
                ArchivePlan plan = archivePlan(key, room.record, encoded.length);
                writablePath(plan.target.getParent(), true);
                if (!plan.exists) writablePath(plan.target, false);
            }
            requireFreeSpace(MAX_SHARD_FILE_BYTES * 2L);
            return "";
        } catch (IOException | RuntimeException refusal) {
            return "room_mutation_capacity_or_permissions";
        }
    }
    public synchronized boolean mayStartNewWork(String key) { return mutationBlockedReason(key).isEmpty(); }

    public synchronized CompoundTag find(String key) {
        Objects.requireNonNull(key, "key");
        if (directory != null) {
            CompoundTag found = readRoom(key).record;
            return found == null ? null : found.copy();
        }
        CompoundTag record = records.get(key);
        return record == null ? null : record.copy();
    }

    /**
     * A persistent put returns only after file force, atomic replacement and an
     * exact read-back check. Claimed therefore cannot be returned merely dirty.
     * An old resumed record must first have an immutable, verified hash archive.
     */
    public synchronized void put(String key, CompoundTag record) {
        Objects.requireNonNull(key, "key"); Objects.requireNonNull(record, "record");
        if (directory == null) {
            if (isReadOnly()) throw new IllegalStateException("Read-only capsule gravity pause journal: " + blockedReason);
            if (record.equals(records.get(key))) return;
            if (!records.containsKey(key) && records.size() >= MAX_MEMORY_RECORDS)
                throw new IllegalStateException("In-memory pause record count budget exceeded");
            CompoundTag candidate = memoryPayload();
            candidate.getCompound("records").put(key, record);
            encodeChecked(candidate);
            CompoundTag copied = record.copy();
            records.put(key, copied); setDirty(); return;
        }
        if (!knownRecord(key, record)) throw new IllegalStateException("Unknown or malformed new room record");
        // Validate both binary size and the exact read-side NBT budget before IO.
        CompoundTag nextRoot = envelope(key, record);
        byte[] nextBytes = encodeChecked(nextRoot);
        try {
            RoomRead before = readRoom(key);
            if (!before.reason.isEmpty()) throw new IOException("Room journal is read-only: " + before.reason);
            ensureDirectory(directory);
            Path lockPath = directory.resolve(baseName(key) + ".lock");
            if (Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Non-regular room lock");
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 FileLock lock = channel.tryLock()) {
                if (lock == null) throw new IOException("Room journal is locked");
                RoomRead old = readRoom(key);
                if (!old.reason.isEmpty()) throw new IOException("Room journal is read-only: " + old.reason);
                if (old.record != null && old.record.equals(record)) {
                    // A legacy-only equality is still migrated to its own shard.
                    if (old.shard) return;
                } else if (old.record != null) {
                    if ("resumed".equals(old.record.getString("status"))) archive(key, old.record);
                    else requireSameEvidenceTransition(old.record, record);
                }
                requireFreeSpace(nextBytes.length);
                atomicWrite(shardPath(key), nextBytes, true);
                RoomRead verified = readShard(key);
                if (!verified.reason.isEmpty() || verified.record == null || !record.equals(verified.record))
                    throw new IOException("Room journal read-back did not confirm the accepted record");
            }
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot durably update capsule gravity pause room " + key, failure);
        }
    }

    private static void requireSameEvidenceTransition(CompoundTag old, CompoundTag next) {
        if (!old.getString("pause").equals(next.getString("pause")))
            throw new IllegalStateException("Cannot overwrite unfinished or failed pause evidence");
        String before = old.getString("status"), after = next.getString("status");
        boolean allowed = before.equals(after)
                || before.equals("paused") && after.equals("claimed")
                || before.equals("claimed") && (after.equals("resumed") || after.equals("aborted"));
        if (!allowed) throw new IllegalStateException("Invalid durable pause transition " + before + " -> " + after);
        for (String field : old.getAllKeys()) {
            if (!next.contains(field) || !MUTABLE_RECORD_KEYS.contains(field)
                    && !sameTag(old.get(field), next.get(field)))
                throw new IllegalStateException("Cannot remove or replace existing pause evidence field: " + field);
        }
        preserveOpaqueCleanup(old.get("cleanup"), next.get("cleanup"));
    }

    /** Known cleanup/status changes remain possible; unknown extension data does not disappear with them. */
    private static void preserveOpaqueCleanup(Tag old, Tag next) {
        if (sameTag(old, next) || old == null) return;
        if (!(old instanceof CompoundTag oldCleanup) || !(next instanceof CompoundTag nextCleanup))
            throw new IllegalStateException("Cannot replace opaque cleanup evidence");
        preserveUnknownFields(oldCleanup, nextCleanup, CLEANUP_KEYS);
        Tag oldTickets = oldCleanup.get("tickets"), nextTickets = nextCleanup.get("tickets");
        if (sameTag(oldTickets, nextTickets) || oldTickets == null) return;
        if (!(oldTickets instanceof net.minecraft.nbt.ListTag before)
                || !(nextTickets instanceof net.minecraft.nbt.ListTag after))
            throw new IllegalStateException("Cannot replace opaque cleanup ticket evidence");
        for (Tag entry : before) {
            if (!(entry instanceof CompoundTag ticket))
                throw new IllegalStateException("Cannot replace unknown cleanup ticket type");
            if (TICKET_KEYS.containsAll(ticket.getAllKeys())) continue;
            CompoundTag match = null;
            for (Tag candidate : after) {
                if (!(candidate instanceof CompoundTag other)) continue;
                boolean identity = true;
                for (String field : new String[]{"type", "x", "z", "radius"})
                    identity &= sameTag(ticket.get(field), other.get(field));
                if (identity) { match = other; break; }
            }
            if (match == null) throw new IllegalStateException("Cannot discard an extended cleanup ticket");
            preserveUnknownFields(ticket, match, TICKET_KEYS);
        }
    }
    private static void preserveUnknownFields(CompoundTag old, CompoundTag next, Set<String> known) {
        for (String field : old.getAllKeys())
            if (!known.contains(field) && !sameTag(old.get(field), next.get(field)))
                throw new IllegalStateException("Cannot discard opaque cleanup extension: " + field);
    }
    private static boolean sameTag(Tag left, Tag right) {
        return left == null ? right == null : right != null
                && CapsuleGravityFingerprint.hash(left).equals(CapsuleGravityFingerprint.hash(right));
    }

    public synchronized CompoundTag inspectPayload() {
        return opaquePayload == null ? null : opaquePayload.copy();
    }
    public synchronized CompoundTag inspectEnvelope() {
        return opaqueEnvelope == null ? null : opaqueEnvelope.copy();
    }
    /** Bounded original room payload, even if its record format cannot execute. */
    public synchronized CompoundTag inspectPayload(String key) {
        if (directory == null) return inspectPayload();
        RoomRead room = readRoom(key);
        return room.payload == null ? null : room.payload.copy();
    }
    public synchronized CompoundTag inspectEnvelope(String key) {
        if (directory == null) return inspectEnvelope();
        RoomRead room = readRoom(key);
        return room.envelope == null ? null : room.envelope.copy();
    }

    @Override public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (directory != null)
            throw new IllegalStateException("Room journals are atomically persisted individually; global save is forbidden");
        if (isReadOnly()) throw new IllegalStateException("Refusing to rewrite capsule gravity pause journal: " + blockedReason);
        CompoundTag candidate = tag.copy();
        CompoundTag payload = memoryPayload();
        candidate.putInt("format", FORMAT); candidate.put("records", payload.get("records"));
        encodeChecked(candidate);
        // Only after the full candidate passed can the caller's destination change.
        tag.putInt("format", FORMAT); tag.put("records", payload.get("records")); return tag;
    }
    private CompoundTag memoryPayload() {
        CompoundTag payload = new CompoundTag(); payload.putInt("format", FORMAT);
        CompoundTag saved = new CompoundTag();
        records.forEach((key, record) -> saved.put(key, record.copy()));
        payload.put("records", saved); return payload;
    }
    public static CapsuleGravityPauseData load(CompoundTag tag, HolderLookup.Provider provider) { return load(tag); }
    /** Original in-memory API is deliberately unchanged for the original checks. */
    public static CapsuleGravityPauseData load(CompoundTag tag) {
        if (tag == null || !tag.getAllKeys().equals(ROOT_KEYS)) return blocked("invalid_payload_shape", tag, null);
        if (!tag.contains("format", Tag.TAG_INT) || tag.getInt("format") != FORMAT)
            return blocked("unsupported_format", tag, null);
        if (!(tag.get("records") instanceof CompoundTag savedRecords)) return blocked("invalid_record_map", tag, null);
        if (savedRecords.size() > MAX_MEMORY_RECORDS) return blocked("memory_record_count_budget", tag, null);
        try { encodeChecked(tag); }
        catch (RuntimeException refused) { return blocked("memory_nbt_budget_or_roundtrip", tag, null); }
        CapsuleGravityPauseData data = new CapsuleGravityPauseData();
        for (String key : savedRecords.getAllKeys()) {
            if (!(savedRecords.get(key) instanceof CompoundTag record)) return blocked("invalid_record_type", tag, null);
            data.records.put(key, record.copy());
        }
        return data;
    }
    static CapsuleGravityPauseData loadStoredRoot(CompoundTag root, HolderLookup.Provider provider) {
        if (root == null || !(root.get("data") instanceof CompoundTag payload))
            return blocked("invalid_saved_data_envelope", null, root);
        CapsuleGravityPauseData data = load(payload, provider);
        if (data.isReadOnly()) data.opaqueEnvelope = root.copy();
        return data;
    }
    static CapsuleGravityPauseData readStoredFile(Path file, HolderLookup.Provider provider) {
        try { return loadStoredRoot(readNbt(file, MAX_LEGACY_FILE_BYTES, MAX_LEGACY_NBT_BYTES), provider); }
        catch (IOException | RuntimeException failure) { return blocked("unreadable_or_overbudget_file", null, null); }
    }
    private static CapsuleGravityPauseData blocked(String reason, CompoundTag payload, CompoundTag envelope) {
        CapsuleGravityPauseData data = new CapsuleGravityPauseData();
        data.blockedReason = reason;
        data.opaquePayload = payload == null ? null : payload.copy();
        data.opaqueEnvelope = envelope == null ? null : envelope.copy();
        return data;
    }

    private RoomRead readRoom(String key) {
        Path path = shardPath(key);
        if (!Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) return readShard(key);
        Legacy legacy = legacy();
        if (!legacy.reason.isEmpty()) return new RoomRead(null, legacy.reason, false,
                legacy.root != null && legacy.root.get("data") instanceof CompoundTag p ? p : null, legacy.root);
        if (legacy.root == null) return new RoomRead(null, "", false, null, null);
        CompoundTag payload = legacy.root.getCompound("data");
        Tag selected = payload.getCompound("records").get(key);
        if (selected == null) return new RoomRead(null, "", false, null, null);
        // Validate only the selected legacy record. A different malformed record
        // does not invalidate this room or manufacture a partial executable map.
        if (!(selected instanceof CompoundTag record))
            return new RoomRead(null, "legacy_record_type", false, payload, legacy.root);
        return new RoomRead(record, knownRecord(key, record) ? "" : "legacy_record_unknown_or_malformed",
                false, payload, legacy.root);
    }
    private RoomRead readShard(String key) {
        CompoundTag root = null, payload = null;
        try {
            if (!safeDirectoryForRead()) throw new IOException("Unsafe data directory");
            root = readNbt(shardPath(key), MAX_SHARD_FILE_BYTES, MAX_SHARD_NBT_BYTES);
            // Writer emits only data. Accepting extra outer fields and then
            // reconstructing an envelope would silently erase unknown evidence.
            if (!root.getAllKeys().equals(SHARD_ENVELOPE_KEYS))
                return new RoomRead(null, "shard_unknown_envelope_fields", true,
                        root.get("data") instanceof CompoundTag p ? p : null, root);
            if (!(root.get("data") instanceof CompoundTag data))
                return new RoomRead(null, "shard_envelope", true, null, root);
            payload = data;
            if (!payload.getAllKeys().equals(SHARD_KEYS) || !payload.contains("format", Tag.TAG_INT)
                    || payload.getInt("format") != SHARD_FORMAT || !payload.contains("key", Tag.TAG_STRING)
                    || !key.equals(payload.getString("key")) || !(payload.get("record") instanceof CompoundTag record)
                    || !payload.contains("record_hash", Tag.TAG_STRING))
                return new RoomRead(payload.get("record") instanceof CompoundTag r ? r : null,
                        "shard_shape_version_or_key", true, payload, root);
            if (!payload.getString("record_hash").equals(CapsuleGravityFingerprint.hash(record)))
                return new RoomRead(record, "shard_record_hash", true, payload, root);
            return new RoomRead(record, knownRecord(key, record) ? "" : "shard_record_unknown_or_malformed", true, payload, root);
        } catch (IOException | RuntimeException failure) {
            return new RoomRead(null, "shard_unreadable_or_overbudget", true, payload, root);
        }
    }
    private static boolean knownRecord(String key, CompoundTag record) {
        if (!record.contains("format", Tag.TAG_INT) || record.getInt("format") != FORMAT
                || !record.contains("key", Tag.TAG_STRING) || !key.equals(record.getString("key"))
                || !record.contains("status", Tag.TAG_STRING) || !STATUSES.contains(record.getString("status"))
                || !record.contains("pause", Tag.TAG_STRING)) return false;
        try { return UUID.fromString(record.getString("pause")).toString().equals(record.getString("pause")); }
        catch (IllegalArgumentException invalid) { return false; }
    }
    private record RoomRead(CompoundTag record, String reason, boolean shard, CompoundTag payload, CompoundTag envelope) {}
    private record Legacy(CompoundTag root, String reason) {}
    private record LegacyStamp(long size, long modifiedMillis, String fileKey) {}

    /** The old bounded root is only weakly cached and is never registered/rewritten. */
    private Legacy legacy() {
        if (directory == null) return new Legacy(null, blockedReason);
        Path path = directory.resolve(NAME + ".dat");
        try {
            if (!safeDirectoryForRead()) return new Legacy(null, "legacy_storage_directory_unavailable");
            if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
                legacyStamp = null; legacyRoot.clear(); legacyReason = ""; legacyAbsent = true;
                return new Legacy(null, "");
            }
            var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.size() <= 0 || attributes.size() > MAX_LEGACY_FILE_BYTES)
                return new Legacy(null, "legacy_unreadable_or_overbudget");
            var stamp = new LegacyStamp(attributes.size(), attributes.lastModifiedTime().toMillis(), String.valueOf(attributes.fileKey()));
            CompoundTag cached = legacyRoot.get();
            if (!legacyAbsent && stamp.equals(legacyStamp) && (!legacyReason.isEmpty() || cached != null))
                return new Legacy(cached, legacyReason);
            CompoundTag root = readNbt(path, MAX_LEGACY_FILE_BYTES, MAX_LEGACY_NBT_BYTES);
            String reason = "";
            if (!(root.get("data") instanceof CompoundTag payload) || !payload.getAllKeys().equals(ROOT_KEYS)
                    || !payload.contains("format", Tag.TAG_INT) || payload.getInt("format") != FORMAT
                    || !(payload.get("records") instanceof CompoundTag)) reason = "legacy_root_unknown_or_malformed";
            legacyStamp = stamp; legacyRoot = new WeakReference<>(root); legacyReason = reason; legacyAbsent = false;
            return new Legacy(root, reason);
        } catch (IOException | RuntimeException failure) {
            return new Legacy(null, "legacy_unreadable_or_overbudget");
        }
    }

    private void archive(String key, CompoundTag oldRecord) throws IOException {
        byte[] bytes = encodeChecked(envelope(key, oldRecord));
        ArchivePlan plan = archivePlan(key, oldRecord, bytes.length);
        if (plan.exists) return;
        Path roomDirectory = plan.target.getParent();
        ensureDirectory(roomDirectory.getParent()); ensureDirectory(roomDirectory);
        requireFreeSpace(bytes.length);
        atomicWrite(plan.target, bytes, false);
        if (!readNbt(plan.target, MAX_SHARD_FILE_BYTES, MAX_SHARD_NBT_BYTES).equals(envelope(key, oldRecord)))
            throw new IOException("Immutable room archive read-back failed");
    }
    private record ArchivePlan(Path target, boolean exists) {}
    /** Both mutation preflight and the locked writer use this same room quota. */
    private ArchivePlan archivePlan(String key, CompoundTag oldRecord, long bytes) throws IOException {
        Path archiveRoot = directory.resolve(NAME + "_archives");
        Path roomDirectory = archiveRoot.resolve(keyHash(key));
        if (!Files.notExists(archiveRoot, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(archiveRoot, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Non-directory archive root");
        if (!Files.notExists(roomDirectory, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(roomDirectory, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Non-directory room archive");
        Path target = roomDirectory.resolve(CapsuleGravityFingerprint.hash(oldRecord) + ".dat");
        long count = 0, total = 0;
        if (!Files.notExists(roomDirectory, LinkOption.NOFOLLOW_LINKS)) {
            try (var stream = Files.newDirectoryStream(roomDirectory)) {
                for (Path entry : stream) {
                    var attributes = Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (!attributes.isRegularFile()) throw new IOException("Unknown non-regular room archive entry");
                    count++; total = Math.addExact(total, attributes.size());
                    if (count > MAX_ARCHIVES_PER_ROOM || total > MAX_ARCHIVE_BYTES_PER_ROOM)
                        throw new IOException("Room archive quota already exceeded; original entries retained");
                }
            }
        }
        if (!Files.notExists(target, LinkOption.NOFOLLOW_LINKS)) {
            CompoundTag existing = readNbt(target, MAX_SHARD_FILE_BYTES, MAX_SHARD_NBT_BYTES);
            if (!existing.equals(envelope(key, oldRecord))) throw new IOException("Immutable room archive collision");
            return new ArchivePlan(target, true);
        }
        if (count >= MAX_ARCHIVES_PER_ROOM || total > MAX_ARCHIVE_BYTES_PER_ROOM - bytes)
            throw new IOException("Room archive quota reached; existing terminal record retained");
        return new ArchivePlan(target, false);
    }
    private void requireFreeSpace(long bytes) throws IOException {
        Path existing = nearestExisting(directory);
        if (Files.getFileStore(existing).getUsableSpace() < Math.addExact(MIN_FREE_BYTES, bytes * 3L))
            throw new IOException("Insufficient free space for durable room update");
    }
    private static void writablePath(Path target, boolean directoryExpected) throws IOException {
        if (!Files.notExists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(target) || !Files.isWritable(target)
                    || directoryExpected && !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)
                    || !directoryExpected && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Journal path is not writable or has unknown type");
        } else {
            Path existing = nearestExisting(target.getParent());
            if (Files.isSymbolicLink(existing) || !Files.isDirectory(existing, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isWritable(existing)) throw new IOException("Journal parent is not writable");
        }
    }
    private static Path nearestExisting(Path path) throws IOException {
        Path current = path;
        while (current != null && Files.notExists(current, LinkOption.NOFOLLOW_LINKS)) current = current.getParent();
        if (current == null || Files.isSymbolicLink(current)) throw new IOException("Unknown journal filesystem");
        return current;
    }
    private static void atomicWrite(Path target, byte[] bytes, boolean replace) throws IOException {
        Path parent = target.getParent();
        ensureDirectory(parent);
        if (!Files.notExists(target, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Non-regular target");
        if (!replace && !Files.notExists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Immutable target already exists");
        Path temporary = parent.resolve("." + target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) output.write(buffer);
                output.force(true);
            }
            try {
                if (replace) Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                else Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                throw new IOException("Atomic room move is required; no non-atomic fallback", unsupported);
            }
        } finally {
            // Only this operation's exclusively created, never-accepted temporary
            // file is removed. Existing room/legacy/archive evidence is not deleted.
            Files.deleteIfExists(temporary);
        }
    }
    private static void ensureDirectory(Path path) throws IOException {
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(path);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))
            throw new IOException("Journal directory is not a real directory: " + path);
    }
    private boolean safeDirectoryForRead() {
        // A confirmed absent directory is a normal fresh world; inspect does not create it.
        return Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(directory);
    }
    private Path shardPath(String key) { return directory.resolve(baseName(key) + ".dat"); }
    private static String baseName(String key) { return NAME + "_room_" + keyHash(key); }
    static String keyHash(String key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    static Path shardPath(Path directory, String key) { return directory.resolve(baseName(key) + ".dat"); }
    private static CompoundTag envelope(String key, CompoundTag record) {
        CompoundTag payload = new CompoundTag();
        payload.putInt("format", SHARD_FORMAT); payload.putString("key", key);
        payload.put("record", record.copy()); payload.putString("record_hash", CapsuleGravityFingerprint.hash(record));
        CompoundTag root = new CompoundTag(); root.put("data", payload); return root;
    }
    private static byte[] encodeChecked(CompoundTag root) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(new LimitedOutput(bytes, MAX_SHARD_FILE_BYTES))) {
                NbtIo.write(root, output);
            }
            byte[] encoded = bytes.toByteArray();
            CompoundTag decoded = decode(new ByteArrayInputStream(encoded), MAX_SHARD_FILE_BYTES, MAX_SHARD_NBT_BYTES);
            if (!CapsuleGravityFingerprint.hash(root).equals(CapsuleGravityFingerprint.hash(decoded)))
                throw new IOException("NBT round-trip changed room evidence");
            return encoded;
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalStateException("Room evidence exceeds read/write budget or cannot round-trip exactly", invalid);
        }
    }
    private static CompoundTag readNbt(Path path, long fileBudget, long nbtBudget) throws IOException {
        var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.size() <= 0 || attributes.size() > fileBudget)
            throw new IOException("Non-regular, empty or oversized NBT file");
        try (InputStream raw = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
             BufferedInputStream input = new BufferedInputStream(new LimitedInput(raw, fileBudget))) {
            input.mark(2); int first = input.read(), second = input.read(); input.reset();
            if (first == 0x1f && second == 0x8b) {
                long binaryBudget = fileBudget == MAX_SHARD_FILE_BYTES ? MAX_SHARD_FILE_BYTES : nbtBudget;
                try (GZIPInputStream gzip = new GZIPInputStream(input)) { return decode(gzip, binaryBudget, nbtBudget); }
            }
            return decode(input, fileBudget, nbtBudget);
        }
    }
    private static CompoundTag decode(InputStream input, long binaryBudget, long nbtBudget) throws IOException {
        LimitedInput limited = new LimitedInput(input, binaryBudget);
        CompoundTag result = NbtIo.read(new DataInputStream(limited), NbtAccounter.create(nbtBudget));
        if (limited.read() != -1) throw new IOException("Trailing data after NBT root");
        return result;
    }
    private static final class LimitedInput extends FilterInputStream {
        private final long limit; private long count;
        LimitedInput(InputStream input, long limit) { super(input); this.limit = limit; }
        private void counted(long amount) throws IOException {
            count += amount; if (count > limit) throw new IOException("NBT byte budget exceeded");
        }
        @Override public int read() throws IOException { int value = in.read(); if (value >= 0) counted(1); return value; }
        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            int amount = in.read(buffer, offset, (int) Math.min(length, Math.max(1L, limit - count + 1L)));
            if (amount > 0) counted(amount); return amount;
        }
        @Override public long skip(long amount) throws IOException {
            long skipped = in.skip(Math.min(amount, Math.max(0L, limit - count)));
            counted(skipped); return skipped;
        }
    }
    private static final class LimitedOutput extends FilterOutputStream {
        private final long limit; private long count;
        LimitedOutput(OutputStream output, long limit) { super(output); this.limit = limit; }
        private void counted(long amount) throws IOException {
            count += amount; if (count > limit) throw new IOException("NBT write byte budget exceeded");
        }
        @Override public void write(int value) throws IOException { counted(1); out.write(value); }
        @Override public void write(byte[] buffer, int offset, int length) throws IOException { counted(length); out.write(buffer, offset, length); }
    }
}
