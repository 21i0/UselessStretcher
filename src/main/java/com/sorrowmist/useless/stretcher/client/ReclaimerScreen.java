package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.FloatingScreen;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import com.sorrowmist.useless.stretcher.client.gui.StretcherConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative history, grouped once per response rather than per frame. */
public final class ReclaimerScreen extends FloatingScreen {
    private enum Page { PLAYERS, TYPES, TARGETS }
    private static final int PANEL_WIDTH = 430;
    private static final int ROW_HEIGHT = 27;
    private static final int LIST_TOP = 21;
    private final Screen parent;
    private final boolean personal;
    private final Map<UUID, List<Network.ReclaimerEntry>> byOwner = new LinkedHashMap<>();
    private List<UUID> owners = List.of();
    private List<Network.ReclaimerEntry> targets = List.of();
    private Page page;
    private UUID selectedOwner;
    private String selectedName = "";
    private boolean range;
    private int left, top, panelWidth, panelHeight, visibleRows, scroll;

    public ReclaimerScreen(Screen parent, List<Network.ReclaimerEntry> entries, boolean personal) {
        super(Component.translatable(personal ? "gui.useless_stretcher.range.history_title"
                : "gui.useless_stretcher.reclaimer.title"), "reclaimer", PANEL_WIDTH, 270, 270, 160);
        this.parent = parent;
        this.personal = personal;
        page = personal ? Page.TYPES : Page.PLAYERS;
        index(entries);
    }

    public boolean personal() { return personal; }

    private void index(List<Network.ReclaimerEntry> entries) {
        byOwner.clear();
        entries.stream().sorted(Comparator.comparing(Network.ReclaimerEntry::ownerName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Network.ReclaimerEntry::owner)
                .thenComparing(Network.ReclaimerEntry::label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Network.ReclaimerEntry::id))
                .forEach(entry -> byOwner.computeIfAbsent(entry.owner(), ignored -> new ArrayList<>()).add(entry));
        owners = List.copyOf(byOwner.keySet());
        if (personal && selectedOwner == null && !owners.isEmpty()) selectedOwner = owners.getFirst();
        var owned = byOwner.getOrDefault(selectedOwner, List.of());
        if (!owned.isEmpty()) selectedName = owned.getFirst().ownerName();
        targets = owned.stream().filter(entry -> entry.range() == range).toList();
    }

    public void update(List<Network.ReclaimerEntry> entries) {
        index(entries);
        scroll = Mth.clamp(scroll, 0, Math.max(0, rowCount() - visibleRows));
        rebuildWidgets();
    }

    private int rowCount() {
        return page == Page.PLAYERS ? (owners.size() + 1) / 2 : page == Page.TYPES ? 2 : targets.size();
    }
    private Component category(boolean ranges) {
        return Component.translatable("gui.useless_stretcher.reclaimer." + (ranges ? "ranges" : "permanent"));
    }
    private void navigate(Page next) { page = next; scroll = 0; rebuildWidgets(); }

    @Override protected void initContent() {
        setContentExtent(contentHeight());
        panelWidth = contentWidth();
        panelHeight = contentHeight();
        visibleRows = Math.max(1, (panelHeight - LIST_TOP - 27) / ROW_HEIGHT);
        left = contentLeft();
        top = contentTop();
        scroll = Mth.clamp(scroll, 0, Math.max(0, rowCount() - visibleRows));
        if (page == Page.PLAYERS) {
            int playerWidth = (panelWidth - 11) / 2;
            for (int row = 0; row < visibleRows; row++) {
                int first = (scroll + row) * 2;
                for (int column = 0; column < 2 && first + column < owners.size(); column++) {
                    int index = first + column;
                    UUID owner = owners.get(index);
                    var owned = byOwner.get(owner);
                    String name = owned.getFirst().ownerName();
                    var button = new SelectableAE2Button(left + 4 + column * (playerWidth + 3),
                        top + LIST_TOP + row * ROW_HEIGHT + 3, playerWidth, 20,
                        Component.literal(font.plainSubstrByWidth(name, playerWidth - 48) + " (" + owned.size() + ")"),
                        ignored -> {
                            selectedOwner = owner;
                            selectedName = name;
                            navigate(Page.TYPES);
                        });
                    button.setTooltip(Tooltip.create(Component.literal(name + " (" + owner + ") ")
                        .append(com.sorrowmist.useless.stretcher.client.gui.ButtonHelp.text("player"))));
                    addRenderableWidget(button);
                }
            }
        } else if (page == Page.TYPES) {
            var owned = byOwner.getOrDefault(selectedOwner, List.of());
            for (int i = 0; i < 2; i++) {
                boolean ranges = i == 1;
                long count = owned.stream().filter(entry -> entry.range() == ranges).count();
                int categoryWidth = (panelWidth - 11) / 2;
                addRenderableWidget(new SelectableAE2Button(left + 4 + i * (categoryWidth + 3), top + LIST_TOP + 3,
                        categoryWidth, 20, Component.translatable("gui.useless_stretcher.reclaimer.category_count", category(ranges), count),
                        ignored -> {
                            range = ranges;
                            targets = byOwner.getOrDefault(selectedOwner, List.of()).stream()
                                    .filter(entry -> entry.range() == range).toList();
                            navigate(Page.TARGETS);
                        }));
            }
        } else {
            for (int i = 0; i < visibleRows && scroll + i < targets.size(); i++) {
                var entry = targets.get(scroll + i);
                addRenderableWidget(new SelectableAE2Button(left + panelWidth - 50,
                        top + LIST_TOP + i * ROW_HEIGHT + 4, 46, 18,
                        Component.translatable("gui.useless_stretcher.range.reclaim"), ignored ->
                        minecraft.setScreen(new StretcherConfirmScreen(confirmed -> {
                            minecraft.setScreen(this);
                            if (confirmed) Network.reclaimTarget(entry.range(), entry.id(), personal);
                        }, Component.translatable("gui.useless_stretcher.range.reclaim"),
                                Component.literal(entry.label() + " · " + entry.dimension() + " " + entry.pos().toShortString())))));
            }
        }
        addRenderableWidget(new SelectableAE2Button(left + panelWidth - 144, top + panelHeight - 20, 69, 18,
                Component.translatable("gui.useless_stretcher.reclaimer.refresh"), ignored -> {
                    if (personal) Network.requestPersonalReclaimer(); else Network.requestReclaimer();
                }));
        addRenderableWidget(new SelectableAE2Button(left + panelWidth - 71, top + panelHeight - 20, 69, 18,
                Component.translatable("gui.useless_stretcher.back"), ignored -> onClose()));
    }

    @Override protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String name = personal ? Component.translatable("gui.useless_stretcher.reclaimer.mine").getString() : selectedName;
        Component subtitle = page == Page.PLAYERS
                ? Component.translatable("gui.useless_stretcher.reclaimer.players", owners.size())
                : Component.literal(name + (page == Page.TARGETS ? " > " + category(range).getString() : ""));
        graphics.drawString(font, font.plainSubstrByWidth(subtitle.getString(), panelWidth - 8),
                left + 4, top + 4, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        StretcherScreenStyle.drawInset(graphics, left, top + LIST_TOP - 4,
                left + panelWidth, top + LIST_TOP + visibleRows * ROW_HEIGHT + 2);
        if (page == Page.TARGETS) {
            for (int i = 0; i < visibleRows && scroll + i < targets.size(); i++) {
                var entry = targets.get(scroll + i);
                int y = top + LIST_TOP + i * ROW_HEIGHT;
                if (mouseX >= left + 2 && mouseX < left + panelWidth - 56 && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                    graphics.fill(left + 2, y, left + panelWidth - 56, y + ROW_HEIGHT, 0x22517497);
                }
                graphics.drawString(font, font.plainSubstrByWidth(entry.label(), panelWidth - 66),
                        left + 6, y + 3, StretcherScreenStyle.TEXT_COLOR, false);
                graphics.drawString(font, font.plainSubstrByWidth(entry.dimension() + " " + entry.pos().toShortString(), panelWidth - 66),
                        left + 6, y + 15, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
            }
        }
        if (rowCount() == 0) graphics.drawString(font, Component.translatable("gui.useless_stretcher.reclaimer.empty"),
                left + 6, top + LIST_TOP + 8, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        if (rowCount() > visibleRows) graphics.drawString(font,
                Component.literal((scroll + 1) + "-" + Math.min(rowCount(), scroll + visibleRows) + " / " + rowCount()),
                left + 2, top + panelHeight - 15, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
    }

    @Override protected void renderContentTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (page != Page.TARGETS || mouseX < left + 2 || mouseX >= left + panelWidth - 56
                || mouseY < top + LIST_TOP) return;
        int row = (mouseY - top - LIST_TOP) / ROW_HEIGHT;
        if (row >= visibleRows || scroll + row >= targets.size()) return;
        var entry = targets.get(scroll + row);
        graphics.renderTooltip(font, List.of(
                Component.literal(entry.ownerName() + " (" + entry.owner() + ")"),
                Component.literal(entry.label()),
                Component.literal(entry.dimension() + " " + entry.pos().toShortString())),
                java.util.Optional.empty(), mouseX, mouseY);
    }

    @Override protected boolean scrollContent(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (mouseY < top + LIST_TOP || mouseY >= top + LIST_TOP + visibleRows * ROW_HEIGHT)
            return super.scrollContent(mouseX, mouseY, deltaX, deltaY);
        if (page == Page.TYPES) return true;
        int next = Mth.clamp(scroll - (int) Math.signum(deltaY), 0, Math.max(0, rowCount() - visibleRows));
        if (next != scroll) { scroll = next; rebuildWidgets(); }
        return true;
    }
    @Override public void onClose() {
        if (page == Page.TARGETS) navigate(Page.TYPES);
        else if (page == Page.TYPES && !personal) navigate(Page.PLAYERS);
        else minecraft.setScreen(parent);
    }
    @Override public boolean isPauseScreen() { return false; }
}
