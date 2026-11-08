package com.sorrowmist.useless.stretcher.content.capsule;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Identifies the serialized native piece layout; the preview is a footprint, not a block map. */
public final class CapsuleVariantLayout {
    private static final int MAX_PIECES = 8_192;
    private static final int MAX_FIELDS = 128;
    private static final int MAX_NODES = 2_048;
    private static final int MAX_DEPTH = 12;
    private static final int MAX_TEXT = 4_096;
    private static final Set<String> POSITION_FIELDS = Set.of(
            "bb", "o", "posx", "posy", "posz", "tpx", "tpy", "tpz",
            "hpos", "ypos", "rotation", "rot", "rot2", "junctions",
            "template", "templatename", "template_name", "pool_element");

    private CapsuleVariantLayout() { }

    public record Layout(String fingerprint, String family, String template,
                         int sx, int sy, int sz, int pieces, int[] pixels) { }

    public static Layout inspect(StructureStart start, StructurePieceSerializationContext context) {
        if (start == null || !start.isValid() || start.getPieces().isEmpty())
            throw new IllegalArgumentException("Cannot inspect an empty native structure plan");
        if (start.getPieces().size() > MAX_PIECES)
            throw new IllegalArgumentException("Native plan has too many pieces to inspect");

        List<Piece> pieces = new ArrayList<>(start.getPieces().size());
        Bounds bounds = null;
        String template = "";
        for (int i = 0; i < start.getPieces().size(); i++) {
            StructurePiece piece = start.getPieces().get(i);
            CompoundTag tag = piece.createTag(context);
            String pieceTemplate = rootTemplate(piece, tag);
            if (i == 0) template = pieceTemplate;
            BoundingBox box = piece.getBoundingBox();
            Bounds part = new Bounds(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
            if (part.x1 < part.x0 || part.y1 < part.y0 || part.z1 < part.z0)
                throw new IllegalArgumentException("Native piece has an invalid bounding box");
            bounds = bounds == null ? part : bounds.union(part);
            // Template pieces commonly leave their generic orientation fixed at NORTH;
            // their true transform is getRotation(). Procedural pieces instead use facing.
            Direction facing = pieceTemplate.isEmpty() ? piece.getOrientation() : null;
            boolean absoluteCenter = piece.getClass().getName().equals("appeng.worldgen.meteorite.MeteoriteStructurePiece");
            pieces.add(new Piece(part, identity(tag, pieceTemplate, absoluteCenter), pieceTemplate.isEmpty() ? -1 : piece.getRotation().ordinal(),
                    facing == null ? 0 : facing.getStepX(), facing == null ? 0 : facing.getStepZ(), anchor(tag, absoluteCenter)));
        }

        // Compare the four complete representations. This normalizes a whole-plan quarter
        // turn while retaining each template's relative rotation and handedness.
        String fingerprint = null;
        for (int turn = 0; turn < 4; turn++) {
            Bounds rotated = bounds.rotate(turn);
            // Transform once per piece, rather than allocating rotated boxes from
            // each comparator call (which grows to millions for large native plans).
            List<View> ordered = new ArrayList<>(pieces.size());
            for (Piece piece : pieces) ordered.add(new View(piece.identity, piece.bounds.rotate(turn),
                    piece.rotation < 0 ? -1 : (piece.rotation + turn) & 3,
                    rotateX(piece.facingX, piece.facingZ, turn), rotateZ(piece.facingX, piece.facingZ, turn),
                    piece.anchor != null,
                    piece.anchor == null ? Long.MIN_VALUE : rotateX((long)piece.anchor.getX(), piece.anchor.getZ(), turn),
                    piece.anchor == null ? Long.MIN_VALUE : piece.anchor.getY(),
                    piece.anchor == null ? Long.MIN_VALUE : rotateZ((long)piece.anchor.getX(), piece.anchor.getZ(), turn)));
            ordered.sort(Comparator.comparing((View p) -> p.identity)
                    .thenComparingLong(p -> p.bounds.x0)
                    .thenComparingLong(p -> p.bounds.y0)
                    .thenComparingLong(p -> p.bounds.z0)
                    .thenComparingLong(p -> p.bounds.x1)
                    .thenComparingLong(p -> p.bounds.y1)
                    .thenComparingLong(p -> p.bounds.z1)
                    .thenComparingInt(p -> p.rotation)
                    .thenComparingInt(p -> p.facingX)
                    .thenComparingInt(p -> p.facingZ)
                    .thenComparingLong(p -> p.anchorX)
                    .thenComparingLong(p -> p.anchorY)
                    .thenComparingLong(p -> p.anchorZ));
            MessageDigest digest = digest();
            text(digest, "capsule-piece-layout-v1");
            number(digest, rotated.width()); number(digest, rotated.height()); number(digest, rotated.depth());
            number(digest, pieces.size());
            text(digest, template);
            for (View piece : ordered) {
                Bounds box = piece.bounds;
                text(digest, piece.identity);
                number(digest, box.x0 - rotated.x0); number(digest, box.y0 - rotated.y0); number(digest, box.z0 - rotated.z0);
                number(digest, box.x1 - rotated.x0); number(digest, box.y1 - rotated.y0); number(digest, box.z1 - rotated.z0);
                number(digest, piece.rotation);
                number(digest, piece.facingX);
                number(digest, piece.facingZ);
                number(digest, piece.hasAnchor ? 1 : 0);
                if (piece.hasAnchor) {
                    number(digest, piece.anchorX - rotated.x0);
                    number(digest, piece.anchorY - rotated.y0);
                    number(digest, piece.anchorZ - rotated.z0);
                }
            }
            String candidate = HexFormat.of().formatHex(digest.digest());
            if (fingerprint == null || candidate.compareTo(fingerprint) < 0) fingerprint = candidate;
        }
        return new Layout(fingerprint, family(template), template, dimension(bounds.width()),
                dimension(bounds.height()), dimension(bounds.depth()), pieces.size(), footprint(pieces, bounds));
    }

    private static String identity(CompoundTag tag, String template, boolean absoluteCenter) {
        MessageDigest digest = digest();
        text(digest, tag.getString("id"));
        text(digest, template);
        int[] budget = {MAX_NODES};
        if (tag.contains("pool_element", Tag.TAG_COMPOUND))
            metadata(digest, tag.getCompound("pool_element"), 0, budget);
        var keys = tag.getAllKeys().stream().sorted().toList();
        if (keys.size() > MAX_FIELDS) throw new IllegalArgumentException("Native piece has too many metadata fields");
        for (String key : keys) {
            String lower = key.toLowerCase(Locale.ROOT);
            if (POSITION_FIELDS.contains(lower) || absoluteCenter && lower.equals("c") || lootSeed(lower)) continue;
            Tag value = tag.get(key);
            if (value == null) continue;
            // Unknown custom compounds may encode a genuinely different layout.
            // Inspect them within a shared budget rather than silently equating them;
            // unknown coordinates can over-split candidates, which is safer than merging.
            text(digest, key); metadata(digest, value, 0, budget);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void metadata(MessageDigest digest, Tag tag, int depth, int[] budget) {
        if (depth > MAX_DEPTH || --budget[0] < 0)
            throw new IllegalArgumentException("Native pool metadata exceeds inspection bounds");
        number(digest, tag.getId());
        if (tag instanceof CompoundTag compound) {
            var keys = compound.getAllKeys().stream().sorted().toList();
            if (keys.size() > MAX_FIELDS) throw new IllegalArgumentException("Native pool has too many metadata fields");
            for (String key : keys) {
                String lower = key.toLowerCase(Locale.ROOT);
                // Nested local block coordinates and custom generation seeds can be
                // part of the layout. Only clearly identified loot randomness is omitted.
                if (lootSeed(lower)) continue;
                text(digest, key); metadata(digest, compound.get(key), depth + 1, budget);
            }
        } else if (tag instanceof ListTag list) {
            if (list.size() > MAX_NODES) throw new IllegalArgumentException("Native pool metadata list is too large");
            number(digest, list.size());
            for (Tag item : list) metadata(digest, item, depth + 1, budget);
        } else if (tag instanceof ByteArrayTag array) {
            byte[] values = array.getAsByteArray();
            arrayBudget(values.length, budget); number(digest, values.length); digest.update(values);
        } else if (tag instanceof IntArrayTag array) {
            int[] values = array.getAsIntArray();
            arrayBudget(values.length, budget); number(digest, values.length);
            for (int value : values) number(digest, value);
        } else if (tag instanceof LongArrayTag array) {
            long[] values = array.getAsLongArray();
            arrayBudget(values.length, budget); number(digest, values.length);
            for (long value : values) number(digest, value);
        } else text(digest, tag.getAsString());
    }

    private static void arrayBudget(int length, int[] budget) {
        if (length > MAX_NODES || (budget[0] -= length) < 0)
            throw new IllegalArgumentException("Native layout array exceeds inspection bounds");
    }

    private static boolean lootSeed(String lower) { return lower.contains("loot") && lower.contains("seed"); }

    private static String rootTemplate(StructurePiece piece, CompoundTag tag) {
        if (tag.contains("pool_element", Tag.TAG_COMPOUND)) {
            String fromPool = poolTemplate(tag.getCompound("pool_element"), 0, new int[]{MAX_NODES});
            if (!fromPool.isEmpty()) return fromPool;
        }
        for (String field : List.of("Template", "template", "TemplateName", "template_name")) {
            String raw = tag.getString(field);
            // Vanilla stores short names for these two TemplateStructurePiece types;
            // their native makeTemplateLocation adds the documented resource prefix.
            if (!raw.isEmpty() && raw.indexOf(':') < 0) {
                if (piece.getType() == StructurePieceType.END_CITY_PIECE) raw = "minecraft:end_city/" + raw;
                else if (piece.getType() == StructurePieceType.WOODLAND_MANSION_PIECE) raw = "minecraft:woodland_mansion/" + raw;
            }
            String id = identifier(raw);
            if (!id.isEmpty()) return id;
        }
        return "";
    }

    private static String poolTemplate(CompoundTag element, int depth, int[] budget) {
        if (depth > MAX_DEPTH || --budget[0] < 0) throw new IllegalArgumentException("Native pool nesting is too deep");
        String id = identifier(element.getString("location"));
        if (!id.isEmpty()) return id;
        ListTag list = element.getList("elements", Tag.TAG_COMPOUND);
        if (list.size() > MAX_NODES) throw new IllegalArgumentException("Native pool list is too large");
        for (int i = 0; i < list.size(); i++) {
            id = poolTemplate(list.getCompound(i), depth + 1, budget);
            if (!id.isEmpty()) return id;
        }
        return "";
    }

    private static String identifier(String raw) {
        if (raw.isEmpty() || raw.length() > 256) return "";
        ResourceLocation id = ResourceLocation.tryParse(raw);
        return id == null ? "" : id.toString();
    }

    private static String family(String template) {
        // Confirmed against the runtime 1.21.1 bastion/starts template pool. These are
        // template-derived labels, not a guess from the structure's translated name.
        for (String branch : List.of("treasure", "bridge", "hoglin_stable", "units"))
            if (template.startsWith("minecraft:bastion/" + branch + "/")) return "minecraft:bastion/" + branch;
        if (template.isEmpty()) return "unknown";
        int slash = template.lastIndexOf('/');
        return slash < 0 ? template : template.substring(0, slash);
    }

    private static BlockPos anchor(CompoundTag tag, boolean absoluteCenter) {
        for (String prefix : List.of("Pos", "TP")) {
            String x = prefix + "X", y = prefix + "Y", z = prefix + "Z";
            if (tag.contains(x, Tag.TAG_ANY_NUMERIC) && tag.contains(y, Tag.TAG_ANY_NUMERIC) && tag.contains(z, Tag.TAG_ANY_NUMERIC))
                return new BlockPos(tag.getInt(x), tag.getInt(y), tag.getInt(z));
        }
        return absoluteCenter && tag.contains("c", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("c")) : null;
    }

    private static int[] footprint(List<Piece> pieces, Bounds bounds) {
        // A 2-D difference grid bounds work at O(piece count + 4096), even when a
        // large root piece overlaps the entire preview. No blocks/chunks are read.
        int[] coverage = new int[65 * 65];
        for (Piece piece : pieces) {
            Bounds box = piece.bounds;
            int x0 = pixel(box.x0 - bounds.x0, bounds.width()), z0 = pixel(box.z0 - bounds.z0, bounds.depth());
            int x1 = pixel(box.x1 - bounds.x0, bounds.width()) + 1, z1 = pixel(box.z1 - bounds.z0, bounds.depth()) + 1;
            coverage[z0 * 65 + x0]++; coverage[z0 * 65 + x1]--;
            coverage[z1 * 65 + x0]--; coverage[z1 * 65 + x1]++;
        }
        int[] pixels = new int[4096];
        for (int z = 0; z < 64; z++) for (int x = 0; x < 64; x++) {
            int i = z * 65 + x;
            int count = coverage[i] + (x > 0 ? coverage[i - 1] : 0)
                    + (z > 0 ? coverage[i - 65] : 0) - (x > 0 && z > 0 ? coverage[i - 66] : 0);
            coverage[i] = count;
            pixels[z * 64 + x] = count <= 0 ? 0xFF171B20 : count == 1 ? 0xFF6C9CA8 : 0xFF8FBCC4;
        }
        Bounds root = pieces.getFirst().bounds;
        int x0 = pixel(root.x0 - bounds.x0, bounds.width()), z0 = pixel(root.z0 - bounds.z0, bounds.depth());
        int x1 = pixel(root.x1 - bounds.x0, bounds.width()), z1 = pixel(root.z1 - bounds.z0, bounds.depth());
        for (int x = x0; x <= x1; x++) { pixels[z0 * 64 + x] = 0xFFE9BF67; pixels[z1 * 64 + x] = 0xFFE9BF67; }
        for (int z = z0; z <= z1; z++) { pixels[z * 64 + x0] = 0xFFE9BF67; pixels[z * 64 + x1] = 0xFFE9BF67; }
        return pixels;
    }

    private static int pixel(long offset, long span) { return (int)Math.clamp(offset * 64L / Math.max(1L, span), 0L, 63L); }
    private static int dimension(long span) {
        if (span <= 0 || span > Integer.MAX_VALUE) throw new IllegalArgumentException("Native layout dimension is out of range");
        return (int)span;
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void text(MessageDigest digest, String value) {
        if (value.length() > MAX_TEXT) throw new IllegalArgumentException("Native layout metadata text is too large");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); number(digest, bytes.length); digest.update(bytes);
    }
    private static void number(MessageDigest digest, long value) {
        for (int shift = 56; shift >= 0; shift -= 8) digest.update((byte)(value >>> shift));
    }
    private static int rotateX(int x, int z, int turn) { return (int)rotateX((long)x, z, turn); }
    private static int rotateZ(int x, int z, int turn) { return (int)rotateZ((long)x, z, turn); }
    private static long rotateX(long x, long z, int turn) { return switch (turn & 3) { case 1 -> -z; case 2 -> -x; case 3 -> z; default -> x; }; }
    private static long rotateZ(long x, long z, int turn) { return switch (turn & 3) { case 1 -> x; case 2 -> -z; case 3 -> -x; default -> z; }; }

    private record Piece(Bounds bounds, String identity, int rotation, int facingX, int facingZ, BlockPos anchor) { }
    private record View(String identity, Bounds bounds, int rotation, int facingX, int facingZ,
                        boolean hasAnchor, long anchorX, long anchorY, long anchorZ) { }
    private record Bounds(long x0, long y0, long z0, long x1, long y1, long z1) {
        long width() { return x1 - x0 + 1; }
        long height() { return y1 - y0 + 1; }
        long depth() { return z1 - z0 + 1; }
        Bounds union(Bounds other) { return new Bounds(Math.min(x0, other.x0), Math.min(y0, other.y0), Math.min(z0, other.z0),
                Math.max(x1, other.x1), Math.max(y1, other.y1), Math.max(z1, other.z1)); }
        Bounds rotate(int turn) { return switch (turn & 3) {
            case 1 -> new Bounds(-z1, y0, x0, -z0, y1, x1);
            case 2 -> new Bounds(-x1, y0, -z1, -x0, y1, -z0);
            case 3 -> new Bounds(z0, y0, -x1, z1, y1, -x0);
            default -> this;
        }; }
    }
}
