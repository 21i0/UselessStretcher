package com.sorrowmist.useless.stretcher.init;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers a small creative tab so the handheld stretcher and the dimension teleport blocks are
 * visible and searchable in JEI. The omniversal myriad block is deliberately left out: it is the
 * addon's wildcard mold, meant to be obtained through its crafting recipe rather than spawned.
 *
 * <p>It is <b>not</b> left out because of JEI recipe duplication (an earlier comment here claimed
 * that — it was wrong). Verified against Useless Mod 2.3.6:
 * <ul>
 *   <li>{@code JEIPlugin.registerRecipeCatalysts} hard-codes the alloy-furnace catalysts to
 *       {@code advanced_alloy_furnace_block} and {@code multiblock_alloy_furnace_core} only, so a
 *       block of ours can never become a recipe catalyst (we ship no JEI plugin at all).</li>
 *   <li>Recipe pages come from {@code AlloyFurnaceRecipeCatalog} and are de-duplicated by
 *       {@code Entry#identity()}, so extra items cannot multiply them.</li>
 *   <li>Being in {@code useless_mod:molds} only makes it appear as a mold catalyst <i>slot inside</i>
 *       existing recipe pages, not as an extra page.</li>
 * </ul>
 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, UselessStretcherMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = CREATIVE_TAB.register(
            "main",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.USELESS_STRETCHER.get()))
                    .title(Component.translatable("itemGroup.useless_stretcher"))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.USELESS_STRETCHER.get());
                        output.accept(ModItems.WONDROUS_STAFF.get());
                        output.accept(com.sorrowmist.useless.stretcher.dimension.init.ModItems.QUAD_CHUNK_DIMENSION_BLOCK_ITEM.get());
                        output.accept(com.sorrowmist.useless.stretcher.dimension.init.ModItems.NINE_CHUNK_DIMENSION_BLOCK_ITEM.get());
                        output.accept(com.sorrowmist.useless.stretcher.dimension.init.ModItems.QUAD_CHUNK_ODD_DIMENSION_BLOCK_ITEM.get());
                        output.accept(com.sorrowmist.useless.stretcher.dimension.init.ModItems.NINE_CHUNK_ODD_DIMENSION_BLOCK_ITEM.get());
                    })
                    .build()
    );

    private ModCreativeTabs() {
    }
}
