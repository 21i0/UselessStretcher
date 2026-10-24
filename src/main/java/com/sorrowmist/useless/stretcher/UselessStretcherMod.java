package com.sorrowmist.useless.stretcher;

import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.config.ServerConfigSync;
import com.sorrowmist.useless.stretcher.dimension.world.TwoIStarfieldDimensions;
import com.sorrowmist.useless.stretcher.init.ModBlockEntities;
import com.sorrowmist.useless.stretcher.init.ModBlocks;
import com.sorrowmist.useless.stretcher.init.ModCreativeTabs;
import com.sorrowmist.useless.stretcher.init.ModEntities;
import com.sorrowmist.useless.stretcher.init.ModItemDefaults;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.ModMenuTypes;
import com.sorrowmist.useless.stretcher.init.ModSounds;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.common.MinecraftForge;
import com.sorrowmist.useless.compat.forge1201.network.RegisterPayloadHandlersEvent;

@Mod(UselessStretcherMod.MODID)
public final class UselessStretcherMod {
    public static final String MODID = "useless_stretcher";

    public UselessStretcherMod() {
        IEventBus modBus=net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        var modContext=net.minecraftforge.fml.ModLoadingContext.get();
        modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                event.enqueueWork(() -> modBus.post(new RegisterPayloadHandlersEvent(MODID))));
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenuTypes.MENUS.register(modBus);
        ModCreativeTabs.CREATIVE_TAB.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        modBus.addListener(ModEntities::registerAttributes);
        StretcherComponents.init(modBus);
        TwoIStarfieldDimensions.init(modBus);
        com.sorrowmist.useless.stretcher.content.capsule.CapsuleDimensions.init(modBus);
        modBus.addListener(Network::register);
        modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) -> {
            if (net.minecraftforge.fml.ModList.get().isLoaded("curios"))
                event.enqueueWork(com.sorrowmist.useless.stretcher.compat.TwoICuriosCompat::register);
        });
        // All gameplay switches live in the server config. MinecraftForge synchronizes this
        // spec to every client connected to the world, so the host/server setting is authoritative.
        ServerConfigSync.migrateLegacy(FMLPaths.CONFIGDIR.get(), FMLPaths.GAMEDIR.get().resolve("defaultconfigs"));
        modContext.registerConfig(ModConfig.Type.SERVER, StretcherConfig.SERVER_SPEC);
        modBus.addListener(ServerConfigSync::onReload);
        MinecraftForge.EVENT_BUS.addListener(ServerConfigSync::onLogin);
    }
}
