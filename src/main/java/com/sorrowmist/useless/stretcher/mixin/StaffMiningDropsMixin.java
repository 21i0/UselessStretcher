package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.item.StaffMiningContext;
import com.sorrowmist.useless.utils.mining.MiningUtils;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(MiningUtils.class)
public abstract class StaffMiningDropsMixin {
    @Inject(method = "mergeItemStacks(Ljava/util/List;)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private static void uselessStretcher$merge(List<ItemStack> drops,
                                               CallbackInfoReturnable<List<ItemStack>> cir) {
        if (StaffMiningContext.active()) cir.setReturnValue(StaffMiningContext.mergeDrops(drops));
    }
}
