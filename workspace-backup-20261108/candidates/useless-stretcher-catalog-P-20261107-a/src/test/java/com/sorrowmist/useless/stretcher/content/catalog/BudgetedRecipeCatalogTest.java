package com.sorrowmist.useless.stretcher.content.catalog;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.api.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeFingerprint;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeConversionUtils;
import com.sorrowmist.useless.content.recipe.adapters.RecipeAdapterCompatRegistry;
import com.sorrowmist.useless.stretcher.VanillaTestRegistries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real native codecs and snapshot ABI, with mocked world handles and no game process. */
class BudgetedRecipeCatalogTest {
    @BeforeAll static void bootstrap() { VanillaTestRegistries.initialize(); }
    @AfterEach void clear() { BudgetedRecipeCatalog.cancelAll(); AlloyFurnaceRecipeCatalog.invalidate(); }

    @Test void detachedSortingMatchesNativeRecipeIdFingerprintOrderingAndStableTies() {
        CatalogSortWork.Key[] input = {
                new CatalogSortWork.Key("z:r", "0", 0), new CatalogSortWork.Key("a:r", "f", 1),
                new CatalogSortWork.Key("a:r", "0", 2), new CatalogSortWork.Key("a:r", "0", 3)
        };
        assertArrayEquals(new int[]{2, 3, 1, 0}, CatalogSortWork.sort(input));
        assertArrayEquals(new int[0], CatalogSortWork.sort(new CatalogSortWork.Key[0]));
    }

    @Test void pendingSnapshotHasRealNativeShapeButIsNeverPublishedAsReady() throws Exception {
        Fixture fixture = new Fixture();
        Object snapshot = BudgetedRecipeCatalog.snapshotOrSchedule(fixture.level);
        assertEquals("com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog$Snapshot", snapshot.getClass().getName());
        assertEquals(List.of(), value(snapshot, "entries"));
        assertTrue(BudgetedRecipeCatalog.pending(fixture.level));
        assertNull(NativeCatalogAccess.ready(fixture.manager));
        assertFalse(AlloyFurnaceRecipeCatalog.isReady(fixture.level));
        assertSame(snapshot, BudgetedRecipeCatalog.snapshotOrSchedule(fixture.level));
    }

    @Test void nativePerManagerGenerationRejectsStalePublication() {
        Fixture fixture = new Fixture();
        long old = NativeCatalogAccess.generation(fixture.manager);
        Object empty = NativeCatalogAccess.empty(old);
        AlloyFurnaceRecipeCatalog.invalidate(fixture.level);
        assertFalse(NativeCatalogAccess.publish(fixture.manager, old, empty, 999));
        assertNull(NativeCatalogAccess.ready(fixture.manager));
        assertEquals(0, AlloyFurnaceRecipeCatalog.currentRecipeCount());
    }

    @Test void cooperativeFingerprintDedupSortAndAllNativeIndexesMatchTheNativeAlgorithm() throws Exception {
        Fixture fixture = new Fixture();
        AdvancedAlloyFurnaceRecipe z = recipe("test:z", Items.IRON_INGOT);
        AdvancedAlloyFurnaceRecipe aGold = recipe("test:a", Items.GOLD_INGOT);
        AdvancedAlloyFurnaceRecipe aDiamond = recipe("test:a", Items.DIAMOND);
        List<Input> input = List.of(new Input(z, "core"), new Input(aGold, "MIXED_SOURCE"),
                new Input(aDiamond, "first"), new Input(aDiamond, "second"));

        // This is the exact native build's fingerprint/encounter-order putIfAbsent/
        // recipe-id-then-fingerprint ordering, independently using its public codec.
        Map<AlloyFurnaceRecipeIdentity, AlloyFurnaceRecipeCatalog.Entry> nativeUnique = new LinkedHashMap<>();
        for (Input item : input) {
            var identity = new AlloyFurnaceRecipeIdentity(item.recipe.id(),
                    AlloyFurnaceRecipeFingerprint.create(item.recipe, fixture.level.registryAccess()));
            nativeUnique.putIfAbsent(identity, new AlloyFurnaceRecipeCatalog.Entry(identity, item.recipe, item.source));
        }
        List<AlloyFurnaceRecipeCatalog.Entry> expected = nativeUnique.values().stream()
                .sorted(Comparator.comparing((AlloyFurnaceRecipeCatalog.Entry entry) -> entry.identity().recipeId().toString())
                        .thenComparing(entry -> entry.identity().fingerprint())).toList();

        startAtFingerprint(fixture, input);
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!AlloyFurnaceRecipeCatalog.isReady(fixture.level) && System.nanoTime() < deadline) {
            BudgetedRecipeCatalog.tick(fixture.level, 2_000_000L, 2);
            Thread.sleep(1);
        }
        assertTrue(AlloyFurnaceRecipeCatalog.isReady(fixture.level));
        Object result = NativeCatalogAccess.ready(fixture.manager);
        assertEquals(expected, value(result, "entries"));
        assertEquals(expected, AlloyFurnaceRecipeCatalog.entries(fixture.level));
        assertEquals(3, AlloyFurnaceRecipeCatalog.currentRecipeCount());
        assertFalse(BudgetedRecipeCatalog.pending(fixture.level));
        assertEquals(nativeUnique, value(result, "byIdentity"));
        Map<?, ?> byId = (Map<?, ?>) value(result, "byRecipeId");
        assertEquals(expected.stream().filter(entry -> entry.identity().recipeId().equals(aGold.id())).toList(), byId.get(aGold.id()));
        Map<?, ?> bySource = (Map<?, ?>) value(result, "bySource");
        assertFalse(bySource.containsKey("second"), "the first duplicate identity retains its source");
        for (var entry : expected) {
            assertEquals(List.of(entry), bySource.get(entry.sourceId()));
            Map<?, ?> combined = (Map<?, ?>) value(result, "bySourceAndRecipeId");
            assertEquals(List.of(entry), combined.get(NativeCatalogAccess.sourceKey(entry.sourceId(), entry.identity().recipeId())));
            assertEquals(entry, AlloyFurnaceRecipeCatalog.resolve(fixture.level, entry.identity()).orElseThrow());
        }
        @SuppressWarnings("unchecked") List<AlloyFurnaceRecipeCatalog.Entry> entries = (List<AlloyFurnaceRecipeCatalog.Entry>) value(result, "entries");
        assertThrows(UnsupportedOperationException.class, () -> entries.add(expected.getFirst()));
        @SuppressWarnings("unchecked") List<AlloyFurnaceRecipeCatalog.Entry> bucket = (List<AlloyFurnaceRecipeCatalog.Entry>) byId.get(aGold.id());
        assertThrows(UnsupportedOperationException.class, bucket::clear);
        assertThrows(UnsupportedOperationException.class, bySource::clear);
    }

    @Test void invalidationBeforeNextSliceDiscardsTheOldBuildWithoutPublishing() throws Exception {
        Fixture fixture = new Fixture();
        startAtFingerprint(fixture, List.of(new Input(recipe("test:a", Items.DIAMOND), "core")));
        AlloyFurnaceRecipeCatalog.invalidate(fixture.level);
        BudgetedRecipeCatalog.tick(fixture.level);
        assertNull(NativeCatalogAccess.ready(fixture.manager));
        assertFalse(BudgetedRecipeCatalog.pending(fixture.level));
        BudgetedRecipeCatalog.request(fixture.level);
        assertTrue(BudgetedRecipeCatalog.pending(fixture.level));
    }

    @Test void offOwnerThreadCannotAdvanceOrCollectRecipes() throws Exception {
        Fixture fixture = new Fixture();
        BudgetedRecipeCatalog.request(fixture.level);
        AtomicReference<Throwable> rejected = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try { BudgetedRecipeCatalog.tick(fixture.level); }
            catch (Throwable failure) { rejected.set(failure); }
        });
        thread.start(); thread.join(3000);
        assertFalse(thread.isAlive());
        assertInstanceOf(IllegalStateException.class, rejected.get());
        verify(fixture.manager, never()).getRecipes();
        assertNull(NativeCatalogAccess.ready(fixture.manager));
    }

    @Test void offOwnerReadyReadNeedsNoQueueAndOffOwnerMissOnlyQueuesOwnerWork() throws Exception {
        Fixture fixture = new Fixture();
        List<Runnable> tasks = new ArrayList<>();
        doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(fixture.server).execute(any(Runnable.class));
        AtomicReference<Object> pending = new AtomicReference<>();
        Thread reader = new Thread(() -> pending.set(BudgetedRecipeCatalog.snapshotOrSchedule(fixture.level)));
        reader.start(); reader.join(3000);
        assertFalse(reader.isAlive());
        assertNotNull(pending.get());
        assertEquals(1, tasks.size());
        assertNull(NativeCatalogAccess.ready(fixture.manager));
        verify(fixture.manager, never()).getRecipes();
        tasks.removeFirst().run();
        assertTrue(BudgetedRecipeCatalog.pending(fixture.level));
        BudgetedRecipeCatalog.cancel(fixture.level);
        Object ready = NativeCatalogAccess.empty(NativeCatalogAccess.generation(fixture.manager));
        assertTrue(NativeCatalogAccess.publish(fixture.manager, NativeCatalogAccess.generation(fixture.manager), ready, 0));
        AtomicReference<Object> result = new AtomicReference<>();
        Thread readyReader = new Thread(() -> result.set(BudgetedRecipeCatalog.snapshotOrSchedule(fixture.level)));
        readyReader.start(); readyReader.join(3000);
        assertSame(ready, result.get());
        assertTrue(tasks.isEmpty());
    }

    @Test void invalidationOrGlobalCancellationPreventsQueuedOffOwnerRequestResurrection() throws Exception {
        for (boolean global : List.of(false, true)) {
            Fixture fixture = new Fixture();
            List<Runnable> tasks = new ArrayList<>();
            doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(fixture.server).execute(any(Runnable.class));
            Thread reader = new Thread(() -> BudgetedRecipeCatalog.snapshotOrSchedule(fixture.level));
            reader.start(); reader.join(3000);
            assertEquals(1, tasks.size());
            if (global) BudgetedRecipeCatalog.cancelAll();
            else AlloyFurnaceRecipeCatalog.invalidate(fixture.level);
            tasks.removeFirst().run();
            assertFalse(BudgetedRecipeCatalog.pending(fixture.level));
            assertNull(NativeCatalogAccess.ready(fixture.manager));
        }
    }

    @Test void repeatedCallbackFailureRetriesOnlyThreeTimesAndExposesFinalFailure() throws Exception {
        Fixture fixture = new Fixture();
        BudgetedRecipeCatalog.request(fixture.level);
        // A standalone JVM has no registered mod recipe type; CAPTURE therefore
        // deliberately fails before touching recipes, exercising real retry logic.
        for (int attempt = 1; attempt <= 3; attempt++) {
            BudgetedRecipeCatalog.tick(fixture.level);
            assertEquals(attempt, BudgetedRecipeCatalog.status(fixture.level).attempts());
            assertEquals(attempt < 3, BudgetedRecipeCatalog.pending(fixture.level));
            Field builds = BudgetedRecipeCatalog.class.getDeclaredField("BUILDS"); builds.setAccessible(true);
            Object build = ((Map<?, ?>) builds.get(null)).get(fixture.manager);
            Field retryAt = build.getClass().getDeclaredField("retryAt"); retryAt.setAccessible(true);
            retryAt.setLong(build, 0L);
        }
        assertFalse(BudgetedRecipeCatalog.failure(fixture.level).isEmpty());
        BudgetedRecipeCatalog.request(fixture.level);
        BudgetedRecipeCatalog.tick(fixture.level);
        assertEquals(3, BudgetedRecipeCatalog.status(fixture.level).attempts());
        assertNull(NativeCatalogAccess.ready(fixture.manager));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test void generatedAndConvertedCollectorsPreserveNativeEncounterOrderAcrossSlices() throws Exception {
        Fixture fixture = new Fixture();
        IRecipeAdapter first = mock(IRecipeAdapter.class);
        IRecipeAdapter second = mock(IRecipeAdapter.class);
        AlloyFurnaceRecipeManager adapters = mock(AlloyFurnaceRecipeManager.class);
        RecipeHolder<?> generatedA = new RecipeHolder(ResourceLocation.parse("test:generated_a"), mock(SmeltingRecipe.class));
        RecipeHolder<?> generatedB = new RecipeHolder(ResourceLocation.parse("test:generated_b"), mock(SmeltingRecipe.class));
        RecipeHolder<?> generatedC = new RecipeHolder(ResourceLocation.parse("test:generated_c"), mock(SmeltingRecipe.class));
        RecipeHolder<?> smelt = new RecipeHolder(ResourceLocation.parse("test:smelt"), mock(SmeltingRecipe.class));
        RecipeHolder<?> smeltAgain = new RecipeHolder(ResourceLocation.parse("test:smelt_again"), mock(SmeltingRecipe.class));
        RecipeHolder<?> blast = new RecipeHolder(ResourceLocation.parse("test:blast"), mock(BlastingRecipe.class));
        AdvancedAlloyFurnaceRecipe shared = recipe("test:shared", Items.DIAMOND);
        AdvancedAlloyFurnaceRecipe fromA = recipe("test:from_a", Items.IRON_INGOT);
        AdvancedAlloyFurnaceRecipe fromB = recipe("test:from_b", Items.EMERALD);
        AdvancedAlloyFurnaceRecipe fromBlast = recipe("test:from_blast", Items.GOLD_INGOT);
        when(first.getGeneratedRecipes(fixture.level)).thenReturn(List.of(generatedA, generatedB));
        when(second.getGeneratedRecipes(fixture.level)).thenReturn(List.of(generatedC));
        when(first.getRecipeClass()).thenReturn(AbstractCookingRecipe.class);
        when(second.getRecipeClass()).thenReturn(SmeltingRecipe.class);
        when(adapters.getAdapterSourceId(first)).thenReturn("first");
        when(adapters.getAdapterSourceId(second)).thenReturn("second");
        when(fixture.manager.getRecipes()).thenReturn(List.of(smelt, smeltAgain, blast));
        try (var managerStatic = mockStatic(AlloyFurnaceRecipeManager.class);
             var convert = mockStatic(RecipeConversionUtils.class);
             var compat = mockStatic(RecipeAdapterCompatRegistry.class)) {
            managerStatic.when(AlloyFurnaceRecipeManager::getInstance).thenReturn(adapters);
            convert.when(() -> RecipeConversionUtils.convertAll(any(IRecipeAdapter.class), any(RecipeHolder.class), eq(fixture.level)))
                    .thenAnswer(call -> {
                        assertSame(fixture.owner, Thread.currentThread(), "adapters must remain on the world owner");
                        Object adapter = call.getArgument(0);
                        Object holder = call.getArgument(1);
                        if (holder == generatedA) return List.of(shared);
                        if (holder == generatedB) return List.of(fromA);
                        if (holder == generatedC) return List.of(fromB);
                        if (holder == blast) return List.of(fromBlast);
                        if (adapter == first || adapter == second) return List.of(shared);
                        return List.of();
                    });
            compat.when(() -> RecipeAdapterCompatRegistry.isLoaded(anyString())).thenReturn(false);
            BudgetedRecipeCatalog.request(fixture.level);
            Object build = buildFor(fixture);
            set(build, "adapters", List.of(first, second).iterator());
            Field stage = build.getClass().getDeclaredField("stage"); stage.setAccessible(true);
            stage.set(build, Enum.valueOf((Class) stage.getType(), "ADAPTERS"));
            for (int slices = 0; slices < 200 && !stage.get(build).toString().equals("FINGERPRINT"); slices++) {
                BudgetedRecipeCatalog.tick(fixture.level, 2_000_000L, 1);
            }
            assertEquals("FINGERPRINT", stage.get(build).toString());
            Field collected = build.getClass().getDeclaredField("collected"); collected.setAccessible(true);
            List<?> actual = (List<?>) collected.get(build);
            List<Input> expected = List.of(new Input(shared, "first"), new Input(fromA, "first"), new Input(fromB, "second"),
                    new Input(shared, "first"), new Input(shared, "second"), new Input(shared, "first"),
                    new Input(shared, "second"), new Input(fromBlast, "first"));
            assertEquals(expected.size(), actual.size());
            for (int i = 0; i < expected.size(); i++) {
                assertSame(expected.get(i).recipe, value(actual.get(i), "recipe"));
                assertEquals(expected.get(i).source, value(actual.get(i), "source"));
            }
            verify(first, times(2)).getRecipeClass();
            verify(second, times(2)).getRecipeClass();
            convert.verify(() -> RecipeConversionUtils.convertAll(second, blast, fixture.level), never());
            long deadline = System.nanoTime() + 10_000_000_000L;
            while (!AlloyFurnaceRecipeCatalog.isReady(fixture.level) && System.nanoTime() < deadline) {
                BudgetedRecipeCatalog.tick(fixture.level, 2_000_000L, 2);
                Thread.sleep(1);
            }
            assertTrue(AlloyFurnaceRecipeCatalog.isReady(fixture.level));
            var entries = AlloyFurnaceRecipeCatalog.entries(fixture.level);
            assertEquals(4, entries.size());
            assertEquals("first", entries.stream().filter(entry -> entry.recipe() == shared).findFirst().orElseThrow().sourceId());
        }
    }

    private record Input(AdvancedAlloyFurnaceRecipe recipe, String source) {}

    private static AdvancedAlloyFurnaceRecipe recipe(String id, Item output) {
        return new AdvancedAlloyFurnaceRecipe(ResourceLocation.parse(id), List.of(), List.of(), List.of(),
                List.of(new ItemStack(output)), List.of(), List.of(), 10L, 20, Ingredient.EMPTY, 0,
                List.of(), AlloyFurnaceMode.NORMAL, 1);
    }

    private static final class Fixture {
        final ServerLevel level = mock(ServerLevel.class);
        final RecipeManager manager = mock(RecipeManager.class);
        final MinecraftServer server = mock(MinecraftServer.class);
        final Thread owner = Thread.currentThread();
        Fixture() {
            when(level.getServer()).thenReturn(server);
            when(level.getRecipeManager()).thenReturn(manager);
            when(server.isSameThread()).thenAnswer(ignored -> Thread.currentThread() == owner);
            when(level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        }
    }

    /** Starts after collection so tests need no mod registration or real adapter/world lifecycle. */
    private static void startAtFingerprint(Fixture fixture, List<Input> input) throws Exception {
        BudgetedRecipeCatalog.request(fixture.level);
        Object build = buildFor(fixture);
        Field collected = build.getClass().getDeclaredField("collected"); collected.setAccessible(true);
        Class<?> item = Class.forName(BudgetedRecipeCatalog.class.getName() + "$Collected");
        Constructor<?> constructor = item.getDeclaredConstructor(AdvancedAlloyFurnaceRecipe.class, String.class); constructor.setAccessible(true);
        List<Object> values = new ArrayList<>();
        for (Input entry : input) values.add(constructor.newInstance(entry.recipe, entry.source));
        collected.set(build, values);
        Field stage = build.getClass().getDeclaredField("stage"); stage.setAccessible(true);
        @SuppressWarnings({"rawtypes", "unchecked"}) Object fingerprint = Enum.valueOf((Class) stage.getType(), "FINGERPRINT");
        stage.set(build, fingerprint);
    }

    private static Object buildFor(Fixture fixture) throws Exception {
        Field builds = BudgetedRecipeCatalog.class.getDeclaredField("BUILDS"); builds.setAccessible(true);
        return ((Map<?, ?>) builds.get(null)).get(fixture.manager);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }

    private static Object value(Object target, String accessor) throws Exception {
        Method method = target.getClass().getDeclaredMethod(accessor); method.setAccessible(true);
        return method.invoke(target);
    }
}
