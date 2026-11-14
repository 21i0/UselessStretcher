package com.sorrowmist.useless.stretcher.content.ae;

import appeng.api.stacks.AEItemKey;
import com.sorrowmist.useless.stretcher.content.mold.MyriadMoldData;
import com.sorrowmist.useless.stretcher.content.mold.MyriadPatternStore;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Resolves the existing world-owned library; the installed item remains a small UUID reference. */
public final class InstalledStretcherPatterns {
    private static final String DIMENSION = "pattern_dimension";
    private static final String ASSEMBLY_SNAPSHOT = "assembly_snapshot_ref";
    private static final String ASSEMBLY_DIMENSION = "assembly_snapshot_dimension";
    private InstalledStretcherPatterns() { }

    public static boolean isStretcher(ItemStack stack) {
        return stack.is(ModItems.USELESS_STRETCHER.get());
    }

    public static void bindDimension(ItemStack stack, ServerLevel level) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putString(DIMENSION, level.dimension().location().toString()));
    }

    public static MyriadPatternStore store(ServerLevel current, ItemStack stack) {
        ServerLevel source = sourceLevel(current, stack);
        return source == null ? new MyriadPatternStore() : MyriadPatternStore.get(source);
    }

    private static ServerLevel sourceLevel(ServerLevel current, ItemStack stack) {
        UUID ref = MyriadMoldData.readPatternRef(stack);
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        String storedDimension = data.copyTag().getString(DIMENSION);
        ResourceLocation dimension = storedDimension.isBlank() ? null : ResourceLocation.tryParse(storedDimension);
        if (!storedDimension.isBlank()) {
            // A missing source dimension must not silently bind to another library.
            return dimension == null ? null
                    : current.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        }
        MyriadPatternStore local = MyriadPatternStore.get(current);
        if (ref == null || local.contains(ref)) return current;
        // Old stretchers have only a UUID. Resolve their original loaded dimension without loading chunks.
        for (ServerLevel level : current.getServer().getAllLevels()) {
            if (level == current) continue;
            MyriadPatternStore candidate = MyriadPatternStore.get(level);
            if (candidate.contains(ref)) return level;
        }
        return current;
    }

    /** An installed snapshot keeps its identity when extracted, saved or moved to another machine. */
    public static boolean isAssemblySnapshot(ItemStack stack) {
        UUID ref = MyriadMoldData.readPatternRef(stack);
        if (ref == null) return false;
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return ref.toString().equals(tag.getString(ASSEMBLY_SNAPSHOT))
                && !tag.getString(DIMENSION).isBlank()
                && tag.getString(DIMENSION).equals(tag.getString(ASSEMBLY_DIMENSION));
    }

    public static boolean canSnapshotForAssembly(ServerLevel current, ItemStack original) {
        UUID ref = MyriadMoldData.readPatternRef(original);
        if (ref == null) return false;
        ServerLevel source = sourceLevel(current, original);
        return source != null && MyriadPatternStore.get(source).isResolved(ref);
    }

    /** Called only for a real installation, never for insertion simulation or client-side validation. */
    public static ItemStack snapshotForAssembly(ServerLevel current, ItemStack original) {
        UUID ref = MyriadMoldData.readPatternRef(original);
        if (ref == null) return ItemStack.EMPTY;
        ServerLevel source = sourceLevel(current, original);
        if (source == null || !MyriadPatternStore.get(source).isResolved(ref)) return ItemStack.EMPTY;
        // A real reinstallation forks too: another extracted/creative copy must not edit this machine's UUID.
        UUID snapshot = MyriadPatternStore.get(source).fork(ref);
        ItemStack installed = original.copy();
        MyriadMoldData.writePatternRef(installed, snapshot);
        bindDimension(installed, source);
        CustomData.update(DataComponents.CUSTOM_DATA, installed, tag -> {
            tag.putString(ASSEMBLY_SNAPSHOT, snapshot.toString());
            tag.putString(ASSEMBLY_DIMENSION, source.dimension().location().toString());
        });
        return installed;
    }

    public static void clearAssemblySnapshot(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(ASSEMBLY_SNAPSHOT);
            tag.remove(ASSEMBLY_DIMENSION);
        });
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
        if (!(reader.getLevel() instanceof ServerLevel level)) return physical;
        var sources = new ArrayList<Iterable<ItemStack>>();
        var libraries = new HashSet<UUID>();
        for (ItemStack stack : physical) {
            if (!isStretcher(stack)) sources.add(List.of(stack));
            else {
                UUID ref = MyriadMoldData.readPatternRef(stack);
                if (ref != null && libraries.add(ref)) {
                    var keys = keys(level, stack, reader);
                    sources.add(() -> keys.stream().map(key -> key.toStack(1)).iterator());
                }
            }
        }
        return () -> sources.stream().flatMap(source -> java.util.stream.StreamSupport.stream(source.spliterator(), false)).iterator();
    }
}
