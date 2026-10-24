package com.sorrowmist.useless.stretcher.content.mold;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.fml.util.thread.EffectiveSide;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Items carry references only. Legacy inline selections are read for automatic migration.
 */
public final class MyriadMoldData {
    private static final String KEY_ENABLED = "enabled_molds";
    private static final String KEY_PATTERN_REF = "pattern_ref";
    private static final String KEY_AE_REF = "ae_ref";
    private static final Cache<CompoundTag, Set<ResourceLocation>> ENABLED_CACHE =
            CacheBuilder.newBuilder().weakKeys().maximumSize(4096).build();

    private static void update(ItemStack stack, java.util.function.Consumer<CompoundTag> operation) {
        CompoundTag copy = stack.getOrCreateTag().copy(); operation.accept(copy); stack.setTag(copy);
    }
    private MyriadMoldData() {
    }

    public static Set<ResourceLocation> readEnabledMolds(ItemStack stack) {
        UUID ref = readMoldRef(stack);
        if (ref != null) {
            var server = ServerLifecycleHooks.getCurrentServer();
            return server != null && EffectiveSide.get().isServer()
                    ? MyriadMoldStore.get(server).resolve(ref) : Set.of();
        }
        CompoundTag custom = stack.getTag();
        if (custom == null || custom.isEmpty()) return Set.of();
        ListTag list = custom.getList(KEY_ENABLED, Tag.TAG_STRING);
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) result.add(id);
        }
        Set<ResourceLocation> immutable = Set.copyOf(result);
        return immutable;
    }

    public static void writeEnabledMolds(ItemStack stack, Collection<ResourceLocation> molds, ServerLevel level) {
        UUID ref = MyriadMoldStore.get(level.getServer()).snapshot(molds);
        if (ref == null) com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.remove(stack, StretcherComponents.MYRIAD_MOLD_REF.get());
        else com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(stack, StretcherComponents.MYRIAD_MOLD_REF.get(), ref.toString());
        if (ref == null) com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.remove(stack, StretcherComponents.MYRIAD_MOLD_COUNT.get());
        else com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(stack, StretcherComponents.MYRIAD_MOLD_COUNT.get(), molds.size());
        update(stack, tag -> tag.remove(KEY_ENABLED));
    }

    public static UUID readMoldRef(ItemStack stack) {
        String value = com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(stack, StretcherComponents.MYRIAD_MOLD_REF.get());
        if (value == null) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public static boolean externalize(ServerLevel level, ItemStack stack) {
        if (!stack.is(ModItems.OMNIVERSAL_MYRIAD.get())) return false;
        CompoundTag custom = stack.getTag();
        if (custom == null || !custom.contains(KEY_ENABLED)) return false;
        writeEnabledMolds(stack, readEnabledMolds(stack), level);
        return true;
    }

    /** Cheap component-only check used by the global container migration guard. */
    public static boolean isLegacyInline(ItemStack stack) {
        if (!stack.is(ModItems.OMNIVERSAL_MYRIAD.get())) return false;
        CompoundTag custom = stack.getTag();
        return custom != null && !custom.isEmpty() && custom.contains(KEY_ENABLED)
                && readMoldRef(stack) == null;
    }

    public static boolean hasEnabledMolds(ItemStack stack) {
        if (readMoldRef(stack) != null && EffectiveSide.get().isClient()) {
            return com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.getOrDefault(stack, StretcherComponents.MYRIAD_MOLD_COUNT.get(), 0) > 0;
        }
        return !readEnabledMolds(stack).isEmpty();
    }

    public static boolean isMoldEnabled(ItemStack stack, ResourceLocation moldId) {
        return moldId != null && readEnabledMolds(stack).contains(moldId);
    }

    public static UUID readPatternRef(ItemStack stack) {
        CompoundTag custom = stack.getTag();
        if (custom == null || custom.isEmpty()) return null;
        String value = custom.getString(KEY_PATTERN_REF);
        if (value.isEmpty()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static void writePatternRef(ItemStack stack, UUID id) {
        update(stack, tag -> {
            if (id == null) tag.remove(KEY_PATTERN_REF);
            else tag.putString(KEY_PATTERN_REF, id.toString());
        });
    }

    public static UUID readAeRef(ItemStack stack) {
        CompoundTag custom = stack.getTag();
        if (custom == null || custom.isEmpty()) return null;
        String value = custom.getString(KEY_AE_REF);
        if (value.isEmpty()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static void writeAeRef(ItemStack stack, UUID id) {
        update(stack, tag -> {
            if (id == null) tag.remove(KEY_AE_REF);
            else tag.putString(KEY_AE_REF, id.toString());
        });
    }
}
