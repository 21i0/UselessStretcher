package com.sorrowmist.useless.stretcher.content.mold;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Reads and writes the small NBT carried by the block / stretcher items: the enabled mold set and
 * a {@link UUID} reference into {@link MyriadPatternStore}. The heavy pattern data lives in the
 * store, not in the item.
 */
public final class MyriadMoldData {
    private static final String KEY_ENABLED = "enabled_molds";
    private static final String KEY_PATTERN_REF = "pattern_ref";
    private static final String KEY_AE_REF = "ae_ref";

    private MyriadMoldData() {
    }

    public static Set<ResourceLocation> readEnabledMolds(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null || custom.isEmpty()) return new LinkedHashSet<>();
        ListTag list = custom.copyTag().getList(KEY_ENABLED, Tag.TAG_STRING);
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) result.add(id);
        }
        return result;
    }

    public static void writeEnabledMolds(ItemStack stack, Collection<ResourceLocation> molds) {
        ListTag list = new ListTag();
        for (ResourceLocation id : molds) {
            if (id != null) list.add(StringTag.valueOf(id.toString()));
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(KEY_ENABLED, list));
    }

    public static boolean isMoldEnabled(ItemStack stack, ResourceLocation moldId) {
        return moldId != null && readEnabledMolds(stack).contains(moldId);
    }

    public static UUID readPatternRef(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null || custom.isEmpty()) return null;
        String value = custom.copyTag().getString(KEY_PATTERN_REF);
        if (value.isEmpty()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static void writePatternRef(ItemStack stack, UUID id) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (id == null) tag.remove(KEY_PATTERN_REF);
            else tag.putString(KEY_PATTERN_REF, id.toString());
        });
    }

    public static UUID readAeRef(ItemStack stack) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null || custom.isEmpty()) return null;
        String value = custom.copyTag().getString(KEY_AE_REF);
        if (value.isEmpty()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static void writeAeRef(ItemStack stack, UUID id) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (id == null) tag.remove(KEY_AE_REF);
            else tag.putString(KEY_AE_REF, id.toString());
        });
    }
}
