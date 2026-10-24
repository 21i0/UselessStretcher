package com.sorrowmist.useless.stretcher.content.entity;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.dimension.world.TwoIStarfieldChunkGenerator;
import com.sorrowmist.useless.stretcher.dimension.world.TwoIStarfieldDimensions;
import com.sorrowmist.useless.stretcher.dimension.world.TwoIStarfieldCourtyard;
import com.sorrowmist.useless.stretcher.network.TwoIStarfieldShowNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Server-owned visits; courtyard blocks belong to first-time chunk generation, never revisit scans. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class TwoIDollService {
    private static final String COMPANION_VISIT = "two_i_starfield_companion";
    private TwoIDollService() {
    }

    public static boolean sendPlayerToStarfield(ServerPlayer player) {
        return sendPlayerToStarfield(player, true);
    }

    /** firstInvitation is false when migrating a doll whose old invitation was already used. */
    public static boolean sendPlayerToStarfield(ServerPlayer player, boolean firstInvitation) {
        return sendPlayerToStarfield(player, firstInvitation, null);
    }

    public static boolean sendPlayerToStarfield(ServerPlayer player, boolean firstInvitation, TwoIDollEntity companion) {
        if (!com.sorrowmist.useless.stretcher.config.StretcherConfig.starfieldInvitationEnabled()) {
            message(player, "starfield_disabled", true);
            return false;
        }
        if (companion != null && (companion.isRemoved() || companion.level() != player.level()
                || !player.getUUID().equals(companion.getOwnerId()) || companion.distanceToSqr(player) > 100.0D)) {
            message(player, "starfield_owner_only", true);
            return false;
        }
        if (player.level().dimension().equals(TwoIStarfieldDimensions.KEY)) {
            message(player, "starfield_active", true);
            return false;
        }
        ServerLevel target = player.getServer().getLevel(TwoIStarfieldDimensions.KEY);
        if (target == null) {
            message(player, "no_starfield", true);
            return false;
        }
        TwoIStarfieldData data = TwoIStarfieldData.get(player.getServer());
        if (target.getChunkSource().getGenerator() instanceof TwoIStarfieldChunkGenerator generator) {
            generator.resolveFloorState();
        }
        BlockPos center = areaCenter(data.slotFor(player.getUUID()));
        Vec3 arrival = safeLocation(target, player, Vec3.atBottomCenterOf(TwoIStarfieldCourtyard.entrance(center)));
        if (arrival == null) {
            message(player, "arrival_blocked", true);
            return false;
        }
        data.setReturnPoint(player.getUUID(), player.level(), player.position(), player.getYRot(), player.getXRot());
        teleport(player, target, arrival, 180.0F, -15.0F);
        if (player.level() != target) {
            data.clearReturnPoint(player.getUUID());
            return false;
        }
        data.beginVisit(player.getUUID(), target.getGameTime(), firstInvitation);
        if (companion != null) {
            var visit = new net.minecraft.nbt.CompoundTag();
            visit.putUUID("visitor", player.getUUID());
            visit.putString("origin", companion.level().dimension().location().toString());
            visit.putDouble("x", companion.getX());
            visit.putDouble("y", companion.getY());
            visit.putDouble("z", companion.getZ());
            companion.getPersistentData().put(COMPANION_VISIT, visit);
            Vec3 companionSpot = safeLocation(target, player, arrival.add(1.5D, 0.0D, 0.0D));
            TwoIDollEntity moved = companionSpot == null ? null : moveCompanion(companion, target, companionSpot, player);
            if (moved == null) {
                companion.getPersistentData().remove(COMPANION_VISIT);
                message(player, "starfield_companion_blocked", false);
            } else {
                moved.playPoseAction(TwoIDollEntity.ACTION_INVITE);
            }
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 70, 30));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.translatable("msg.useless_stretcher.two_i.starfield_subtitle")));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable("dimension.useless_stretcher.two_i_starfield")));
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.7F, 0.8F);
        message(player, "starfield_enter_gate", false);
        return true;
    }

    static BlockPos areaCenter(int slot) {
        return new BlockPos((Math.floorMod(slot, 1024) - 512) * TwoIStarfieldData.AREA_SPACING + 8,
                TwoIStarfieldChunkGenerator.SURFACE_Y,
                (Math.floorDiv(slot, 1024) - 512) * TwoIStarfieldData.AREA_SPACING + 8);
    }

    public static boolean returnFromStarfield(ServerPlayer player) {
        boolean isStarfield = player.level().dimension().equals(TwoIStarfieldDimensions.KEY);
        if (!isStarfield) return false;
        TwoIStarfieldData data = TwoIStarfieldData.get(player.getServer());
        TwoIStarfieldData.ReturnPoint point = data.returnPoint(player.getUUID());
        // Commands / old saves can put someone here without an invitation. They still need a way out.
        ServerLevel target = point == null || point.dimension().equals(TwoIStarfieldDimensions.KEY.location())
                ? null : player.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, point.dimension()));
        Vec3 destination = target == null ? null : safeLocation(target, player,
                new Vec3(point.x(), point.y(), point.z()));
        boolean fallback = destination == null;
        if (fallback) {
            target = player.getServer().overworld();
            destination = safeLocation(target, player, Vec3.atBottomCenterOf(target.getSharedSpawnPos()));
        }
        if (destination == null) {
            message(player, "return_unavailable", true);
            return true;
        }
        teleport(player, target, destination, point == null ? 0.0F : point.yaw(), point == null ? 0.0F : point.pitch());
        if (player.level() != target) return true;
        for (TwoIDollEntity companion : TwoIDollEntity.trackedFor(player.getServer(), player.getUUID())) {
            restoreCompanion(companion, player);
        }
        if (fallback && point != null) {
            // Keep the original coordinates if a temporarily absent dimension becomes available again.
            data.endCelebration(player.getUUID());
        } else {
            data.clearReturnPoint(player.getUUID());
        }
        message(player, fallback ? "returned_spawn" : "returned", false);
        return true;
    }

    private static void teleport(ServerPlayer player, ServerLevel target, Vec3 pos, float yaw, float pitch) {
        player.fallDistance = 0.0F;
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(target, pos.x, pos.y, pos.z, yaw, pitch);
        player.fallDistance = 0.0F;
        player.setDeltaMovement(Vec3.ZERO);
    }

    /** The only cross-dimension follower: the exact doll which issued this player's invitation. */
    static boolean hasCompanionVisit(TwoIDollEntity companion) {
        return companion.getPersistentData().contains(COMPANION_VISIT, net.minecraft.nbt.Tag.TAG_COMPOUND);
    }

    static void tickCompanion(TwoIDollEntity companion) {
        if (companion.tickCount % 100 != 0 || !hasCompanionVisit(companion)
                || !(companion.level() instanceof ServerLevel level)) return;
        var visit = companion.getPersistentData().getCompound(COMPANION_VISIT);
        if (!visit.hasUUID("visitor") || !visit.getUUID("visitor").equals(companion.getOwnerId())) {
            companion.getPersistentData().remove(COMPANION_VISIT);
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(visit.getUUID("visitor"));
        if (owner == null || !owner.isAlive()) return; // Keep the entity's saved marker for reconnect/restart.
        if (owner.level().dimension().equals(TwoIStarfieldDimensions.KEY)) return;
        restoreCompanion(companion, owner);
    }

    private static void restoreCompanion(TwoIDollEntity companion, ServerPlayer player) {
        if (companion.isRemoved() || !hasCompanionVisit(companion)
                || !player.getUUID().equals(companion.getOwnerId())) return;
        var visit = companion.getPersistentData().getCompound(COMPANION_VISIT);
        if (!visit.hasUUID("visitor") || !player.getUUID().equals(visit.getUUID("visitor"))) return;
        if (companion.level() == player.level()) {
            companion.getPersistentData().remove(COMPANION_VISIT);
            return;
        }
        Vec3 spot = safeLocation(player.serverLevel(), player, player.position().add(1.5D, 0.0D, 0.0D));
        if (spot == null) return;
        TwoIDollEntity moved = moveCompanion(companion, player.serverLevel(), spot, player);
        if (moved != null) {
            moved.getPersistentData().remove(COMPANION_VISIT);
        } else if (!visit.getBoolean("return_warned")) {
            visit.putBoolean("return_warned", true);
            message(player, "starfield_companion_return_blocked", false);
        }
    }

    /** Vanilla transfer preserves UUID and saved state. A cancelled arrival restores the source, never a duplicate. */
    private static TwoIDollEntity moveCompanion(TwoIDollEntity companion, ServerLevel target, Vec3 pos, ServerPlayer owner) {
        ServerLevel source = (ServerLevel) companion.level();
        companion.getNavigation().stop();
        Entity moved = companion.changeDimension(target, new net.minecraftforge.common.util.ITeleporter() {
            @Override public Entity placeEntity(Entity entity, ServerLevel current, ServerLevel destination,
                    float yaw, java.util.function.Function<Boolean, Entity> reposition) {
                Entity result = reposition.apply(false);
                if (result != null) result.moveTo(pos.x,pos.y,pos.z,owner.getYRot(),0);
                return result;
            }
        });
        // A hidden, non-player-tracked chunk need not emit onTrackingEnd during a native move.
        if (companion.isRemoved()) companion.onRemovedFromWorld();
        if (moved instanceof TwoIDollEntity result && result.isAddedToWorld() && !result.isRemoved()) {
            result.fallDistance = 0.0F;
            return result;
        }
        // A travel-event cancellation leaves the original untouched. A cancelled entity-join may
        // happen after vanilla removes it; re-add that same source object or return it as its item.
        if (companion.isRemoved()) {
            companion.revive();
            if (!source.addFreshEntity(companion)) {
                // A rejected destination handle has never joined a world and is intentionally not kept.
                companion.reclaimRemote(owner);
                companion.onRemovedFromWorld();
            }
        }
        return null;
    }

    /** Bounded travel-time search only. Existing builds are never replaced to make a landing pad. */
    static Vec3 safeLocation(ServerLevel level, ServerPlayer player, Vec3 desired) {
        if (!Double.isFinite(desired.x) || !Double.isFinite(desired.y) || !Double.isFinite(desired.z)
                || !level.getWorldBorder().isWithinBounds(BlockPos.containing(desired))) return null;
        if (safeAt(level, player, desired)) return desired;
        BlockPos origin = BlockPos.containing(desired);
        for (int radius = 0; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int dy = -2; dy <= 8; dy++) {
                        Vec3 candidate = Vec3.atBottomCenterOf(origin.offset(dx, dy, dz));
                        if (safeAt(level, player, candidate)) return candidate;
                    }
                }
            }
        }
        // A new building may cover the old ground-level point: try its roof, never erase it.
        for (int dx = -8; dx <= 8; dx += 4) {
            for (int dz = -8; dz <= 8; dz += 4) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                Vec3 candidate = new Vec3(x + 0.5D, y, z + 0.5D);
                if (safeAt(level, player, candidate)) return candidate;
            }
        }
        return null;
    }

    private static boolean safeAt(ServerLevel level, ServerPlayer player, Vec3 pos) {
        BlockPos feet = BlockPos.containing(pos);
        if (feet.getY() < level.getMinBuildHeight() + 1 || feet.getY() > level.getMaxBuildHeight() - 3
                || !level.getWorldBorder().isWithinBounds(feet)) return false;
        var below = level.getBlockState(feet.below());
        if (below.getCollisionShape(level, feet.below()).isEmpty() || below.is(Blocks.MAGMA_BLOCK)
                || below.is(Blocks.CACTUS) || below.is(Blocks.CAMPFIRE) || below.is(Blocks.SOUL_CAMPFIRE)) return false;
        for (int dy = 0; dy <= 1; dy++) {
            var state = level.getBlockState(feet.above(dy));
            if (!state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                    || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH)) return false;
        }
        // A crouching/swimming visitor must still be able to stand up after arriving.
        return level.noCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(pos));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        // Nothing is kept alive for absent players. Safety/rewards touch only actual visitors once a second.
        if (event.getServer().getTickCount() % 20 != 0) return;
        ServerLevel level = event.getServer().getLevel(TwoIStarfieldDimensions.KEY);
        if (level == null || level.players().isEmpty()) return;
        TwoIStarfieldData data = TwoIStarfieldData.get(event.getServer());
        for (ServerPlayer player : java.util.List.copyOf(level.players())) {
            if (!player.isAlive()) continue;
            if (player.getY() < TwoIStarfieldChunkGenerator.SURFACE_Y - 8
                    || !level.getWorldBorder().isWithinBounds(player.blockPosition())) {
                returnFromStarfield(player);
                message(player, "starfield_rescued", false);
                continue;
            }
            tryStartCourtyardShow(player, data);
            long end = data.celebrationEnd(player.getUUID());
            if (end == 0L) continue;
            long remaining = end - level.getGameTime();
            if (remaining <= 0L) {
                data.endCelebration(player.getUUID());
                message(player, "starfield_finished", false);
                player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.5F, 1.2F);
                continue;
            }
            if (remaining % 60 < 20 && data.rewardsRemaining(player.getUUID()) > 0) dropReward(player, data);
            if (remaining <= 100L) player.displayClientMessage(Component.translatable(
                    "msg.useless_stretcher.two_i.starfield_countdown", (remaining + 19L) / 20L), true);
        }
    }

    private static void dropReward(ServerPlayer player, TwoIStarfieldData data) {
        if (!player.level().dimension().equals(TwoIStarfieldDimensions.KEY)
                || data.rewardsRemaining(player.getUUID()) <= 0 || data.celebrationEnd(player.getUUID()) <= player.level().getGameTime()) return;
        var item = BuiltInRegistries.ITEM.get(new ResourceLocation("ae2", "creative_storage_cell"));
        if (item == Items.AIR) return;
        Vec3 pos = player.position().add(player.getRandom().nextDouble() * 4.0D - 2.0D, 7.0D,
                player.getRandom().nextDouble() * 4.0D - 2.0D);
        // Eight lifetime rewards, including interruptions/restarts, without scanning nearby entities.
        ItemEntity drop = new ItemEntity(player.level(), pos.x, pos.y, pos.z, new ItemStack(item));
        drop.setTarget(player.getUUID());
        drop.setInvulnerable(true);
        drop.setExtendedLifetime();
        drop.setPickUpDelay(10);
        drop.setDeltaMovement(0.0D, -0.08D, 0.0D);
        if (player.level().addFreshEntity(drop)) {
            data.recordRewardDrop(player.getUUID());
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.25F,
                    0.8F + 0.1F * (TwoIStarfieldData.MAX_REWARD_DROPS - data.rewardsRemaining(player.getUUID())));
        }
    }

    static boolean tryStartCourtyardShow(ServerPlayer player, TwoIStarfieldData data) {
        if (!player.level().dimension().equals(TwoIStarfieldDimensions.KEY)
                || !data.awaitsCourtyardEntry(player.getUUID())) return false;
        BlockPos center = areaCenter(data.slotFor(player.getUUID()));
        if (!TwoIStarfieldCourtyard.inside(center, player.position())) return false;
        if (!data.beginCelebration(player.getUUID(), player.level().getGameTime())) return false;
        TwoIStarfieldShowNetwork.send(player, center, data.celebrationEnd(player.getUUID()));
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.8F, 1.0F);
        message(player, data.rewardsRemaining(player.getUUID()) > 0 ? "starfield_show_started" : "starfield_show_replay", false);
        return true;
    }

    @SubscribeEvent
    public static void rejoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !player.level().dimension().equals(TwoIStarfieldDimensions.KEY)) return;
        TwoIStarfieldData data = TwoIStarfieldData.get(player.getServer());
        long end = data.celebrationEnd(player.getUUID());
        if (end > player.level().getGameTime()) {
            TwoIStarfieldShowNetwork.send(player, areaCenter(data.slotFor(player.getUUID())), end);
        }
    }

    @SubscribeEvent
    public static void preventTravelHazards(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !player.level().dimension().equals(TwoIStarfieldDimensions.KEY)) return;
        if (event.getSource().is(DamageTypes.FALL) || event.getSource().is(DamageTypes.IN_WALL)
                || event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            event.setCanceled(true);
            if (!event.getSource().is(DamageTypes.FALL)) {
                returnFromStarfield(player);
                message(player, "starfield_rescued", false);
            }
        }
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        ServerLevel level = event.getServer().getLevel(TwoIStarfieldDimensions.KEY);
        if (level != null && level.getChunkSource().getGenerator() instanceof TwoIStarfieldChunkGenerator generator) {
            // Also covers players who log back in here without going through a new invitation.
            generator.resolveFloorState();
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        TwoIDollEntity.clearTracking();
    }

    private static void message(ServerPlayer player, String suffix, boolean actionBar) {
        player.displayClientMessage(Component.translatable("msg.useless_stretcher.two_i." + suffix), actionBar);
    }
}
