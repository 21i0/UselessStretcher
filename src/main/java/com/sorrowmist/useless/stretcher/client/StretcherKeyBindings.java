package com.sorrowmist.useless.stretcher.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/** Client key bindings added by Useless Stretcher. */
public final class StretcherKeyBindings {
    public static final String CATEGORY = "key.categories.useless_stretcher";

    /** Toggles the wondrous staff between normal (30s) and permanent (no expiry) mode. */
    public static final KeyMapping WONDROUS_STAFF_MODE = new KeyMapping(
            "key.useless_stretcher.wondrous_staff_mode",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY);

    private StretcherKeyBindings() {
    }
}
