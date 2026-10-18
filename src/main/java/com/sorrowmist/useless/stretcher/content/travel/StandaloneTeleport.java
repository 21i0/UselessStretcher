package com.sorrowmist.useless.stretcher.content.travel;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.content.entity.TimeStopManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Server-authoritative entry: no held item, client-supplied destination or synthetic staff. */
public final class StandaloneTeleport {
    private static final Map<ServerPlayer, Integer> NEXT_ATTEMPT = new WeakHashMap<>();
    private static final Method NATIVE_BLINK = nativeHelper("tryShortTeleport", Level.class, Player.class);
    private static final Method NATIVE_ANCHOR = nativeHelper("tryAnchorTeleport", Level.class, Player.class, boolean.class);
    private static boolean reportedFailure;
    private StandaloneTeleport() { }

    public static void activate(ServerPlayer player) {
        if (!player.isAlive() || player.isRemoved() || player.isSpectator() || player.isPassenger()
                || TimeStopManager.isPaused(player.serverLevel())
                && !TimeStopManager.mayPlayerTickDuringPause(player)) return;
        int now = player.getServer().getTickCount();
        if (now < NEXT_ATTEMPT.getOrDefault(player, Integer.MIN_VALUE)) return;
        // The base tool also uses a five-tick cooldown; rate-limit failed requests too.
        NEXT_ATTEMPT.put(player, now + 5);
        boolean enderIo = ModList.get().isLoaded("enderio");
        try {
            InteractionResult result = dispatch(player, enderIo, NATIVE_BLINK, NATIVE_ANCHOR);
            if (result == InteractionResult.PASS) player.displayClientMessage(Component.translatable(
                    player.isShiftKeyDown() && !enderIo
                            ? "msg.useless_stretcher.teleport.needs_anchor_mod" : "msg.useless_stretcher.teleport.no_target"), true);
        } catch (ReflectiveOperationException | LinkageError failure) {
            // Do not retry after an uncertain native action: it may have already teleported.
            if (!reportedFailure) {
                reportedFailure = true;
                LogUtils.getLogger().warn("Standalone travel could not invoke the installed travel integration", failure);
            }
            player.displayClientMessage(Component.translatable("msg.useless_stretcher.teleport.unavailable"), true);
        }
    }

    private static InteractionResult dispatch(ServerPlayer player, boolean enderIo, Method blink, Method anchor)
            throws ReflectiveOperationException {
        if (!player.isShiftKeyDown()) {
            // The legacy combined helper branches on the real sneak state in
            // the opposite direction. Its private short-teleport helper is
            // independently accessible, so never fake a player's sneak state.
            return blink != null ? (InteractionResult) blink.invoke(null, player.level(), player)
                    : StandaloneBlink.tryShortTeleport(player.level(), player);
        }
        if (!enderIo) return InteractionResult.PASS;
        if (anchor != null) return (InteractionResult) anchor.invoke(null, player.level(), player, true);
        // Older base versions have no dedicated anchor helper. Call Ender IO's
        // two anchor operations directly, never its sneak-sensitive dispatcher.
        Class<?> handler = Class.forName("com.enderio.enderio.content.travel.TravelHandler");
        boolean success = (boolean) handler.getMethod("blockTeleport", Level.class, Player.class)
                .invoke(null, player.level(), player);
        if (!success) success = (boolean) handler.getMethod("interact", Level.class, Player.class)
                .invoke(null, player.level(), player);
        return success ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    private static Method nativeHelper(String name, Class<?>... parameters) {
        try {
            Method method = Class.forName("com.sorrowmist.useless.content.items.BeefTeleportHandler")
                    .getDeclaredMethod(name, parameters);
            return method.trySetAccessible() ? method : null;
        } catch (ReflectiveOperationException | LinkageError unavailableInOlderBase) {
            return null;
        }
    }
}
