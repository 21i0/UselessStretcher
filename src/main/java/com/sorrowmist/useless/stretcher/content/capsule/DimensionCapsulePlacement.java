package com.sorrowmist.useless.stretcher.content.capsule;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Uses vanilla template placement in bounded batches, after a region ticket has loaded the chunk. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID)
public final class DimensionCapsulePlacement {
    /** Each callback is bounded so a large template yields to the server tick loop. */
    private static final int BLOCK_BUDGET = 256;
    /** A batch may not fan out into an unbounded number of chunk loads. */
    private static final int MAX_BATCH_CHUNKS = 32;
    private static final int MAX_JOBS = 4;
    private static final long TIME_BUDGET = 2_000_000L;
    private static final int MAX_JOB_TICKS = 1_000_000;
    private static final TicketType<ChunkPos> TICKET = TicketType.create(
            "useless_stretcher_capsule", Comparator.comparingLong(ChunkPos::toLong));
    private static final ArrayDeque<Job> JOBS = new ArrayDeque<>();

    private DimensionCapsulePlacement() { }

    public static boolean busy(String key) {
        return JOBS.stream().anyMatch(job -> job.key.equals(key));
    }

    public static boolean busyForPlayer(java.util.UUID player) {
        return JOBS.stream().anyMatch(job -> job.key.startsWith(player + "|"));
    }

    public static boolean enqueue(String key, ServerLevel level, StructureTemplate template,
                                  BlockPos origin, StructurePlaceSettings settings, Consumer<Boolean> completed) {
        return enqueue(key, level, template, origin, settings, false, completed);
    }

    public static boolean enqueue(String key, ServerLevel level, StructureTemplate template,
                                  BlockPos origin, StructurePlaceSettings settings, boolean recovering,
                                  Consumer<Boolean> completed) {
        if (JOBS.size() >= MAX_JOBS || busy(key)) return false;
        JOBS.add(new Job(key, level, template, origin, settings, recovering, completed));
        return true;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Job job = JOBS.poll();
        if (job == null) return;
        try {
            if (!job.step()) JOBS.add(job);
            else job.finish(true);
        } catch (Exception failure) {
            com.mojang.logging.LogUtils.getLogger().error("Capsule placement failed: {}", job.key, failure);
            job.finish(false);
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        while (!JOBS.isEmpty()) JOBS.remove().release();
    }

    private static final class Job {
        private final String key;
        private final ServerLevel level;
        private final BlockPos origin;
        private final StructurePlaceSettings settings;
        private final Consumer<Boolean> completed;
        private final CompoundTag source;
        private final String entityTag;
        private final RandomSource random = RandomSource.create();
        private final List<ChunkPos> tickets = new ArrayList<>();
        private int blockCursor;
        private int shapeCursor;
        private int entityCursor;
        private int ticks;
        private int cleanupCursor;
        private int cleanupMinChunkX;
        private int cleanupMaxChunkX;
        private int cleanupMinChunkZ;
        private int cleanupMaxChunkZ;
        private long cleanupCount;

        private Job(String key, ServerLevel level, StructureTemplate template, BlockPos origin,
                    StructurePlaceSettings settings, boolean recovering, Consumer<Boolean> completed) {
            this.key = key;
            this.level = level;
            this.origin = origin;
            this.settings = settings;
            this.completed = completed;
            entityTag = "useless_stretcher_capsule_" + java.util.UUID.nameUUIDFromBytes(
                    key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            source = template.save(new CompoundTag());
            // Pick one palette for the whole room, so a batch never changes the palette midway.
            if (source.contains("palettes", Tag.TAG_LIST)) {
                ListTag palettes = source.getList("palettes", Tag.TAG_LIST);
                if (!palettes.isEmpty()) source.put("palette", palettes.getList(random.nextInt(palettes.size())));
                source.remove("palettes");
            }
            // Keep the serialized template as the only block source.  Older code expanded
            // every block into a world-position list and every chunk into a batch list, which
            // multiplied the memory peak for large mod structures.
            ListTag entities = source.getList("entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < entities.size(); i++) tagEntity(entities.getCompound(i).getCompound("nbt"));
            if (recovering) {
                var bounds = template.getBoundingBox(settings, origin);
                cleanupMinChunkX = (bounds.minX() - 16) >> 4;
                cleanupMaxChunkX = (bounds.maxX() + 16) >> 4;
                cleanupMinChunkZ = (bounds.minZ() - 16) >> 4;
                cleanupMaxChunkZ = (bounds.maxZ() + 16) >> 4;
                cleanupCount = ((long) cleanupMaxChunkX - cleanupMinChunkX + 1L)
                        * ((long) cleanupMaxChunkZ - cleanupMinChunkZ + 1L);
            }
        }

        private void tagEntity(CompoundTag entity) {
            ListTag tags = entity.getList("Tags", Tag.TAG_STRING);
            if (!tags.contains(StringTag.valueOf(entityTag))) tags.add(StringTag.valueOf(entityTag));
            entity.put("Tags", tags);
            ListTag passengers = entity.getList("Passengers", Tag.TAG_COMPOUND);
            for (int i = 0; i < passengers.size(); i++) tagEntity(passengers.getCompound(i));
        }

        private boolean ready(Iterable<ChunkPos> required) {
            Set<ChunkPos> unique = new LinkedHashSet<>();
            for (ChunkPos chunk : required) unique.add(chunk);
            if (!tickets.equals(List.copyOf(unique))) {
                release();
                tickets.addAll(unique);
                for (ChunkPos chunk : tickets)
                    level.getChunkSource().addRegionTicket(TICKET, chunk, 2, chunk);
            }
            // A ticket requests generation without blocking this server tick on a future.
            for (ChunkPos chunk : tickets) {
                if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null
                        || !level.areEntitiesLoaded(chunk.toLong())) return false;
            }
            return true;
        }

        private boolean ready(ChunkPos chunk) { return ready(List.of(chunk)); }

        private BlockPos worldPos(CompoundTag block) {
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            return StructureTemplate.calculateRelativePosition(settings,
                    new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2))).offset(origin);
        }

        private boolean placeBlockBatch(ListTag blocks) {
            if (blockCursor >= blocks.size()) return true;
            int next = blockCursor;
            ListTag batch = new ListTag();
            List<ChunkPos> required = new ArrayList<>();
            Set<Long> requiredKeys = new LinkedHashSet<>();
            while (next < blocks.size() && batch.size() < BLOCK_BUDGET) {
                CompoundTag block = blocks.getCompound(next);
                ChunkPos chunk = new ChunkPos(worldPos(block));
                if (!requiredKeys.contains(chunk.toLong()) && requiredKeys.size() >= MAX_BATCH_CHUNKS
                        && !batch.isEmpty()) break;
                if (requiredKeys.add(chunk.toLong())) required.add(chunk);
                batch.add(block);
                next++;
            }
            if (!ready(required)) return false;
            CompoundTag batchTag = new CompoundTag();
            batchTag.put("size", source.get("size").copy());
            if (source.contains("palette")) batchTag.put("palette", source.get("palette").copy());
            batchTag.put("blocks", batch);
            batchTag.put("entities", new ListTag());
            StructureTemplate part = new StructureTemplate();
            part.load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), batchTag);
            if (!part.placeInWorld(level, origin, origin,
                    settings.copy().setKnownShape(true).setIgnoreEntities(true), random, 18))
                throw new IllegalStateException("Empty or invalid template block batch");
            blockCursor = next;
            return false;
        }

        private boolean placeEntity(ListTag entities) {
            if (entityCursor >= entities.size()) return true;
            CompoundTag entity = entities.getCompound(entityCursor);
            ListTag pos = entity.getList("blockPos", Tag.TAG_INT);
            BlockPos world = StructureTemplate.calculateRelativePosition(settings,
                    new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2))).offset(origin);
            if (!ready(new ChunkPos(world))) return false;
            ListTag single = new ListTag();
            single.add(entity);
            CompoundTag batchTag = new CompoundTag();
            batchTag.put("size", source.get("size").copy());
            if (source.contains("palette")) batchTag.put("palette", source.get("palette").copy());
            batchTag.put("blocks", new ListTag());
            batchTag.put("entities", single);
            StructureTemplate part = new StructureTemplate();
            part.load(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), batchTag);
            if (!part.placeInWorld(level, origin, origin,
                    settings.copy().setKnownShape(true).setIgnoreEntities(false), random, 18))
                throw new IllegalStateException("Invalid template entity batch");
            entityCursor++;
            return false;
        }

        private boolean step() {
            if (++ticks > MAX_JOB_TICKS) throw new IllegalStateException("Capsule generation timed out");
            if (cleanupCursor < cleanupCount) {
                long width = (long) cleanupMaxChunkX - cleanupMinChunkX + 1L;
                ChunkPos chunk = new ChunkPos(cleanupMinChunkX + (int) (cleanupCursor % width),
                        cleanupMinChunkZ + (int) (cleanupCursor / width));
                if (!ready(chunk)) return false;
                var area = new AABB(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                        chunk.getMaxBlockX() + 1, level.getMaxBuildHeight(), chunk.getMaxBlockZ() + 1);
                for (var entity : level.getEntities((net.minecraft.world.entity.Entity) null, area,
                        entity -> entity.getTags().contains(entityTag)
                                && !(entity instanceof net.minecraft.world.entity.player.Player))) entity.discard();
                cleanupCursor++;
                return false;
            }
            ListTag blocks = source.getList("blocks", Tag.TAG_COMPOUND);
            if (blockCursor < blocks.size()) return placeBlockBatch(blocks);
            long end = System.nanoTime() + TIME_BUDGET;
            int count = 0;
            while (shapeCursor < blocks.size() && count++ < BLOCK_BUDGET && System.nanoTime() < end) {
                BlockPos pos = worldPos(blocks.getCompound(shapeCursor));
                if (!ready(new ChunkPos(pos))) return false;
                var state = level.getBlockState(pos);
                var updated = Block.updateFromNeighbourShapes(state, level, pos);
                if (state != updated) level.setBlock(pos, updated, 18);
                level.blockUpdated(pos, updated.getBlock());
                shapeCursor++;
            }
            if (shapeCursor < blocks.size()) return false;
            ListTag entities = source.getList("entities", Tag.TAG_COMPOUND);
            if (entityCursor < entities.size()) return placeEntity(entities);
            return true;
        }

        private void finish(boolean success) {
            release();
            completed.accept(success);
        }

        private void release() {
            for (ChunkPos ticket : tickets)
                level.getChunkSource().removeRegionTicket(TICKET, ticket, 2, ticket);
            tickets.clear();
        }
    }
}
