package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.stretcher.content.ae.InstalledPatternHost;
import com.sorrowmist.useless.stretcher.content.ae.InstalledStretcherPatterns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnaceAeManager;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AdvancedAlloyFurnaceBlockEntity.class, MultiblockAlloyFurnaceCoreBlockEntity.class})
public abstract class InstalledStretcherFurnaceMixin implements InstalledPatternHost {
    @Shadow(remap = false) @Final private AdvancedAlloyFurnaceAeManager aeManager;

    @Inject(method = "getPatternStacks", at = @At("RETURN"), cancellable = true)
    private void uselessStretcher$expand(CallbackInfoReturnable<Iterable<ItemStack>> cir) {
        cir.setReturnValue(InstalledStretcherPatterns.expand(cir.getReturnValue(), (BlockEntity) (Object) this));
    }

    @Override public void uselessStretcher$refreshInstalledPatterns() { aeManager.updatePatterns(); }
}
