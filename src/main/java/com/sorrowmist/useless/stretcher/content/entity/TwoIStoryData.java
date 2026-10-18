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

/** Per-player story progress survives death, reclaiming the doll, and server restarts. */
public final class TwoIStoryData extends SavedData {
    /** Two integers per player, never a growing conversation transcript. */
    private record Progress(int completedMask, int lastChapter) {}
    private final Map<UUID, Progress> players = new HashMap<>();

    public static TwoIStoryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(TwoIStoryData::new, TwoIStoryData::load), "useless_stretcher_two_i_story");
    }

    public boolean metBefore(UUID player) { return players.containsKey(player); }

    /** Read-only library access; looking at old chapters must never advance progress. */
    public int completedMask(UUID player) {
        Progress progress = players.get(player);
        return progress == null ? 0 : progress.completedMask() & 63;
    }

    public void complete(UUID player) {
        complete(player, 0);
    }

    public int nextChapter(UUID player, int bond) {
        Progress progress = players.get(player);
        if (progress == null) return 0;
        return TwoIStorySession.nextChapterFor(bond, progress.completedMask(), progress.lastChapter());
    }

    public void complete(UUID player, int chapter) {
        if (chapter < 0 || chapter > 5) return;
        Progress old = players.getOrDefault(player, new Progress(0, -1));
        players.put(player, new Progress(old.completedMask() | (1 << chapter), chapter));
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag savedPlayers = new ListTag();
        for (Map.Entry<UUID, Progress> player : players.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player.getKey());
            entry.putInt("completed", player.getValue().completedMask());
            entry.putInt("last", player.getValue().lastChapter());
            savedPlayers.add(entry);
        }
        tag.put("met_players", savedPlayers);
        return tag;
    }

    private static TwoIStoryData load(CompoundTag tag, HolderLookup.Provider registries) {
        TwoIStoryData data = new TwoIStoryData();
        ListTag players = tag.getList("met_players", Tag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag entry = players.getCompound(index);
            if (entry.hasUUID("player")) data.players.put(entry.getUUID("player"),
                    new Progress(entry.contains("completed") ? entry.getInt("completed") & 63 : 1,
                            Math.max(0, Math.min(5, entry.getInt("last")))));
        }
        return data;
    }
}
