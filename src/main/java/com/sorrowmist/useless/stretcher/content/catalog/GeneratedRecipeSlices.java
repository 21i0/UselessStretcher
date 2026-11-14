package com.sorrowmist.useless.stretcher.content.catalog;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.recipe.IRecipeAdapter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Verified native generated-recipe loops split at real item/fluid inputs, not at
 * an already computed result list. No recipe, capability, codec or registry work
 * is dispatched off the thread that creates and advances the cursor.
 */
public final class GeneratedRecipeSlices {
    private static final String JDTE_ADAPTER = "com.sorrowmist.useless.content.recipe.adapters.justdirethings.jdte.InfusionRecipeAdapter";
    private static final String VERIFIED_ADAPTER_SHA256 = "a10245fbcb26de8e194e726676409da2bc0ba15902deeaee73d32638fe59dd45";
    private static final ClassValue<Optional<JdteAbi>> ADAPTERS = new ClassValue<>() {
        @Override protected Optional<JdteAbi> computeValue(Class<?> type) {
            try { return Optional.of(new JdteAbi(type)); }
            catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
                LogUtils.getLogger().warn("Generated recipe slicing unavailable for {}; native callback retained: {}", type.getName(), error.toString());
                return Optional.empty();
            }
        }
    };

    private GeneratedRecipeSlices() {}

    /** Null means unsupported/unverified: retain that adapter's original callback. */
    public static Cursor tryCreate(IRecipeAdapter<?> adapter, Level level) {
        if (adapter == null || level == null || !adapter.getClass().getName().equals(JDTE_ADAPTER)) return null;
        Optional<JdteAbi> abi = ADAPTERS.get(adapter.getClass());
        return abi.map(value -> new Cursor(value, level, BuiltInRegistries.ITEM, BuiltInRegistries.FLUID)).orElse(null);
    }

    /** Owner-only cursor with one output slot; drain it before advancing again. */
    public static final class Cursor {
        private final JdteAbi abi;
        private final Level level;
        private final HolderLookup.Provider registries;
        private final RecipeManager manager;
        private final Thread owner = Thread.currentThread();
        private final Iterable<Item> itemSource;
        private final Iterable<Fluid> fluidSource;
        private final Map<ResourceLocation, RecipeHolder<?>> base = new LinkedHashMap<>();
        private Iterator<Item> items;
        private Iterator<Fluid> fluids = Collections.emptyIterator();
        private Iterator<RecipeHolder<?>> cached = Collections.emptyIterator();
        private Iterator<Map.Entry<Item, ItemStack>> eggs = Collections.emptyIterator();
        private ItemStack input;
        private RecipeHolder<?> output;
        private Phase phase;
        private long steps, attemptedPairs, emitted;

        private Cursor(JdteAbi abi, Level level, Iterable<Item> items, Iterable<Fluid> fluids) {
            this.abi = abi; this.level = level; this.registries = level.registryAccess();
            this.manager = level.getRecipeManager(); this.itemSource = items; this.fluidSource = fluids;
            List<RecipeHolder<?>> existing;
            synchronized (abi.cache) { existing = abi.cache.get(manager); }
            if (existing != null) { cached = existing.iterator(); phase = Phase.CACHED_BASE; }
            else { this.items = itemSource.iterator(); phase = Phase.CONTAINERS; }
        }

        public boolean hasOutput() { checkOwner(); return output != null; }

        public RecipeHolder<?> poll() {
            checkOwner();
            RecipeHolder<?> result = output; output = null;
            return result;
        }

        public boolean done() { checkOwner(); return phase == Phase.DONE && output == null; }
        public String phase() { return phase.name(); }
        public long steps() { return steps; }
        public long attemptedPairs() { return attemptedPairs; }
        public long emitted() { return emitted; }

        /**
         * At most one native container-pair, bottle or spawn-egg entry per call.
         * Individual third-party capability/helper calls cannot be preempted.
         */
        public boolean advance() {
            checkOwner();
            if (output != null || phase == Phase.DONE) return false;
            steps++;
            switch (phase) {
                case CACHED_BASE -> {
                    if (cached.hasNext()) emit(cached.next());
                    else phase = Phase.SPAWN_CAPTURE;
                }
                case CONTAINERS -> containerStep();
                case WATER_BOTTLE -> {
                    addBase(abi.bottle(registries, new FluidStack(Fluids.WATER, 250)));
                    fluids = fluidSource.iterator();
                    phase = Phase.HONEY_BOTTLES;
                }
                case HONEY_BOTTLES -> {
                    if (fluids.hasNext()) {
                        Fluid fluid = fluids.next();
                        if (fluid != Fluids.EMPTY && abi.fillable(fluid) && abi.honey(fluid)) {
                            addBase(abi.bottle(registries, new FluidStack(fluid, 250)));
                        }
                    } else phase = Phase.CACHE_BASE;
                }
                case CACHE_BASE -> {
                    // Match native first-writer cache semantics. Never publish partial work.
                    List<RecipeHolder<?>> completed = List.copyOf(base.values());
                    synchronized (abi.cache) { abi.cache.putIfAbsent(manager, completed); }
                    phase = Phase.SPAWN_CAPTURE;
                }
                case SPAWN_CAPTURE -> {
                    // The upstream loot/client map helper is itself atomic; retain its semantics.
                    // Entries after this capture are handled one per owner slice.
                    eggs = abi.spawnEggRecipes(level).entrySet().iterator();
                    phase = Phase.SPAWN_EGGS;
                }
                case SPAWN_EGGS -> {
                    if (eggs.hasNext()) spawnEggStep(eggs.next());
                    else phase = Phase.DONE;
                }
                case DONE -> { return false; }
            }
            return true;
        }

        private void containerStep() {
            if (input == null) {
                if (!items.hasNext()) { phase = Phase.WATER_BOTTLE; return; }
                ItemStack candidate = items.next().getDefaultInstance();
                if (!candidate.isEmpty()) { input = candidate; fluids = fluidSource.iterator(); }
                return;
            }
            if (!fluids.hasNext()) { input = null; return; }
            Fluid fluid = fluids.next();
            if (!abi.fillable(fluid)) return;
            attemptedPairs++;
            // Native catches runtime failures per pair and continues the same ordered scan.
            try { addBase(abi.container(registries, input, new FluidStack(fluid, 8000))); }
            catch (RuntimeException ignored) { }
        }

        private void spawnEggStep(Map.Entry<Item, ItemStack> egg) {
            Item inputItem = egg.getKey();
            ItemStack eggOutput = egg.getValue();
            if (inputItem == null || eggOutput == null || eggOutput.isEmpty()) return;
            ItemStack eggInput = inputItem.getDefaultInstance();
            if (eggInput.isEmpty() || eggInput.getMaxStackSize() <= 1) return;
            eggInput.setCount(eggInput.getMaxStackSize());
            emit(abi.holder(registries, "spawn_egg", eggInput,
                    new FluidStack(abi.lifeFluid(), 64000), eggOutput.copyWithCount(1), 100000));
        }

        private void addBase(RecipeHolder<?> holder) {
            if (holder != null && base.putIfAbsent(holder.id(), holder) == null) emit(holder);
        }

        private void emit(RecipeHolder<?> holder) { output = holder; emitted++; }
        private void checkOwner() {
            if (Thread.currentThread() != owner) throw new IllegalStateException("Generated recipe cursor advanced off owner thread");
        }
    }

    private enum Phase { CACHED_BASE, CONTAINERS, WATER_BOTTLE, HONEY_BOTTLES, CACHE_BASE, SPAWN_CAPTURE, SPAWN_EGGS, DONE }

    /** Test seam changes input domain only; all recipe creation still invokes native helpers. */
    static Cursor forInputs(IRecipeAdapter<?> adapter, Level level, Iterable<Item> items, Iterable<Fluid> fluids) {
        JdteAbi abi = ADAPTERS.get(adapter.getClass()).orElseThrow();
        return new Cursor(abi, level, items, fluids);
    }

    private static final class JdteAbi {
        final Map<RecipeManager, List<RecipeHolder<?>>> cache;
        final Method container, bottle, holder, eggs, fillable, honey;
        final Field life;

        @SuppressWarnings("unchecked")
        JdteAbi(Class<?> type) throws ReflectiveOperationException {
            if (!type.getName().equals(JDTE_ADAPTER) || !verifiedBytes(type)) throw new IllegalStateException("Unverified JDTE adapter bytes");
            container = method(type, "createFluidContainerRecipe", HolderLookup.Provider.class, ItemStack.class, FluidStack.class);
            bottle = method(type, "createBottleRecipe", HolderLookup.Provider.class, FluidStack.class);
            holder = method(type, "holder", HolderLookup.Provider.class, String.class, ItemStack.class, FluidStack.class, ItemStack.class, int.class);
            eggs = method(type, "spawnEggRecipes", Level.class);
            Field cacheField = type.getDeclaredField("GENERATED_BASE_RECIPES");
            if (!cacheField.trySetAccessible()) throw new IllegalAccessException(cacheField.getName());
            Object cacheValue = cacheField.get(null);
            if (!(cacheValue instanceof Map)) throw new IllegalStateException("Unexpected native base cache");
            cache = (Map<RecipeManager, List<RecipeHolder<?>>>) cacheValue;
            ClassLoader loader = type.getClassLoader();
            Class<?> helper = Class.forName("com.jdte.common.utils.InfusionFluidHelper", false, loader);
            fillable = method(helper, "isFillableSourceFluid", Fluid.class);
            honey = method(helper, "isHoneyFluid", Fluid.class);
            Class<?> fluids = Class.forName("com.jdte.setup.JDTEFluids", false, loader);
            life = fluids.getField("LIFE_FLUID_SOURCE");
        }

        RecipeHolder<?> container(HolderLookup.Provider registries, ItemStack input, FluidStack fluid) { return (RecipeHolder<?>) invoke(container, registries, input, fluid); }
        RecipeHolder<?> bottle(HolderLookup.Provider registries, FluidStack fluid) { return (RecipeHolder<?>) invoke(bottle, registries, fluid); }
        RecipeHolder<?> holder(HolderLookup.Provider registries, String kind, ItemStack input, FluidStack fluid, ItemStack output, int energy) { return (RecipeHolder<?>) invoke(holder, registries, kind, input, fluid, output, energy); }
        boolean fillable(Fluid fluid) { return (boolean) invoke(fillable, fluid); }
        boolean honey(Fluid fluid) { return (boolean) invoke(honey, fluid); }
        @SuppressWarnings("unchecked") Map<Item, ItemStack> spawnEggRecipes(Level level) { return (Map<Item, ItemStack>) invoke(eggs, level); }
        Fluid lifeFluid() {
            try { return (Fluid) ((Supplier<?>) life.get(null)).get(); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot read native life fluid", error); }
        }
    }

    private static boolean verifiedBytes(Class<?> type) {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream bytes = type.getResourceAsStream(resource)) {
            if (bytes == null) return false;
            return VERIFIED_ADAPTER_SHA256.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes())));
        } catch (Exception error) { throw new IllegalStateException("Cannot verify generated-recipe adapter", error); }
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) throws ReflectiveOperationException {
        Method method = type.getDeclaredMethod(name, parameters);
        if (!method.trySetAccessible()) throw new IllegalAccessException(name);
        return method;
    }

    private static Object invoke(Method method, Object... arguments) {
        try { return method.invoke(null, arguments); }
        catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error fatal) throw fatal;
            throw new IllegalStateException("Native generated recipe callback failed", cause);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Native generated recipe ABI failed", error); }
    }
}
