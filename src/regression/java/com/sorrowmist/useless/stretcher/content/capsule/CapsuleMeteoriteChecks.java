package com.sorrowmist.useless.stretcher.content.capsule;

import appeng.core.definitions.AEBlocks;
import appeng.worldgen.meteorite.MeteoriteStructurePiece;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

/** Real AE2 generation and final blocks on both sides of its one-layer metadata box. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class CapsuleMeteoriteChecks {
    private static final UUID OWNER = UUID.fromString("dbe081b5-cd5e-4af0-9ba5-c6eac9e57755");
    private static final String ID = "ae2:meteorite", DIM = DimensionCapsuleCatalog.CAPSULE_OVERWORLD;
    private static ServerPlayer player;
    private static CapsuleInstances.Instance room;
    private static int ticks, stage;
    private static boolean finished;
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if (!Boolean.getBoolean("useless_stretcher.capsule_repair_only")) return;
        try {
            var server = event.getServer();
            checkMoves(server.overworld());
            var profile = new GameProfile(OWNER, "MeteoriteRepair");
            player = new ServerPlayer(server, server.overworld(), profile, net.minecraft.server.level.ClientInformation.createDefault());
            var transport = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
                @Override public void setListenerForServerboundHandshake(net.minecraft.network.PacketListener listener) { }
            };
            new io.netty.channel.embedded.EmbeddedChannel(transport);
            player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server, transport, player,
                    net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
                @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) { }
            };
            NativeCapsules.request(player, ID, DIM, 1, 0);
            room = CapsuleInstances.get(server).rooms.get(CapsuleInstances.key(OWNER, ID, DIM));
            check(room != null, "real meteorite job accepted");
        } catch (Throwable failure) { fail(event.getServer(), failure); }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("useless_stretcher.capsule_repair_only") || finished) return;
        try {
            check(++ticks < 18000, "meteorite generation timeout");
            check(room != null && room.error.isEmpty(), "meteorite error: " + (room == null ? "missing" : room.error));
            if (room.plan.isEmpty()) return;
            var start = StructureStart.loadStaticStart(StructurePieceSerializationContext.fromLevel(player.serverLevel()), room.plan, room.sourceSeed);
            check(start != null && start.isValid(), "meteorite plan decodes");
            var piece = (MeteoriteStructurePiece)start.getPieces().getFirst();
            BlockPos center = piece.getSettings().getPos();
            check(piece.getBoundingBox().isInside(center), "relocated meteorite center follows its piece: " + center + " versus " + piece.getBoundingBox());
            check(room.minY <= center.getY() - 8 && room.maxY >= center.getY() + 7,
                    "meteorite envelope encloses both halves and keeps bedrock below the body: " + room.minY + ".." + room.maxY);
            if (!room.ready) return;
            ServerLevel level = event.getServer().getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(DIM)));
            int upper = 0, lower = 0, cubes = 0, quartz = 0;
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) for (int y = -8; y <= 8; y++) {
                BlockPos pos = center.offset(x, y, z); var state = level.getBlockState(pos);
                digest.update(state.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if (state.is(AEBlocks.SKY_STONE_BLOCK.block())) { if (y > 0) upper++; else if (y < 0) lower++; }
                if (state.is(AEBlocks.MYSTERIOUS_CUBE.block())) cubes++;
                var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
                if (id.getNamespace().equals("ae2") && id.getPath().contains("quartz")) quartz++;
                check(!state.is(Blocks.BEDROCK), "bedrock does not cut through the meteorite");
            }
            check(upper > 30 && lower > 30 && cubes == 1 && quartz >= 1,
                    "complete real AE2 body and native contents: upper=" + upper + " lower=" + lower + " cubes=" + cubes + " quartz=" + quartz);
            check(level.getBlockState(new BlockPos(center.getX(), room.minY - 1, center.getZ())).is(Blocks.BEDROCK), "bedrock supports the complete body");
            check(room.geometryVersion == CapsulePieceGeometry.VERSION, "fixed geometry version is persisted");
            check(room.save().getInt("geometry_version") == room.geometryVersion, "geometry marker is included in SavedData");
            LogUtils.getLogger().info("CAPSULE METEORITE CONTENT PASSED instance={} upper={} lower={} cubes={} quartz={} center={} envelope={}..{} ticks={} body_sha256={} plan_hash={}",
                    room.instanceId, upper, lower, cubes, quartz, center, room.minY, room.maxY, ticks,
                    java.util.HexFormat.of().formatHex(digest.digest()), room.plan.hashCode());
            if (stage++ == 0) {
                NativeCapsules.request(player, ID, DIM, 3, 0, 0);
                room = CapsuleInstances.get(event.getServer()).rooms.get(CapsuleInstances.key(OWNER, ID, DIM, 1));
                check(room != null, "adjacent meteorite is allocated");
                return;
            }
            LogUtils.getLogger().info("CAPSULE METEORITE CHECKS PASSED: live source placement, complete upper/lower bodies, relocated centers, native cube/quartz, adjacent instance and support layer");
            finished = true; event.getServer().halt(false);
        } catch (Throwable failure) { fail(event.getServer(), failure); }
    }
    private static void fail(net.minecraft.server.MinecraftServer server, Throwable failure) {
        finished = true; LogUtils.getLogger().error("CAPSULE METEORITE CHECKS FAILED", failure); server.halt(false);
    }
    private static void checkMoves(ServerLevel level) {
        var tag = new CompoundTag();
        BlockPos center = new BlockPos(-24, -62, 19);
        tag.putIntArray("BB", new int[]{-64,-62,-64,79,-62,79});
        tag.putLong("c", center.asLong()); tag.putFloat("r", 6);
        tag.putByte("t", (byte)appeng.worldgen.meteorite.CraterType.NONE.ordinal());
        tag.putByte("f", (byte)appeng.worldgen.meteorite.fallout.FalloutMode.NONE.ordinal());
        var piece = new MeteoriteStructurePiece(tag);
        var context = StructurePieceSerializationContext.fromLevel(level);
        var moved = (MeteoriteStructurePiece)CapsulePieceGeometry.move(piece,31,20,-45,context);
        check(moved.getSettings().getPos().equals(center.offset(31,20,-45)), "all three axes move the absolute AE2 center");
        check(moved.getBoundingBox().minY() == -50 && moved.getBoundingBox().maxY() == -34, "vertical lift preserves the full body");
        var restored = new MeteoriteStructurePiece(moved.createTag(context));
        check(restored.getSettings().getPos().equals(moved.getSettings().getPos())
                && restored.getBoundingBox().equals(moved.getBoundingBox()), "AE2 settings and full envelope survive NBT");
        LogUtils.getLogger().info("CAPSULE METEORITE COORDINATES PASSED: negative coordinates, three-axis relocation, vertical clearance and native NBT round trip");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
