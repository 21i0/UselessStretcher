package com.sorrowmist.useless.stretcher.content.leaf;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Per-world rolls and claims for the independent one-time leaf rewards. */
public final class StaffLeafDropData extends SavedData {
    private static final String NAME = "useless_stretcher_staff_leaf_drops";
    private final Set<UUID> staffRolled = new HashSet<>();
    private final Set<UUID> staffClaimed = new HashSet<>();
    private final Set<UUID> dollRolled = new HashSet<>();
    private final Set<UUID> dollClaimed = new HashSet<>();

    public static StaffLeafDropData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(StaffLeafDropData::new, StaffLeafDropData::load), NAME);
    }

    public boolean hasRolledStaff(UUID playerId) {
        return staffRolled.contains(playerId);
    }

    public boolean hasClaimedStaff(UUID playerId) {
        return staffClaimed.contains(playerId);
    }

    public boolean hasPendingStaff(UUID playerId) {
        return hasRolledStaff(playerId) && !hasClaimedStaff(playerId);
    }

    public boolean hasRolledDoll(UUID playerId) {
        return dollRolled.contains(playerId);
    }

    public boolean hasClaimedDoll(UUID playerId) {
        return dollClaimed.contains(playerId);
    }

    public boolean hasPendingDoll(UUID playerId) {
        return hasRolledDoll(playerId) && !hasClaimedDoll(playerId);
    }

    public boolean isPending(UUID playerId) {
        return hasPendingStaff(playerId) || hasPendingDoll(playerId);
    }

    public boolean beginStaffDelivery(UUID playerId) {
        if (!staffRolled.add(playerId)) return false;
        setDirty();
        return true;
    }

    public boolean beginDollDelivery(UUID playerId) {
        if (!dollRolled.add(playerId)) return false;
        setDirty();
        return true;
    }

    public void markStaffDelivered(UUID playerId) {
        if (staffRolled.contains(playerId) && staffClaimed.add(playerId)) setDirty();
    }

    public void markDollDelivered(UUID playerId) {
        if (dollRolled.contains(playerId) && dollClaimed.add(playerId)) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("staff_rolled_players", saveUuidSet(staffRolled));
        tag.put("staff_claimed_players", saveUuidSet(staffClaimed));
        tag.put("doll_rolled_players", saveUuidSet(dollRolled));
        tag.put("doll_claimed_players", saveUuidSet(dollClaimed));
        return tag;
    }

    private static StaffLeafDropData load(CompoundTag tag, HolderLookup.Provider registries) {
        StaffLeafDropData data = new StaffLeafDropData();
        if (tag.contains("staff_rolled_players", Tag.TAG_LIST)) {
            loadUuidSet(tag.getList("staff_rolled_players", Tag.TAG_COMPOUND), data.staffRolled);
            loadUuidSet(tag.getList("staff_claimed_players", Tag.TAG_COMPOUND), data.staffClaimed);
            loadUuidSet(tag.getList("doll_rolled_players", Tag.TAG_COMPOUND), data.dollRolled);
            loadUuidSet(tag.getList("doll_claimed_players", Tag.TAG_COMPOUND), data.dollClaimed);
        } else {
            // Original saves tracked staff rolls and pending staff separately. An interim
            // format also wrote reward bits and a repeatable doll queue.
            Set<UUID> pendingStaff = new HashSet<>();
            loadUuidSet(tag.getList("triggered_players", Tag.TAG_COMPOUND), data.staffRolled);
            loadUuidSet(tag.getList("pending_players", Tag.TAG_COMPOUND), pendingStaff);
            data.staffRolled.addAll(pendingStaff);
            data.staffClaimed.addAll(data.staffRolled);
            data.staffClaimed.removeAll(pendingStaff);

            // Bundled versions promised a doll for pending staff rewards. Staff-only
            // versions did not, and the separate-roll format already has its own queue.
            if (tag.contains("delivered_rewards", Tag.TAG_LIST) && !tag.getBoolean("doll_rewards_migrated")) {
                data.dollRolled.addAll(pendingStaff);
            }

            ListTag rewards = tag.getList("delivered_rewards", Tag.TAG_COMPOUND);
            for (int i = 0; i < rewards.size(); i++) {
                CompoundTag entry = rewards.getCompound(i);
                if (!entry.hasUUID("player")) continue;
                UUID player = entry.getUUID("player");
                int mask = entry.getInt("mask");
                if ((mask & 1) != 0) {
                    data.staffRolled.add(player);
                    data.staffClaimed.add(player);
                }
                if ((mask & 2) != 0) {
                    data.dollRolled.add(player);
                    data.dollClaimed.add(player);
                }
            }

            ListTag pendingDolls = tag.getList("pending_dolls", Tag.TAG_COMPOUND);
            for (int i = 0; i < pendingDolls.size(); i++) {
                CompoundTag entry = pendingDolls.getCompound(i);
                if (!entry.hasUUID("player") || entry.getInt("count") <= 0) continue;
                UUID player = entry.getUUID("player");
                data.dollRolled.add(player);
                // An explicit historical claim wins over a leftover repeatable
                // queue. Clearing that claim would hand out a second doll.
            }
            data.setDirty();
        }

        // A claim without a roll is still a consumed chance, including damaged records.
        boolean repairedStaff = data.staffRolled.addAll(data.staffClaimed);
        boolean repairedDoll = data.dollRolled.addAll(data.dollClaimed);
        if (repairedStaff || repairedDoll) data.setDirty();
        return data;
    }

    private static ListTag saveUuidSet(Set<UUID> values) {
        ListTag list = new ListTag();
        for (UUID value : values) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", value);
            list.add(entry);
        }
        return list;
    }

    private static void loadUuidSet(ListTag list, Set<UUID> destination) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("player")) continue;
            try {
                destination.add(entry.getUUID("player"));
            } catch (IllegalArgumentException ignored) {
                // Skip one damaged entry without preventing the world from loading.
            }
        }
    }
}
