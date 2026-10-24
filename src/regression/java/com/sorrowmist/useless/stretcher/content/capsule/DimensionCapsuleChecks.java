package com.sorrowmist.useless.stretcher.content.capsule;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.network.Network;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Actual ticket-driven placement, restart state and network checks in the isolated regression world. */
public final class DimensionCapsuleChecks {
    private static final BlockPos ORIGIN = new BlockPos(1024 + (UUID.randomUUID().hashCode() & 255) * 256, 180, 1024);
    private static final boolean[] finished = new boolean[4];
    private static final boolean[] success = new boolean[4];
    private static final String key = UUID.randomUUID() + "|capsule-regression";
    private static StructureTemplate template;
    private static int ticks;
    private static int phase;
    private static boolean retried;
    private static boolean retrySuccess;
    private static boolean done;
    private static ArmorStand guest;

    public static void run(ServerLevel level) throws Exception {
        dimensionRules();
        savedRooms(level);
        pages(level);
        template = new StructureTemplate();
        var source = TagParser.parseTag("{size:[32,3,32],palette:[{Name:'minecraft:stone'}],blocks:[],"
                + "entities:[{pos:[1.5d,1.0d,1.5d],blockPos:[1,1,1],"
                + "nbt:{id:'minecraft:armor_stand',NoGravity:1b,Marker:1b,Tags:['capsule_fixture']}}]}");
        var blocks = source.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
            CompoundTag block = new CompoundTag();
            block.put("pos", TagParser.parseTag("{p:[" + x + ",0," + z + "]}").get("p"));
            block.putInt("state", 0);
            blocks.add(block);
        }
        template.load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), source);
        // Keep every fixture visible until all four asynchronous jobs have been inspected.
        for (int x = 0; x < 16; x++) for (int z = 0; z < 2; z++)
            level.setChunkForced((ORIGIN.getX() >> 4) + x, (ORIGIN.getZ() >> 4) + z, true);
        for (int i = 0; i < 4; i++) {
            int index = i;
            check(DimensionCapsulePlacement.enqueue(key + i, level, template, ORIGIN.offset(i * 64, 0, 0),
                    new StructurePlaceSettings().setFinalizeEntities(true), ok -> {
                        finished[index] = true; success[index] = ok;
                    }), "queue accepts four rooms");
        }
        check(!DimensionCapsulePlacement.enqueue(key + 0, level, template, ORIGIN,
                new StructurePlaceSettings(), ok -> { }), "duplicate room rejected");
        check(!DimensionCapsulePlacement.enqueue(key + "overflow", level, template, ORIGIN,
                new StructurePlaceSettings(), ok -> { }), "queue is bounded");
        LogUtils.getLogger().info("CAPSULE synchronous checks passed: dimensions, saved state, 2048 catalogue entries; awaiting real batch placement");
    }

    public static boolean tick(ServerLevel level) {
        if (done) return true;
        check(++ticks < 2400, "capsule asynchronous generation timed out");
        if (ticks == 1) check(!finished[0], "large template cannot finish in one tick");
        if (phase == 0) {
            for (int i = 0; i < 4; i++) if (!finished[i]) return false;
            for (boolean ok : success) check(ok, "batched template placement succeeds");
            for (int i = 0; i < 4; i++) {
                BlockPos origin = ORIGIN.offset(i * 64, 0, 0);
                for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
                    check(level.getBlockState(origin.offset(x, 0, z)).is(Blocks.STONE), "all 1024 floor blocks placed");
                }
                check(stands(level, origin).size() == 1, "template entity spawned once after all blocks: room "
                        + i + " has " + stands(level, origin).size());
            }
            guest = EntityType.ARMOR_STAND.create(level);
            check(guest != null, "unrelated entity fixture");
            guest.setNoGravity(true);
            guest.setPos(ORIGIN.getX() + 2.5, ORIGIN.getY() + 1, ORIGIN.getZ() + 2.5);
            level.addFreshEntity(guest);
            check(DimensionCapsulePlacement.enqueue(key + 0, level, template, ORIGIN,
                    new StructurePlaceSettings().setFinalizeEntities(true), true, ok -> {
                        retried = true; retrySuccess = ok;
                    }), "interrupted room can be retried");
            phase = 1;
            return false;
        }
        if (!retried) return false;
        check(retrySuccess, "retry completes");
        var entities = stands(level, ORIGIN);
        check(entities.size() == 2 && entities.contains(guest), "retry removes only generated duplicates, preserves unrelated entity");
        check(entities.stream().filter(e -> e.getTags().contains("capsule_fixture")).count() == 1,
                "retry has exactly one template entity");
        check(!DimensionCapsulePlacement.busy(key + 0), "completed job releases queue slot");
        for (int x = 0; x < 16; x++) for (int z = 0; z < 2; z++)
            level.setChunkForced((ORIGIN.getX() >> 4) + x, (ORIGIN.getZ() >> 4) + z, false);
        done = true;
        LogUtils.getLogger().info("CAPSULE CHECKS PASSED: 4 x 1024 blocks, bounded queue, real template entities, tagged retry without duplication; {} ticks", ticks);
        return true;
    }

    private static List<ArmorStand> stands(ServerLevel level, BlockPos origin) {
        return level.getEntitiesOfClass(ArmorStand.class, new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + 32, origin.getY() + 4, origin.getZ() + 32));
    }

    private static void dimensionRules() {
        check(hint("ae2lt:firmament_starship/firmament_starship").equals(DimensionCapsuleCatalog.CAPSULE_END), "AE2LT end ship");
        check(hint("ae2lt:alien_starship/alien_starship").equals(DimensionCapsuleCatalog.CAPSULE_OVERWORLD), "AE2LT overworld ship");
        check(hint("example:nether/room").equals(DimensionCapsuleCatalog.CAPSULE_NETHER), "nether token");
        check(hint("minecraft:end_city/base_floor").equals(DimensionCapsuleCatalog.CAPSULE_END), "end token");
        check(hint("example:weekend_room").equals(DimensionCapsuleCatalog.CAPSULE_OVERWORLD), "no substring end false positive");
    }

    private static String hint(String id) { return DimensionCapsuleCatalog.dimensionHint(new ResourceLocation(id)); }

    private static void savedRooms(ServerLevel level) {
        var data = new DimensionCapsuleData();
        var first = data.room("owner-a|template");
        var second = data.room("owner-b|template");
        check(!first.origin(64).equals(second.origin(64)), "owners have distinct rooms");
        data.markStarted("owner-a|template");
        var loaded = DimensionCapsuleData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        var pending = loaded.room("owner-a|template");
        check(pending.started() && !pending.placed() && pending.slot() == first.slot(), "partial room survives serialization");
        loaded.markPlaced("owner-a|template");
        var complete = DimensionCapsuleData.load(loaded.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        check(complete.room("owner-a|template").placed(), "completed room persists and cannot be regenerated by enter");
        var world = DimensionCapsuleData.get(level.getServer());
        var restart = world.room("regression-restart-marker");
        if (restart.started()) {
            check(!restart.placed(), "previous process saved an interrupted room");
            LogUtils.getLogger().info("CAPSULE RESTART PASSED: previous server process persisted pending room slot {}", restart.slot());
        } else {
            world.markStarted("regression-restart-marker");
            LogUtils.getLogger().info("CAPSULE RESTART fixture saved for the next server process");
        }
    }

    private static void pages(ServerLevel level) {
        List<String> restored = new ArrayList<>();
        for (int page = 0; page < 16; page++) {
            List<String> entries = new ArrayList<>();
            for (int i = 0; i < 128; i++) entries.add("example|structure_" + (page * 128 + i) + "|32,3,32|" + DimensionCapsuleCatalog.CAPSULE_OVERWORLD);
            var payload = new Network.DimensionCapsuleCatalogPayload(entries, true, page, 16);
            var buf = new FriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            try {
                Network.DimensionCapsuleCatalogPayload.STREAM_CODEC.encode(buf, payload);
                check(buf.readableBytes() < 32_768, "catalogue page stays small");
                var decoded = Network.DimensionCapsuleCatalogPayload.STREAM_CODEC.decode(buf);
                check(decoded.entries().equals(entries) && decoded.part() == page && decoded.parts() == 16 && decoded.offhand(),
                        "page metadata and entries roundtrip");
                check(!buf.isReadable(), "entire catalogue packet consumed");
                restored.addAll(decoded.entries());
            } finally { buf.release(); }
        }
        check(restored.size() == 2048 && restored.getLast().contains("structure_2047|"), "directory is not truncated at entry 128");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
