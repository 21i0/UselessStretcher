package com.sorrowmist.useless.stretcher.content.ae;

import appeng.api.stacks.AEItemKey;
import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData;
import com.sorrowmist.useless.stretcher.content.mold.MyriadPatternStore;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Resolves the existing world-owned library; the installed item remains a small UUID reference. */
public final class InstalledStretcherPatterns {
    private static final String DIMENSION = "pattern_dimension";

    private InstalledStretcherPatterns() {
    }

    public static boolean isStretcher(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.USELESS_STRETCHER.get());
    }

    /** Forge 1.20.1 keeps item components in the ordinary item tag. */
    public static void bindDimension(ItemStack stack, ServerLevel level) {
        if (stack == null || level == null) return;
        CompoundTag tag = stack.getTag();
        CompoundTag copy = tag == null ? new CompoundTag() : tag.copy();
        copy.putString(DIMENSION, level.dimension().location().toString());
        stack.setTag(copy);
    }

    public static MyriadPatternStore store(ServerLevel current, ItemStack stack) {
        UUID ref = MyriadMoldData.readPatternRef(stack);
        CompoundTag tag = stack.getTag();
        String storedDimension = tag == null ? "" : tag.getString(DIMENSION);
        ResourceLocation dimension = storedDimension.isBlank() ? null : ResourceLocation.tryParse(storedDimension);
        if (dimension != null) {
            ServerLevel source = current.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            // A missing source dimension must not silently bind to another library.
            return source == null ? new MyriadPatternStore() : MyriadPatternStore.get(source);
        }
        MyriadPatternStore local = MyriadPatternStore.get(current);
        if (ref == null || local.contains(ref)) return local;
        // Old stretchers have only a UUID. Resolve their original loaded dimension without loading chunks.
        for (ServerLevel level : current.getServer().getAllLevels()) {
            if (level == current) continue;
            MyriadPatternStore candidate = MyriadPatternStore.get(level);
            if (candidate.contains(ref)) return candidate;
        }
        return local;
    }

    public static List<AEItemKey> keys(ServerLevel level, ItemStack stack, BlockEntity reader) {
        UUID ref = MyriadMoldData.readPatternRef(stack);
        if (ref == null) return List.of();
        MyriadPatternStore store = store(level, stack);
        if (reader != null) store.watch(ref, reader);
        return store.snapshot(ref);
    }

    /** Expand only when the native provider rebuilds; ordinary pattern stacks stay untouched. */
    public static Iterable<ItemStack> expand(Iterable<ItemStack> physical, BlockEntity reader) {
        if (reader == null || !(reader.getLevel() instanceof ServerLevel level)) return physical;
        var sources = new ArrayList<Iterable<ItemStack>>();
        var libraries = new HashSet<UUID>();
        for (ItemStack stack : physical) {
            if (!isStretcher(stack)) {
                sources.add(List.of(stack));
                continue;
            }
            UUID ref = MyriadMoldData.readPatternRef(stack);
            if (ref != null && libraries.add(ref)) {
                var keys = keys(level, stack, reader);
                sources.add(() -> keys.stream().map(key -> key.toStack(1)).iterator());
            }
        }
        return () -> sources.stream()
                .flatMap(source -> java.util.stream.StreamSupport.stream(source.spliterator(), false))
                .iterator();
    }
}
