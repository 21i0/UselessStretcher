package com.sorrowmist.useless.stretcher.event;

import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.item.WondrousStaffLootRefresh;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Server-side staff features that operate on the normal block-drops pipeline. */
@EventBusSubscriber(modid = com.sorrowmist.useless.stretcher.UselessStretcherMod.MODID)
public final class WondrousStaffFeatureEvents {
    private WondrousStaffFeatureEvents() {
    }

    /**
     * Mirrors the miner manual's safe point of interception: drops are replaced before their
     * ItemEntity is inserted into the world, so no second pickup pass or inventory scan is needed.
     */
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        Entity breaker = event.getBreaker();
        if (!(breaker instanceof Player player) || player.level().isClientSide()) return;

        ItemStack tool = event.getTool();
        if (!tool.is(ModItems.WONDROUS_STAFF.get())
                || !WondrousStaffAcceleration.isAutoSmeltEnabled(tool)) return;

        Level level = player.level();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack input = drop.getItem();
            if (input.isEmpty()) continue;
            ItemStack result = level.getRecipeManager()
                    .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level)
                    .map(holder -> holder.value().getResultItem(level.registryAccess()))
                    .orElse(ItemStack.EMPTY);
            if (result.isEmpty()) continue;

            // The loot table has already applied Fortune. Only the item identity is converted;
            // the original drop count is preserved exactly like the miner manual.
            drop.setItem(result.copyWithCount(result.getCount() * input.getCount()));
        }
    }

    /** Minecart containers use entity interaction rather than Item#interactLivingEntity. */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack staff = event.getItemStack();
        if (!staff.is(ModItems.WONDROUS_STAFF.get())
                || !player.isShiftKeyDown()
                || !StretcherConfig.enableStaffLootRefresh()
                || !WondrousStaffAcceleration.isLootRefreshEnabled(staff)
                || !WondrousStaffLootRefresh.isSupportedEntity(event.getTarget())) {
            return;
        }

        InteractionResult result = WondrousStaffLootRefresh.tryRefreshEntity(player, event.getTarget());
        if (result != InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide()));
        }
    }
}
