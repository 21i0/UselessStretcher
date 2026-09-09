package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.network.PassiveCraftingStatusPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The status packet rejects slots above the inlined
 * {@code PassiveCraftingHatchBlockEntity.MAX_PATTERN_SLOTS} (540). Raise both
 * guards to 4096 so the enlarged hatch can sync status for every page.
 */
@Mixin(PassiveCraftingStatusPacket.class)
public abstract class PassiveCraftingStatusPacketMixin {

    @ModifyConstant(
            method = "decode(Lnet/minecraft/network/FriendlyByteBuf;)Lcom/sorrowmist/useless/network/PassiveCraftingStatusPacket;",
            constant = @Constant(intValue = 540))
    private static int uselessStretcher$expandDecode(int original) {
        return 4096;
    }

    @ModifyConstant(method = "validateStatuses(Ljava/util/List;)V", constant = @Constant(intValue = 540))
    private static int uselessStretcher$expandValidate(int original) {
        return 4096;
    }

    @ModifyConstant(method = "<init>(ILnet/minecraft/core/BlockPos;Ljava/util/List;)V",
            constant = @Constant(intValue = 27))
    private static int uselessStretcher$ctorPageSize(int original) {
        return 90;
    }

    @ModifyConstant(
            method = "decode(Lnet/minecraft/network/FriendlyByteBuf;)Lcom/sorrowmist/useless/network/PassiveCraftingStatusPacket;",
            constant = @Constant(intValue = 27))
    private static int uselessStretcher$decodePageSize(int original) {
        return 90;
    }

    @ModifyConstant(method = "validateStatuses(Ljava/util/List;)V", constant = @Constant(intValue = 27))
    private static int uselessStretcher$validatePageSize(int original) {
        return 90;
    }
}
