package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.client.render.WondrousStaffAccelerationRenderer;
import com.sorrowmist.useless.stretcher.init.ModEntities;
import com.sorrowmist.useless.stretcher.init.ModMenuTypes;
import com.sorrowmist.useless.stretcher.screen.OmniversalMyriadScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.OMNIVERSAL_MYRIAD.get(), OmniversalMyriadScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WONDROUS_STAFF_ACCELERATION.get(),
                WondrousStaffAccelerationRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(StretcherKeyBindings.WONDROUS_STAFF_MODE);
    }
}
