package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog.Entry;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.library.recipes.collect.RecipeMap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogJeiPublicationTimingTest {
    @SuppressWarnings("unchecked")
    @Test void exactNativeArgumentsArePassedThroughOnceWithoutAnActivePublicationScope() {
        IRecipeCategory<Entry> category = mock(IRecipeCategory.class);
        var builder = mock(IRecipeLayoutBuilder.class);
        var focuses = mock(IFocusGroup.class);
        var entry = entry(1);
        CatalogJeiPublicationTiming.build(category, builder, entry, focuses);
        verify(category).setRecipe(same(builder), same(entry), same(focuses));
        verifyNoMoreInteractions(category);
    }

    @SuppressWarnings("unchecked")
    @Test void nativeBuildAndIndexTimesAndIdentifiersAreMeasuredWithoutExpandingTheRecipe() {
        var clock = new AtomicLong();
        var timing = new CatalogJeiPublicationTiming(clock::get);
        IRecipeCategory<Entry> category = mock(IRecipeCategory.class);
        var map = mock(RecipeMap.class);
        RecipeType<Entry> type = mock(RecipeType.class);
        var supplier = mock(IIngredientSupplier.class);
        var builder = mock(IRecipeLayoutBuilder.class);
        var focuses = mock(IFocusGroup.class);
        var entry = entry(2);
        doAnswer(call -> { clock.addAndGet(30); return null; }).when(category).setRecipe(builder, entry, focuses);
        doAnswer(call -> { clock.addAndGet(40); return null; }).when(map).addRecipe(type, entry, supplier);
        try (var scope = timing.begin("ADD", List.of(entry))) {
            CatalogJeiPublicationTiming.build(category, builder, entry, focuses);
            CatalogJeiPublicationTiming.index(map, type, entry, supplier);
            clock.addAndGet(5);
        }
        timing.guard("ADD:before", 9);
        var result = timing.snapshot();
        assertEquals(1, result.nativeCalls());
        assertEquals(75, result.nativeNanos());
        assertEquals(30, result.buildNanos());
        assertEquals(40, result.indexNanos());
        assertEquals(9, result.guardNanos());
        assertTrue(result.maximumNative().contains("batch=1"));
        assertTrue(result.maximumBuild().contains("id=timing_test:r2"));
        assertTrue(result.maximumIndex().contains("source=modified-pack"));
        verify(map).addRecipe(same(type), same(entry), same(supplier));
        verifyNoInteractions(supplier);
    }

    @SuppressWarnings("unchecked")
    @Test void originalNativeExceptionIsPropagatedAndScopeCannotLeakOrReplayTheCall() {
        var clock = new AtomicLong();
        var timing = new CatalogJeiPublicationTiming(clock::get);
        IRecipeCategory<Entry> category = mock(IRecipeCategory.class);
        var builder = mock(IRecipeLayoutBuilder.class);
        var focuses = mock(IFocusGroup.class);
        var entry = entry(3);
        var error = new IllegalStateException("native category failure");
        doAnswer(call -> { clock.addAndGet(7); throw error; }).when(category).setRecipe(builder, entry, focuses);
        assertSame(error, assertThrows(IllegalStateException.class, () -> {
            try (var scope = timing.begin("ADD", List.of(entry))) {
                CatalogJeiPublicationTiming.build(category, builder, entry, focuses);
            }
        }));
        assertEquals(1, timing.snapshot().nativeCalls());
        assertEquals(7, timing.snapshot().buildNanos());
        assertSame(error, assertThrows(IllegalStateException.class,
                () -> CatalogJeiPublicationTiming.build(category, builder, entry, focuses)));
        assertEquals(1, timing.snapshot().buildCalls(), "scope was removed, so later calls cannot pollute its timing");
        verify(category, times(2)).setRecipe(builder, entry, focuses);
    }

    @SuppressWarnings("unchecked")
    @Test void unrelatedRecipeAndEqualButDifferentEntryObjectsAreNotAttributedToThisBatch() {
        var clock = new AtomicLong();
        var timing = new CatalogJeiPublicationTiming(clock::get);
        IRecipeCategory<Object> category = mock(IRecipeCategory.class);
        var builder = mock(IRecipeLayoutBuilder.class);
        var focuses = mock(IFocusGroup.class);
        Entry entry = entry(4), equal = entry(4);
        assertEquals(entry, equal);
        assertNotSame(entry, equal);
        try (var scope = timing.begin("ADD", List.of(entry))) {
            CatalogJeiPublicationTiming.build(category, builder, new Object(), focuses);
            CatalogJeiPublicationTiming.build(category, builder, equal, focuses);
        }
        assertEquals(0, timing.snapshot().buildCalls());
        verify(category, times(2)).setRecipe(same(builder), any(), same(focuses));
    }

    @SuppressWarnings("unchecked")
    @Test void nestedScopeRestoresTheOuterOwnerAndCloseIsIdempotent() {
        var clock = new AtomicLong();
        var first = new CatalogJeiPublicationTiming(clock::get);
        var second = new CatalogJeiPublicationTiming(clock::get);
        IRecipeCategory<Entry> category = mock(IRecipeCategory.class);
        var builder = mock(IRecipeLayoutBuilder.class);
        var focuses = mock(IFocusGroup.class);
        var entry = entry(5);
        doAnswer(call -> { clock.incrementAndGet(); return null; }).when(category).setRecipe(builder, entry, focuses);
        var outer = first.begin("ADD", List.of(entry));
        try (var inner = second.begin("ADD", List.of(entry))) {
            CatalogJeiPublicationTiming.build(category, builder, entry, focuses);
        }
        CatalogJeiPublicationTiming.build(category, builder, entry, focuses);
        outer.close(); outer.close();
        assertEquals(1, first.snapshot().nativeCalls());
        assertEquals(1, second.snapshot().nativeCalls());
        assertEquals(1, first.snapshot().buildCalls());
        assertEquals(1, second.snapshot().buildCalls());
    }

    @Test void slowDiagnosticOutputIsBoundedButAllSlowCallsAreStillCounted() {
        var clock = new AtomicLong();
        var timing = new CatalogJeiPublicationTiming(clock::get);
        for (int i = 0; i < 20; i++) {
            try (var scope = timing.begin("ADD", List.of(entry(i)))) { clock.addAndGet(20_000_000); }
        }
        assertEquals(20, timing.snapshot().nativeCalls());
        assertEquals(20, timing.snapshot().slowCalls());
        assertEquals(8, timing.snapshot().slowReports());
    }

    private static Entry entry(int number) {
        return new Entry(new AlloyFurnaceRecipeIdentity(ResourceLocation.parse("timing_test:r" + number), "fp" + number),
                null, "modified-pack");
    }
}
