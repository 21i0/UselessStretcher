package com.sorrowmist.useless.stretcher.content.mold;

import appeng.crafting.pattern.AEProcessingPattern;
import com.sorrowmist.useless.compat.forge1201.recipe.NativeProcessingPatterns;
import com.sorrowmist.useless.compat.forge1201.recipe.NativeCraftingPatternData;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;

import java.util.List;

/** Reads encoded outputs without resolving a recipe or replacing its binding metadata. */
public final class PatternOutputs {
    private PatternOutputs() { }
    record Processing(List<GenericStack> inputs, List<GenericStack> outputs) {}
    static Processing processing(AEItemKey key) {
        if (key == null || !NativeProcessingPatterns.hasPayload(key.toStack(1))) return null;
        var tag = key.getTag();
        return new Processing(read(tag.getList("in",10)), read(tag.getList("out",10)));
    }
    private static List<GenericStack> read(net.minecraft.nbt.ListTag list) {
        return java.util.stream.IntStream.range(0,list.size()).mapToObj(i -> GenericStack.readTag(list.getCompound(i))).toList();
    }

    public static GenericStack primary(AEItemKey pattern) {
        var processing = processing(pattern);
        if (processing != null) {
            GenericStack first = null;
            long amount = 0;
            for (GenericStack output : processing.outputs()) {
                if (output == null || output.amount() <= 0) continue;
                if (first == null) first = output;
                if (first.what().equals(output.what())) {
                    if (amount > Long.MAX_VALUE - output.amount()) return null;
                    amount += output.amount();
                }
            }
            return first == null ? null : new GenericStack(first.what(), amount);
        }
        var crafting = NativeCraftingPatternData.read(pattern);
        if (crafting != null) return GenericStack.fromItemStack(crafting.result());
        return null;
    }

    public static AEItemKey withoutByproducts(AEItemKey pattern) {
        var encoded = processing(pattern);
        if (encoded == null) return pattern;
        GenericStack primary = primary(pattern);
        if (primary == null || encoded.outputs().stream().noneMatch(
                output -> output != null && !primary.what().equals(output.what()))) return pattern;
        var replacement = pattern.toStack(1);
        NativeProcessingPatterns.write(replacement, encoded.inputs(), List.of(primary));
        var data = com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(pattern, UComponents.OMNIVERSAL_PATTERN_DATA.get());
        if (data != null) {
            // Dynamic output flags refer to condensed output slots, not the recipe's identity.
            com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(replacement, UComponents.OMNIVERSAL_PATTERN_DATA.get(), new OmniversalPatternData(
                    data.version(), data.recipeId(), data.recipeFingerprint(), data.sourceId(),
                    data.requiresMold(), data.displayMold(), data.displayMolds(), data.tagInputSlots(),
                    data.fluidTagInputSlots(), data.moldTagInputSlots(), data.itemIdInputSlots(),
                    data.itemIdOutputSlots().contains(0) ? List.of(0) : List.of()));
        }
        return AEItemKey.of(replacement);
    }
}
