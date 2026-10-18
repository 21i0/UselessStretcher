package com.sorrowmist.useless.stretcher.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.content.travel.StandaloneTeleport;
import com.sorrowmist.useless.stretcher.network.Network;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stat;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Real ServerPlayer transport checks. FakePlayer's teleport overrides would hide movement failures. */
public final class StandaloneTeleportChecks {
    private static final BlockPos ORIGIN = new BlockPos(240, 200, 240);
    private static final Vec3 START = Vec3.atBottomCenterOf(ORIGIN);

    private StandaloneTeleportChecks() { }

    public static void run(ServerLevel level) throws Exception {
        Method fallback = Class.forName("com.sorrowmist.useless.stretcher.content.travel.StandaloneBlink")
                .getDeclaredMethod("tryShortTeleport", Level.class, Player.class);
        fallback.setAccessible(true);
        var cooldownField = StandaloneTeleport.class.getDeclaredField("NEXT_ATTEMPT");
        cooldownField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<ServerPlayer, Integer> cooldowns = (Map<ServerPlayer, Integer>) cooldownField.get(null);
        List<RecordingPlayer> players = new ArrayList<>();
        Map<BlockPos, BlockState> fixture = new LinkedHashMap<>();
        try {
            prepareFixture(level, fixture);
            checkPacket(level);

            RecordingPlayer emptyHand = player(level, players, "TravelEmptyHand", false);
            check(emptyHand.getMainHandItem().isEmpty() && emptyHand.getOffhandItem().isEmpty(), "empty hands fixture");
            emptyHand.fallDistance = 12.0F;
            StandaloneTeleport.activate(emptyHand);
            check(emptyHand.position().distanceToSqr(START) > 4.0D, "ordinary standalone action blinks without any staff");
            check(!emptyHand.isShiftKeyDown() && emptyHand.shiftWrites == 0, "blink never synthesizes a Shift state");
            check(emptyHand.getMainHandItem().isEmpty() && emptyHand.getOffhandItem().isEmpty(),
                    "activation does not insert a synthetic staff");
            check(emptyHand.packets.stream().anyMatch(ClientboundPlayerPositionPacket.class::isInstance),
                    "native ServerPlayer teleport sends a position packet");
            check(emptyHand.fallDistance == 0.0F, "successful blink resets fall distance");
            check(cooldowns.get(emptyHand) == level.getServer().getTickCount() + 5,
                    "every accepted activation reserves exactly five ticks");
            reset(emptyHand, false);
            int packetsBeforeRetry = emptyHand.packets.size();
            StandaloneTeleport.activate(emptyHand);
            check(emptyHand.position().equals(START) && emptyHand.packets.size() == packetsBeforeRetry,
                    "same-tick repeat does not blink or send a second packet");
            cooldowns.put(emptyHand, level.getServer().getTickCount() + 1);
            StandaloneTeleport.activate(emptyHand);
            check(emptyHand.position().equals(START), "last blocked tick remains rate-limited");
            cooldowns.put(emptyHand, level.getServer().getTickCount());
            StandaloneTeleport.activate(emptyHand);
            check(emptyHand.position().distanceToSqr(START) > 4.0D, "exact cooldown boundary permits next activation");

            if (!ModList.get().isLoaded("enderio")) {
                RecordingPlayer noAnchor = player(level, players, "TravelNoAnchor", true);
                StandaloneTeleport.activate(noAnchor);
                check(noAnchor.position().equals(START), "Shift action without Ender IO does not blink");
                check(noAnchor.isShiftKeyDown() && noAnchor.shiftWrites == 0, "failed anchor request preserves Shift");
                check(noAnchor.packets.stream().noneMatch(ClientboundPlayerPositionPacket.class::isInstance),
                        "no-anchor action sends no teleport packet");
                noAnchor.setShiftKeyDown(false);
                StandaloneTeleport.activate(noAnchor);
                check(noAnchor.position().equals(START), "failed anchor request also rate-limits immediate blink retry");
            } else {
                checkEnderIoAnchor(level, players);
            }

            RecordingPlayer canceled = player(level, players, "TravelCanceled", false);
            int[] canceledEvents = {0};
            Consumer<EntityTeleportEvent> cancel = event -> {
                if (event.getEntity() == canceled) {
                    check(!canceled.isShiftKeyDown(), "teleport listeners observe the player's real non-Shift state");
                    canceledEvents[0]++;
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(cancel);
            try {
                StandaloneTeleport.activate(canceled);
                check(canceledEvents[0] == 1 && canceled.position().equals(START),
                        "standalone activation honors teleport event cancellation");
                check(canceled.packets.stream().noneMatch(ClientboundPlayerPositionPacket.class::isInstance),
                        "canceled activation sends no teleport packet");
                StandaloneTeleport.activate(canceled);
                check(canceledEvents[0] == 1, "canceled attempts are rate-limited too");
                InteractionResult result = (InteractionResult) fallback.invoke(null, level, canceled);
                check(result == InteractionResult.SUCCESS && canceledEvents[0] == 2
                                && canceled.position().equals(START),
                        "legacy fallback consumes canceled interaction without moving player");
            } finally {
                NeoForge.EVENT_BUS.unregister(cancel);
            }

            checkNativeParity(level, players, fallback);
            checkLegacyDispatch(level, players);
            LogUtils.getLogger().info("REGRESSION standalone travel: empty hands, ordinary blink, Shift anchor, unchanged sneak state, legacy split fallback, event cancellation, five-tick limits and zero-coordinate payload passed");
        } finally {
            for (RecordingPlayer player : players) cooldowns.remove(player);
            for (Map.Entry<BlockPos, BlockState> entry : fixture.entrySet())
                level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
    }

    private static void checkPacket(ServerLevel level) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            var packet = new Network.StandaloneTeleportPayload();
            Network.StandaloneTeleportPayload.STREAM_CODEC.encode(buffer, packet);
            check(buffer.readableBytes() == 0, "request encodes neither client coordinates nor held-item state");
            check(packet.equals(Network.StandaloneTeleportPayload.STREAM_CODEC.decode(buffer)), "request payload roundtrip");
            check(packet.type().equals(Network.StandaloneTeleportPayload.TYPE), "request has standalone action type");
        } finally {
            buffer.release();
        }
    }

    private static void checkEnderIoAnchor(ServerLevel level, List<RecordingPlayer> players) throws Exception {
        BlockPos pos = ORIGIN.offset(0, -1, 12);
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .get(net.minecraft.resources.ResourceLocation.parse("enderio:travel_anchor"));
        check(block != Blocks.AIR, "actual Ender IO travel anchor registered");
        level.setBlock(pos, block.defaultBlockState(), 3);
        Class<?> targetType = Class.forName("com.enderio.enderio.api.travel.TravelTarget");
        Class<?> apiType = Class.forName("com.enderio.enderio.api.travel.TravelTargetApi");
        Object api = apiType.getField("INSTANCE").get(null);
        Object target = Class.forName("com.enderio.enderio.content.travel.travel_anchor.AnchorTravelTarget")
                .getConstructor(BlockPos.class, String.class, net.minecraft.world.item.Item.class, boolean.class)
                .newInstance(pos, "Regression anchor", net.minecraft.world.item.Items.ENDER_PEARL, true);
        try {
            apiType.getMethod("set", Level.class, targetType).invoke(api, level, target);
            RecordingPlayer player = player(level, players, "TravelRealAnchor", true);
            StandaloneTeleport.activate(player);
            check(player.position().distanceToSqr(Vec3.atBottomCenterOf(pos.above())) < 0.01D,
                    "Shift independent key uses actual EIO anchor while empty-handed");
            check(player.isShiftKeyDown() && player.shiftWrites == 0, "anchor never releases or reapplies Shift");
            check(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty(), "anchor requires no temporary item");
            // Simulate the legacy helper set: a short helper but no dedicated
            // anchor helper. Exercise real EIO fallback with the same true Shift.
            reset(player, true);
            dispatchWithoutNativeAnchor(player, true);
            check(player.position().distanceToSqr(Vec3.atBottomCenterOf(pos.above())) < 0.01D
                            && player.isShiftKeyDown() && player.shiftWrites == 0,
                    "legacy base uses the actual EIO anchor without changing Shift");
            LogUtils.getLogger().info("REGRESSION actual EIO: empty-handed Shift key and legacy fallback teleported to registered anchor");
        } finally {
            apiType.getMethod("removeAt", Level.class, BlockPos.class).invoke(api, level, pos);
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        }
    }

    private static void checkNativeParity(ServerLevel level, List<RecordingPlayer> players, Method fallback)
            throws Exception {
        Method nativeHelper;
        try {
            Class<?> nativeType = Class.forName("com.sorrowmist.useless.content.items.BeefTeleportHandler");
            // Both the new split API and the old combined API have this helper;
            // it is private in the legacy version and intentionally invoked directly.
            nativeHelper = nativeType.getDeclaredMethod("tryShortTeleport", Level.class, Player.class);
            nativeHelper.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchMethodException absentOnLegacyBase) {
            LogUtils.getLogger().info("REGRESSION standalone travel: native helper absent; fallback-only base version");
            return;
        }
        RecordingPlayer nativePlayer = player(level, players, "TravelNative", false);
        RecordingPlayer fallbackPlayer = player(level, players, "TravelFallback", false);
        for (int scenario = 0; scenario < 4; scenario++) {
            clearObstacles(level);
            if (scenario == 1 || scenario == 2) {
                int distance = scenario == 1 ? 2 : 9;
                for (int x = -1; x <= 1; x++) for (int y = 0; y < 3; y++)
                    level.setBlock(ORIGIN.offset(x, y, distance), Blocks.STONE.defaultBlockState(), 3);
            } else if (scenario == 3) {
                level.setBlock(ORIGIN.offset(0, 0, 8), Blocks.STONE_SLAB.defaultBlockState(), 3);
            }
            reset(nativePlayer, false);
            reset(fallbackPlayer, false);
            if (scenario == 3) {
                nativePlayer.setXRot(10.0F);
                fallbackPlayer.setXRot(10.0F);
            }
            Object nativeResult = nativeHelper.invoke(null, level, nativePlayer);
            Object fallbackResult = fallback.invoke(null, level, fallbackPlayer);
            check(nativeResult.equals(fallbackResult), "native/fallback result agrees for fixture " + scenario);
            check(nativePlayer.position().distanceToSqr(fallbackPlayer.position()) < 1.0E-10D,
                    "native/fallback destination agrees for fixture " + scenario);
        }
        clearObstacles(level);
        LogUtils.getLogger().info("REGRESSION standalone travel: native and legacy fallback match in open space, near wall, far wall and slab fixtures");
    }

    private static void checkLegacyDispatch(ServerLevel level, List<RecordingPlayer> players) throws Exception {
        RecordingPlayer legacy = player(level, players, "TravelLegacyPath", false);
        InteractionResult blink = dispatchWithoutNativeAnchor(legacy, ModList.get().isLoaded("enderio"));
        check(blink == InteractionResult.SUCCESS && legacy.position().distanceToSqr(START) > 4
                        && !legacy.isShiftKeyDown() && legacy.shiftWrites == 0,
                "legacy short helper is independent of anchor API and uses real ordinary state");
        reset(legacy, true);
        InteractionResult unavailable = dispatchWithoutNativeAnchor(legacy, false);
        check(unavailable == InteractionResult.PASS && legacy.position().equals(START)
                        && legacy.isShiftKeyDown() && legacy.shiftWrites == 0,
                "legacy Shift path without EIO never falls through to blink");
        reset(legacy, false);
        Method dispatch = standaloneDispatch();
        InteractionResult fallback = (InteractionResult) dispatch.invoke(null, legacy, false, null, null);
        check(fallback == InteractionResult.SUCCESS && legacy.position().distanceToSqr(START) > 4
                        && !legacy.isShiftKeyDown() && legacy.shiftWrites == 0,
                "no-native-helper base also uses ordinary key for fallback blink");
    }

    private static InteractionResult dispatchWithoutNativeAnchor(RecordingPlayer player, boolean enderIo) throws Exception {
        Method blink;
        try {
            blink = Class.forName("com.sorrowmist.useless.content.items.BeefTeleportHandler")
                    .getDeclaredMethod("tryShortTeleport", Level.class, Player.class);
            blink.setAccessible(true);
        } catch (ReflectiveOperationException absent) {
            blink = null;
        }
        return (InteractionResult) standaloneDispatch().invoke(null, player, enderIo, blink, null);
    }

    private static Method standaloneDispatch() throws ReflectiveOperationException {
        Method dispatch = StandaloneTeleport.class.getDeclaredMethod("dispatch", ServerPlayer.class,
                boolean.class, Method.class, Method.class);
        dispatch.setAccessible(true);
        return dispatch;
    }

    private static void prepareFixture(ServerLevel level, Map<BlockPos, BlockState> original) {
        for (BlockPos cursor : BlockPos.betweenClosed(ORIGIN.offset(-2, -1, -2), ORIGIN.offset(2, 4, 27))) {
            BlockPos pos = cursor.immutable();
            original.put(pos, level.getBlockState(pos));
            level.setBlock(pos, pos.getY() == ORIGIN.getY() - 1
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void clearObstacles(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed(ORIGIN.offset(-1, 0, 1), ORIGIN.offset(1, 3, 11)))
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    private static RecordingPlayer player(ServerLevel level, List<RecordingPlayer> players, String name, boolean crouching) {
        var player = new RecordingPlayer(level, new GameProfile(UUID.randomUUID(), name));
        players.add(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        reset(player, crouching);
        return player;
    }

    private static void reset(RecordingPlayer player, boolean crouching) {
        player.moveTo(START.x, START.y, START.z, 0.0F, 0.0F);
        player.setShiftKeyDown(crouching);
        player.shiftWrites = 0;
        player.connection.resetPosition();
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError("Standalone travel: " + message);
    }

    private static final class RecordingPlayer extends ServerPlayer {
        private final List<Packet<?>> packets = new ArrayList<>();
        private int shiftWrites;

        private RecordingPlayer(ServerLevel level, GameProfile profile) {
            super(level.getServer(), level, profile, ClientInformation.createDefault());
            Connection transport = new Connection(PacketFlow.SERVERBOUND) {
                @Override public void setListenerForServerboundHandshake(PacketListener listener) { }
            };
            connection = new ServerGamePacketListenerImpl(level.getServer(), transport, this,
                    CommonListenerCookie.createInitial(profile, false)) {
                @Override public void send(Packet<?> packet) { packets.add(packet); }
                @Override public void send(Packet<?> packet, PacketSendListener listener) { packets.add(packet); }
            };
        }

        @Override public void awardStat(Stat<?> stat, int amount) {
            getStats().increment(this, stat, amount);
        }

        @Override public void setShiftKeyDown(boolean crouching) {
            shiftWrites++;
            super.setShiftKeyDown(crouching);
        }
    }
}
