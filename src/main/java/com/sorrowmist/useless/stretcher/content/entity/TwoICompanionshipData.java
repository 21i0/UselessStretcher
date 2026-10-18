package com.sorrowmist.useless.stretcher.content.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A server-only five-minute clock: periodic checks never send inventory/network updates. */
public final class TwoICompanionshipData extends SavedData {
    public static final int CHECK_INTERVAL = 100;
    public static final int REWARD_INTERVAL = 6000;
    private static final int DAILY_CAP = 2;
    private static final Factory<TwoICompanionshipData> FACTORY =
            new Factory<>(TwoICompanionshipData::new, TwoICompanionshipData::load);
    public record Progress(long day, int earned, int ticks, long lastSample) {
        private Progress forDay(long time) {
            long today = Math.max(0, time / 24000L);
            return day == today ? this : new Progress(today, 0, 0, lastSample);
        }

        public TwoIDollMemory merge(TwoIDollMemory memory) {
            // Other interaction budgets remain on the doll. Only the companion clock lives here.
            var old = memory.interaction();
            long mergedDay = Math.max(old.day(), day);
            if (old.day() < mergedDay) old = old.forDay(Math.max(0, mergedDay) * 24000L);
            return new TwoIDollMemory(memory.cooldowns(), new TwoIDollMemory.Interaction(mergedDay,
                    old.pats(), old.talks(), old.stories(), day == mergedDay ? earned : 0,
                    day == mergedDay ? ticks : 0, old.lastChat(),
                    old.lastGift(), old.repeatedGift(), old.gifts()), lastSample);
        }
    }

    public record Sample(Progress progress, boolean awarded) { }
    private final Map<UUID, Progress> dolls = new HashMap<>();

    public static TwoICompanionshipData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                FACTORY,
                "useless_stretcher_two_i_companionship");
    }

    /** The first access imports old item memory once; stale snapshots never overwrite world progress. */
    public Progress progress(UUID doll, TwoIDollMemory legacy) {
        Progress existing = dolls.get(doll);
        if (existing != null) return existing;
        var old = legacy.interaction();
        Progress imported = new Progress(old.day(), old.companionship(),
                Math.clamp(old.companionTicks(), 0, REWARD_INTERVAL - 1), legacy.lastCompanionSample());
        dolls.put(doll, imported);
        setDirty();
        return imported;
    }

    public Sample sample(UUID doll, TwoIDollMemory legacy, long time) {
        Progress stored = progress(doll, legacy);
        Progress before = stored.forDay(time);
        boolean awarded = false;
        Progress next = before;
        if (time % CHECK_INTERVAL == 0 && before.lastSample() < time && before.earned() < DAILY_CAP) {
            // Add only the current active interval. Offline/stored time is never caught up.
            int ticks = before.ticks() + CHECK_INTERVAL;
            awarded = ticks >= REWARD_INTERVAL;
            next = new Progress(before.day(), before.earned() + (awarded ? 1 : 0),
                    awarded ? ticks - REWARD_INTERVAL : ticks, time);
        }
        if (!next.equals(stored)) {
            dolls.put(doll, next);
            setDirty();
        }
        return new Sample(next, awarded);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var record : dolls.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("doll", record.getKey());
            Progress p = record.getValue();
            entry.putLong("day", p.day());
            entry.putInt("earned", p.earned());
            entry.putInt("ticks", p.ticks());
            entry.putLong("last_sample", p.lastSample());
            list.add(entry);
        }
        tag.put("dolls", list);
        return tag;
    }

    public static TwoICompanionshipData load(CompoundTag tag, HolderLookup.Provider registries) {
        TwoICompanionshipData data = new TwoICompanionshipData();
        ListTag list = tag.getList("dolls", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("doll")) continue;
            data.dolls.put(entry.getUUID("doll"), new Progress(Math.max(-1, entry.getLong("day")),
                    Math.clamp(entry.getInt("earned"), 0, DAILY_CAP),
                    Math.clamp(entry.getInt("ticks"), 0, REWARD_INTERVAL - 1),
                    entry.contains("last_sample") ? Math.max(-1, entry.getLong("last_sample")) : -1));
        }
        return data;
    }
}
