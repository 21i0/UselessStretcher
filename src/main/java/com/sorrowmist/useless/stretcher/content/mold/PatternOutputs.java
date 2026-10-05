package com.sorrowmist.useless.stretcher.content.mold;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.EncodedProcessingPattern;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;

import java.util.List;

/** Reads encoded outputs without resolving a recipe or replacing its binding metadata. */
public final class PatternOutputs {
    private PatternOutputs() { }

    public static GenericStack primary(AEItemKey pattern) {
        var processing = pattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (processing != null) {
            GenericStack first = null;
            long amount = 0;
            for (GenericStack output : processing.sparseOutputs()) {
                if (output == null || output.amount() <= 0) continue;
                if (first == null) first = output;
                if (first.what().equals(output.what())) {
                    if (amount > Long.MAX_VALUE - output.amount()) return null;
                    amount += output.amount();
                }
            }
            return first == null ? null : new GenericStack(first.what(), amount);
        }
        var crafting = pattern.get(AEComponents.ENCODED_CRAFTING_PATTERN);
        if (crafting != null) return GenericStack.fromItemStack(crafting.result());
        var stonecutting = pattern.get(AEComponents.ENCODED_STONECUTTING_PATTERN);
        if (stonecutting != null) return GenericStack.fromItemStack(stonecutting.output());
        var smithing = pattern.get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN);
        return smithing == null ? null : GenericStack.fromItemStack(smithing.resultItem());
    }

    public static AEItemKey withoutByproducts(AEItemKey pattern) {
        var encoded = pattern.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (encoded == null) return pattern;
        GenericStack primary = primary(pattern);
        if (primary == null || encoded.sparseOutputs().stream().noneMatch(
                output -> output != null && !primary.what().equals(output.what()))) return pattern;
        var replacement = pattern.toStack(1);
        replacement.set(AEComponents.ENCODED_PROCESSING_PATTERN,
                new EncodedProcessingPattern(encoded.sparseInputs(), List.of(primary)));
        var data = pattern.get(UComponents.OMNIVERSAL_PATTERN_DATA.get());
        if (data != null) {
            // Dynamic output flags refer to condensed output slots, not the recipe's identity.
            replacement.set(UComponents.OMNIVERSAL_PATTERN_DATA.get(), new OmniversalPatternData(
                    data.version(), data.recipeId(), data.recipeFingerprint(), data.sourceId(),
                    data.requiresMold(), data.displayMold(), data.displayMolds(), data.tagInputSlots(),
                    data.fluidTagInputSlots(), data.moldTagInputSlots(), data.itemIdInputSlots(),
                    data.itemIdOutputSlots().contains(0) ? List.of(0) : List.of()));
        }
        return AEItemKey.of(replacement);
    }
}
