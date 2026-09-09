package com.sorrowmist.useless.stretcher;

import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.dimension.DimensionCompat;
import com.sorrowmist.useless.stretcher.init.ModBlockEntities;
import com.sorrowmist.useless.stretcher.init.ModBlocks;
import com.sorrowmist.useless.stretcher.init.ModCreativeTabs;
import com.sorrowmist.useless.stretcher.init.ModEntities;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.ModMenuTypes;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.network.Network;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(UselessStretcherMod.MODID)
public final class UselessStretcherMod {
    public static final String MODID = "useless_stretcher";

    public UselessStretcherMod(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenuTypes.MENUS.register(modBus);
        ModCreativeTabs.CREATIVE_TAB.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        StretcherComponents.init(modBus);
        DimensionCompat.init(modBus);
        modBus.addListener(Network::register);
        container.registerConfig(ModConfig.Type.COMMON, StretcherConfig.COMMON_SPEC);
    }
}
