package com.sorrowmist.useless.stretcher.content.apotheosis;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

/** World-owned virtual bookshelf bonuses keyed by dimension and enchanting-table position. */
public final class ApotheosisTableData extends SavedData {
    private final Map<TableKey, Boost> tables = new HashMap<>();
    /** The player who last marked a table. Used so the staff can edit its own marked tables. */
    private final Map<TableKey, java.util.UUID> owners = new HashMap<>();

    public static ApotheosisTableData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(ApotheosisTableData::new, ApotheosisTableData::load),
                "useless_stretcher_apotheosis_tables");
    }

    public static Boost get(ServerLevel level, BlockPos pos) {
        return get(level.getServer()).tables.getOrDefault(
                new TableKey(level.dimension().location(), pos.asLong()), Boost.ZERO);
    }

    public boolean set(ResourceLocation dimension, BlockPos pos, Boost boost) {
        return set(null, dimension, pos, boost);
    }

    public boolean set(java.util.UUID owner, ResourceLocation dimension, BlockPos pos, Boost boost) {
        TableKey key = new TableKey(dimension, pos.asLong());
        Boost normalized = boost.normalized();
        java.util.UUID previousOwner = owners.get(key);
        Boost previous = tables.put(key, normalized);
        if (owner != null) owners.put(key, owner);
        if (normalized.equals(previous) && (owner == null || owner.equals(previousOwner))) return false;
        setDirty();
        return true;
    }

    public List<BlockPos> ownedTables(java.util.UUID owner, ResourceLocation dimension, int limit) {
        return owners.entrySet().stream()
                .filter(entry -> owner.equals(entry.getValue()) && dimension.equals(entry.getKey().dimension()))
                .map(entry -> BlockPos.of(entry.getKey().pos())).limit(limit).toList();
    }

    public java.util.UUID owner(ResourceLocation dimension, BlockPos pos) {
        return owners.get(new TableKey(dimension, pos.asLong()));
    }

    public boolean contains(ResourceLocation dimension, BlockPos pos) {
        return tables.containsKey(new TableKey(dimension, pos.asLong()));
    }

    /** Applies a newly saved staff preset to every table marked by that same player. */
    public int updateOwned(java.util.UUID owner, Boost boost) {
        Boost normalized = boost.normalized();
        java.util.Set<TableKey> changed = new java.util.HashSet<>();
        owners.forEach((key, tableOwner) -> {
            if (owner.equals(tableOwner) && !normalized.equals(tables.get(key))) {
                tables.put(key, normalized);
                changed.add(key);
            }
        });
        if (!changed.isEmpty()) setDirty();
        return changed.size();
    }

    /** Refreshes open Apothic Enchanting menus once after a table value changes. */
    public static void refreshOpenMenus(MinecraftServer server) {
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            Object menu = player.containerMenu;
            if (menu instanceof ApotheosisMenuRefresh refresh) {
                refresh.uselessStretcher$refreshStats();
                continue;
            }
            if (!menu.getClass().getName().startsWith("dev.shadowsoffire.apothic_enchanting.")) continue;
            // Keep the bridge reflection-only: Apothic Enchanting is optional at runtime.
            try {
                java.lang.reflect.Field slotsField = null;
                for (Class<?> type = menu.getClass(); type != null && slotsField == null; type = type.getSuperclass()) {
                    try {
                        slotsField = type.getDeclaredField("enchantSlots");
                    } catch (NoSuchFieldException ignored) {
                        // The field is inherited from vanilla EnchantmentMenu in current versions.
                    }
                }
                if (slotsField != null) {
                    slotsField.setAccessible(true);
                    Object slots = slotsField.get(menu);
                    menu.getClass().getMethod("slotsChanged", net.minecraft.world.Container.class)
                            .invoke(menu, slots);
                } else {
                    menu.getClass().getMethod("gatherStats").invoke(menu);
                }
                menu.getClass().getMethod("broadcastChanges").invoke(menu);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // No optional enchanting menu is open, or a future release changed its API.
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        tables.forEach((key, boost) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("dimension", key.dimension().toString());
            entry.putLong("pos", key.pos());
            entry.putInt("eterna", boost.eterna());
            entry.putInt("quanta", boost.quanta());
            entry.putInt("arcana", boost.arcana());
            entry.putInt("clues", boost.clues());
            java.util.UUID owner = owners.get(key);
            if (owner != null) entry.putUUID("owner", owner);
            entries.add(entry);
        });
        tag.put("tables", entries);
        return tag;
    }

    public static ApotheosisTableData load(CompoundTag tag, HolderLookup.Provider registries) {
        ApotheosisTableData data = new ApotheosisTableData();
        ListTag entries = tag.getList("tables", 10);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("dimension"));
            if (dimension == null) continue;
            Boost boost = new Boost(entry.getInt("eterna"), entry.getInt("quanta"),
                    entry.getInt("arcana"), entry.getInt("clues")).normalized();
            TableKey key = new TableKey(dimension, entry.getLong("pos"));
            data.tables.put(key, boost);
            if (entry.hasUUID("owner")) data.owners.put(key, entry.getUUID("owner"));
        }
        return data;
    }

    public record Boost(int eterna, int quanta, int arcana, int clues) {
        public static final Boost ZERO = new Boost(0, 0, 0, 0);

        public Boost normalized() {
            return new Boost(clamp(eterna, 0, 100), clamp(quanta, 0, 100),
                    clamp(arcana, 0, 100), clamp(clues, 0, 15));
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    private record TableKey(ResourceLocation dimension, long pos) { }
}
