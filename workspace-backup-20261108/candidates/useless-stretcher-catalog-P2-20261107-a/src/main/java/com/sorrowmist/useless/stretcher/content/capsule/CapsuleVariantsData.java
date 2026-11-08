package com.sorrowmist.useless.stretcher.content.capsule;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.io.*;
import java.util.*;

/** Unplaced native plans are separate from allocated/inhabited capsule rooms. */
public final class CapsuleVariantsData extends SavedData {
    static final int MAX_SEARCHES = 16, MAX_PER_OWNER = 4, MAX_CANDIDATES = 32;
    static final int MAX_PLAN_BYTES = 2 * 1024 * 1024, MAX_CACHE_BYTES = 32 * 1024 * 1024;
    final Map<String, Search> searches = new LinkedHashMap<>();
    public static CapsuleVariantsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(CapsuleVariantsData::new,
                CapsuleVariantsData::load), "useless_stretcher_capsule_variants");
    }
    static String key(UUID owner, String id, String target) { return owner + "|" + id + "|" + target; }
    int bytes() { return searches.values().stream().flatMap(s -> s.candidates.values().stream()).mapToInt(c -> c.bytes).sum(); }
    static final class Search {
        final UUID owner; final String id, target;
        String source, signature = "", phase = "idle";
        long seed, lastAccess;
        int cursor, roundStart, roundLimit = 256;
        boolean active;
        final Map<String, Candidate> candidates = new LinkedHashMap<>();
        Search(UUID owner, String id, String target, String source, long seed) {
            this.owner = owner; this.id = id; this.target = target; this.source = source; this.seed = seed;
        }
        String key() { return CapsuleVariantsData.key(owner, id, target); }
    }
    static final class Candidate {
        final CompoundTag plan; final CapsuleVariantLayout.Layout layout; final int bytes;
        int generatedInstanceId = -1;
        Candidate(CompoundTag plan, CapsuleVariantLayout.Layout layout, int bytes) {
            this.plan = plan; this.layout = layout; this.bytes = bytes;
        }
    }
    static int planBytes(CompoundTag tag) {
        class Limited extends OutputStream {
            int count;
            public void write(int value) throws IOException { if (++count > MAX_PLAN_BYTES) throw new IOException("Variant plan exceeds cache bound"); }
            public void write(byte[] b, int off, int len) throws IOException {
                if (len > MAX_PLAN_BYTES - count) throw new IOException("Variant plan exceeds cache bound"); count += len;
            }
        }
        Limited counter = new Limited();
        try { NbtIo.write(tag, new DataOutputStream(counter)); return counter.count; }
        catch (IOException failure) { throw new IllegalArgumentException("Variant plan exceeds cache bound", failure); }
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Search s : searches.values()) {
            CompoundTag n = new CompoundTag(); n.putUUID("owner", s.owner); n.putString("id", s.id); n.putString("target", s.target);
            n.putString("source", s.source); n.putLong("seed", s.seed); n.putLong("last_access", s.lastAccess); n.putString("signature", s.signature);
            n.putString("phase", s.phase); n.putInt("cursor", s.cursor); n.putInt("round_start", s.roundStart);
            n.putInt("round_limit", s.roundLimit); n.putBoolean("active", s.active);
            ListTag plans = new ListTag();
            for (Candidate c : s.candidates.values()) {
                var l = c.layout; CompoundTag p = new CompoundTag(); p.put("plan", c.plan.copy());
                p.putString("fingerprint", l.fingerprint()); p.putString("family", l.family()); p.putString("template", l.template());
                p.putInt("sx", l.sx()); p.putInt("sy", l.sy()); p.putInt("sz", l.sz()); p.putInt("pieces", l.pieces());
                p.putIntArray("pixels", l.pixels()); p.putInt("instance", c.generatedInstanceId); plans.add(p);
            }
            n.put("candidates", plans); list.add(n);
        }
        tag.put("searches", list); tag.putInt("format", 1); return tag;
    }
    static CapsuleVariantsData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new CapsuleVariantsData(); int total = 0;
        for (Tag entry : tag.getList("searches", Tag.TAG_COMPOUND)) {
            if (data.searches.size() >= MAX_SEARCHES) break;
            try {
                CompoundTag n = (CompoundTag)entry;
                Search s = new Search(n.getUUID("owner"), n.getString("id"), n.getString("target"), n.getString("source"), n.getLong("seed"));
                if (data.searches.values().stream().filter(other -> other.owner.equals(s.owner)).count() >= MAX_PER_OWNER) continue;
                s.lastAccess = n.getLong("last_access"); s.signature = n.getString("signature"); s.phase = n.getString("phase"); s.cursor = Math.max(0, n.getInt("cursor"));
                s.roundStart = Math.min(s.cursor, Math.max(0, n.getInt("round_start")));
                s.roundLimit = Math.clamp(n.getInt("round_limit"), 1, 256); s.active = n.getBoolean("active");
                for (Tag plan : n.getList("candidates", Tag.TAG_COMPOUND)) {
                    if (s.candidates.size() >= MAX_CANDIDATES) break;
                    CompoundTag p = (CompoundTag)plan, raw = p.getCompound("plan"); int bytes = planBytes(raw);
                    String token = p.getString("fingerprint"); int[] pixels = p.getIntArray("pixels");
                    if (!com.sorrowmist.useless.stretcher.network.CapsuleVariantNetwork.validToken(token) || pixels.length != 4096
                            || total + bytes > MAX_CACHE_BYTES) continue;
                    var layout = new CapsuleVariantLayout.Layout(token, p.getString("family"), p.getString("template"),
                            p.getInt("sx"), p.getInt("sy"), p.getInt("sz"), p.getInt("pieces"), pixels);
                    if (layout.sx() <= 0 || layout.sy() <= 0 || layout.sz() <= 0 || layout.sx() > 8192 || layout.sz() > 8192
                            || layout.sy() > 65536 || layout.pieces() < 1 || layout.pieces() > 8192
                            || layout.family().length() > 256 || layout.template().length() > 256) continue;
                    Candidate c = new Candidate(raw, layout, bytes); c.generatedInstanceId = Math.max(-1, p.getInt("instance"));
                    if (s.candidates.putIfAbsent(token, c) == null) total += bytes;
                }
                data.searches.put(s.key(), s);
            } catch (RuntimeException invalid) {
                com.mojang.logging.LogUtils.getLogger().warn("Skipped invalid unplaced capsule variant cache", invalid);
            }
        }
        return data;
    }
}
