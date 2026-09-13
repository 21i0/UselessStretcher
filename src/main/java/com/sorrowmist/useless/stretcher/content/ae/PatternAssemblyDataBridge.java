package com.sorrowmist.useless.stretcher.content.ae;

import com.sorrowmist.useless.core.component.MultiblockPartData;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Converts between legacy inline assembly data and the small external-store reference. */
public final class PatternAssemblyDataBridge {
    private PatternAssemblyDataBridge() {
    }

    public static boolean externalize(ServerLevel level, ItemStack stack) {
        return externalize(level, stack, null) != null;
    }

    public static UUID externalize(ServerLevel level, ItemStack stack, UUID preferredReference) {
        MultiblockPartData inline = stack.get(UComponents.MULTIBLOCK_PART_DATA.get());
        if (inline == null) return null;
        UUID id = PatternAssemblyItemStore.get(level).release(preferredReference, inline);
        stack.remove(UComponents.MULTIBLOCK_PART_DATA.get());
        stack.set(StretcherComponents.PATTERN_ASSEMBLY_REF.get(), id.toString());
        return id;
    }

    public static MultiblockPartData resolve(ServerLevel level, ItemStack stack) {
        return PatternAssemblyItemStore.get(level).get(reference(stack));
    }

    public static PatternAssemblyItemStore.Claim claim(ServerLevel level, ItemStack stack) {
        return PatternAssemblyItemStore.get(level).claim(reference(stack));
    }

    public static UUID reference(ItemStack stack) {
        String value = stack.get(StretcherComponents.PATTERN_ASSEMBLY_REF.get());
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
