/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  appeng.api.stacks.AEKey
 *  com.jdte.common.recipes.InfusionRecipe
 *  com.jdte.common.utils.InfusionFluidHelper
 *  com.jdte.common.utils.MobLootSpawnEggHelper
 *  com.jdte.setup.JDTEBlocks
 *  com.jdte.setup.JDTEFluids
 *  com.jdte.setup.JDTERecipes
 *  com.sorrowmist.useless.api.enums.AlloyFurnaceMode
 *  com.sorrowmist.useless.content.recipe.AdapterUtils
 *  com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe
 *  com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeFingerprint
 *  com.sorrowmist.useless.content.recipe.CountedIngredient
 *  com.sorrowmist.useless.content.recipe.FluidIngredientAllocator
 *  com.sorrowmist.useless.content.recipe.IRecipeAdapter
 *  com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient
 *  net.minecraft.core.Holder
 *  net.minecraft.core.HolderLookup$Provider
 *  net.minecraft.core.RegistryAccess
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.packs.resources.ResourceManager
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.Items
 *  net.minecraft.world.item.alchemy.PotionContents
 *  net.minecraft.world.item.alchemy.Potions
 *  net.minecraft.world.item.crafting.Ingredient
 *  net.minecraft.world.item.crafting.Recipe
 *  net.minecraft.world.item.crafting.RecipeHolder
 *  net.minecraft.world.item.crafting.RecipeManager
 *  net.minecraft.world.item.crafting.RecipeType
 *  net.minecraft.world.level.ItemLike
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.material.Fluid
 *  net.minecraft.world.level.material.Fluids
 *  net.neoforged.neoforge.capabilities.Capabilities$FluidHandler
 *  net.neoforged.neoforge.common.crafting.DataComponentIngredient
 *  net.neoforged.neoforge.fluids.FluidStack
 *  net.neoforged.neoforge.fluids.capability.IFluidHandler$FluidAction
 *  net.neoforged.neoforge.fluids.capability.IFluidHandlerItem
 *  org.jetbrains.annotations.Nullable
 */
package com.sorrowmist.useless.content.recipe.adapters.justdirethings.jdte;

import appeng.api.stacks.AEKey;
import com.jdte.common.recipes.InfusionRecipe;
import com.jdte.common.utils.InfusionFluidHelper;
import com.jdte.common.utils.MobLootSpawnEggHelper;
import com.jdte.setup.JDTEBlocks;
import com.jdte.setup.JDTEFluids;
import com.jdte.setup.JDTERecipes;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeFingerprint;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.Nullable;

public final class InfusionRecipeAdapter
implements IRecipeAdapter<InfusionRecipe> {
    private static final Map<RecipeManager, List<RecipeHolder<InfusionRecipe>>> GENERATED_BASE_RECIPES = Collections.synchronizedMap(new WeakHashMap());

    public String sourceId() {
        return "justdirethings";
    }

    public Class<InfusionRecipe> getRecipeClass() {
        return InfusionRecipe.class;
    }

    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack((ItemLike)JDTEBlocks.ADVANCED_INFUSION_MACHINE.get());
    }

    public List<RecipeHolder<InfusionRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) {
            return List.of();
        }
        ArrayList<RecipeHolder<InfusionRecipe>> result = new ArrayList<RecipeHolder<InfusionRecipe>>(InfusionRecipeAdapter.baseGeneratedRecipes(level));
        InfusionRecipeAdapter.addSpawnEggRecipes(level, result);
        return List.copyOf(result);
    }

    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<InfusionRecipe> holder, Level level) {
        ItemStack output;
        FluidStack fluid;
        if (holder == null || holder.value() == null) {
            return null;
        }
        InfusionRecipe source = (InfusionRecipe)holder.value();
        ItemStack input = source.getInput();
        if (!InfusionRecipeAdapter.isValid(input, fluid = source.getFluidInput(), output = source.getOutput())) {
            return null;
        }
        return new AdvancedAlloyFurnaceRecipe(AdapterUtils.convertedId((ResourceLocation)holder.id()), List.of(new CountedIngredient(InfusionRecipeAdapter.itemIngredient(input), (long)input.getCount())), List.of(LongSizedFluidIngredient.from((FluidStack)fluid)), List.of(), List.of(output), List.of(), List.of(), (long)Math.max(1, source.getEnergyCost()), 20, Ingredient.EMPTY, 0, List.of(AdapterUtils.toMoldIngredient((ItemStack)this.getMoldItem())), AlloyFurnaceMode.NORMAL);
    }

    public List<RecipeHolder<InfusionRecipe>> findMatchingRecipes(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mold == null || mold.isEmpty()) {
            return List.of();
        }
        Map<Ingredient, Long> availableInputs = mergedInputs == null ? Map.of() : mergedInputs;
        Map<FluidStack, Long> availableFluids = mergedFluids == null ? Map.of() : mergedFluids;
        LinkedHashMap<ResourceLocation, RecipeHolder<InfusionRecipe>> matches = new LinkedHashMap<ResourceLocation, RecipeHolder<InfusionRecipe>>();
        for (RecipeHolder recipeHolder : level.getRecipeManager().getAllRecipesFor((RecipeType)JDTERecipes.INFUSION_RECIPE_TYPE.get())) {
            InfusionRecipeAdapter.addMatchingHolder(matches, (RecipeHolder<InfusionRecipe>)recipeHolder, availableInputs, availableFluids);
        }
        for (RecipeHolder recipeHolder : this.getGeneratedRecipes(level)) {
            InfusionRecipeAdapter.addMatchingHolder(matches, (RecipeHolder<InfusionRecipe>)recipeHolder, availableInputs, availableFluids);
        }
        return List.copyOf(matches.values());
    }

    public List<RecipeHolder<InfusionRecipe>> findMatchingRecipes(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || mold == null || mold.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<ResourceLocation, RecipeHolder<InfusionRecipe>> matches = new LinkedHashMap<ResourceLocation, RecipeHolder<InfusionRecipe>>();
        for (RecipeHolder<InfusionRecipe> holder : this.findMatchingRecipes(level, mergedInputs, mergedFluids, mold)) {
            matches.put(holder.id(), holder);
        }
        if (actualInputs == null || actualInputs.isEmpty()) {
            return List.copyOf(matches.values());
        }
        Map<FluidStack, Long> availableFluids = mergedFluids == null ? Map.of() : mergedFluids;
        for (ItemStack input : actualInputs) {
            if (input == null || input.isEmpty()) continue;
            InfusionRecipeAdapter.addRuntimeContainerMatches(level, matches, input, availableFluids);
            InfusionRecipeAdapter.addRuntimeBottleMatches(level, matches, input, availableFluids);
            InfusionRecipeAdapter.addRuntimeSpawnEggMatch(matches, level, input, availableFluids);
        }
        return List.copyOf(matches.values());
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static List<RecipeHolder<InfusionRecipe>> baseGeneratedRecipes(Level level) {
        RecipeManager recipeManager = level.getRecipeManager();
        RegistryAccess registries = level.registryAccess();
        Map<RecipeManager, List<RecipeHolder<InfusionRecipe>>> map = GENERATED_BASE_RECIPES;
        synchronized (map) {
            return GENERATED_BASE_RECIPES.computeIfAbsent(recipeManager, arg_0 -> InfusionRecipeAdapter.lambda$baseGeneratedRecipes$0((HolderLookup.Provider)registries, arg_0));
        }
    }

    private static List<RecipeHolder<InfusionRecipe>> buildBaseGeneratedRecipes(HolderLookup.Provider registries) {
        LinkedHashMap<ResourceLocation, RecipeHolder<InfusionRecipe>> recipes = new LinkedHashMap<ResourceLocation, RecipeHolder<InfusionRecipe>>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack input = item.getDefaultInstance();
            if (input.isEmpty()) continue;
            for (Fluid fluid : BuiltInRegistries.FLUID) {
                if (!InfusionFluidHelper.isFillableSourceFluid((Fluid)fluid)) continue;
                try {
                    RecipeHolder<InfusionRecipe> holder = InfusionRecipeAdapter.createFluidContainerRecipe(registries, input, new FluidStack(fluid, 8000));
                    if (holder == null) continue;
                    recipes.putIfAbsent(holder.id(), holder);
                }
                catch (RuntimeException runtimeException) {}
            }
        }
        InfusionRecipeAdapter.addBottleRecipe(registries, recipes, new FluidStack((Fluid)Fluids.WATER, 250));
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            if (fluid == Fluids.EMPTY || !InfusionFluidHelper.isFillableSourceFluid((Fluid)fluid) || !InfusionFluidHelper.isHoneyFluid((Fluid)fluid)) continue;
            InfusionRecipeAdapter.addBottleRecipe(registries, recipes, new FluidStack(fluid, 250));
        }
        return List.copyOf(recipes.values());
    }

    private static void addBottleRecipe(HolderLookup.Provider registries, Map<ResourceLocation, RecipeHolder<InfusionRecipe>> recipes, FluidStack fluid) {
        RecipeHolder<InfusionRecipe> holder = InfusionRecipeAdapter.createBottleRecipe(registries, fluid);
        if (holder != null) {
            recipes.putIfAbsent(holder.id(), holder);
        }
    }

    @Nullable
    private static RecipeHolder<InfusionRecipe> createFluidContainerRecipe(HolderLookup.Provider registries, ItemStack input, FluidStack available) {
        if (input == null || input.isEmpty() || available == null || available.isEmpty()) {
            return null;
        }
        ItemStack container = input.copyWithCount(1);
        ItemStack originalContainer = container.copy();
        IFluidHandlerItem handler = (IFluidHandlerItem)container.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null || handler.getTanks() <= 0) {
            return null;
        }
        int fillAmount = handler.fill(available.copy(), IFluidHandler.FluidAction.SIMULATE);
        if (fillAmount <= 0 || fillAmount > available.getAmount()) {
            return null;
        }
        FluidStack toFill = available.copyWithAmount(fillAmount);
        int filled = handler.fill(toFill, IFluidHandler.FluidAction.EXECUTE);
        if (filled <= 0) {
            return null;
        }
        ItemStack result = handler.getContainer();
        if (result.isEmpty() || ItemStack.isSameItemSameComponents((ItemStack)originalContainer, (ItemStack)result)) {
            return null;
        }
        result.setCount(1);
        return InfusionRecipeAdapter.holder(registries, "container", originalContainer, available.copyWithAmount(filled), result, 500);
    }

    @Nullable
    private static RecipeHolder<InfusionRecipe> createBottleRecipe(HolderLookup.Provider registries, FluidStack fluid) {
        ItemStack output;
        if (fluid == null || fluid.isEmpty() || fluid.getAmount() < 250) {
            return null;
        }
        if (fluid.is((Fluid)Fluids.WATER)) {
            output = PotionContents.createItemStack((Item)Items.POTION, (Holder)Potions.WATER);
        } else if (InfusionFluidHelper.isHoneyFluid((FluidStack)fluid)) {
            output = new ItemStack((ItemLike)Items.HONEY_BOTTLE);
        } else {
            return null;
        }
        output.setCount(1);
        return InfusionRecipeAdapter.holder(registries, "bottle", Items.GLASS_BOTTLE.getDefaultInstance(), fluid.copyWithAmount(250), output, 500);
    }

    private static void addSpawnEggRecipes(Level level, List<RecipeHolder<InfusionRecipe>> result) {
        RegistryAccess registries = level.registryAccess();
        for (Map.Entry<Item, ItemStack> entry : InfusionRecipeAdapter.spawnEggRecipes(level).entrySet()) {
            ItemStack input;
            Item inputItem = entry.getKey();
            ItemStack output = entry.getValue();
            if (inputItem == null || output == null || output.isEmpty() || (input = inputItem.getDefaultInstance()).isEmpty() || input.getMaxStackSize() <= 1) continue;
            input.setCount(input.getMaxStackSize());
            RecipeHolder<InfusionRecipe> holder = InfusionRecipeAdapter.holder((HolderLookup.Provider)registries, "spawn_egg", input, new FluidStack((Fluid)JDTEFluids.LIFE_FLUID_SOURCE.get(), 64000), output.copyWithCount(1), 100000);
            result.add(holder);
        }
    }

    private static void addRuntimeContainerMatches(Level level, Map<ResourceLocation, RecipeHolder<InfusionRecipe>> matches, ItemStack input, Map<FluidStack, Long> availableFluids) {
        RegistryAccess registries = level.registryAccess();
        for (FluidStack available : InfusionRecipeAdapter.simulationFluids(availableFluids)) {
            RecipeHolder<InfusionRecipe> holder;
            try {
                holder = InfusionRecipeAdapter.createFluidContainerRecipe((HolderLookup.Provider)registries, input, available);
            }
            catch (RuntimeException ignored) {
                continue;
            }
            InfusionRecipeAdapter.addIfFluidIsAvailable(matches, holder, availableFluids);
        }
    }

    private static void addRuntimeBottleMatches(Level level, Map<ResourceLocation, RecipeHolder<InfusionRecipe>> matches, ItemStack input, Map<FluidStack, Long> availableFluids) {
        if (!input.is(Items.GLASS_BOTTLE)) {
            return;
        }
        RegistryAccess registries = level.registryAccess();
        for (FluidStack available : InfusionRecipeAdapter.simulationFluids(availableFluids)) {
            RecipeHolder<InfusionRecipe> holder = InfusionRecipeAdapter.createBottleRecipe((HolderLookup.Provider)registries, available);
            InfusionRecipeAdapter.addIfFluidIsAvailable(matches, holder, availableFluids);
        }
    }

    private static void addRuntimeSpawnEggMatch(Map<ResourceLocation, RecipeHolder<InfusionRecipe>> matches, Level level, ItemStack input, Map<FluidStack, Long> availableFluids) {
        ItemStack output = InfusionRecipeAdapter.spawnEggRecipes(level).get(input.getItem());
        if (output == null || output.isEmpty() || input.getMaxStackSize() <= 1 || input.getCount() < input.getMaxStackSize()) {
            return;
        }
        RecipeHolder<InfusionRecipe> holder = InfusionRecipeAdapter.holder((HolderLookup.Provider)level.registryAccess(), "spawn_egg", input.copyWithCount(input.getMaxStackSize()), new FluidStack((Fluid)JDTEFluids.LIFE_FLUID_SOURCE.get(), 64000), output.copyWithCount(1), 100000);
        InfusionRecipeAdapter.addIfFluidIsAvailable(matches, holder, availableFluids);
    }

    private static List<FluidStack> simulationFluids(Map<FluidStack, Long> availableFluids) {
        if (availableFluids == null || availableFluids.isEmpty()) {
            return List.of();
        }
        ArrayList<FluidStack> result = new ArrayList<FluidStack>();
        for (Map.Entry<FluidStack, Long> entry : availableFluids.entrySet()) {
            long simulationAmount;
            long amount;
            FluidStack stack = entry.getKey();
            long l = amount = entry.getValue() == null ? 0L : entry.getValue();
            if (stack == null || stack.isEmpty() || amount <= 0L || (simulationAmount = Math.min(Math.min(amount, 8000L), Integer.MAX_VALUE)) <= 0L) continue;
            result.add(stack.copyWithAmount((int)simulationAmount));
        }
        return List.copyOf(result);
    }

    private static void addIfFluidIsAvailable(Map<ResourceLocation, RecipeHolder<InfusionRecipe>> matches, @Nullable RecipeHolder<InfusionRecipe> holder, Map<FluidStack, Long> availableFluids) {
        if (holder == null || !InfusionRecipeAdapter.hasFluid(availableFluids, ((InfusionRecipe)holder.value()).getFluidInput())) {
            return;
        }
        matches.putIfAbsent(holder.id(), holder);
    }

    private static boolean hasFluid(Map<FluidStack, Long> availableFluids, FluidStack required) {
        if (required == null || required.isEmpty() || availableFluids == null) {
            return false;
        }
        for (Map.Entry<FluidStack, Long> entry : availableFluids.entrySet()) {
            long amount;
            FluidStack available = entry.getKey();
            long l = amount = entry.getValue() == null ? 0L : entry.getValue();
            if (available == null || !FluidStack.isSameFluidSameComponents((FluidStack)available, (FluidStack)required) || amount < (long)required.getAmount()) continue;
            return true;
        }
        return false;
    }

    private static void addMatchingHolder(Map<ResourceLocation, RecipeHolder<InfusionRecipe>> matches, RecipeHolder<InfusionRecipe> holder, Map<Ingredient, Long> availableInputs, Map<FluidStack, Long> availableFluids) {
        ItemStack output;
        FluidStack fluid;
        if (holder == null || holder.value() == null) {
            return;
        }
        InfusionRecipe source = (InfusionRecipe)holder.value();
        ItemStack input = source.getInput();
        if (!(InfusionRecipeAdapter.isValid(input, fluid = source.getFluidInput(), output = source.getOutput()) && AdapterUtils.matchesRequired(availableInputs, Map.of(InfusionRecipeAdapter.itemIngredient(input), Long.valueOf(input.getCount()))) && FluidIngredientAllocator.matchesLong(List.of(LongSizedFluidIngredient.from((FluidStack)fluid)), availableFluids, (long)1L))) {
            return;
        }
        matches.putIfAbsent(holder.id(), holder);
    }

    private static RecipeHolder<InfusionRecipe> holder(HolderLookup.Provider registries, String kind, ItemStack input, FluidStack fluid, ItemStack output, int energyCost) {
        ResourceLocation id = InfusionRecipeAdapter.dynamicId(registries, kind, input, fluid, output);
        return new RecipeHolder(id, (Recipe)new InfusionRecipe(id, input.copy(), fluid.copy(), output.copy(), Math.max(1, energyCost)));
    }

    private static ResourceLocation dynamicId(HolderLookup.Provider registries, String kind, ItemStack input, FluidStack fluid, ItemStack output) {
        String signature = kind + "|" + AlloyFurnaceRecipeFingerprint.safeItemStack((ItemStack)input, (HolderLookup.Provider)registries) + "|" + AlloyFurnaceRecipeFingerprint.safeFluidStack((FluidStack)fluid, (HolderLookup.Provider)registries) + "|" + AlloyFurnaceRecipeFingerprint.safeItemStack((ItemStack)output, (HolderLookup.Provider)registries);
        return ResourceLocation.fromNamespaceAndPath((String)"jdte", (String)("infusion/" + kind + "/" + Integer.toUnsignedString(signature.hashCode(), 16)));
    }

    private static Map<Item, ItemStack> spawnEggRecipes(Level level) {
        ServerLevel serverLevel;
        if (level instanceof ServerLevel && (serverLevel = (ServerLevel)level).getServer() != null) {
            return MobLootSpawnEggHelper.getRecipes((ResourceManager)serverLevel.getServer().getResourceManager());
        }
        try {
            Class<?> cache = Class.forName("com.jdte.client.SpawnEggRecipeClientCache");
            Object value = cache.getMethod("get", new Class[0]).invoke(null, new Object[0]);
            if (!(value instanceof Map)) {
                return Map.of();
            }
            Map synced = (Map)value;
            LinkedHashMap<Item, ItemStack> result = new LinkedHashMap<Item, ItemStack>();
            for (Map.Entry entry : synced.entrySet()) {
                Object object = entry.getKey();
                if (!(object instanceof ResourceLocation)) continue;
                ResourceLocation inputId = (ResourceLocation)object;
                object = entry.getValue();
                if (!(object instanceof ResourceLocation)) continue;
                ResourceLocation outputId = (ResourceLocation)object;
                Item input = BuiltInRegistries.ITEM.getOptional(inputId).orElse(null);
                Item output = BuiltInRegistries.ITEM.getOptional(outputId).orElse(null);
                if (input == null || output == null) continue;
                result.put(input, output.getDefaultInstance());
            }
            return Map.copyOf(result);
        }
        catch (LinkageError | ReflectiveOperationException | RuntimeException ignored) {
            return Map.of();
        }
    }

    private static Ingredient itemIngredient(ItemStack stack) {
        return DataComponentIngredient.of((boolean)true, (ItemStack)stack.copyWithCount(1));
    }

    private static boolean isValid(ItemStack input, FluidStack fluid, ItemStack output) {
        return input != null && !input.isEmpty() && input.getCount() > 0 && fluid != null && !fluid.isEmpty() && fluid.getAmount() > 0 && output != null && !output.isEmpty();
    }

    private static /* synthetic */ List lambda$baseGeneratedRecipes$0(HolderLookup.Provider registries, RecipeManager ignored) {
        return InfusionRecipeAdapter.buildBaseGeneratedRecipes(registries);
    }
}
