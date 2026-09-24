package com.sorrowmist.useless.stretcher.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Client key bindings added by Useless Stretcher. */
public final class StretcherKeyBindings {
    public static final String CATEGORY = "key.categories.useless_stretcher";

    /** Opens the staff acceleration configuration screen. */
    public static final KeyMapping WONDROUS_STAFF_MODE = new KeyMapping(
            "key.useless_stretcher.wondrous_staff_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            CATEGORY);


    private StretcherKeyBindings() {
    }
}
