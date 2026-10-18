package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/** Brain-based monsters can write ATTACK_TARGET directly, bypassing Mob.setTarget. */
@Mixin(Brain.class)
public abstract class TwoIDollBrainTargetMixin {
    @Inject(method = "setMemoryInternal", at = @At("HEAD"), cancellable = true)
    private void uselessStretcher$ignoreDollAttackMemory(MemoryModuleType<?> type,
            Optional<? extends ExpirableValue<?>> value, CallbackInfo ci) {
        if (type == MemoryModuleType.ATTACK_TARGET && value.isPresent()
                && value.get().getValue() instanceof TwoIDollEntity) {
            ((Brain<?>)(Object)this).eraseMemory(MemoryModuleType.ATTACK_TARGET);
            ci.cancel();
        }
    }
}
