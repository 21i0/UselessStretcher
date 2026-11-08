package com.sorrowmist.useless.stretcher.content.catalog;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Ordered, native identity evidence. The exporter receives immutable strings only. */
public final class CatalogTupleEvidence {
    public static final String FILE_HEADER = "USCTUPLE\t1\tSHA-256\tUTF8-LP32BE";
    private static final byte[] DOMAIN = "USCTUPLE\0".getBytes(StandardCharsets.US_ASCII);
    private static final ThreadPoolExecutor EXPORTER = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), runnable -> {
                Thread worker = new Thread(runnable, "stretcher-catalog-tuple-export");
                worker.setDaemon(true);
                return worker;
            }, new ThreadPoolExecutor.AbortPolicy());

    private CatalogTupleEvidence() {}

    public record Tuple(String recipeId, String fingerprint, String sourceId) {
        public Tuple {
            Objects.requireNonNull(recipeId, "recipeId");
            Objects.requireNonNull(fingerprint, "fingerprint");
            Objects.requireNonNull(sourceId, "sourceId");
        }
    }

    public record SourceSummary(String sourceId, long count, String sha256) {}

    public record Summary(long count, String sha256, List<SourceSummary> sources) {
        public Summary { sources = List.copyOf(sources); }

        /** The full ordered digest and every source's ordered digest, for the normal completion log. */
        public String logLine() {
            StringBuilder text = new StringBuilder("catalog-tuples-v1 count=").append(count)
                    .append(" sha256=").append(sha256).append(" sources=[");
            for (int index = 0; index < sources.size(); index++) {
                SourceSummary source = sources.get(index);
                if (index != 0) text.append(',');
                text.append(encode(source.sourceId())).append(':').append(source.count())
                        .append(':').append(source.sha256());
            }
            return text.append(']').toString();
        }
    }

    public static final class Completed {
        private final Summary summary;
        private final List<Tuple> tuples;

        private Completed(Summary summary, List<Tuple> tuples) {
            this.summary = summary;
            // Ownership transfers from the closed accumulator: no O(n) copy on the game thread.
            this.tuples = tuples == null ? null : Collections.unmodifiableList(tuples);
        }

        public Summary summary() { return summary; }
        public boolean retainsFullTuples() { return tuples != null; }
        public List<Tuple> tuples() {
            if (tuples == null) throw new IllegalStateException("Full tuple retention was not enabled");
            return tuples;
        }
    }

    /** Construct and append on the catalog's owner, once per completed ordered entry. */
    public static final class Accumulator {
        private final Thread owner = Thread.currentThread();
        private final Digest total = new Digest();
        private final LinkedHashMap<String, Digest> sources = new LinkedHashMap<>();
        private final List<Tuple> tuples;
        private boolean finished;

        public Accumulator(boolean retainFullTuples) { tuples = retainFullTuples ? new ArrayList<>() : null; }

        public void append(AlloyFurnaceRecipeCatalog.Entry entry) {
            checkOwner();
            Objects.requireNonNull(entry, "entry");
            append(entry.identity().recipeId().toString(), entry.identity().fingerprint(), entry.sourceId());
        }

        /** Pure-data overload also allows the encoding to be independently tested without game registries. */
        public void append(String recipeId, String fingerprint, String sourceId) {
            checkOwner();
            Objects.requireNonNull(recipeId, "recipeId");
            Objects.requireNonNull(fingerprint, "fingerprint");
            Objects.requireNonNull(sourceId, "sourceId");
            byte[] idBytes = recipeId.getBytes(StandardCharsets.UTF_8);
            byte[] fingerprintBytes = fingerprint.getBytes(StandardCharsets.UTF_8);
            byte[] sourceBytes = sourceId.getBytes(StandardCharsets.UTF_8);
            total.append(idBytes, fingerprintBytes, sourceBytes);
            sources.computeIfAbsent(sourceId, ignored -> new Digest()).append(idBytes, fingerprintBytes, sourceBytes);
            if (tuples != null) tuples.add(new Tuple(recipeId, fingerprint, sourceId));
        }

        public Completed finish() {
            checkOwner();
            finished = true;
            List<SourceSummary> summaries = new ArrayList<>(sources.size());
            sources.forEach((source, digest) -> summaries.add(new SourceSummary(source, digest.count, digest.finish())));
            return new Completed(new Summary(total.count, total.finish(), summaries), tuples);
        }

        private void checkOwner() {
            if (Thread.currentThread() != owner) throw new IllegalStateException("Tuple evidence must stay on its catalog owner");
            if (finished) throw new IllegalStateException("Tuple evidence is already frozen");
        }
    }

    private static final class Digest {
        private final MessageDigest digest;
        private final byte[] number = new byte[8];
        private long count;

        Digest() {
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
            digest.update(DOMAIN);
            number[3] = 1;
            digest.update(number, 0, 4);
        }

        void append(byte[] recipeId, byte[] fingerprint, byte[] sourceId) {
            digest.update((byte) 1);
            field(recipeId); field(fingerprint); field(sourceId);
            count++;
        }

        void field(byte[] bytes) {
            number[0] = (byte) (bytes.length >>> 24); number[1] = (byte) (bytes.length >>> 16);
            number[2] = (byte) (bytes.length >>> 8); number[3] = (byte) bytes.length;
            digest.update(number, 0, 4);
            digest.update(bytes);
        }

        String finish() {
            digest.update((byte) 0);
            for (int index = 0; index < 8; index++) number[index] = (byte) (count >>> ((7 - index) * 8));
            digest.update(number);
            return HexFormat.of().formatHex(digest.digest());
        }
    }

    /**
     * Queue capacity is one in addition to the current writer. No registry, ItemStack, Entry,
     * world or recipe object crosses this boundary. The caller supplies a pure token check,
     * owns publication lifecycle, and must select a distinct filename per generation/session.
     * No destination is replaced. Queue saturation and stale tokens complete exceptionally.
     */
    public static CompletableFuture<Path> exportAsync(Completed completed, Path destination, BooleanSupplier stillCurrent) {
        Objects.requireNonNull(completed, "completed");
        Objects.requireNonNull(stillCurrent, "stillCurrent");
        completed.tuples(); // Fail before queueing if retention was disabled.
        Path target = requireDPath(destination);
        CompletableFuture<Path> result = new CompletableFuture<>();
        try {
            EXPORTER.execute(() -> {
                Path temporary = null;
                try {
                    checkCurrent(stillCurrent, result);
                    Path parent = target.getParent();
                    checkExistingAncestors(parent);
                    Files.createDirectories(parent);
                    requireDPath(parent.toRealPath());
                    if (Files.exists(target)) throw new IOException("Tuple export already exists: " + target);
                    temporary = Files.createTempFile(parent, ".catalog-tuples-", ".part");
                    try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                        line(writer, FILE_HEADER);
                        long index = 0;
                        for (Tuple tuple : completed.tuples()) {
                            if ((index & 1023L) == 0) checkCurrent(stillCurrent, result);
                            line(writer, "T\t" + index++ + '\t' + encode(tuple.recipeId()) + '\t'
                                    + encode(tuple.fingerprint()) + '\t' + encode(tuple.sourceId()));
                        }
                        Summary summary = completed.summary();
                        line(writer, "TOTAL\t" + summary.count() + '\t' + summary.sha256());
                        for (SourceSummary source : summary.sources()) {
                            line(writer, "S\t" + encode(source.sourceId()) + '\t' + source.count() + '\t' + source.sha256());
                        }
                        line(writer, "END");
                    }
                    checkCurrent(stillCurrent, result);
                    // Same-directory completed-file publication without REPLACE_EXISTING.
                    Files.move(temporary, target);
                    temporary = null;
                    result.complete(target);
                } catch (Exception failure) {
                    result.completeExceptionally(failure);
                } finally {
                    if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
                }
            });
        } catch (RejectedExecutionException busy) { result.completeExceptionally(busy); }
        return result;
    }

    private static void checkCurrent(BooleanSupplier current, CompletableFuture<?> result) {
        if (result.isCancelled() || !current.getAsBoolean()) throw new CancellationException("Stale catalog tuple export");
    }

    private static Path requireDPath(Path path) {
        Objects.requireNonNull(path, "destination");
        if (!path.isAbsolute() || path.getRoot() == null || !"D:\\".equalsIgnoreCase(path.getRoot().toString())) {
            throw new IllegalArgumentException("Tuple evidence exports require an absolute D: path");
        }
        Path normalized = path.normalize();
        if (normalized.getParent() == null) throw new IllegalArgumentException("Tuple export must name a file");
        return normalized;
    }

    private static void checkExistingAncestors(Path parent) throws IOException {
        Path existing = parent;
        while (existing != null && !Files.exists(existing)) existing = existing.getParent();
        if (existing == null) throw new IOException("No existing D: export ancestor");
        // Resolve junctions before creating or writing anything.
        Path real = existing.toRealPath();
        if (real.getRoot() == null || !"D:\\".equalsIgnoreCase(real.getRoot().toString())) {
            throw new IOException("Tuple export ancestor resolves outside D: " + existing);
        }
    }

    private static String encode(String text) { return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)); }
    private static void line(BufferedWriter writer, String text) throws IOException { writer.write(text); writer.write('\n'); }
}
