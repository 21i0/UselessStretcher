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

    private StaffPhaseManager() {}

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            State state = state(player);
            applyNoPhysics(player, state.phase());
            State previous = LAST_SENT.put(player.getUUID(), state);
            if (!state.equals(previous)) Network.sendStaffPhaseState(player, state.phase(), state.interaction());
        }
        LAST_SENT.keySet().removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
    }

    public static void clear() {
        LAST_SENT.clear();
        COLLISION_STATES.clear();
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
}
