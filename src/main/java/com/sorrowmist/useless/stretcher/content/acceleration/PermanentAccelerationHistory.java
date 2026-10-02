package com.sorrowmist.useless.stretcher.content.acceleration;

import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** World-owned index: history/reclaim never loads the machine's chunk or stores work on a player. */
public final class PermanentAccelerationHistory extends SavedData {
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    // Values must not retain the block entity (or its chunk); failures expire with that instance.
    private final Map<BlockEntity, Boolean> failedNames = new WeakHashMap<>();

    public record Entry(UUID id, UUID owner, ResourceLocation dimension, BlockPos pos,
                        String label, boolean reclaimed) {}

    public static PermanentAccelerationHistory get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(PermanentAccelerationHistory::new, PermanentAccelerationHistory::load),
                "useless_stretcher_permanent_history");
    }

    public boolean isReclaimed(UUID id) {
        Entry entry = entries.get(id);
        return entry != null && entry.reclaimed();
    }

    public void track(ServerLevel level, WondrousStaffAccelerationEntity marker) {
        if (!marker.isPermanent() || marker.isEntityMode() || marker.isTimeMode()
                || marker.getOwnerUuid() == null) {
            forget(marker.getUUID());
            return;
        }
        if (isReclaimed(marker.getUUID())) return;
        BlockPos pos = marker.getTargetPos();
        if (pos == null || !level.hasChunkAt(pos)) return;
        String name = machineName(level.getBlockEntity(pos), level.getBlockState(pos).getBlock());
        String label = name + " · x" + marker.getSpeed()
                + (marker.isIdleThrottleDisabled() ? " · 无休眠降频" : " · 动态降频");
        if (label.length() > 128) label = label.substring(0, 128);
        Entry next = new Entry(marker.getUUID(), marker.getOwnerUuid(), level.dimension().location(),
                pos.immutable(), label, false);
        if (!next.equals(entries.put(next.id(), next))) setDirty();
    }

    private String machineName(BlockEntity target, Block block) {
        if (target instanceof Nameable named && !failedNames.containsKey(target)) {
            try {
                String name = named.getDisplayName().getString();
                if (!name.isBlank()) return name;
            } catch (RuntimeException | LinkageError failure) {
                failedNames.put(target, Boolean.TRUE);
                LogUtils.getLogger().warn("Cannot read acceleration target name for {} at {}; using block name",
                        BuiltInRegistries.BLOCK.getKey(block), target.getBlockPos(), failure);
            }
        }
        try {
            String name = block.getName().getString();
            if (!name.isBlank()) return name;
        } catch (RuntimeException | LinkageError ignored) {
            // A display-only addon error must not prevent acceleration or remote reclaim.
        }
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    public List<Entry> activeEntries(UUID owner) {
        return entries.values().stream().filter(entry -> !entry.reclaimed()
                && (owner == null || owner.equals(entry.owner()))).toList();
    }

    public boolean reclaim(MinecraftServer server, UUID id, UUID requiredOwner) {
        Entry entry = entries.get(id);
        if (entry == null || entry.reclaimed()
                || (requiredOwner != null && !requiredOwner.equals(entry.owner()))) return false;
        // Keep a tombstone until the unloaded marker is seen again; it must not resume after reload.
        entries.put(id, new Entry(id, entry.owner(), entry.dimension(), entry.pos(), entry.label(), true));
        setDirty();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, entry.dimension()));
        if (level != null && level.getEntity(id) instanceof WondrousStaffAccelerationEntity marker) marker.discard();
        return true;
    }

    public void forget(UUID id) {
        if (entries.remove(id) != null) setDirty();
    }

    public static PermanentAccelerationHistory load(CompoundTag root, HolderLookup.Provider lookup) {
        var result = new PermanentAccelerationHistory();
        ListTag list = root.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            if (!tag.hasUUID("id") || !tag.hasUUID("owner")) continue;
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
            if (dimension == null) continue;
            Entry entry = new Entry(tag.getUUID("id"), tag.getUUID("owner"), dimension,
                    BlockPos.of(tag.getLong("pos")), tag.getString("label"), tag.getBoolean("reclaimed"));
            result.entries.put(entry.id(), entry);
        }
        return result;
    }

    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        for (Entry entry : entries.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", entry.id());
            tag.putUUID("owner", entry.owner());
            tag.putString("dimension", entry.dimension().toString());
            tag.putLong("pos", entry.pos().asLong());
            tag.putString("label", entry.label());
            tag.putBoolean("reclaimed", entry.reclaimed());
            list.add(tag);
        }
        root.put("entries", list);
        return root;
    }
}
