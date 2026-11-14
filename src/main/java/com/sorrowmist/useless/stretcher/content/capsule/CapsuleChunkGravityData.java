package com.sorrowmist.useless.stretcher.content.capsule;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Sparse, chunk-owned provenance for ordinary gravity blocks. This class has no
 * world references, registries, global position map, dirty flag or migration path.
 * Each allocated section stores 4096 two-bit cells in exactly 128 longs.
 */
public final class CapsuleChunkGravityData {
    public static final int FORMAT = 1;
    public static final int CELLS_PER_SECTION = 4096;
    public static final int LONGS_PER_SECTION = 128;
    public static final int NONE = 0;
    public static final int GRAVEL = 1;
    public static final int SAND = 2;
    public static final int RED_SAND = 3;

    private static final long LOW_BITS = 0x5555555555555555L;
    private static final Set<String> ROOT_KEYS = Set.of("format", "sections");
    private static final Set<String> SECTION_KEYS = Set.of("y", "cells");

    private final int minSectionInclusive;
    private final int maxSectionExclusive;
    private final Map<Integer, Section> sections = new TreeMap<>();
    private long cellCount;
    private String blockedReason = "";
    private Tag opaquePayload;

    /** The same half-open section interval used by the owning chunk's height accessor. */
    public CapsuleChunkGravityData(int minSectionInclusive, int maxSectionExclusive) {
        if (maxSectionExclusive <= minSectionInclusive)
            throw new IllegalArgumentException("Empty or reversed chunk section interval");
        this.minSectionInclusive = minSectionInclusive;
        this.maxSectionExclusive = maxSectionExclusive;
    }

    public int minSectionInclusive() { return minSectionInclusive; }
    public int maxSectionExclusive() { return maxSectionExclusive; }
    public int sectionCount() { return sections.size(); }
    public long cellCount() { return cellCount; }
    public boolean isEmpty() { return sections.isEmpty(); }
    public boolean isBlocked() { return !blockedReason.isEmpty(); }
    public String blockedReason() { return blockedReason; }

    /** localIndex = (localY << 8) | (localZ << 4) | localX, each coordinate 0..15. */
    public int get(int sectionY, int localIndex) {
        checkAddress(sectionY, localIndex);
        Section section = sections.get(sectionY);
        return section == null ? NONE : codeAt(section.cells, localIndex);
    }

    /**
     * Returns whether provenance changed. The caller alone marks its chunk unsaved.
     * Refusing blocked writes explicitly prevents a caller from confusing them with
     * a successful unchanged write. Setting the last occupied cell to NONE releases
     * the entire section array; reads never allocate an array.
     */
    public boolean set(int sectionY, int localIndex, int code) {
        checkAddress(sectionY, localIndex);
        if (code < NONE || code > RED_SAND)
            throw new IllegalArgumentException("Gravity cell code must be 0..3");
        if (isBlocked())
            throw new IllegalStateException("Read-only capsule gravity provenance: " + blockedReason);
        Section section = sections.get(sectionY);
        int previous = section == null ? NONE : codeAt(section.cells, localIndex);
        if (previous == code) return false;
        if (section == null) {
            section = new Section(new long[LONGS_PER_SECTION], 0);
            sections.put(sectionY, section);
        }
        int word = localIndex >>> 5;
        int shift = (localIndex & 31) << 1;
        long mask = 3L << shift;
        section.cells[word] = (section.cells[word] & ~mask) | ((long) code << shift);
        if (previous == NONE) {
            section.occupied++;
            cellCount++;
        } else if (code == NONE) {
            section.occupied--;
            cellCount--;
            if (section.occupied == 0) sections.remove(sectionY);
        }
        return true;
    }

    public boolean clear(int sectionY, int localIndex) {
        return set(sectionY, localIndex, NONE);
    }

    /**
     * Compound-only convenience accessor. Attachment serializers must use saveTag()
     * so an unknown non-compound payload is preserved without a narrowing cast.
     * No dirty state is changed.
     */
    public CompoundTag save() {
        if (isBlocked()) {
            if (opaquePayload instanceof CompoundTag compound) return compound.copy();
            throw new IllegalStateException("Non-compound capsule gravity payload requires saveTag()");
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("format", FORMAT);
        ListTag savedSections = new ListTag();
        for (Map.Entry<Integer, Section> entry : sections.entrySet()) {
            CompoundTag savedSection = new CompoundTag();
            savedSection.putInt("y", entry.getKey());
            savedSection.putLongArray("cells", entry.getValue().cells.clone());
            savedSections.add(savedSection);
        }
        tag.put("sections", savedSections);
        return tag;
    }

    /**
     * Attachment serializer boundary: never narrows an opaque input to CompoundTag.
     * Unknown or malformed payloads are returned as deep copies of the original
     * tag, including its tag type. This avoids AttachmentHolder dropping a failed
     * read/write and silently manufacturing a new default attachment.
     */
    public Tag saveTag() {
        return isBlocked() ? opaquePayload.copy() : save();
    }

    /**
     * Existing attachment payloads must have the exact format-1 shape. A missing
     * attachment uses the constructor instead; an existing empty compound is not
     * evidence of an empty valid store. Invalid input leaves zero effective cells
     * and preserves its original payload. There is intentionally no automatic
     * reset, partial salvage, old global-data import, room scan or world edit.
     */
    public static CapsuleChunkGravityData load(Tag original,
                                               int minSectionInclusive, int maxSectionExclusive) {
        CapsuleChunkGravityData data = new CapsuleChunkGravityData(minSectionInclusive, maxSectionExclusive);
        if (!(original instanceof CompoundTag tag))
            return blocked(original, minSectionInclusive, maxSectionExclusive, "invalid_root_type");
        if (!tag.getAllKeys().equals(ROOT_KEYS))
            return blocked(original, minSectionInclusive, maxSectionExclusive, "invalid_root_shape");
        if (!tag.contains("format", Tag.TAG_INT) || tag.getInt("format") != FORMAT)
            return blocked(tag, minSectionInclusive, maxSectionExclusive, "unsupported_format");
        if (!(tag.get("sections") instanceof ListTag savedSections)
                || savedSections.getElementType() != Tag.TAG_COMPOUND
                    && !(savedSections.isEmpty() && savedSections.getElementType() == Tag.TAG_END))
            return blocked(tag, minSectionInclusive, maxSectionExclusive, "invalid_section_list");
        long sectionCapacity = (long) maxSectionExclusive - minSectionInclusive;
        if (savedSections.size() > sectionCapacity)
            return blocked(tag, minSectionInclusive, maxSectionExclusive, "too_many_sections");
        for (int i = 0; i < savedSections.size(); i++) {
            CompoundTag savedSection = savedSections.getCompound(i);
            if (!savedSection.getAllKeys().equals(SECTION_KEYS)
                    || !savedSection.contains("y", Tag.TAG_INT)
                    || !savedSection.contains("cells", Tag.TAG_LONG_ARRAY))
                return blocked(tag, minSectionInclusive, maxSectionExclusive, "invalid_section_shape");
            int sectionY = savedSection.getInt("y");
            if (sectionY < minSectionInclusive || sectionY >= maxSectionExclusive)
                return blocked(tag, minSectionInclusive, maxSectionExclusive, "section_out_of_bounds");
            if (data.sections.containsKey(sectionY))
                return blocked(tag, minSectionInclusive, maxSectionExclusive, "duplicate_section");
            long[] cells = savedSection.getLongArray("cells");
            if (cells.length != LONGS_PER_SECTION)
                return blocked(tag, minSectionInclusive, maxSectionExclusive, "invalid_cell_array_length");
            int occupied = 0;
            for (long word : cells) occupied += Long.bitCount((word | (word >>> 1)) & LOW_BITS);
            if (occupied == 0)
                return blocked(tag, minSectionInclusive, maxSectionExclusive, "noncanonical_empty_section");
            data.sections.put(sectionY, new Section(cells.clone(), occupied));
            data.cellCount += occupied;
        }
        return data;
    }

    private static CapsuleChunkGravityData blocked(Tag original, int minSectionInclusive,
                                                   int maxSectionExclusive, String reason) {
        CapsuleChunkGravityData data = new CapsuleChunkGravityData(minSectionInclusive, maxSectionExclusive);
        data.blockedReason = reason;
        data.opaquePayload = original == null ? new CompoundTag() : original.copy();
        return data;
    }

    private void checkAddress(int sectionY, int localIndex) {
        if (sectionY < minSectionInclusive || sectionY >= maxSectionExclusive)
            throw new IllegalArgumentException("Section outside owning chunk height: " + sectionY);
        if (localIndex < 0 || localIndex >= CELLS_PER_SECTION)
            throw new IllegalArgumentException("Local gravity cell index must be 0..4095");
    }

    private static int codeAt(long[] cells, int localIndex) {
        return (int) ((cells[localIndex >>> 5] >>> ((localIndex & 31) << 1)) & 3L);
    }

    private static final class Section {
        private final long[] cells;
        private int occupied;

        private Section(long[] cells, int occupied) {
            this.cells = cells;
            this.occupied = occupied;
        }
    }
}
