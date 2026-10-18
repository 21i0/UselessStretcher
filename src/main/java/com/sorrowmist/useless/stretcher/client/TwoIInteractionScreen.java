package com.sorrowmist.useless.stretcher.client;

import net.minecraft.client.gui.screens.Screen;

/** The entity whose already-open interaction a screen is keeping alive; item mode has none. */
public interface TwoIInteractionScreen {
    int interactionEntityId();

    static int entityFor(Screen screen) {
        return screen instanceof TwoIInteractionScreen interaction ? interaction.interactionEntityId() : -1;
    }
}
