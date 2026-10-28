package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.client.gui.PortConfigScreen;
import com.sorrowmist.useless.client.gui.ldl.api.Ui;
import com.sorrowmist.useless.stretcher.client.gui.ButtonHelp;
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

/**
 * One server category. LAN hosts may edit it even after publishing their world.
 *
 * <p>Layout comes from Useless Mod's Apple UI API: the category button, the synchronized-config
 * hint and the done button are declared with {@code Ui.column} and laid out by
 * {@code FloatingScreen.layoutBody}, so the hint grows with its real line count through
 * {@code FloatingScreen.wrappedText} instead of a hand measured height. Both buttons stay native
 * {@link SelectableAE2Button}s embedded with {@code FloatingScreen.widgetNode}, keeping keyboard
 * focus, narration, the host-only tooltip and the {@code PortConfigScreen} navigation unchanged.</p>
 */
public final class StretcherConfigScreen extends FloatingScreen {
    /** Embedded native button height; spacing comes from the shared Apple layout. */
    private static final int BUTTON_HEIGHT = 20;
    private static final int PANEL_GAP = 10;

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
        var server = new SelectableAE2Button(0, 0, 100, BUTTON_HEIGHT,
                Component.translatable("useless_stretcher.configuration.server"), ignored ->
                        minecraft.setScreen(new PortConfigScreen(this,
                                StretcherConfig.SERVER_SPEC,
                                Component.translatable("useless_stretcher.configuration.server"))));
        server.active = editable;
        server.setTooltip(Tooltip.create(editable ? ButtonHelp.text("server_config")
                .copy().append(" ").append(config.getFullPath().toString())
                : Component.translatable("useless_stretcher.configuration.host_only")));
        var done = new SelectableAE2Button(0, 0, 100, BUTTON_HEIGHT,
                CommonComponents.GUI_DONE, ignored -> onClose());
        layoutBody(Ui.column(
                widgetNode(server),
                wrappedText(() -> Component.translatable("useless_stretcher.configuration.synced"),
                        StretcherScreenStyle.SUBTLE_TEXT_COLOR),
                widgetNode(done)
        ).gap(PANEL_GAP));
    }

    public static void accept(ServerConfigSync.Payload payload) {
        // The integrated server owns this same spec in memory: don't replace its writable config.
        if (Minecraft.getInstance().hasSingleplayerServer()) return;
        var config = ServerConfigSync.config();
        if (config != null) config.acceptSyncedConfig(payload.contents());
    }

    /** Buttons and the wrapped hint are painted by the shared Apple host; nothing is drawn here. */
    @Override protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return true; }
}
