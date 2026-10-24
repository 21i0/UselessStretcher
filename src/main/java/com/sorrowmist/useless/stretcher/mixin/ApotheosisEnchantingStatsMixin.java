package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.content.apotheosis.ApotheosisTableData;
import dev.shadowsoffire.apotheosis.ench.table.ApothEnchantmentMenu.TableStats;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds saved virtual-bookshelf bonuses to native Apotheosis 1.20.1 table stats. */
@Pseudo
@Mixin(value = dev.shadowsoffire.apotheosis.ench.table.ApothEnchantmentMenu.class, remap = false)
public abstract class ApotheosisEnchantingStatsMixin {
    /** Native Apotheosis passes the current table level. */
    @Inject(method = "gatherStats(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;I)Ldev/shadowsoffire/apotheosis/ench/table/ApothEnchantmentMenu$TableStats;",
            at = @At("RETURN"), cancellable = true, remap = false, require = 1)
    private static void uselessStretcher$addMarkedTableStats(net.minecraft.world.level.Level level, BlockPos pos,
            int tableLevel, CallbackInfoReturnable<TableStats> callback) {
        uselessStretcher$boostMarkedTable(level, pos, callback);
    }

    private static void uselessStretcher$boostMarkedTable(net.minecraft.world.level.Level level, BlockPos pos,
            CallbackInfoReturnable<TableStats> callback) {
        if (!StretcherConfig.enableApotheosisCompat() || !(level instanceof ServerLevel serverLevel)) return;
        ApotheosisTableData.Boost bonus = ApotheosisTableData.get(serverLevel, pos);
        if (bonus.equals(ApotheosisTableData.Boost.ZERO)) return;
        TableStats current = callback.getReturnValue();
        callback.setReturnValue(new TableStats(
                current.eterna() + bonus.eterna(), current.quanta() + bonus.quanta(),
                current.arcana() + bonus.arcana(), current.rectification(), current.clues() + bonus.clues(),
                current.blacklist(), current.treasure()));
    }
}
