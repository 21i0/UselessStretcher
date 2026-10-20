package com.sorrowmist.useless.stretcher.content.capsule;

import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Complete registered structures, including procedural buildings and assembled jigsaw villages. */
public final class DimensionCapsuleCatalog {
    public static final int MAX_ENTRIES = 2048;
    /** The physical rooms are isolated from ordinary worlds, while retaining one environment
     * compatible with the source world that supplied the native StructureStart. */
    public static final String CAPSULE_OVERWORLD = "useless_stretcher:capsule_mirror/minecraft/overworld";
    public static final String CAPSULE_NETHER = "useless_stretcher:capsule_mirror/minecraft/the_nether";
    public static final String CAPSULE_END = "useless_stretcher:capsule_mirror/minecraft/the_end";
    /** Hard safety bound for template rooms; placement itself is streamed in small batches. */
    public static final int MAX_AXIS = 4096;
    public record Entry(ResourceLocation id, Vec3i size, String dimensionHint) {
        public String wire() {
            return id.getNamespace() + "|" + id.getPath() + "|" + size.getX() + "," + size.getY()
                    + "," + size.getZ() + "|" + dimensionHint;
        }
    }

    private DimensionCapsuleCatalog() { }

    public static List<Entry> scan(ServerPlayer player) {
        List<Entry> entries = new ArrayList<>();
        var registry = player.level().registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (ResourceLocation id : registry.keySet().stream().sorted().toList()) {
            List<String> targets = new ArrayList<>(dimensions(player, registry.get(id)));
            // Keep a previously saved room reachable at its original physical location.
            for (var room : CapsuleInstances.get(player.getServer()).rooms.values()) {
                if (room.owner.equals(player.getUUID()) && room.structure.equals(id.toString())
                        && !targets.contains(room.dimension)) targets.add(room.dimension);
            }
            entries.add(new Entry(id, Vec3i.ZERO, String.join(",", targets)));
            if (entries.size() == MAX_ENTRIES) break;
        }
        entries.sort(Comparator.comparing((Entry e) -> e.id().getNamespace()).thenComparing(e -> e.id().getPath()));
        return entries.size() > MAX_ENTRIES ? List.copyOf(entries.subList(0, MAX_ENTRIES)) : List.copyOf(entries);
    }

    public static List<String> dimensions(ServerPlayer player, Structure structure) {
        List<ServerLevel> matching = matchingSources(player, structure);
        // A structure set is the authoritative runtime placement rule for a generator.  If
        // the pack/datapack assigns this structure to one or more dimensions, do not offer a
        // merely biome-compatible dimension as a substitute.  Some mods register structures
        // for capsule-only use without a structure set; those still use the biome fallback.
        List<ServerLevel> scheduled = matching.stream().filter(level -> isScheduled(level, structure)).toList();
        if (!scheduled.isEmpty()) matching = new ArrayList<>(scheduled);
        matching.sort(Comparator.comparingInt((ServerLevel level) ->
                level.dimension().location().getNamespace().equals("minecraft") ? 0 : 1)
                .thenComparing(level -> level.dimension().location().toString()));
        return matching.stream().map(DimensionCapsuleCatalog::targetFor).distinct().toList();
    }

    /** Resolve the real level used to assemble a plan for a requested isolated target. */
    public static ServerLevel sourceLevel(ServerPlayer player, Structure structure, String target) {
        return matchingSources(player, structure).stream()
                .filter(level -> compatibleTarget(level, target))
                .sorted(Comparator.comparingInt((ServerLevel level) ->
                        level.dimension().location().getNamespace().equals("minecraft") ? 0 : 1)
                        .thenComparing(level -> level.dimension().location().toString()))
                .findFirst().orElse(null);
    }

    /**
     * Resolve a previously persisted source dimension without falling back to another
     * world.  A native plan is tied to the generator, biome source, template manager and
     * seed of this level; silently selecting a replacement after a restart changes the
     * structure or can make a mod-specific structure invalid.
     */
    public static ServerLevel sourceLevel(ServerPlayer player, Structure structure, String target,
                                          String sourceDimension) {
        if (sourceDimension == null || sourceDimension.isBlank()) return sourceLevel(player, structure, target);
        final ResourceLocation id;
        try {
            id = new ResourceLocation(sourceDimension);
        } catch (RuntimeException invalid) {
            return null;
        }
        ServerLevel level = player.getServer().getLevel(
                net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, id));
        if (level == null || isCapsuleLevel(level) || !compatibleTarget(level, target)) return null;
        var biomeSource = level.getChunkSource().getGenerator().getBiomeSource();
        return biomeSource.possibleBiomes().stream().anyMatch(structure.biomes()::contains) ? level : null;
    }

    private static List<ServerLevel> matchingSources(ServerPlayer player, Structure structure) {
        List<ServerLevel> matching = new ArrayList<>();
        for (ServerLevel level : player.getServer().getAllLevels()) {
            if (isCapsuleLevel(level)) continue;
            var source = level.getChunkSource().getGenerator().getBiomeSource();
            if (source.possibleBiomes().stream().anyMatch(structure.biomes()::contains)) matching.add(level);
        }
        // A structure set is the authoritative runtime placement rule for a generator. If
        // the pack assigns this structure to one or more worlds, do not use a biome-only
        // fallback from another world.
        List<ServerLevel> scheduled = matching.stream().filter(level -> isScheduled(level, structure)).toList();
        return scheduled.isEmpty() ? matching : new ArrayList<>(scheduled);
    }

    private static boolean isCapsuleLevel(ServerLevel level) {
        return CapsuleDimensions.isCapsule(level.dimension().location());
    }

    /** Map custom mod dimensions by their registered environment rather than by mod id. */
    public static String targetFor(ServerLevel level) {
        return CapsuleDimensions.targetFor(level.dimension().location());
    }

    /** Only persisted rooms may keep the older, coarse environment mapping. */
    private static boolean compatibleTarget(ServerLevel level, String target) {
        return targetFor(level).equals(target) || legacyTargetFor(level).equals(target);
    }

    private static String legacyTargetFor(ServerLevel level) {
        ResourceLocation id = level.dimension().location();
        if (id.equals(new ResourceLocation("minecraft:the_nether"))
                || level.dimensionType().ultraWarm()
                || id.getPath().contains("nether") || id.getPath().contains("hell")) return "useless_stretcher:capsule_nether";
        if (id.equals(new ResourceLocation("minecraft:the_end"))
                || level.dimensionType().effectsLocation().equals(new ResourceLocation("minecraft:the_end"))
                || id.getPath().contains("end") || id.getPath().contains("other")) return "useless_stretcher:capsule_end";
        return "useless_stretcher:capsule_overworld";
    }

    private static boolean isScheduled(ServerLevel level, Structure structure) {
        return level.getChunkSource().getGeneratorState().possibleStructureSets().stream()
                .anyMatch(set -> set.value().structures().stream()
                        .anyMatch(entry -> entry.structure().value() == structure));
    }

    /** Legacy template-room migration only; native structures use runtime biome/generator membership. */
    public static String dimensionHint(ResourceLocation id) {
        String path = id.getPath();
        // AE2LT's actual biome tags put these two ships in different vanilla dimensions.
        if (id.getNamespace().equals("ae2lt")) {
            if (path.startsWith("firmament_starship/")) return CAPSULE_END;
            if (path.startsWith("alien_starship/")) return CAPSULE_OVERWORLD;
        }
        List<String> tokens = List.of((id.getNamespace() + ":" + path).split("[/:_.-]+"));
        if (tokens.contains("end") || tokens.contains("ender")) return CAPSULE_END;
        if (tokens.contains("nether") || tokens.contains("hell") || tokens.contains("bastion"))
            return CAPSULE_NETHER;
        if (tokens.contains("overworld") || tokens.contains("surface")) return CAPSULE_OVERWORLD;
        return CAPSULE_OVERWORLD;
    }

    public static Entry find(ServerPlayer player, String id) {
        if (id == null || id.isBlank()) return null;
        return scan(player).stream().filter(entry -> entry.id().toString().equals(id)).findFirst().orElse(null);
    }
}
