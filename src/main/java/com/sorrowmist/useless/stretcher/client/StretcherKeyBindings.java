package com.sorrowmist.useless.stretcher.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Client key bindings added by Useless Stretcher. */
public final class StretcherKeyBindings {
    public static final String CATEGORY = "key.categories.useless_stretcher";
    public static final KeyMapping STANDALONE_TELEPORT = new KeyMapping(
            "key.useless_stretcher.standalone_teleport", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN, CATEGORY);

    /** Opens the staff acceleration configuration screen. */
    public static final KeyMapping WONDROUS_STAFF_MODE = new KeyMapping(
            "key.useless_stretcher.wondrous_staff_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            CATEGORY);
    public static final KeyMapping WONDROUS_STAFF_TIME_STOP = new KeyMapping(
            "key.useless_stretcher.wondrous_staff_time_stop",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            CATEGORY);
    public static final KeyMapping TWO_I_REMOTE_RECLAIM = new KeyMapping(
            "key.useless_stretcher.two_i_remote_reclaim",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY);


    private StretcherKeyBindings() {
    }
}
