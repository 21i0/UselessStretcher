package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.client.render.StaffLeafRewardRenderer;
import com.sorrowmist.useless.stretcher.client.render.TimeFlowRenderer;
import com.sorrowmist.useless.stretcher.client.render.TwoIDollRenderer;
import com.sorrowmist.useless.stretcher.client.render.TwoIDollItemRenderer;
import com.sorrowmist.useless.stretcher.client.render.TwoIDollDisplayRenderer;
import com.sorrowmist.useless.stretcher.client.render.WondrousStaffAccelerationRenderer;
import com.sorrowmist.useless.stretcher.init.ModEntities;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.ModBlockEntities;
import com.sorrowmist.useless.stretcher.init.ModMenuTypes;
import com.sorrowmist.useless.stretcher.screen.OmniversalMyriadScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

@EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void setupOptionalCurios(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        if (net.minecraftforge.fml.ModList.get().isLoaded("curios"))
            event.enqueueWork(com.sorrowmist.useless.stretcher.client.render.TwoIDollCurioRenderer::register);
    }

    @SubscribeEvent
    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenuTypes.OMNIVERSAL_MYRIAD.get(), OmniversalMyriadScreen::new));
    }

    @SubscribeEvent
    public static void registerDollLayer(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TwoIDollRenderer.LAYER, TwoIDollRenderer.Model::createLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WONDROUS_STAFF_ACCELERATION.get(),
                WondrousStaffAccelerationRenderer::new);
        event.registerEntityRenderer(ModEntities.STAFF_LEAF_REWARD.get(), StaffLeafRewardRenderer::new);
        event.registerEntityRenderer(ModEntities.TIME_FLOW.get(), TimeFlowRenderer::new);
        event.registerEntityRenderer(ModEntities.TWO_I_DOLL.get(), TwoIDollRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TWO_I_DOLL_DISPLAY.get(), TwoIDollDisplayRenderer::new);
    }

    public static void registerItemRenderer(java.util.function.Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return TwoIDollItemRenderer.get();
            }
        });
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(TwoIDollItemRenderer.get());
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(StretcherKeyBindings.STANDALONE_TELEPORT);
        event.register(StretcherKeyBindings.WONDROUS_STAFF_MODE);
        event.register(StretcherKeyBindings.WONDROUS_STAFF_TIME_STOP);
        event.register(StretcherKeyBindings.TWO_I_REMOTE_RECLAIM);
    }
}
