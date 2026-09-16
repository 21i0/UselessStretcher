package com.sorrowmist.useless.stretcher.content.range;

import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Validated data-component access for the range placement settings stored on a staff. */
public final class RangeAccelerationSettings {
    public static final int DEFAULT_SIZE = 3;

    private RangeAccelerationSettings() {
    }

    public static boolean placementMode(ItemStack staff) {
        return staff.getOrDefault(StretcherComponents.RANGE_PLACEMENT_MODE.get(), false);
    }

    public static boolean filterMarkingMode(ItemStack staff) {
        return staff.getOrDefault(StretcherComponents.RANGE_FILTER_MARKING_MODE.get(), false);
    }

    public static boolean markSleepList(ItemStack staff) {
        return staff.getOrDefault(StretcherComponents.RANGE_MARK_SLEEP_LIST.get(), false);
    }

    public static int sizeX(ItemStack staff) {
        return RangeAccelerationSavedData.clampSize(
                staff.getOrDefault(StretcherComponents.RANGE_SIZE_X.get(), DEFAULT_SIZE));
    }

    public static int sizeY(ItemStack staff) {
        return RangeAccelerationSavedData.clampSize(
                staff.getOrDefault(StretcherComponents.RANGE_SIZE_Y.get(), DEFAULT_SIZE));
    }

    public static int sizeZ(ItemStack staff) {
        return RangeAccelerationSavedData.clampSize(
                staff.getOrDefault(StretcherComponents.RANGE_SIZE_Z.get(), DEFAULT_SIZE));
    }

    public static int offsetX(ItemStack staff) {
        return RangeAccelerationSavedData.clampOffset(
                staff.getOrDefault(StretcherComponents.RANGE_OFFSET_X.get(), 0));
    }

    public static int offsetY(ItemStack staff) {
        return RangeAccelerationSavedData.clampOffset(
                staff.getOrDefault(StretcherComponents.RANGE_OFFSET_Y.get(), 0));
    }

    public static int offsetZ(ItemStack staff) {
        return RangeAccelerationSavedData.clampOffset(
                staff.getOrDefault(StretcherComponents.RANGE_OFFSET_Z.get(), 0));
    }

    public static boolean idleThrottleDisabled(ItemStack staff) {
        return staff.getOrDefault(StretcherComponents.RANGE_IDLE_THROTTLE_DISABLED.get(), false);
    }

    /** Empty sleep blacklist = every target may sleep; empty sleep whitelist = no target may sleep. */
    public static boolean sleepWhitelistMode(ItemStack staff) {
        return idleThrottleDisabled(staff);
    }

    public static boolean whitelistMode(ItemStack staff) {
        return staff.getOrDefault(StretcherComponents.RANGE_WHITELIST_MODE.get(), false);
    }

    public static int accelerationMarkCount(ItemStack staff) {
        return Math.max(0, staff.getOrDefault(StretcherComponents.RANGE_ACCELERATION_MARK_COUNT.get(), 0));
    }

    public static int sleepMarkCount(ItemStack staff) {
        return Math.max(0, staff.getOrDefault(StretcherComponents.RANGE_SLEEP_MARK_COUNT.get(), 0));
    }

    public static void setMarkCounts(ItemStack staff, int accelerationCount, int sleepCount) {
        staff.set(StretcherComponents.RANGE_ACCELERATION_MARK_COUNT.get(), Math.max(0, accelerationCount));
        staff.set(StretcherComponents.RANGE_SLEEP_MARK_COUNT.get(), Math.max(0, sleepCount));
    }

    public static List<ResourceLocation> filters(ItemStack staff) {
        List<ResourceLocation> values = staff.getOrDefault(StretcherComponents.RANGE_FILTERS.get(), List.of());
        if (values.size() <= RangeAccelerationSavedData.MAX_FILTERS) return List.copyOf(values);
        return List.copyOf(values.subList(0, RangeAccelerationSavedData.MAX_FILTERS));
    }

    public static boolean toggleFilter(ItemStack staff, ResourceLocation id) {
        if (id == null) return false;
        List<ResourceLocation> values = new ArrayList<>(filters(staff));
        boolean added;
        if (values.remove(id)) {
            added = false;
        } else {
            if (values.size() >= RangeAccelerationSavedData.MAX_FILTERS) return false;
            values.add(id);
            added = true;
        }
        staff.set(StretcherComponents.RANGE_FILTERS.get(), List.copyOf(values));
        return added;
    }

    public static void set(ItemStack staff, boolean placementMode, boolean filterMarkingMode,
                           boolean markSleepList,
                           int sizeX, int sizeY, int sizeZ,
                           int offsetX, int offsetY, int offsetZ,
                           boolean accelerationWhitelistMode, boolean sleepWhitelistMode) {
        // These are both exclusive interaction modes. Marking wins if malformed data enables both.
        if (filterMarkingMode) placementMode = false;
        staff.set(StretcherComponents.RANGE_PLACEMENT_MODE.get(), placementMode);
        staff.set(StretcherComponents.RANGE_FILTER_MARKING_MODE.get(), filterMarkingMode);
        staff.set(StretcherComponents.RANGE_MARK_SLEEP_LIST.get(), markSleepList);
        staff.set(StretcherComponents.RANGE_SIZE_X.get(), RangeAccelerationSavedData.clampSize(sizeX));
        staff.set(StretcherComponents.RANGE_SIZE_Y.get(), RangeAccelerationSavedData.clampSize(sizeY));
        staff.set(StretcherComponents.RANGE_SIZE_Z.get(), RangeAccelerationSavedData.clampSize(sizeZ));
        staff.set(StretcherComponents.RANGE_OFFSET_X.get(), RangeAccelerationSavedData.clampOffset(offsetX));
        staff.set(StretcherComponents.RANGE_OFFSET_Y.get(), RangeAccelerationSavedData.clampOffset(offsetY));
        staff.set(StretcherComponents.RANGE_OFFSET_Z.get(), RangeAccelerationSavedData.clampOffset(offsetZ));
        staff.set(StretcherComponents.RANGE_IDLE_THROTTLE_DISABLED.get(), sleepWhitelistMode);
        staff.set(StretcherComponents.RANGE_WHITELIST_MODE.get(), accelerationWhitelistMode);
    }

    /** Compatibility overload for callers that do not expose offsets. */
    public static void set(ItemStack staff, boolean placementMode, boolean filterMarkingMode,
                           boolean markSleepList,
                           int sizeX, int sizeY, int sizeZ,
                           boolean accelerationWhitelistMode, boolean sleepWhitelistMode) {
        set(staff, placementMode, filterMarkingMode, markSleepList,
                sizeX, sizeY, sizeZ, 0, 0, 0,
                accelerationWhitelistMode, sleepWhitelistMode);
    }
}
