package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.item.WondrousStaffLootRefresh;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractMinecartContainer.class)
public abstract class MinecartContainerLootMemoryMixin {
    @Inject(method = "setLootTable(Lnet/minecraft/resources/ResourceKey;)V", at = @At("TAIL"))
    private void uselessStretcher$rememberLootTable(ResourceKey<LootTable> lootTable, CallbackInfo ci) {
        WondrousStaffLootRefresh.remember((AbstractMinecartContainer) (Object) this, lootTable);
    }

    @Inject(method = "setLootTable(Lnet/minecraft/resources/ResourceKey;J)V", at = @At("TAIL"))
    private void uselessStretcher$rememberLootTableWithSeed(ResourceKey<LootTable> lootTable, long seed,
                                                             CallbackInfo ci) {
        WondrousStaffLootRefresh.remember((AbstractMinecartContainer) (Object) this, lootTable);
    }
}
