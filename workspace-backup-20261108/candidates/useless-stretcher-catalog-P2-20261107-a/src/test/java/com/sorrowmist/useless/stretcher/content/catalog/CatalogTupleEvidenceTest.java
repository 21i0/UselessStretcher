package com.sorrowmist.useless.stretcher.content.catalog;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CatalogTupleEvidenceTest {
    @Test void completeOrderedDigestMatchesIndependentLengthPrefixedEncoding() throws Exception {
        var tuples = List.of(new CatalogTupleEvidence.Tuple("mod:配方", "fp\t\n\0α", "Source"),
                new CatalogTupleEvidence.Tuple("mod:two", "", "source"));
        var completed = evidence(true, tuples);
        assertEquals(2, completed.summary().count());
        assertEquals(independentDigest(tuples), completed.summary().sha256());
        assertEquals(tuples, completed.tuples());
        assertEquals(2, completed.summary().sources().size(), "source keys are case-sensitive");
        assertTrue(completed.summary().logLine().contains(completed.summary().sha256()));
    }

    @Test void orderAndFieldBoundariesChangeTheDigestWithoutChangingTheCount() {
        var ab = new CatalogTupleEvidence.Tuple("a", "bc", "source");
        var ba = new CatalogTupleEvidence.Tuple("ab", "c", "source");
        var forward = evidence(false, List.of(ab, ba));
        var reverse = evidence(false, List.of(ba, ab));
        assertEquals(forward.summary().count(), reverse.summary().count());
        assertNotEquals(forward.summary().sha256(), reverse.summary().sha256());
        assertNotEquals(evidence(false, List.of(ab)).summary().sha256(), evidence(false, List.of(ba)).summary().sha256());
    }

    @Test void sourceDigestsPreserveEachSourcesSubsequenceAndDoNotHideInterleaving() {
        var a = new CatalogTupleEvidence.Tuple("mod:a", "a", "core");
        var b = new CatalogTupleEvidence.Tuple("mod:b", "b", "adapter");
        var c = new CatalogTupleEvidence.Tuple("mod:c", "c", "core");
        var first = evidence(false, List.of(a, b, c));
        var second = evidence(false, List.of(a, c, b));
        assertNotEquals(first.summary().sha256(), second.summary().sha256());
        assertEquals(first.summary().sources(), second.summary().sources());
        assertEquals(evidence(false, List.of(a, c)).summary().sha256(), first.summary().sources().get(0).sha256());
        assertEquals(2, first.summary().sources().get(0).count());
    }

    @Test void disabledRetentionStillHashesButFrozenStateCannotBeMutatedOrReused() {
        var accumulator = new CatalogTupleEvidence.Accumulator(false);
        accumulator.append("mod:a", "fp", "core");
        var completed = accumulator.finish();
        assertFalse(completed.retainsFullTuples());
        assertThrows(IllegalStateException.class, completed::tuples);
        assertThrows(IllegalStateException.class, accumulator::finish);
        assertThrows(IllegalStateException.class, () -> accumulator.append("b", "f", "s"));
        var retained = evidence(true, List.of(new CatalogTupleEvidence.Tuple("a", "f", "s")));
        assertThrows(UnsupportedOperationException.class, () -> retained.tuples().clear());
        assertThrows(UnsupportedOperationException.class, () -> retained.summary().sources().clear());
    }

    @Test void wrongOwnerCannotReadNativeEntriesOrAppendPureTuples() throws Exception {
        var accumulator = new CatalogTupleEvidence.Accumulator(false);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { accumulator.append("a", "f", "s"); }
            catch (Throwable error) { failure.set(error); }
        });
        worker.start(); worker.join(2000);
        assertFalse(worker.isAlive());
        assertInstanceOf(IllegalStateException.class, failure.get());
        assertEquals(0, accumulator.finish().summary().count());
    }

    @Test void emptyCatalogHasVersionedDigestAndNoInventedSources() throws Exception {
        var empty = evidence(true, List.of());
        assertEquals(independentDigest(List.of()), empty.summary().sha256());
        assertEquals(0, empty.summary().count());
        assertTrue(empty.summary().sources().isEmpty());
    }

    @Test void asyncExportWritesCompleteEvidenceWithoutOverwritingOrPublishingStaleTokens() throws Exception {
        Path folder = directory();
        var completed = evidence(true, List.of(new CatalogTupleEvidence.Tuple("mod:a", "fp", "core"),
                new CatalogTupleEvidence.Tuple("mod:雪", "line\n\t", "adapter")));
        Path file = folder.resolve("tuple-a.tsv");
        assertEquals(file, CatalogTupleEvidence.exportAsync(completed, file, () -> true).get(5, TimeUnit.SECONDS));
        var lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(CatalogTupleEvidence.FILE_HEADER, lines.get(0));
        assertEquals("T\t0\tbW9kOmE=\tZnA=\tY29yZQ==", lines.get(1));
        assertEquals("TOTAL\t2\t" + completed.summary().sha256(), lines.get(3));
        assertEquals("END", lines.getLast());
        byte[] original = Files.readAllBytes(file);
        assertThrows(CompletionException.class, () -> CatalogTupleEvidence.exportAsync(completed, file, () -> true).join());
        assertArrayEquals(original, Files.readAllBytes(file));
        Path stale = folder.resolve("stale.tsv");
        assertThrows(CancellationException.class, () -> CatalogTupleEvidence.exportAsync(completed, stale, () -> false).join());
        assertFalse(Files.exists(stale));
        assertThrows(IllegalArgumentException.class, () -> CatalogTupleEvidence.exportAsync(completed, Path.of("C:\\tuple.tsv"), () -> true));
        assertThrows(IllegalArgumentException.class, () -> CatalogTupleEvidence.exportAsync(completed, Path.of("relative.tsv"), () -> true));
    }

    @Test void exporterRejectsBeyondOneRunningAndOneQueuedTask() throws Exception {
        Path folder = directory();
        var completed = evidence(true, List.of());
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        var first = CatalogTupleEvidence.exportAsync(completed, folder.resolve("one.tsv"), () -> {
            entered.countDown();
            try { return release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
        });
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var second = CatalogTupleEvidence.exportAsync(completed, folder.resolve("two.tsv"), () -> true);
            var third = CatalogTupleEvidence.exportAsync(completed, folder.resolve("three.tsv"), () -> true);
            CompletionException rejected = assertThrows(CompletionException.class, third::join);
            assertInstanceOf(RejectedExecutionException.class, rejected.getCause());
            release.countDown();
            first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); }
    }

    private static CatalogTupleEvidence.Completed evidence(boolean retain, List<CatalogTupleEvidence.Tuple> tuples) {
        var accumulator = new CatalogTupleEvidence.Accumulator(retain);
        for (var tuple : tuples) accumulator.append(tuple.recipeId(), tuple.fingerprint(), tuple.sourceId());
        return accumulator.finish();
    }

    private static Path directory() throws Exception {
        Path parent = Path.of(System.getProperty("useless_stretcher.tupleTestDirectory",
                "D:\\AI\\candidates\\useless-stretcher-catalog-P2-20261107-a\\verification\\catalog-P2-20261107\\tuple-tests"));
        if (!parent.isAbsolute() || !"D:\\".equalsIgnoreCase(parent.getRoot().toString())) throw new IllegalStateException("Test writes must stay on D:");
        Files.createDirectories(parent);
        return Files.createTempDirectory(parent, "evidence-");
    }

    private static String independentDigest(List<CatalogTupleEvidence.Tuple> tuples) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.write("USCTUPLE\0".getBytes(StandardCharsets.US_ASCII)); out.writeInt(1);
            for (var tuple : tuples) {
                out.writeByte(1);
                for (String field : List.of(tuple.recipeId(), tuple.fingerprint(), tuple.sourceId())) {
                    byte[] encoded = field.getBytes(StandardCharsets.UTF_8);
                    out.writeInt(encoded.length); out.write(encoded);
                }
            }
            out.writeByte(0); out.writeLong(tuples.size());
        }
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
    }
}
