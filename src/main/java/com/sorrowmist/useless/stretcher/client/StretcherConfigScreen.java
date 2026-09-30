package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.config.ServerConfigSync;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

/** One server category. LAN hosts may edit it even after publishing their world. */
public final class StretcherConfigScreen extends Screen {
    private final Screen parent;

    public StretcherConfigScreen(Screen parent) {
        super(Component.translatable("useless_stretcher.configuration.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        var config = ServerConfigSync.config();
        boolean editable = minecraft.hasSingleplayerServer() && config != null
                && StretcherConfig.SERVER_SPEC.isLoaded();
        Button server = addRenderableWidget(Button.builder(
                Component.translatable("useless_stretcher.configuration.server"), ignored ->
                        minecraft.setScreen(new ConfigurationScreen.ConfigurationSectionScreen(this,
                                ModConfig.Type.SERVER, config,
                                Component.translatable("useless_stretcher.configuration.server"))))
                .bounds(width / 2 - 110, height / 2 - 24, 220, 20).build());
        server.active = editable;
        server.setTooltip(Tooltip.create(editable ? Component.literal(config.getFullPath().toString())
                : Component.translatable("useless_stretcher.configuration.host_only")));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, ignored -> onClose())
                .bounds(width / 2 - 100, height - 35, 200, 20).build());
    }

    public static void accept(ServerConfigSync.Payload payload) {
        // The integrated server owns this same spec in memory: don't replace its writable config.
        if (Minecraft.getInstance().hasSingleplayerServer()) return;
        var config = ServerConfigSync.config();
        if (config != null) ConfigTracker.acceptSyncedConfig(config, payload.contents());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 25, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("useless_stretcher.configuration.synced"),
                width / 2, height / 2 + 10, 0xAAAAAA);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
