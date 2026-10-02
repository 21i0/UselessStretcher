package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.client.gui.FloatingScreen;
import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.config.ServerConfigSync;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

/** One server category. LAN hosts may edit it even after publishing their world. */
public final class StretcherConfigScreen extends FloatingScreen {
    private final Screen parent;

    public StretcherConfigScreen(Screen parent) {
        super(Component.translatable("useless_stretcher.configuration.title"),
                "config", 320, 180, 250, 140);
        this.parent = parent;
    }

    @Override protected void initContent() {
        var config = ServerConfigSync.config();
        boolean editable = minecraft.hasSingleplayerServer() && config != null
                && StretcherConfig.SERVER_SPEC.isLoaded();
        var server = addRenderableWidget(new SelectableAE2Button(contentLeft(), contentTop() + 4,
                contentWidth(), 20,
                Component.translatable("useless_stretcher.configuration.server"), ignored ->
                        minecraft.setScreen(new ConfigurationScreen.ConfigurationSectionScreen(this,
                                ModConfig.Type.SERVER, config,
                                Component.translatable("useless_stretcher.configuration.server")))));
        server.active = editable;
        server.setTooltip(Tooltip.create(editable ? com.sorrowmist.useless.stretcher.client.gui.ButtonHelp.text("server_config")
                .copy().append(" ").append(config.getFullPath().toString())
                : Component.translatable("useless_stretcher.configuration.host_only")));
        int hintHeight = font.split(Component.translatable("useless_stretcher.configuration.synced"),
                Math.max(1, contentWidth())).size() * (font.lineHeight + 2);
        int doneTop = 36 + hintHeight + 14;
        addRenderableWidget(new SelectableAE2Button(contentLeft(), contentTop() + doneTop,
                contentWidth(), 20, CommonComponents.GUI_DONE, ignored -> onClose()));
        setContentExtent(doneTop + 24);
    }

    public static void accept(ServerConfigSync.Payload payload) {
        // The integrated server owns this same spec in memory: don't replace its writable config.
        if (Minecraft.getInstance().hasSingleplayerServer()) return;
        var config = ServerConfigSync.config();
        if (config != null) ConfigTracker.acceptSyncedConfig(config, payload.contents());
    }

    @Override protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int y = contentTop() + 36;
        for (var line : font.split(Component.translatable("useless_stretcher.configuration.synced"),
                Math.max(1, contentWidth()))) {
            graphics.drawString(font, line, contentLeft(), y, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
            y += font.lineHeight + 2;
        }
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return true; }
}
