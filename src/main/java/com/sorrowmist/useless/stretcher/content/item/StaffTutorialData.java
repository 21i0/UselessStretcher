package com.sorrowmist.useless.stretcher.content.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** World-scoped record of players who have already seen the staff key hint. */
public final class StaffTutorialData extends SavedData {
    private static final String NAME = "useless_stretcher_staff_tutorial";
    private final Set<UUID> shownPlayers = new HashSet<>();

    public static StaffTutorialData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(StaffTutorialData::new, StaffTutorialData::load), NAME);
    }

    public boolean markShown(UUID playerId) {
        if (!shownPlayers.add(playerId)) return false;
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        for (UUID playerId : shownPlayers) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", playerId);
            players.add(entry);
        }
        tag.put("shown_players", players);
        return tag;
    }

    private static StaffTutorialData load(CompoundTag tag, HolderLookup.Provider registries) {
        StaffTutorialData data = new StaffTutorialData();
        ListTag players = tag.getList("shown_players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            if (entry.hasUUID("player")) data.shownPlayers.add(entry.getUUID("player"));
        }
        return data;
    }
}
