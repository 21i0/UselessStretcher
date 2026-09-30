package com.sorrowmist.useless.stretcher;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import com.sorrowmist.useless.stretcher.client.StretcherConfigScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Registers the same editable NeoForge config screen used by Useless Mod. */
@Mod(value = UselessStretcherMod.MODID, dist = Dist.CLIENT)
public final class UselessStretcherClient {
    public UselessStretcherClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new StretcherConfigScreen(parent));
    }
}
