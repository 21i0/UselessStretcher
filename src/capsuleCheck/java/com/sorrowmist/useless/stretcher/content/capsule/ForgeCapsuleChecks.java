package com.sorrowmist.useless.stretcher.content.capsule;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

/** Native callbacks and persistent rooms, executed inside a real dedicated Forge server. */
@Mod.EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class ForgeCapsuleChecks {
    private static final UUID OWNER = UUID.fromString("75af2910-299a-436d-91ee-271b729e8e05");
    private static final String[] IDS = {"minecraft:desert_pyramid", "minecraft:jungle_pyramid",
            "minecraft:swamp_hut", "minecraft:pillager_outpost", "minecraft:fortress",
            "minecraft:bastion_remnant", "minecraft:trail_ruins", "minecraft:end_city",
            "minecraft:desert_pyramid", "minecraft:fortress"};
    private static String mode;
    private static ServerPlayer player;
    private static CapsuleInstances.Instance room;
    private static int index, ticks, journey, beforeProgress;
    private static BlockPos original;
    private static CompoundTag parentPlan;
    private static boolean finished;

    @SubscribeEvent public static void start(ServerStartedEvent event) {
        mode = System.getProperty("stretcher.capsule.check");
        if (mode == null) return;
        try {
            MinecraftServer server = event.getServer();
            CapsuleDimensions.ensure(server);
            for (ServerLevel source : server.getAllLevels()) {
                if (CapsuleDimensions.isCapsule(source.dimension().location())) continue;
                ServerLevel mirror = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                        new ResourceLocation(CapsuleDimensions.targetFor(source.dimension().location()))));
                check(mirror != null && mirror.getChunkSource().getGenerator() instanceof CapsuleVoidChunkGenerator,
                        "void mirror exists for " + source.dimension().location());
                check(mirror.dimensionType() == source.dimensionType() && mirror.getHeight() == source.getHeight(),
                        "mirror retains source environment");
            }
            player = new ServerPlayer(server, server.overworld(), new GameProfile(OWNER, "ForgeCapsule"));
            Connection transport = new Connection(PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(transport);
            player.connection = new ServerGamePacketListenerImpl(server, transport, player) {
                @Override public void send(Packet<?> packet) { }
                @Override public void send(Packet<?> packet, PacketSendListener listener) { }
            };
            original = new BlockPos(16, 100, 16);
            player.setPos(original.getX() + .5, original.getY(), original.getZ() + .5);
            check(DimensionCapsuleCatalog.scan(player).size() >= IDS.length, "live registry catalogue");
            if (!mode.equals("checkpoint") && !mode.equals("floorcheckpoint")) {
                var saved = CapsuleInstances.get(server).rooms.get(CapsuleInstances.key(OWNER, IDS[0], dimension(0)));
                check(saved != null && !saved.plan.isEmpty(), "actual previous-process plan is loaded");
                check(mode.equals("verify") ? saved.ready : saved.ready || saved.generating,
                        "previous-process job state survives");
                LogUtils.getLogger().info("FORGE CAPSULE RESTART LOADED phase={} chunk={} piece={} ready={}",
                        saved.phase, saved.chunk, saved.piece, saved.ready);
            }
            request(server);
        } catch (Throwable failure) { fail(event.getServer(), failure); }
    }
    private static String dimension(int i) {
        if (i == 9) return "useless_stretcher:capsule_mirror/stretcher_capsule_check/custom_nether";
        return i == 4 || i == 5 ? DimensionCapsuleCatalog.CAPSULE_NETHER
                : i == 7 ? DimensionCapsuleCatalog.CAPSULE_END : DimensionCapsuleCatalog.CAPSULE_OVERWORLD;
    }
    private static int instance() { return index == 8 ? 1 : 0; }
    private static void request(MinecraftServer server) {
        beforeProgress = 0;
        if (index == 8) parentPlan = CapsuleInstances.get(server).rooms.get(
                CapsuleInstances.key(OWNER, IDS[index], dimension(index))).plan.copy();
        if (index == 8 && !mode.equals("verify")) {
            NativeCapsules.request(player, IDS[index], dimension(index), 3, 0, 0);
            NativeCapsules.request(player, IDS[index], dimension(index), 3, 0, 0);
        } else NativeCapsules.request(player, IDS[index], dimension(index), mode.equals("verify") ? 0 : 1, 0, instance());
        room = CapsuleInstances.get(server).rooms.get(CapsuleInstances.key(OWNER, IDS[index], dimension(index), instance()));
        check(room != null, "request allocated/loaded " + IDS[index]);
        if (index == 7) {
            var source = server.getLevel(net.minecraft.world.level.Level.END);
            int height = source.getChunkSource().getGenerator().getBaseHeight(room.x(), room.z(),
                    net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, source,
                    source.getChunkSource().randomState());
            LogUtils.getLogger().info("FORGE CAPSULE END SOURCE DIAGNOSTIC storage=({}, {}) sourceHeightAtStorage={}", room.x(), room.z(), height);
        }
        if (mode.equals("verify")) check(room.ready, "completed room is stable after process restart");
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (mode == null || finished || event.phase != TickEvent.Phase.END || room == null) return;
        MinecraftServer server = event.getServer();
        try {
            check(++ticks < 42000, "native generation timed out: " + IDS[index] + " " + room.phase);
            check(room.error.isEmpty(), "native failure: " + IDS[index] + " " + room.error);
            if (ticks % 200 == 0) LogUtils.getLogger().info("FORGE CAPSULE HARNESS {} phase={} chunk={} piece={} progress={}",
                    IDS[index], room.phase, room.chunk, room.piece, room.progress);
            check(room.progress >= beforeProgress && room.progress <= 100, "monotonic bounded progress");
            beforeProgress = room.progress;
            if (mode.equals("checkpoint") && room.phase.equals("placing") && room.piece > 0
                    || mode.equals("floorcheckpoint") && room.phase.equals("bedrock") && room.bedrockChunk > 0) {
                check(room.generating && !room.plan.isEmpty(), "persist accepted partially placed job");
                server.saveEverything(false, true, true);
                LogUtils.getLogger().info("FORGE CAPSULE CHECKPOINT PASSED: stop actual process mode={} phase={} chunk={} piece={} floorChunk={}", mode, room.phase, room.chunk, room.piece, room.bedrockChunk);
                finished = true; server.halt(false); return;
            }
            if (!room.ready) return;
            if (journey == 0) {
                validate(server);
                check(!DimensionCapsuleService.hasReturnPoint(player), "clean return state before entry");
                NativeCapsules.request(player, IDS[index], dimension(index), 2, 0, instance());
                journey = 1; return;
            }
            if (journey == 1 || journey == 3) {
                if (!DimensionCapsuleService.hasReturnPoint(player)) return;
                check(player.serverLevel().dimension().location().toString().equals(dimension(index)), "entered mirror");
                DimensionCapsuleService.enter(player, new net.minecraft.world.item.ItemStack(
                        com.sorrowmist.useless.stretcher.init.ModItems.DIMENSION_CAPSULE.get()));
                check(player.serverLevel() == server.overworld() && !DimensionCapsuleService.hasReturnPoint(player)
                        && player.blockPosition().equals(original), "precise return and cleared return token");
                if (journey == 1) {
                    NativeCapsules.request(player, IDS[index], dimension(index), 2, 1, instance());
                    journey = 3; return;
                }
                LogUtils.getLogger().info("FORGE CAPSULE ROUND TRIPS PASSED {}", IDS[index]);
                journey = 0;
                if (++index < (mode.startsWith("floor") ? 1 : IDS.length)) { request(server); return; }
                server.saveEverything(false, true, true);
                LogUtils.getLogger().info("FORGE CAPSULE {} CHECKS PASSED: {} native structures, loot, archaeology, environment, maps and twice-per-room round trips; {} ticks", mode, index, ticks);
                finished = true; server.halt(false);
            }
        } catch (Throwable failure) { fail(server, failure); }
    }
    private static void validate(MinecraftServer server) {
        ServerLevel target = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(room.dimension)));
        ServerLevel source = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(room.sourceDimension)));
        check(source != null && source.getSeed() == room.sourceSeed, "source seed and dimension contract");
        StructureStart start = StructureStart.loadStaticStart(StructurePieceSerializationContext.fromLevel(source), room.plan, room.sourceSeed);
        check(start != null && start.isValid(), "complete native piece graph deserializes");
        int chests = 0, loot = 0, archaeology = 0, tnt = 0, spawners = 0, blocks = 0;
        for (int x = room.minX; x <= room.maxX; x++) for (int z = room.minZ; z <= room.maxZ; z++) {
            BlockPos floor = new BlockPos(x, room.minY - 1, z);
            check(target.getBlockState(floor).is(Blocks.BEDROCK), "full basement foundation at " + floor + " got " + target.getBlockState(floor));
            for (int y = room.minY; y <= room.maxY; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                var state = target.getBlockState(pos);
                if (!state.isAir()) blocks++;
                if (state.is(Blocks.CHEST)) chests++;
                if (state.is(Blocks.TNT)) tnt++;
                if (state.is(Blocks.SPAWNER)) spawners++;
                var entity = target.getBlockEntity(pos);
                if (entity instanceof RandomizableContainerBlockEntity container
                        && container.saveWithFullMetadata().contains("LootTable")) loot++;
                if (entity instanceof BrushableBlockEntity brush && brush.saveWithFullMetadata().contains("LootTable")) archaeology++;
            }
        }
        check(blocks > 100, "actual structure blocks exist");
        if (index == 0 || index == 8) check(chests == 4 && loot >= 4 && tnt == 9 && archaeology >= 5,
                "desert chests/traps/archaeology " + chests + "/" + loot + "/" + tnt + "/" + archaeology);
        if (index == 3 || index == 5 || index == 7) check(loot > 0, "native container loot retained");
        if (index == 4 || index == 9) check(spawners > 0, "fortress native spawners retained");
        if (index == 6) check(archaeology > 0, "trail ruins archaeology retained");
        if (index == 8) {
            var parent = CapsuleInstances.get(server).rooms.get(CapsuleInstances.key(OWNER, IDS[index], dimension(index)));
            check(parent.ready && parent.plan.equals(parentPlan)
                            && CapsuleInstances.get(server).isAdjacentToExisting(room)
                            && !CapsuleInstances.boundsOverlap(parent, room),
                    "new native instance joins the shared neighboring-room cluster and retains its completed parent");
            check(CapsuleInstances.get(server).instanceCount(OWNER, IDS[index], dimension(index)) == 2,
                    "duplicate adjacent packets allocate only once");
        }
        if (index == 9) check(room.sourceDimension.equals("stretcher_capsule_check:custom_nether"),
                "custom source dimension retained");
        check(java.util.Arrays.stream(room.pixels).allMatch(pixel -> pixel != 0), "complete preview map");
        CompoundTag persisted = CapsuleInstances.get(server).save(new CompoundTag());
        check(!persisted.getList("rooms", 10).isEmpty(), "room data serializes");
        LogUtils.getLogger().info("FORGE CAPSULE CONTENT PASSED {} blocks={} pieces={} loot={} archaeology={} spawners={}",
                IDS[index], blocks, start.getPieces().size(), loot, archaeology, spawners);
    }
    private static void fail(MinecraftServer server, Throwable failure) {
        finished = true;
        LogUtils.getLogger().error("FORGE CAPSULE CHECKS FAILED mode=" + mode + " index=" + index, failure);
        server.halt(false);
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
