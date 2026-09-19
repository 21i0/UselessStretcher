package com.sorrowmist.useless.stretcher.content.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** World-scoped, per-player state for the staff's three unobtrusive first-use hints. */
public final class StaffTutorialData extends SavedData {
    private static final String NAME = "useless_stretcher_staff_tutorial";
    public static final int HINT_FIRST_HELD = 1;
    public static final int HINT_ACCELERATION = 1 << 1;
    public static final int HINT_GEAR_CHANGE = 1 << 2;

    private final Map<UUID, Integer> shownHints = new HashMap<>();
    private final Set<UUID> openedUiPlayers = new HashSet<>();

    public static StaffTutorialData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(StaffTutorialData::new, StaffTutorialData::load), NAME);
    }

    /** Marks the X configuration UI as opened, suppressing any remaining hints for this player. */
    public void markUiOpened(UUID playerId) {
        if (openedUiPlayers.add(playerId)) setDirty();
    }

    /** Returns true once for each of the three specified first-use situations. */
    public boolean markHintShown(UUID playerId, int hint) {
        if (openedUiPlayers.contains(playerId)) return false;
        int previous = shownHints.getOrDefault(playerId, 0);
        if ((previous & hint) != 0) return false;
        shownHints.put(playerId, previous | hint);
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, Integer> hintEntry : shownHints.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", hintEntry.getKey());
            entry.putInt("hints", hintEntry.getValue());
            players.add(entry);
        }
        tag.put("shown_players", players);
        ListTag opened = new ListTag();
        for (UUID playerId : openedUiPlayers) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", playerId);
            opened.add(entry);
        }
        tag.put("opened_ui_players", opened);
        return tag;
    }

    private static StaffTutorialData load(CompoundTag tag, HolderLookup.Provider registries) {
        StaffTutorialData data = new StaffTutorialData();
        ListTag players = tag.getList("shown_players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            if (entry.hasUUID("player")) {
                // 1.3.8 stored a set of players who received the original first-held hint.
                // Retain that state as the first stage while allowing the two new fallback hints.
                data.shownHints.put(entry.getUUID("player"), entry.contains("hints", Tag.TAG_INT)
                        ? entry.getInt("hints") : HINT_FIRST_HELD);
            }
        }
        ListTag opened = tag.getList("opened_ui_players", Tag.TAG_COMPOUND);
        for (int i = 0; i < opened.size(); i++) {
            CompoundTag entry = opened.getCompound(i);
            if (entry.hasUUID("player")) data.openedUiPlayers.add(entry.getUUID("player"));
        }
        return data;
    }
}
