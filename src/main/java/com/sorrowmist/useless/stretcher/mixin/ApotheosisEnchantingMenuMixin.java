package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.apotheosis.ApotheosisMenuRefresh;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Reuses the vanilla menu base field accessor to refresh optional Apothic Enchanting menus. */
@Pseudo
@Mixin(targets = "dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentMenu", remap = false)
public abstract class ApotheosisEnchantingMenuMixin implements ApotheosisMenuRefresh {
    @Override
    public void uselessStretcher$refreshStats() {
        // Re-run the menu's normal slot-change path, not only the stats packet. This
        // recalculates enchantment costs/clues while preserving its current item/seed.
        ((EnchantmentMenu) (Object) this).slotsChanged(
                ((EnchantmentMenuAccessor) (Object) this).uselessStretcher$getEnchantSlots());
    }
}
