package com.sorrowmist.useless.stretcher.content.catalog;

import com.sorrowmist.useless.api.recipe.IRecipeAdapter;
import com.sorrowmist.useless.stretcher.VanillaTestRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native class/helpers and codec outputs; no game instance or off-thread registry work. */
class GeneratedRecipeSlicesTest {
    private static final String ADAPTER = "com.sorrowmist.useless.content.recipe.adapters.justdirethings.jdte.InfusionRecipeAdapter";

    @BeforeAll static void bootstrap() throws Exception {
        VanillaTestRegistries.initialize();
        initializeNativeVanillaFluidTypes();
    }

    /** Complete the real NeoForge registration omitted by standalone vanilla bootstrap. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void initializeNativeVanillaFluidTypes() throws Exception {
        Registry<FluidType> types = NeoForgeRegistries.FLUID_TYPES;
        ResourceKey<? extends Registry<FluidType>> key = NeoForgeRegistries.Keys.FLUID_TYPES;
        if (!BuiltInRegistries.REGISTRY.containsKey(key.location())) {
            MappedRegistry<?> root = (MappedRegistry<?>) BuiltInRegistries.REGISTRY;
            root.unfreeze();
            try { Registry.register((Registry) root, key.location(), types); }
            finally { root.freeze(); }
        }
        if (!types.containsKey(ResourceLocation.withDefaultNamespace("water"))) {
            // Use NeoForge's own registered suppliers and RegisterEvent callback. Neither
            // CommonHooks nor JDTE's fluid classification/recipe helper is replaced.
            Field deferredField = NeoForgeMod.class.getDeclaredField("VANILLA_FLUID_TYPES");
            deferredField.setAccessible(true);
            DeferredRegister<FluidType> deferred = (DeferredRegister<FluidType>) deferredField.get(null);
            var constructor = RegisterEvent.class.getDeclaredConstructor(ResourceKey.class, Registry.class);
            constructor.setAccessible(true);
            RegisterEvent event = constructor.newInstance(key, types);
            Method register = method(DeferredRegister.class, "addEntries", RegisterEvent.class);
            register.invoke(deferred, event);
            types.freeze();
        }
        assertSame(NeoForgeMod.EMPTY_TYPE.value(), Fluids.EMPTY.getFluidType());
        assertSame(NeoForgeMod.WATER_TYPE.value(), Fluids.WATER.getFluidType());
        assertSame(NeoForgeMod.WATER_TYPE.value(), Fluids.FLOWING_WATER.getFluidType());
        assertSame(NeoForgeMod.LAVA_TYPE.value(), Fluids.LAVA.getFluidType());
        assertSame(NeoForgeMod.LAVA_TYPE.value(), Fluids.FLOWING_LAVA.getFluidType());
    }

    @Test void fullVanillaRegistryColdAndWarmResultsEqualNativeGeneratedRecipes() throws Exception {
        Fixture fixture = new Fixture();
        List<? extends RecipeHolder<?>> expected = fixture.adapter.getGeneratedRecipes(fixture.level);
        assertFalse(expected.isEmpty(), "native water-bottle recipe must exercise real construction");
        nativeCache().remove(fixture.manager);

        GeneratedRecipeSlices.Cursor cold = GeneratedRecipeSlices.tryCreate(fixture.adapter, fixture.level);
        assertNotNull(cold);
        List<RecipeHolder<?>> actual = drain(cold);
        assertNativeContentsEqual(expected, actual, fixture.registries);
        assertTrue(cold.attemptedPairs() > 0, "cold cursor must visit actual item-fluid pairs");
        assertTrue(cold.steps() > actual.size());
        List<RecipeHolder<?>> cached = nativeCache().get(fixture.manager);
        assertNotNull(cached);
        assertNativeContentsEqual(expected, cached, fixture.registries);

        GeneratedRecipeSlices.Cursor warm = GeneratedRecipeSlices.tryCreate(fixture.adapter, fixture.level);
        assertNotNull(warm);
        assertNativeContentsEqual(fixture.adapter.getGeneratedRecipes(fixture.level), drain(warm), fixture.registries);
        assertEquals(0, warm.attemptedPairs(), "warm cache must retain native cache behavior");
    }

    @Test void realNativeContainerCapabilityAndBottleHelpersKeepIdsContentsAndOrder() throws Exception {
        Fixture fixture = new Fixture();
        Item item = mock(Item.class);
        ItemStack offered = mock(ItemStack.class);
        ItemStack container = mock(ItemStack.class);
        IFluidHandlerItem capability = mock(IFluidHandlerItem.class);
        when(item.getDefaultInstance()).thenReturn(offered);
        when(offered.isEmpty()).thenReturn(false);
        when(offered.copyWithCount(1)).thenReturn(container);
        when(container.copy()).thenAnswer(ignored -> new ItemStack(Items.BUCKET));
        when(container.getCapability(Capabilities.FluidHandler.ITEM)).thenReturn(capability);
        when(capability.getTanks()).thenReturn(1);
        AtomicReference<Fluid> filledFluid = new AtomicReference<>();
        when(capability.fill(any(FluidStack.class), any(IFluidHandler.FluidAction.class))).thenAnswer(call -> {
            FluidStack fluid = call.getArgument(0);
            if (call.getArgument(1) == IFluidHandler.FluidAction.EXECUTE) filledFluid.set(fluid.getFluid());
            return 1000;
        });
        when(capability.getContainer()).thenAnswer(ignored -> new ItemStack(filledFluid.get() == Fluids.WATER ? Items.WATER_BUCKET : Items.LAVA_BUCKET));
        // Repeated item/fluid inputs deliberately exercise first-ID putIfAbsent.
        List<Item> items = List.of(item, item);
        List<Fluid> fluids = List.of(Fluids.WATER, Fluids.LAVA, Fluids.WATER, Fluids.EMPTY, Fluids.FLOWING_WATER);
        Class<?> type = fixture.adapter.getClass();
        Method createContainer = method(type, "createFluidContainerRecipe", HolderLookup.Provider.class, ItemStack.class, FluidStack.class);
        Method createBottle = method(type, "createBottleRecipe", HolderLookup.Provider.class, FluidStack.class);
        Method fillable = method(Class.forName("com.jdte.common.utils.InfusionFluidHelper"), "isFillableSourceFluid", Fluid.class);
        Map<ResourceLocation, RecipeHolder<?>> nativeLoop = new LinkedHashMap<>();
        for (Item inputItem : items) {
            ItemStack input = inputItem.getDefaultInstance();
            for (Fluid fluid : fluids) {
                if (!(boolean) invoke(fillable, fluid)) continue;
                RecipeHolder<?> result = (RecipeHolder<?>) invoke(createContainer, fixture.registries, input, new FluidStack(fluid, 8000));
                if (result != null) nativeLoop.putIfAbsent(result.id(), result);
            }
        }
        RecipeHolder<?> waterBottle = (RecipeHolder<?>) invoke(createBottle, fixture.registries, new FluidStack(Fluids.WATER, 250));
        nativeLoop.putIfAbsent(waterBottle.id(), waterBottle);
        GeneratedRecipeSlices.Cursor cursor = GeneratedRecipeSlices.forInputs(fixture.adapter, fixture.level, items, fluids);
        List<RecipeHolder<?>> actual = drain(cursor);
        assertEquals(3, actual.size(), "two real container recipes and one bottle, with duplicates suppressed");
        assertNativeContentsEqual(List.copyOf(nativeLoop.values()), actual, fixture.registries);
        assertTrue(actual.get(0).id().getPath().contains("container"));
        assertTrue(actual.get(1).id().getPath().contains("container"));
        assertTrue(actual.get(2).id().getPath().contains("bottle"));
        assertEquals(6, cursor.attemptedPairs());
    }

    @Test void singleStepNeverDrainsAnEntireCrossProductOrOverwritesAnUnreadOutput() throws Exception {
        Fixture fixture = new Fixture();
        GeneratedRecipeSlices.Cursor cursor = GeneratedRecipeSlices.forInputs(fixture.adapter, fixture.level,
                List.of(Items.BUCKET), List.of(Fluids.WATER, Fluids.LAVA));
        assertNotNull(cursor);
        assertFalse(nativeCache().containsKey(fixture.manager));
        cursor.advance(); // choose one item only
        assertEquals(0, cursor.attemptedPairs());
        cursor.advance();
        assertEquals(1, cursor.attemptedPairs());
        assertFalse(nativeCache().containsKey(fixture.manager), "partial base cache must never be published");
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (!cursor.hasOutput() && !cursor.done() && System.nanoTime() < deadline) cursor.advance();
        assertTrue(cursor.hasOutput());
        long before = cursor.steps();
        assertFalse(cursor.advance(), "consumer must drain a holder before the cursor advances again");
        assertEquals(before, cursor.steps());
        assertNotNull(cursor.poll());
        drain(cursor);
        assertTrue(nativeCache().containsKey(fixture.manager));
    }

    @Test void cursorRejectsOffOwnerAdvancementAndUnrelatedAdaptersUseNativeFallback() throws Exception {
        Fixture fixture = new Fixture();
        assertNull(GeneratedRecipeSlices.tryCreate(mock(IRecipeAdapter.class), fixture.level));
        GeneratedRecipeSlices.Cursor cursor = GeneratedRecipeSlices.tryCreate(fixture.adapter, fixture.level);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread worker = new Thread(() -> { try { cursor.advance(); } catch (Throwable failure) { error.set(failure); } });
        worker.start(); worker.join(3000);
        assertFalse(worker.isAlive());
        assertInstanceOf(IllegalStateException.class, error.get());
        assertEquals(0, cursor.steps());
    }

    private static List<RecipeHolder<?>> drain(GeneratedRecipeSlices.Cursor cursor) {
        List<RecipeHolder<?>> result = new ArrayList<>();
        for (int i = 0; i < 1_000_000 && !cursor.done(); i++) {
            if (cursor.hasOutput()) result.add(cursor.poll());
            else cursor.advance();
        }
        assertTrue(cursor.done(), "finite input cursor must finish");
        return result;
    }

    private static void assertNativeContentsEqual(List<? extends RecipeHolder<?>> expected,
                                                   List<? extends RecipeHolder<?>> actual, HolderLookup.Provider registries) throws Exception {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).id(), actual.get(i).id(), "holder order and native dynamic ID at index " + i);
            Object left = expected.get(i).value(), right = actual.get(i).value();
            ItemStack leftInput = (ItemStack) invoke(method(left.getClass(), "getInput"), left, true);
            ItemStack rightInput = (ItemStack) invoke(method(right.getClass(), "getInput"), right, true);
            ItemStack leftOutput = (ItemStack) invoke(method(left.getClass(), "getOutput"), left, true);
            ItemStack rightOutput = (ItemStack) invoke(method(right.getClass(), "getOutput"), right, true);
            FluidStack leftFluid = (FluidStack) invoke(method(left.getClass(), "getFluidInput"), left, true);
            FluidStack rightFluid = (FluidStack) invoke(method(right.getClass(), "getFluidInput"), right, true);
            assertEquals(leftInput.saveOptional(registries), rightInput.saveOptional(registries));
            assertEquals(leftOutput.saveOptional(registries), rightOutput.saveOptional(registries));
            assertTrue(FluidStack.isSameFluidSameComponents(leftFluid, rightFluid));
            assertEquals(leftFluid.getAmount(), rightFluid.getAmount());
            assertEquals(invoke(method(left.getClass(), "getEnergyCost"), left, true), invoke(method(right.getClass(), "getEnergyCost"), right, true));
        }
    }

    private static final class Fixture {
        final ServerLevel level = mock(ServerLevel.class);
        final RecipeManager manager = mock(RecipeManager.class);
        final RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        final IRecipeAdapter<?> adapter;
        Fixture() throws Exception {
            adapter = (IRecipeAdapter<?>) Class.forName(ADAPTER).getConstructor().newInstance();
            when(level.getRecipeManager()).thenReturn(manager);
            when(level.registryAccess()).thenReturn(registries);
            nativeCache().remove(manager);
        }
    }

    @SuppressWarnings("unchecked") private static Map<RecipeManager, List<RecipeHolder<?>>> nativeCache() throws Exception {
        Field field = Class.forName(ADAPTER).getDeclaredField("GENERATED_BASE_RECIPES"); field.setAccessible(true);
        return (Map<RecipeManager, List<RecipeHolder<?>>>) field.get(null);
    }
    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method method = type.getDeclaredMethod(name, parameters); method.setAccessible(true); return method;
    }
    private static Object invoke(Method method, Object... arguments) throws Exception {
        try { return method.invoke(null, arguments); }
        catch (InvocationTargetException failure) { throw new IllegalStateException(failure.getCause()); }
    }
    private static Object invoke(Method method, Object receiver, boolean instance) throws Exception { return method.invoke(receiver); }
}
