package com.sorrowmist.useless.stretcher.mixin;

import com.sorrowmist.useless.core.config.ConfigManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Forces both the omniversal pattern assembly and the passive crafting hatch
 * to 4096 slots regardless of the server config values (both are capped at 540
 * in the upstream config).
 */
@Mixin(ConfigManager.class)
public abstract class ConfigManagerMixin {

    /** @reason Expand the pattern assembly to a fixed 4096 slots. */
    @Overwrite
    public static int getOmniversalPatternSlots() {
        return 4096;
    }

    /** @reason Expand the passive crafting hatch to a fixed 4096 configured slots. */
    @Overwrite
    public static int getOmniversalPassivePatternSlots() {
        return 4096;
    }
}
