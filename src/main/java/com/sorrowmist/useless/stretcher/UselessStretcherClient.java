package com.sorrowmist.useless.stretcher;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Registers the same editable NeoForge config screen used by Useless Mod. */
@Mod(value = UselessStretcherMod.MODID, dist = Dist.CLIENT)
public final class UselessStretcherClient {
    public UselessStretcherClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
