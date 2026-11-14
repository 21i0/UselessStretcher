package com.sorrowmist.useless.stretcher.content.capsule;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.fml.ModList;

/** Typed checkpoint hashes, not a guarantee that arbitrary mod callbacks are replay-safe. */
public final class CapsuleGravityFingerprint {
    private static final int MAX_DEPTH = 512;
    private static final int MAX_RESOURCE_FILES = 100_000;
    private static final long MAX_RESOURCE_BYTES = 128L * 1024L * 1024L;
    private static final List<String> RESOURCE_ROOTS = List.of("worldgen", "structure", "structures",
            "dimension", "dimension_type", "tags", "loot_table", "loot_tables", "neoforge", "forge",
            "biome_modifier", "structure_modifier", "global_loot_modifiers");

    private CapsuleGravityFingerprint() {}

    /**
     * Canonical typed encoding of TAG_End plus all twelve value types. Compound
     * keys sort by Java UTF-16 order, lists retain element type and order, strings
     * retain exact UTF-16 code units, and floating-point values retain raw bits.
     * Counts and lengths are framed; neither SNBT/toString nor Object.hashCode is
     * used. Unsupported tag IDs or mismatched implementations are rejected.
     */
    public static String hash(Tag tag) {
        Objects.requireNonNull(tag, "tag");
        MessageDigest digest = digest();
        digest.update("capsule-gravity-canonical-nbt-v1".getBytes(StandardCharsets.US_ASCII));
        try (DataOutputStream output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            writeTag(output, tag, 0);
        } catch (IOException impossible) {
            throw new IllegalStateException("Cannot encode canonical gravity checkpoint", impossible);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Keep plan, identity, versions, source binding and every semantic cursor. */
    public static CompoundTag room(CapsuleInstances.Instance room) {
        CompoundTag snapshot = Objects.requireNonNull(room, "room").save().copy();
        for (String key : List.of("error", "generating", "progress", "pixels",
                "completion_notified", "planning_off_thread")) snapshot.remove(key);
        return snapshot;
    }

    public static String roomHash(CapsuleInstances.Instance room) {
        return hash(room(room));
    }

    /**
     * A conservative comparable snapshot within the caller's unchanged epoch.
     * Includes level/type/generator codecs, registry key sets, mod versions and
     * effective relevant JSON/NBT resource bytes. Resource failures fail closed.
     * This is explicit preflight work, never a placement/tick hot-path operation.
     * It does not capture arbitrary runtime mod state, external config/scripts,
     * code changes hidden behind an unchanged version, or transactional side effects.
     */
    public static String environment(ServerLevel source, ServerLevel target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        MinecraftServer server = source.getServer();
        if (server != target.getServer())
            throw new IllegalArgumentException("Source and target must belong to the same server epoch");
        ResourceManager resources = server.getResourceManager();
        CompoundTag snapshot = new CompoundTag();
        snapshot.putInt("format", 1);
        snapshot.put("source", level(source));
        snapshot.put("target", level(target));
        CompoundTag registries = new CompoundTag();
        for (var entry : server.registryAccess().registries()
                .sorted(Comparator.comparing(value -> value.key().location().toString())).toList()) {
            ListTag keys = new ListTag();
            entry.value().keySet().stream().map(ResourceLocation::toString).sorted()
                    .forEach(key -> keys.add(StringTag.valueOf(key)));
            registries.put(entry.key().location().toString(), keys);
        }
        snapshot.put("registry_keys", registries);
        snapshot.putString("resource_signature", resourceSignature(server, resources));
        if (resources != server.getResourceManager())
            throw new IllegalStateException("Resource manager changed during gravity preflight");
        return hash(snapshot);
    }

    private static CompoundTag level(ServerLevel level) {
        CompoundTag snapshot = new CompoundTag();
        snapshot.putString("dimension", level.dimension().location().toString());
        snapshot.putLong("seed", level.getSeed());
        snapshot.putInt("min_y", level.getMinBuildHeight());
        snapshot.putInt("height", level.getHeight());
        snapshot.putInt("sea_level", level.getSeaLevel());
        snapshot.putString("type_key", level.dimensionTypeRegistration().unwrapKey()
                .map(key -> key.location().toString()).orElse(""));
        var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        snapshot.put("type", DimensionType.DIRECT_CODEC.encodeStart(ops, level.dimensionType()).result()
                .orElseThrow(() -> new IllegalStateException("Cannot fingerprint source/target dimension type")));
        var generator = level.getChunkSource().getGenerator();
        snapshot.putString("generator_class", generator.getClass().getName());
        snapshot.put("generator", ChunkGenerator.CODEC.encodeStart(ops, generator).result()
                .orElseThrow(() -> new IllegalStateException("Cannot fingerprint source/target generator")));
        return snapshot;
    }

    /** Same resource coverage as CapsuleVariants.resourceSignature, without accessing its private method. */
    private static String resourceSignature(MinecraftServer server, ResourceManager manager) {
        MessageDigest digest = digest();
        digest.update("capsule-gravity-environment-resources-v1".getBytes(StandardCharsets.US_ASCII));
        var resources = new TreeMap<ResourceLocation, Resource>();
        for (String root : RESOURCE_ROOTS) {
            if (Thread.currentThread().isInterrupted())
                throw new IllegalStateException("Gravity resource fingerprint interrupted");
            resources.putAll(manager.listResources(root,
                    id -> id.getPath().endsWith(".json") || id.getPath().endsWith(".nbt")));
            if (resources.size() > MAX_RESOURCE_FILES)
                throw new IllegalStateException("Gravity resource fingerprint file limit");
        }
        try (DataOutputStream output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            var mods = ModList.get().getMods().stream().sorted(Comparator.comparing(mod -> mod.getModId())).toList();
            output.writeInt(mods.size());
            for (var mod : mods) {
                writeString(output, mod.getModId());
                writeString(output, mod.getVersion().toString());
            }
            output.writeInt(resources.size());
            long bytes = 0;
            byte[] buffer = new byte[8192];
            for (var entry : resources.entrySet()) {
                writeString(output, entry.getKey().toString());
                writeString(output, entry.getValue().sourcePackId());
                MessageDigest content = digest();
                try (var input = entry.getValue().open()) {
                    while (true) {
                        if (Thread.currentThread().isInterrupted())
                            throw new IllegalStateException("Gravity resource fingerprint interrupted");
                        int count = input.read(buffer);
                        if (count < 0) break;
                        if (count == 0) {
                            int single = input.read();
                            if (single < 0) break;
                            bytes++;
                            content.update((byte) single);
                        } else {
                            bytes += count;
                            content.update(buffer, 0, count);
                        }
                        if (bytes > MAX_RESOURCE_BYTES)
                            throw new IllegalStateException("Gravity resource fingerprint byte limit");
                    }
                }
                output.write(content.digest());
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot fingerprint gravity environment resources", failure);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void writeTag(DataOutputStream output, Tag tag, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("Canonical NBT depth limit");
        output.writeByte(tag.getId());
        switch (tag.getId()) {
            case Tag.TAG_END -> requireType(tag, EndTag.class);
            case Tag.TAG_BYTE -> output.writeByte(requireType(tag, ByteTag.class).getAsByte());
            case Tag.TAG_SHORT -> output.writeShort(requireType(tag, ShortTag.class).getAsShort());
            case Tag.TAG_INT -> output.writeInt(requireType(tag, IntTag.class).getAsInt());
            case Tag.TAG_LONG -> output.writeLong(requireType(tag, LongTag.class).getAsLong());
            case Tag.TAG_FLOAT -> output.writeInt(Float.floatToRawIntBits(requireType(tag, FloatTag.class).getAsFloat()));
            case Tag.TAG_DOUBLE -> output.writeLong(Double.doubleToRawLongBits(requireType(tag, DoubleTag.class).getAsDouble()));
            case Tag.TAG_BYTE_ARRAY -> {
                byte[] values = requireType(tag, ByteArrayTag.class).getAsByteArray();
                output.writeInt(values.length);
                output.write(values);
            }
            case Tag.TAG_STRING -> writeString(output, requireType(tag, StringTag.class).getAsString());
            case Tag.TAG_LIST -> {
                ListTag list = requireType(tag, ListTag.class);
                int elementType = list.getElementType();
                if (elementType < Tag.TAG_END || elementType > Tag.TAG_LONG_ARRAY
                        || elementType == Tag.TAG_END && !list.isEmpty())
                    throw new IllegalArgumentException("Unsupported NBT list element type");
                output.writeByte(list.getElementType());
                output.writeInt(list.size());
                for (Tag entry : list) {
                    if (entry.getId() != elementType)
                        throw new IllegalArgumentException("NBT list element ID/type mismatch");
                    writeTag(output, entry, depth + 1);
                }
            }
            case Tag.TAG_COMPOUND -> {
                CompoundTag compound = requireType(tag, CompoundTag.class);
                var keys = compound.getAllKeys().stream().sorted().toList();
                output.writeInt(keys.size());
                for (String key : keys) {
                    writeString(output, key);
                    writeTag(output, Objects.requireNonNull(compound.get(key), "compound value"), depth + 1);
                }
            }
            case Tag.TAG_INT_ARRAY -> {
                int[] values = requireType(tag, IntArrayTag.class).getAsIntArray();
                output.writeInt(values.length);
                for (int value : values) output.writeInt(value);
            }
            case Tag.TAG_LONG_ARRAY -> {
                long[] values = requireType(tag, LongArrayTag.class).getAsLongArray();
                output.writeInt(values.length);
                for (long value : values) output.writeLong(value);
            }
            default -> throw new IllegalArgumentException("Unsupported NBT tag ID: " + tag.getId());
        }
    }

    private static void writeString(DataOutputStream output, String text) throws IOException {
        output.writeInt(text.length());
        for (int i = 0; i < text.length(); i++) output.writeChar(text.charAt(i));
    }

    private static <T extends Tag> T requireType(Tag tag, Class<T> type) {
        if (!type.isInstance(tag)) throw new IllegalArgumentException("NBT tag ID/type mismatch");
        return type.cast(tag);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
