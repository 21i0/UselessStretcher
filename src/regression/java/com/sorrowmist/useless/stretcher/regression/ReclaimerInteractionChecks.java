package com.sorrowmist.useless.stretcher.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.content.entity.TimeFlowEntity;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Exercise air use plus targeted events and verify the actual screen-state packet is sent. */
final class ReclaimerInteractionChecks {
    static void run(ServerLevel level) {
        boolean enabled = StretcherConfig.serverRemoteReclaimer();
        var packets = new ArrayList<Network.ReclaimerStatePayload>();
        var notices = new ArrayList<Component>();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ReclaimerUiTest")) {
            @Override public void displayClientMessage(Component message, boolean actionBar) { notices.add(message); }
        };
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND) {
            @Override public void setListenerForServerboundHandshake(PacketListener listener) {}
        }, player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof Network.ReclaimerStatePayload state) packets.add(state);
            }
        };
        var ranges = RangeAccelerationSavedData.get(level.getServer());
        UUID rangeId = null;
        try {
            StretcherConfig.SERVER_REMOTE_RECLAIMER.set(true);
            ItemStack tool = new ItemStack(ModItems.RANGE_RECLAIMER.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, tool);
            player.setShiftKeyDown(false);
            check(tool.use(level, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.PASS
                    && packets.isEmpty(), "ordinary air right-click does not open or reclaim");
            player.setShiftKeyDown(true);
            check(tool.use(level, player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.SUCCESS,
                    "Shift + item-use opens UI when pointing at air");
            check(packets.size() == 1 && !packets.getFirst().personal(), "air use sends exactly one management screen payload");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, tool);
            tool.use(level, player, InteractionHand.OFF_HAND);
            check(packets.size() == 2, "offhand air use also opens UI");
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.MAIN_HAND, tool);

            BlockPos pos = new BlockPos(6, 240, 6);
            var blockClick = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            blockClick.setCanceled(true);
            blockClick.setCancellationResult(InteractionResult.FAIL);
            NeoForge.EVENT_BUS.post(blockClick);
            check(blockClick.isCanceled() && blockClick.getCancellationResult() == InteractionResult.SUCCESS
                    && packets.size() == 3, "targeted block click opens same UI and consumes machine use");

            var owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ReclaimerOther"));
            var placed = ranges.place(level, owner, pos, new ItemStack(ModItems.WONDROUS_STAFF.get()));
            rangeId = placed.id();
            ranges.tick(level.getServer());
            var marker = (TimeFlowEntity) level.getEntity(rangeId);
            check(marker != null, "time-flow target exists");
            var specific = new PlayerInteractEvent.EntityInteractSpecific(player, InteractionHand.MAIN_HAND, marker, Vec3.ZERO);
            NeoForge.EVENT_BUS.post(specific);
            check(specific.isCanceled() && specific.getCancellationResult() == InteractionResult.SUCCESS
                    && packets.size() == 4, "specific entity click opens UI only once");
            var general = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, marker);
            NeoForge.EVENT_BUS.post(general);
            check(general.isCanceled() && general.getCancellationResult() == InteractionResult.SUCCESS
                    && packets.size() == 5, "general entity interaction also opens UI");
            check(packets.getLast().entries().stream().anyMatch(entry -> entry.id().equals(marker.getUUID())),
                    "UI includes another player's range without OP requirement");
            check(!marker.isRemoved() && ranges.getField(rangeId) != null, "opening UI never deletes target range");
            player.setShiftKeyDown(false);
            var plain = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, marker);
            NeoForge.EVENT_BUS.post(plain);
            check(!plain.isCanceled() && marker.interact(player, InteractionHand.MAIN_HAND) == InteractionResult.PASS
                    && !marker.isRemoved() && packets.size() == 5, "old direct-reclaim interaction is removed");

            StretcherConfig.SERVER_REMOTE_RECLAIMER.set(false);
            player.setShiftKeyDown(true);
            tool.use(level, player, InteractionHand.MAIN_HAND);
            check(packets.size() == 5 && !notices.isEmpty(), "disabled server setting gives feedback without opening UI");
            check(ranges.getField(rangeId) != null, "disabled tool cannot reclaim");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            var other = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, marker);
            NeoForge.EVENT_BUS.post(other);
            check(!other.isCanceled() && packets.size() == 5, "unrelated items remain untouched");
            LogUtils.getLogger().info("REGRESSION reclaimer: main/offhand air, block, entity, packet delivery, no direct deletion and disabled-config feedback passed");
        } finally {
            StretcherConfig.SERVER_REMOTE_RECLAIMER.set(enabled);
            if (rangeId != null) ranges.reclaimByOperator(level.getServer(), rangeId);
        }
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
