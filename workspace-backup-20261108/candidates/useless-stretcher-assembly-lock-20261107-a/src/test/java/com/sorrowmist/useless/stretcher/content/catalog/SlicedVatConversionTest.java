package com.sorrowmist.useless.stretcher.content.catalog;

import com.enderio.enderio.content.machines.vat.FermentingRecipe;
import com.enderio.enderio.init.EIOBlocks;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.adapters.enderio.VatFermentingRecipeAdapter;
import com.sorrowmist.useless.stretcher.VanillaTestRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.flag.FeatureFlagRegistry;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native adapter versus cooperative output, with real items/tags/fluids and complete native codecs. */
class SlicedVatConversionTest {
    @BeforeAll static void initialize() throws Exception {
        VanillaTestRegistries.initialize();
        // Native block declarations read two mod-loader feature flags even though no
        // block is instantiated here. Supply aliases only during their initialization.
        Field names = FeatureFlagRegistry.class.getDeclaredField("names"); names.setAccessible(true);
        Object previous = names.get(FeatureFlags.REGISTRY);
        var flags = new java.util.LinkedHashMap<>(FeatureFlags.REGISTRY.getAllFlags());
        flags.put(ResourceLocation.parse("enderio:enderface"), FeatureFlags.VANILLA);
        flags.put(ResourceLocation.parse("enderio:niard"), FeatureFlags.VANILLA);
        names.set(FeatureFlags.REGISTRY, flags);
        try { Class.forName(EIOBlocks.class.getName()); }
        finally { names.set(FeatureFlags.REGISTRY, previous); }
        // Register the real NeoForge component ingredient codec in this standalone
        // registry; the normal mod loading registration event does not run in JUnit.
        var componentId = ResourceLocation.parse("neoforge:components");
        var componentType = NeoForgeRegistries.INGREDIENT_TYPES.get(componentId);
        if (componentType == null) componentType = Registry.register(NeoForgeRegistries.INGREDIENT_TYPES,
                componentId, new IngredientType<>(DataComponentIngredient.CODEC));
        Field holder = DeferredHolder.class.getDeclaredField("holder"); holder.setAccessible(true);
        holder.set(NeoForgeMod.DATA_COMPONENT_INGREDIENT_TYPE,
                NeoForgeRegistries.INGREDIENT_TYPES.wrapAsHolder(componentType));
    }

    @Test void overlappingReagentsKeepNativeReversePairOrderIdsAndComponents() throws Exception {
        try (var fixture = new Fixture()) {
            var holder = fixture.recipe(175, 33);
            try (var modifiers = mockStatic(FermentingRecipe.class, CALLS_REAL_METHODS)) {
                modifiers.when(() -> FermentingRecipe.getModifier(any(ItemStack.class), any()))
                        .thenAnswer(call -> ((ItemStack) call.getArgument(0)).is(Items.IRON_INGOT) ? 1.25 : 2.5);
                var actual = fixture.compare(holder);
                assertEquals(4, actual.size());
                assertEquals(Items.IRON_INGOT, actual.get(0).inputs().get(0).ingredient().getItems()[0].getItem());
                assertEquals(Items.GOLD_INGOT, actual.get(1).inputs().get(1).ingredient().getItems()[0].getItem());
                assertEquals(Items.GOLD_INGOT, actual.get(2).inputs().get(0).ingredient().getItems()[0].getItem());
                assertEquals("retained", actual.getLast().outputFluids().getFirst()
                        .get(DataComponents.CUSTOM_DATA).copyTag().getString("component_marker"));
            }
        }
    }

    @Test void factoryProbeDoesNotCallNativeConversionHelpers() throws Exception {
        try (var fixture = new Fixture(); var helpers = mockStatic(VatFermentingRecipeAdapter.class, CALLS_REAL_METHODS)) {
            var cursor = SlicedRecipeConversions.tryCreate(fixture.adapter, fixture.recipe(100, 40), null);
            assertNotNull(cursor);
            helpers.verifyNoInteractions();
        }
    }

    @Test void incompatibleReflectionAbiReturnsNativeFallbackInsteadOfAFailingCursor() throws Exception {
        try (var fixture = new Fixture()) {
            var probe = SlicedRecipeConversions.class.getDeclaredMethod("probe", Class.class, RecipeHolder.class);
            probe.setAccessible(true);
            assertNull(probe.invoke(null, Object.class, fixture.recipe(100, 40)));
        }
    }

    @Test void identicalMultiplierResultsRetainNativeSingleTagCompression() throws Exception {
        try (var fixture = new Fixture(); var modifiers = mockStatic(FermentingRecipe.class, CALLS_REAL_METHODS)) {
            modifiers.when(() -> FermentingRecipe.getModifier(any(ItemStack.class), any())).thenReturn(2.0);
            var actual = fixture.compare(fixture.recipe(250, 40));
            assertEquals(1, actual.size());
            assertEquals(1000, actual.getFirst().outputFluids().getFirst().getAmount());
            assertEquals(2, actual.getFirst().inputs().getFirst().ingredient().getItems().length);
        }
    }

    @Test void equalRoundedAmountsWithDifferentMultipliersAreNotCollapsed() throws Exception {
        try (var fixture = new Fixture(); var modifiers = mockStatic(FermentingRecipe.class, CALLS_REAL_METHODS)) {
            modifiers.when(() -> FermentingRecipe.getModifier(any(ItemStack.class), any()))
                    .thenAnswer(call -> ((ItemStack) call.getArgument(0)).is(Items.IRON_INGOT) ? 1.1 : 1.2);
            var actual = fixture.compare(fixture.recipe(1, 40));
            assertEquals(4, actual.size());
            assertTrue(actual.stream().allMatch(recipe -> recipe.outputFluids().getFirst().getAmount() == 1));
        }
    }

    @Test void incrementalTagDedupMatchesNativeCountInsensitiveComponentEquality() throws Exception {
        ItemStack one = new ItemStack(Items.IRON_INGOT, 1);
        ItemStack many = new ItemStack(Items.IRON_INGOT, 19);
        ItemStack named = one.copy(); named.set(DataComponents.CUSTOM_NAME, Component.literal("different"));
        ItemStack[] offered = {null, ItemStack.EMPTY, one, many, named, new ItemStack(Items.GOLD_INGOT)};
        Class<?> utils = Class.forName("com.sorrowmist.useless.content.recipe.adapters.enderio.EnderIOAdapterUtils");
        var nativeDistinct = utils.getDeclaredMethod("distinctStacks", List.class); nativeDistinct.setAccessible(true);
        @SuppressWarnings("unchecked") List<ItemStack> expected = (List<ItemStack>) nativeDistinct.invoke(null, java.util.Arrays.asList(offered));
        Class<?> sliced = Class.forName(SlicedRecipeConversions.class.getName() + "$DistinctStacks");
        var constructor = sliced.getDeclaredConstructor(ItemStack[].class); constructor.setAccessible(true);
        Object cursor = constructor.newInstance((Object) offered);
        var advance = sliced.getDeclaredMethod("advance"); advance.setAccessible(true);
        for (int step = 0; step < offered.length; step++) assertEquals(step == offered.length - 1, advance.invoke(cursor));
        var values = sliced.getDeclaredField("values"); values.setAccessible(true);
        @SuppressWarnings("unchecked") List<ItemStack> actual = (List<ItemStack>) values.get(cursor);
        assertEquals(3, actual.size());
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            assertTrue(ItemStack.isSameItemSameComponents(expected.get(index), actual.get(index)));
            assertEquals(expected.get(index).getCount(), actual.get(index).getCount());
            assertNotSame(offered[2], actual.get(index));
        }
    }

    @Test void invalidLatePairDiscardsWholeConversionBeforePublishingAnyOutput() throws Exception {
        try (var fixture = new Fixture(); var modifiers = mockStatic(FermentingRecipe.class, CALLS_REAL_METHODS)) {
            modifiers.when(() -> FermentingRecipe.getModifier(any(ItemStack.class), any()))
                    .thenAnswer(call -> ((ItemStack) call.getArgument(0)).is(Items.GOLD_INGOT) ? Double.NaN : 1.0);
            assertTrue(fixture.compare(fixture.recipe(100, 40)).isEmpty());
            assertTrue(fixture.compare(fixture.recipe(Integer.MAX_VALUE, 40)).isEmpty());
        }
    }

    @Test void invalidSourceAndEmptyTagDisplayMatchNativeAndForeignThreadCannotAdvance() throws Exception {
        try (var fixture = new Fixture()) {
            assertTrue(fixture.compare(fixture.recipe(100, 0)).isEmpty());
            var cursor = SlicedRecipeConversions.tryCreate(fixture.adapter, fixture.recipe(100, 40), null);
            var thrown = new AtomicReference<Throwable>();
            Thread worker = Thread.ofPlatform().unstarted(() -> {
                try { cursor.advance(ignored -> fail("foreign thread output")); }
                catch (Throwable error) { thrown.set(error); }
            });
            worker.start(); worker.join();
            assertInstanceOf(IllegalStateException.class, thrown.get());
            BuiltInRegistries.ITEM.bindTags(Map.of(fixture.first, List.of(), fixture.second, List.of()));
            // Vanilla exposes the empty-tag display barrier; preserve the adapter's behavior.
            fixture.compare(fixture.recipe(100, 40));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final VatFermentingRecipeAdapter adapter = new VatFermentingRecipeAdapter();
        final TagKey<Item> first = TagKey.create(Registries.ITEM, ResourceLocation.parse("test:sliced_vat_first"));
        final TagKey<Item> second = TagKey.create(Registries.ITEM, ResourceLocation.parse("test:sliced_vat_second"));
        final Field deferredHolder;
        final Object oldHolder;
        final Map<TagKey<Item>, List<Holder<Item>>> previousTags = new java.util.LinkedHashMap<>();

        Fixture() throws Exception {
            BuiltInRegistries.ITEM.getTags().forEach(pair -> previousTags.put(pair.getFirst(), pair.getSecond().stream().toList()));
            var entries = List.<Holder<Item>>of(Items.IRON_INGOT.builtInRegistryHolder(), Items.GOLD_INGOT.builtInRegistryHolder());
            BuiltInRegistries.ITEM.bindTags(Map.of(first, entries, second, entries));
            // Standalone tests have no mod registration. Substitute only the VAT mold's block holder;
            // native createRecipe still executes and serializes the resulting real vanilla item.
            deferredHolder = DeferredHolder.class.getDeclaredField("holder"); deferredHolder.setAccessible(true);
            oldHolder = deferredHolder.get(EIOBlocks.VAT);
            deferredHolder.set(EIOBlocks.VAT, Blocks.IRON_BLOCK.builtInRegistryHolder());
        }

        RecipeHolder<FermentingRecipe> recipe(int amount, int ticks) {
            FluidStack output = new FluidStack(Fluids.LAVA, amount);
            CompoundTag marker = new CompoundTag(); marker.putString("component_marker", "retained");
            output.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
            return new RecipeHolder<>(ResourceLocation.parse("test:sliced_vat"), new FermentingRecipe(
                    SizedFluidIngredient.of(Fluids.WATER, 1000), first, second, output, ticks));
        }

        List<AdvancedAlloyFurnaceRecipe> compare(RecipeHolder<FermentingRecipe> holder) {
            List<AdvancedAlloyFurnaceRecipe> expected = adapter.convertAll(holder, null);
            List<AdvancedAlloyFurnaceRecipe> actual = new ArrayList<>();
            var cursor = SlicedRecipeConversions.tryCreate(adapter, holder, null);
            assertNotNull(cursor);
            boolean done = false;
            for (int step = 0; step < 1000 && !done; step++) {
                int before = actual.size();
                done = cursor.advance(actual::add);
                assertTrue(actual.size() - before <= 1, "one owner step cannot materialize a whole conversion");
            }
            assertTrue(done);
            assertEquals(expected.size(), actual.size());
            var ops = RegistryOps.create(NbtOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            for (int index = 0; index < expected.size(); index++) {
                assertEquals(expected.get(index).id(), actual.get(index).id(), "native encounter order and variant ID");
                assertEquals(AdvancedAlloyFurnaceRecipe.CODEC.codec().encodeStart(ops, expected.get(index)).getOrThrow(),
                        AdvancedAlloyFurnaceRecipe.CODEC.codec().encodeStart(ops, actual.get(index)).getOrThrow(),
                        "all native Codec fields for tuple " + index);
            }
            int before = actual.size();
            assertTrue(cursor.advance(actual::add));
            assertEquals(before, actual.size(), "completed conversion cannot emit twice");
            return actual;
        }

        @Override public void close() throws Exception {
            deferredHolder.set(EIOBlocks.VAT, oldHolder);
            BuiltInRegistries.ITEM.bindTags(previousTags);
        }
    }
}
