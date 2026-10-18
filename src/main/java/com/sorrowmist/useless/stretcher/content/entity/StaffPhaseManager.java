package com.sorrowmist.useless.stretcher.content.entity;

import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.network.Network;
import com.sorrowmist.useless.stretcher.mixin.EntityNoPhysicsAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative holder state for the staff's phase-through-blocks feature. */
public final class StaffPhaseManager {
    private static final Map<UUID, State> LAST_SENT = new HashMap<>();
    private static final Map<UUID, CollisionState> COLLISION_STATES = new HashMap<>();
    // Key by player object: integrated clients and servers share UUIDs, respawns do not share state.
    private static final Map<Player, FlightState> FLIGHT = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
    private static final String FLIGHT_SNAPSHOT = "useless_stretcher_phase_flight";

    private StaffPhaseManager() {}

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            State state = state(player);
            applyNoPhysics(player, state.phase());
            State previous = LAST_SENT.put(player.getUUID(), state);
            if (!state.equals(previous)) Network.sendStaffPhaseState(player, state.phase(), state.interaction());
        }
        LAST_SENT.keySet().removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
        synchronized (FLIGHT) {
            FLIGHT.keySet().removeIf(p -> p instanceof ServerPlayer
                    && server.getPlayerList().getPlayer(p.getUUID()) != p);
        }
    }

    public static void clear() {
        LAST_SENT.clear();
        COLLISION_STATES.clear();
        FLIGHT.clear();
    }

    public static void clientTick(Player current) {
        synchronized (FLIGHT) {
            FLIGHT.keySet().removeIf(p -> p.level().isClientSide() && p != current);
        }
        if (current != null) movementTick(current);
    }

    public static void apply(ServerPlayer player) {
        State state = state(player);
        applyNoPhysics(player, state.phase());
        LAST_SENT.put(player.getUUID(), state);
        Network.sendStaffPhaseState(player, state.phase(), state.interaction());
    }

    public static State state(Player player) {
        if (player == null) return State.OFF;
        State main = from(player.getMainHandItem());
        if (main.phase()) return main;
        State off = from(player.getOffhandItem());
        return off.phase() ? off : State.OFF;
    }

    public static boolean blocksInteraction(Player player) {
        State state = state(player);
        return state.phase() && !state.interaction();
    }

    /** Called after vanilla resets noPhysics, before movement on both sides. */
    public static void movementTick(Player player) {
        var abilities = player.getAbilities();
        if (state(player).phase() && player.isAlive() && !player.isSpectator()) {
            var flight = FLIGHT.computeIfAbsent(player, p -> {
                var saved = player.getPersistentData();
                if (!player.level().isClientSide() && saved.contains(FLIGHT_SNAPSHOT)) {
                    var tag = saved.getCompound(FLIGHT_SNAPSHOT);
                    return new FlightState(tag.getBoolean("mayfly"), tag.getBoolean("flying"));
                }
                if (!player.level().isClientSide()) {
                    var tag = new net.minecraft.nbt.CompoundTag();
                    tag.putBoolean("mayfly", abilities.mayfly); tag.putBoolean("flying", abilities.flying);
                    saved.put(FLIGHT_SNAPSHOT, tag);
                }
                return new FlightState(abilities.mayfly, abilities.flying);
            });
            if (flight.safeTick != player.level().getGameTime() && player.level().noCollision(player, player.getBoundingBox())) {
                flight.safe = player.position();
                flight.dimension = player.level().dimension();
            }
            flight.safeTick = player.level().getGameTime();
            abilities.mayfly = true;
            abilities.flying = true;
            if (player instanceof ServerPlayer serverPlayer) applyNoPhysics(serverPlayer, true);
            else setNoPhysics(player, true);
            player.setOnGround(false);
            player.fallDistance = 0;
        } else {
            var flight = FLIGHT.remove(player);
            if (flight == null && !player.level().isClientSide() && player.getPersistentData().contains(FLIGHT_SNAPSHOT)) {
                var saved = player.getPersistentData().getCompound(FLIGHT_SNAPSHOT);
                flight = new FlightState(saved.getBoolean("mayfly"), saved.getBoolean("flying"));
            }
            if (flight == null) return;
            player.getPersistentData().remove(FLIGHT_SNAPSHOT);
            if (player instanceof ServerPlayer serverPlayer) applyNoPhysics(serverPlayer, false);
            else setNoPhysics(player, player.isSpectator());
            // Never take flight away from creative/spectator or a newer external grant.
            if (!player.isCreative() && !player.isSpectator()) {
                abilities.mayfly = flight.mayfly;
                abilities.flying = flight.flying && flight.mayfly;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                com.sorrowmist.useless.event.EventHandler.applyBeefToolFlightNow(player);
                if (!player.level().noCollision(player, player.getBoundingBox())
                        && flight.safe != null && player.level().dimension().equals(flight.dimension)
                        && player.level().hasChunkAt(net.minecraft.core.BlockPos.containing(flight.safe))
                        && player.level().noCollision(player, player.getBoundingBox().move(flight.safe.subtract(player.position())))) {
                    serverPlayer.connection.teleport(flight.safe.x, flight.safe.y, flight.safe.z,
                            player.getYRot(), player.getXRot());
                }
                serverPlayer.onUpdateAbilities();
            }
        }
    }

    public static void setNoPhysics(Player player, boolean value) {
        ((EntityNoPhysicsAccessor) (Object) player).uselessStretcher$setNoPhysics(value);
    }

    public static boolean noPhysics(Player player) {
        return ((EntityNoPhysicsAccessor) (Object) player).uselessStretcher$getNoPhysics();
    }

    private static void applyNoPhysics(ServerPlayer player, boolean phase) {
        UUID id = player.getUUID();
        boolean current = noPhysics(player);
        if (phase) {
            CollisionState collision = COLLISION_STATES.get(id);
            if (collision == null) {
                COLLISION_STATES.put(id, new CollisionState(current, true));
            } else if (current != collision.lastApplied()) {
                // Merge an external change as the new post-phase value, then
                // reassert phase for this tick.
                COLLISION_STATES.put(id, new CollisionState(current, true));
            }
            setNoPhysics(player, true);
        } else {
            CollisionState collision = COLLISION_STATES.remove(id);
            if (collision != null && current == collision.lastApplied()) {
                setNoPhysics(player, collision.original());
            }
        }
    }

    private static State from(ItemStack stack) {
        if (!stack.is(ModItems.WONDROUS_STAFF.get())
                || !stack.getOrDefault(StretcherComponents.WONDROUS_STAFF_PHASE_ENABLED.get(), false)) {
            return State.OFF;
        }
        return new State(true, stack.getOrDefault(
                StretcherComponents.WONDROUS_STAFF_PHASE_INTERACTION.get(), true));
    }

    public record State(boolean phase, boolean interaction) {
        private static final State OFF = new State(false, true);
    }

    private record CollisionState(boolean original, boolean lastApplied) { }
    private static final class FlightState {
        final boolean mayfly, flying;
        net.minecraft.world.phys.Vec3 safe;
        long safeTick = Long.MIN_VALUE;
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        FlightState(boolean mayfly, boolean flying) {
            this.mayfly = mayfly; this.flying = flying;
        }
    }
}
