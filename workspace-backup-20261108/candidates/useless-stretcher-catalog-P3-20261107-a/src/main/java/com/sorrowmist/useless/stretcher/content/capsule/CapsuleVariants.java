package com.sorrowmist.useless.stretcher.content.capsule;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.network.CapsuleVariantNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.FutureTask;

/** Bounded sampling of native plans. A round is a sample, never an exhaustive variant catalogue. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class CapsuleVariants {
    private static final long CALLBACK_NANOS = 30_000_000_000L, ROUND_NANOS = 60_000_000_000L;
    private static MinecraftServer current;
    private static String signature = "";
    private static FutureTask<String> hashing;
    private static FutureTask<NativeCapsules.VariantPlan> pending;
    private static CapsuleVariantsData.Search running;
    private static NativeCapsules.VariantProbe probe;
    private static long began, memory, roundNanos;
    private CapsuleVariants() { }

    public static void request(ServerPlayer player, String id, String dimension, int action, String candidateId,
                               boolean offhand, int parentInstanceId, int side) {
        var server = player.getServer();
        var structure = player.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse(id));
        if (structure == null || !DimensionCapsuleCatalog.dimensions(player, structure).contains(dimension)) {
            send(player, null, id, dimension, "unavailable", "", -1); return;
        }
        var source = DimensionCapsuleCatalog.sourceLevel(player, structure, dimension);
        if (source == null || server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension))) == null) return;
        var data = CapsuleVariantsData.get(server); String key = CapsuleVariantsData.key(player.getUUID(), id, dimension);
        var s = data.searches.get(key);
        if (s == null) {
            // Only inactive, unplaced caches are evicted. Actual rooms are never removed.
            while (data.searches.size() >= CapsuleVariantsData.MAX_SEARCHES || data.searches.values().stream()
                    .filter(other -> other.owner.equals(player.getUUID())).count() >= CapsuleVariantsData.MAX_PER_OWNER) {
                boolean ownerFull = data.searches.values().stream().filter(other -> other.owner.equals(player.getUUID())).count() >= CapsuleVariantsData.MAX_PER_OWNER;
                var victim = data.searches.values().stream().filter(other -> !other.active && other != running
                        && (!ownerFull || other.owner.equals(player.getUUID())))
                        .min(Comparator.comparingLong(other -> other.lastAccess)).orElse(null);
                if (victim == null) break;
                data.searches.remove(victim.key()); data.setDirty();
            }
            if (data.searches.size() >= CapsuleVariantsData.MAX_SEARCHES || data.searches.values().stream()
                    .filter(other -> other.owner.equals(player.getUUID())).count() >= CapsuleVariantsData.MAX_PER_OWNER) {
                send(player, null, id, dimension, "cache_full", "", -1); return;
            }
            s = new CapsuleVariantsData.Search(player.getUUID(), id, dimension, source.dimension().location().toString(), source.getSeed());
            data.searches.put(key, s); data.setDirty();
        }
        s.lastAccess = System.currentTimeMillis();
        boolean resourcesReady = !signature.isEmpty() && !signature.equals("unavailable");
        if (resourcesReady) {
            refresh(s, data);
            if (!s.source.equals(source.dimension().location().toString()) || s.seed != source.getSeed()) {
                if (running == s && pending != null) { pending.cancel(true); pending = null; running = null; probe = null; }
                s.candidates.clear(); s.cursor = s.roundStart = 0; s.active = false; s.phase = "stale";
                s.source = source.dimension().location().toString(); s.seed = source.getSeed(); data.setDirty();
            }
        }
        int generated = -1; String responsePhase = null;
        if (action == 1 && resourcesReady && !s.active && s.candidates.size() < CapsuleVariantsData.MAX_CANDIDATES) {
            s.roundStart = s.cursor; s.roundLimit = Math.min(256, Integer.MAX_VALUE - s.cursor);
            s.active = s.roundLimit > 0; s.phase = s.active ? "searching" : "finished"; data.setDirty();
        } else if (action == 2) {
            s.active = false; s.phase = "paused"; data.setDirty();
            if (running == s && pending != null) { pending.cancel(true); pending = null; running = null; probe = null; }
        } else if (action == 3 && resourcesReady && signature.equals(s.signature)) {
            var c = s.candidates.get(candidateId);
            if (c != null) {
                // The room itself is the durable idempotency record if a crash happened between the two saves.
                var rooms = CapsuleInstances.get(server);
                final String expectedSource = s.source, expectedSignature = s.signature; final long expectedSeed = s.seed;
                var existing = rooms.rooms.values().stream().filter(r -> r.owner.equals(player.getUUID()) && r.structure.equals(id)
                        && r.dimension.equals(dimension) && r.variantFingerprint.equals(candidateId)
                        && r.sourceDimension.equals(expectedSource) && r.sourceSeed == expectedSeed
                        && r.variantSignature.equals(expectedSignature)).findFirst().orElse(null);
                if (existing != null) generated = c.generatedInstanceId = existing.instanceId;
                else generated = NativeCapsules.installVariant(player, id, dimension, s.source, s.seed, c.plan, candidateId, s.signature);
                if (generated >= 0) c.generatedInstanceId = generated;
                else responsePhase = "busy";
                data.setDirty();
            } else responsePhase = "stale";
        }
        send(player, s, id, dimension, signature.isEmpty() ? "busy" : signature.equals("unavailable") ? "unavailable"
                : responsePhase == null ? s.phase : responsePhase, candidateId, generated);
    }
    private static void send(ServerPlayer player, CapsuleVariantsData.Search s, String id, String target,
                             String phase, String selected, int generated) {
        var entries = new ArrayList<CapsuleVariantNetwork.Entry>(); int[] pixels = new int[0];
        if (s != null) {
            if (selected.isEmpty() || !s.candidates.containsKey(selected)) selected = s.candidates.keySet().stream().findFirst().orElse("");
            for (var c : s.candidates.values()) {
                var l = c.layout; entries.add(new CapsuleVariantNetwork.Entry(l.fingerprint(), l.family(), l.template(), l.sx(), l.sy(), l.sz(), l.pieces()));
                if (l.fingerprint().equals(selected)) pixels = l.pixels();
            }
        }
        CapsuleVariantNetwork.state(player, new CapsuleVariantNetwork.State(id, target, phase, s == null ? 0 : s.cursor,
                s == null ? 0 : s.roundStart, s == null ? 256 : s.roundLimit, entries, selected, pixels, generated));
    }
    private static void refresh(CapsuleVariantsData.Search s, CapsuleVariantsData data) {
        if (signature.equals(s.signature)) return;
        boolean fresh = s.signature.isEmpty() && s.cursor == 0 && s.candidates.isEmpty();
        s.candidates.clear(); s.cursor = s.roundStart = 0; s.signature = signature;
        s.active = false; s.phase = fresh ? "idle" : "stale"; data.setDirty();
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (current != server) { reset(); current = server; }
        var data = CapsuleVariantsData.get(server);
        if (data.searches.isEmpty()) return;
        long now = System.nanoTime();
        if (signature.isEmpty()) {
            if (hashing == null) { hashing = NativeCapsules.submitPlanning(() -> resourceSignature(server)); began = now; }
            if (hashing == null) return;
            if (!hashing.isDone()) { if (now - began > CALLBACK_NANOS) hashing.cancel(true); return; }
            try { signature = hashing.get(); for (var s : data.searches.values()) refresh(s, data); }
            catch (Exception failure) {
                for (var s : data.searches.values()) { s.active = false; s.phase = "failed"; }
                LogUtils.getLogger().warn("Capsule resource signature unavailable; discovery stopped", failure);
                signature = "unavailable";
            }
            hashing = null;
        }
        if (signature.equals("unavailable")) return;
        if (pending != null) {
            if (!pending.isDone()) {
                if (now - began > CALLBACK_NANOS || usedMemory() - memory > 256L * 1024 * 1024) {
                    pending.cancel(true); running.active = false; running.phase = "failed"; data.setDirty();
                }
                return;
            }
            try {
                var result = pending.get(); running.cursor++;
                if (result != null && !running.candidates.containsKey(result.layout().fingerprint())) {
                    var l = result.layout();
                    if (l.sx() > 8192 || l.sz() > 8192 || l.sy() > 65536 || data.bytes() + result.bytes() > CapsuleVariantsData.MAX_CACHE_BYTES) {
                        running.active = false; running.phase = "cache_full";
                    } else running.candidates.put(l.fingerprint(), new CapsuleVariantsData.Candidate(result.plan(), l, result.bytes()));
                }
                roundNanos += now - began;
                if (running.cursor - running.roundStart >= running.roundLimit || roundNanos >= ROUND_NANOS) {
                    running.active = false; running.phase = "finished";
                }
                if (running.candidates.size() >= CapsuleVariantsData.MAX_CANDIDATES) { running.active = false; running.phase = "cache_full"; }
                data.setDirty();
            } catch (Exception failure) {
                running.active = false; running.phase = "failed"; data.setDirty();
                LogUtils.getLogger().warn("Capsule variant sampling stopped for {}", running.id, failure);
            }
            pending = null;
        }
        var next = data.searches.values().stream().filter(s -> s.active).findFirst().orElse(null);
        if (next == null) { running = null; probe = null; return; }
        try {
            if (running != next || probe == null) {
                var source = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(next.source)));
                if (source == null || source.getSeed() != next.seed) throw new IllegalStateException("Candidate source unavailable");
                probe = new NativeCapsules.VariantProbe(server, next.owner, next.id, next.target, source); running = next; roundNanos = 0;
            }
            final int cursor = next.cursor; final var work = probe;
            pending = NativeCapsules.submitPlanning(() -> work.plan(cursor));
            if (pending != null) { began = now; memory = usedMemory(); }
        } catch (RuntimeException failure) {
            next.active = false; next.phase = "unavailable"; data.setDirty(); running = null; probe = null;
            LogUtils.getLogger().warn("Capsule variant source unavailable for {}", next.id, failure);
        }
    }
    private static long usedMemory() { var r = Runtime.getRuntime(); return r.totalMemory() - r.freeMemory(); }

    private static String resourceSignature(MinecraftServer server) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update("capsule-variants-v1".getBytes(StandardCharsets.UTF_8));
        for (var mod : net.neoforged.fml.ModList.get().getMods().stream().sorted(Comparator.comparing(m -> m.getModId())).toList())
            digest.update((mod.getModId() + "=" + mod.getVersion() + "\n").getBytes(StandardCharsets.UTF_8));
        // An empty prefix is invalid for directory packs. Include indirect processor inputs too.
        var resources = new TreeMap<ResourceLocation, net.minecraft.server.packs.resources.Resource>();
        for (String root : List.of("worldgen", "structure", "structures", "dimension", "dimension_type", "tags",
                "loot_table", "loot_tables", "neoforge", "forge", "biome_modifier", "structure_modifier", "global_loot_modifiers"))
            resources.putAll(server.getResourceManager().listResources(root,
                    id -> id.getPath().endsWith(".json") || id.getPath().endsWith(".nbt")));
        if (resources.size() > 100_000) throw new IllegalStateException("Resource signature file limit");
        long bytes = 0; byte[] buffer = new byte[8192];
        for (var id : resources.keySet().stream().sorted().toList()) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            digest.update(id.toString().getBytes(StandardCharsets.UTF_8)); digest.update((byte)0);
            try (var input = resources.get(id).open()) {
                int count; while ((count = input.read(buffer)) >= 0) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    bytes += count; if (bytes > 128L * 1024 * 1024) throw new IllegalStateException("Resource signature byte limit");
                    digest.update(buffer, 0, count);
                }
            }
            digest.update((byte)0xff);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    @SubscribeEvent public static void reload(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) { reset(); }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { reset(); current = null; }
    private static void reset() {
        if (pending != null) pending.cancel(true); if (hashing != null) hashing.cancel(true);
        pending = null; hashing = null; running = null; probe = null; signature = ""; roundNanos = 0;
    }
}
