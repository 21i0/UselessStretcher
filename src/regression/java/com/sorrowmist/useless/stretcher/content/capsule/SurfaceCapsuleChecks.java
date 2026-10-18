package com.sorrowmist.useless.stretcher.content.capsule;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import java.util.UUID;

/** Actual native surface pieces, basement loot and persistent background jobs. */
public final class SurfaceCapsuleChecks {
    private static final String[] IDS = {"minecraft:desert_pyramid", "minecraft:jungle_pyramid", "minecraft:swamp_hut"};
    private static ServerPlayer player;
    private static CapsuleInstances.Instance room;
    private static int index, ticks;
    private static boolean resumed;
    private static int readyPackets;
    public static void start(MinecraftServer server) {
        var profile = new GameProfile(UUID.randomUUID(), "SurfaceCapsule");
        player = new ServerPlayer(server, server.overworld(), profile, net.minecraft.server.level.ClientInformation.createDefault());
        var transport = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
            @Override public void setListenerForServerboundHandshake(net.minecraft.network.PacketListener listener) { }
        };
        new io.netty.channel.embedded.EmbeddedChannel(transport);
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server, transport, player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket payload
                        && payload.payload() instanceof com.sorrowmist.useless.stretcher.network.CapsuleNetwork.Complete) readyPackets++;
            }
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) { }
        };
        request(server);
    }
    private static void request(MinecraftServer server) {
        NativeCapsules.request(player, IDS[index], DimensionCapsuleCatalog.CAPSULE_OVERWORLD, 1, 0);
        room = CapsuleInstances.get(server).rooms.get(CapsuleInstances.key(player.getUUID(), IDS[index], DimensionCapsuleCatalog.CAPSULE_OVERWORLD));
        check(room != null && room.generating, "accepted native job remains active without a screen");
        resumed = false;
    }
    public static boolean tick(MinecraftServer server) throws Exception {
        check(++ticks < 18000, "surface generation timeout");
        if (room == null) return false;
        check(room.error.isEmpty(), "surface generation error: " + IDS[index] + " " + room.error);
        if (!resumed && room.phase.equals("placing") && room.piece > 0) {
            var saved = CapsuleInstances.get(server).save(new CompoundTag(), server.registryAccess());
            var load = CapsuleInstances.class.getDeclaredMethod("load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
            load.setAccessible(true);
            var restored = (CapsuleInstances) load.invoke(null, saved, server.registryAccess());
            check(restored.rooms.get(room.key()).generating, "accepted job survives SavedData serialization");
            var before = room.plan.copy();
            NativeCapsules.stopped(null);
            NativeCapsules.started(new net.neoforged.neoforge.event.server.ServerStartedEvent(server));
            check(room.plan.equals(before), "background resume retains exact native plan");
            resumed = true;
        }
        if (!room.ready) return false;
        check(resumed && !room.generating && !room.completionNotified, "offline owner has one pending ready notification");
        int beforePackets=readyPackets;
        NativeCapsules.notifyReady(CapsuleInstances.get(server),room,player);
        NativeCapsules.notifyReady(CapsuleInstances.get(server),room,player);
        check(room.completionNotified && readyPackets==beforePackets+1,"login completion sends exactly one ready payload");
        var target = server.getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(room.dimension)));
        int chests=0, loot=0, archaeology=0, tnt=0, dispensers=0, blocks=0;
        for (int x=room.minX; x<=room.maxX; x++) for (int z=room.minZ; z<=room.maxZ; z++) {
            check(target.getBlockState(new BlockPos(x,room.minY-1,z)).is(Blocks.BEDROCK), "complete foundation below basement");
            for (int y=room.minY; y<=room.maxY; y++) {
                var pos=new BlockPos(x,y,z); var state=target.getBlockState(pos);
                if (!state.isAir()) blocks++;
                if (state.is(Blocks.TNT)) tnt++;
                if (state.is(Blocks.DISPENSER)) dispensers++;
                if (state.is(Blocks.CHEST)) chests++;
                var be=target.getBlockEntity(pos);
                if (be instanceof RandomizableContainerBlockEntity container && container.getLootTable()!=null) loot++;
                if (be instanceof BrushableBlockEntity brush && brush.saveWithFullMetadata(target.registryAccess()).contains("LootTable")) archaeology++;
            }
        }
        if (index==0) check(chests==4 && loot>=4 && tnt==9 && archaeology>=5,
                "desert native basement contains four loot chests, nine TNT and archaeology: "+chests+"/"+loot+"/"+tnt+"/"+archaeology);
        if (index==1) check(chests==2 && dispensers==2 && loot>=4, "jungle native traps and loot survive cleanup");
        check(blocks>100, "real structure exists, beyond the support floor");
        var saved=room.save();
        check(saved.getBoolean("completion_notified"),"delivered completion persists across restart");
        saved.remove("completion_notified"); saved.putBoolean("ready",true);
        var all = new CompoundTag(); var list = new net.minecraft.nbt.ListTag(); list.add(saved); all.put("rooms",list);
        var load=CapsuleInstances.class.getDeclaredMethod("load",CompoundTag.class,net.minecraft.core.HolderLookup.Provider.class); load.setAccessible(true);
        check(((CapsuleInstances)load.invoke(null,all,server.registryAccess())).rooms.get(room.key()).completionNotified,
                "legacy completed rooms never send retroactive notifications");
        LogUtils.getLogger().info("SURFACE NATIVE CONTENT PASSED {} blocks={} chests={} loot={} TNT={} archaeology={} bounds={}..{} resume={}",
                IDS[index],blocks,chests,loot,tnt,archaeology,room.minY,room.maxY,resumed);
        if (++index==IDS.length) return true;
        request(server); return false;
    }
    private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}
