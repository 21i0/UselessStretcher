package com.sorrowmist.useless.stretcher.content.capsule;

import com.sorrowmist.useless.stretcher.content.item.DimensionCapsuleItem;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Places validated templates in a per-player area and preserves a return point. */
public final class DimensionCapsuleService {
    private static final String RETURN_DIM = "useless_stretcher_capsule_return_dim";
    private static final String RETURN_X = "useless_stretcher_capsule_return_x";
    private static final String RETURN_Y = "useless_stretcher_capsule_return_y";
    private static final String RETURN_Z = "useless_stretcher_capsule_return_z";
    private static final String RETURN_YAW = "useless_stretcher_capsule_return_yaw";
    private static final String RETURN_PITCH = "useless_stretcher_capsule_return_pitch";
    /** Four million blocks is large enough for mod megastructures while remaining a finite input bound. */
    private static final long MAX_BLOCKS = 4_000_000L;
    /** Entity NBT is retained for loot, mobs and block entities, but cannot create an unbounded spawn burst. */
    private static final int MAX_ENTITIES = 32_768;

    private DimensionCapsuleService() { }

    public static void enter(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        if (!DimensionCapsuleItem.is(stack)) return;
        if (hasReturnPoint(player)) returnToOwner(player);
        else com.sorrowmist.useless.stretcher.network.Network.sendDimensionCapsuleCatalog(player, stack == player.getOffhandItem());
    }

    private static void enterLegacy(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        if (hasReturnPoint(player)) {
            returnToOwner(player);
            return;
        }
        String raw = com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(stack, StretcherComponents.CAPSULE_STRUCTURE.get());
        DimensionCapsuleCatalog.Entry entry = DimensionCapsuleCatalog.find(player, raw);
        if (entry == null) {
            player.displayClientMessage(Component.translatable("msg.useless_stretcher.dimension_capsule.select_first"), true);
            return;
        }
        ServerLevel target = targetLevel(player, entry.dimensionHint());
        if (target == null) {
            player.displayClientMessage(Component.translatable("msg.useless_stretcher.dimension_capsule.dimension_missing",
                    entry.dimensionHint()), true);
            return;
        }
        StructureTemplate template = target.getStructureManager().get(entry.id()).orElse(null);
        if (template == null) {
            player.displayClientMessage(Component.translatable("msg.useless_stretcher.dimension_capsule.not_found"), true);
            return;
        }
        var size = template.getSize();
        long volume = (long) size.getX() * size.getY() * size.getZ();
        CompoundTag serialized = template.save(new CompoundTag());
        long blockEntries = serialized.getList("blocks", Tag.TAG_COMPOUND).size();
        int entityEntries = serialized.getList("entities", Tag.TAG_COMPOUND).size();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0 || volume > MAX_BLOCKS
                || blockEntries > MAX_BLOCKS || entityEntries > MAX_ENTITIES
                || size.getX() > DimensionCapsuleCatalog.MAX_AXIS
                || size.getY() > DimensionCapsuleCatalog.MAX_AXIS
                || size.getZ() > DimensionCapsuleCatalog.MAX_AXIS
                || size.getY() + 4 > target.getHeight()) {
            player.displayClientMessage(Component.translatable("msg.useless_stretcher.dimension_capsule.too_large",
                    MAX_BLOCKS), true);
            return;
        }
        int facing = com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(stack, StretcherComponents.CAPSULE_ROTATION.get(), 0) & 3;
        String roomKey = player.getUUID() + "|" + entry.id() + "|" + entry.dimensionHint() + "|" + facing;
        DimensionCapsuleData data = DimensionCapsuleData.get(player.getServer());
        DimensionCapsuleData.Room room = data.room(roomKey);
        if (room == null) {
            player.displayClientMessage(Component.translatable("msg.useless_stretcher.dimension_capsule.failed"), true);
            return;
        }
        int y = Math.max(target.getMinBuildHeight() + 2,
                Math.min(64, target.getMaxBuildHeight() - size.getY() - 2));
        BlockPos origin = room.origin(y);
        Rotation rotation = rotation(facing);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation)
                .setIgnoreEntities(false).setFinalizeEntities(true).setKnownShape(false);
        var bounds = template.getBoundingBox(settings, origin);
        BlockPos entrance = new BlockPos(bounds.minX() + (bounds.maxX() - bounds.minX()) / 2,
                origin.getY() + 1, bounds.maxZ() + 3);
        if (!room.placed()) {
            if (DimensionCapsulePlacement.busyForPlayer(player.getUUID())) {
                player.displayClientMessage(Component.translatable("msg.useless_stretcher.dimension_capsule.generating"), true);
                return;
            }
            boolean queued = DimensionCapsulePlacement.enqueue(roomKey, target, template, origin, settings,
                    room.started(), success -> {
                if (!success) {
                    player.displayClientMessage(Component.translatable("msg.useless_stretcher.dimension_capsule.failed"), true);
                    return;
                }
                data.markPlaced(roomKey);
                if (player.isRemoved() || !player.isAlive() || hasReturnPoint(player)) return;
                prepareEntrance(target, entrance);
                saveReturnPoint(player);
                player.teleportTo(target, entrance.getX() + 0.5D, entrance.getY(), entrance.getZ() + 0.5D,
                        player.getYRot(), player.getXRot());
                player.displayClientMessage(Component.translatable("msg.useless_stretcher.dimension_capsule.entered",
                        entry.id().toString(), entry.dimensionHint()), true);
                    });
            if (!queued) {
                player.displayClientMessage(Component.translatable("msg.useless_stretcher.dimension_capsule.queue_full"), true);
                return;
            }
            data.markStarted(roomKey);
            player.displayClientMessage(Component.translatable("msg.useless_stretcher.dimension_capsule.generating"), true);
            return;
        }

        prepareEntrance(target, entrance);
        saveReturnPoint(player);
        player.teleportTo(target, entrance.getX() + 0.5D, entrance.getY(), entrance.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        player.displayClientMessage(Component.translatable("msg.useless_stretcher.dimension_capsule.entered",
                entry.id().toString(), entry.dimensionHint()), true);
    }

    private static void saveReturnPoint(ServerPlayer player) {
        player.getPersistentData().putString(RETURN_DIM, player.level().dimension().location().toString());
        player.getPersistentData().putDouble(RETURN_X, player.getX());
        player.getPersistentData().putDouble(RETURN_Y, player.getY());
        player.getPersistentData().putDouble(RETURN_Z, player.getZ());
        player.getPersistentData().putFloat(RETURN_YAW, player.getYRot());
        player.getPersistentData().putFloat(RETURN_PITCH, player.getXRot());
    }

    public static void enterAt(ServerPlayer player, ServerLevel target, BlockPos entrance, float yaw) {
        if (hasReturnPoint(player)) return;
        saveReturnPoint(player);
        player.teleportTo(target, entrance.getX() + 0.5D, entrance.getY(), entrance.getZ() + 0.5D, yaw, 0);
        if (player.serverLevel() != target || player.distanceToSqr(entrance.getX() + .5,
                entrance.getY(), entrance.getZ() + .5) > 4) clearReturnPoint(player);
    }

    private static void prepareEntrance(ServerLevel target, BlockPos entrance) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos floor = entrance.offset(dx, -1, dz);
            target.setBlock(floor, net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState(), 2);
            target.setBlock(floor.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            target.setBlock(floor.above(2), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        }
    }

    public static boolean hasReturnPoint(ServerPlayer player) {
        return !player.getPersistentData().getString(RETURN_DIM).isEmpty();
    }

    private static ServerLevel targetLevel(ServerPlayer player, String hint) {
        ResourceLocation id;
        try { id = new ResourceLocation(hint); } catch (Exception ignored) { return null; }
        ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id);
        return player.getServer().getLevel(key);
    }

    private static Rotation rotation(int value) {
        return switch (value & 3) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static void returnToOwner(ServerPlayer player) {
        String dim = player.getPersistentData().getString(RETURN_DIM);
        if (dim.isEmpty()) return;
        ResourceLocation id;
        try { id = new ResourceLocation(dim); } catch (Exception ignored) { return; }
        ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id);
        ServerLevel target = player.getServer().getLevel(key);
        if (target == null) {
            player.displayClientMessage(Component.translatable(
                    "msg.useless_stretcher.dimension_capsule.dimension_missing", dim), true);
            return;
        }
        double x = player.getPersistentData().getDouble(RETURN_X);
        double y = player.getPersistentData().getDouble(RETURN_Y);
        double z = player.getPersistentData().getDouble(RETURN_Z);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        player.teleportTo(target, x, y, z, player.getPersistentData().getFloat(RETURN_YAW),
                player.getPersistentData().getFloat(RETURN_PITCH));
        if (player.serverLevel() == target && player.distanceToSqr(x, y, z) < 4) clearReturnPoint(player);
    }

    private static void clearReturnPoint(ServerPlayer player) {
        player.getPersistentData().remove(RETURN_DIM);
        player.getPersistentData().remove(RETURN_X);
        player.getPersistentData().remove(RETURN_Y);
        player.getPersistentData().remove(RETURN_Z);
        player.getPersistentData().remove(RETURN_YAW);
        player.getPersistentData().remove(RETURN_PITCH);
    }
}
