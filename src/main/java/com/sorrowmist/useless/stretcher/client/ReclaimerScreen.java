package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.client.gui.ldl.api.Flex;
import com.sorrowmist.useless.client.gui.ldl.api.Ui;
import com.sorrowmist.useless.client.gui.ldl.api.UiNode;
import com.sorrowmist.useless.stretcher.client.gui.ButtonHelp;
import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.StretcherConfirmScreen;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative history, grouped once per response rather than per frame.
 *
 * <p>Rebuilt on Useless Mod's Apple UI API: the page caption, the player/category grid, the target
 * rows and the footer are declared with {@code Ui.column/section/row} and laid out by
 * {@code FloatingScreen.layoutBody}, so no list rectangle or row coordinate is measured by hand.
 * Every row still holds a native {@link SelectableAE2Button} embedded with
 * {@code FloatingScreen.widgetNode}, so the category/player/reclaim callbacks, the confirmation
 * screen, keyboard focus, narration and hover help are unchanged; the row budget only decides how
 * many paged rows are declared, exactly like the previous widget paging.</p>
 */
public final class ReclaimerScreen extends StaffScreen {
    private enum Page { PLAYERS, TYPES, TARGETS }
    private static final int PANEL_WIDTH = 430;
    /** Target rows keep both text lines, so the row rhythm stays the previous 27px. */
    private static final int ROW_HEIGHT = 27;
    /** Embedded native heights: the player/category grid and the action buttons. */
    private static final int GRID_ROW_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 18;
    private static final int RECLAIM_WIDTH = 46;
    private static final int FOOTER_BUTTON_WIDTH = 69;
    private static final int ROW_GAP = 3;
    /** One caption line; the API renders it at its theme scale but reserves the real line box. */
    private static final int LABEL_HEIGHT = 9;

    private final Screen parent;
    private final boolean personal;
    private final Map<UUID, List<Network.ReclaimerEntry>> byOwner = new LinkedHashMap<>();
    private List<UUID> owners = List.of();
    private List<Network.ReclaimerEntry> targets = List.of();
    private Page page;
    private UUID selectedOwner;
    private String selectedName = "";
    private boolean range;
    private int visibleRows, scroll;
    private boolean requested;

    public ReclaimerScreen(Screen parent, List<Network.ReclaimerEntry> entries, boolean personal) {
        super(Component.translatable(personal ? "gui.useless_stretcher.staff_tabs.machines"
                : "gui.useless_stretcher.reclaimer.title"), personal && parent instanceof StaffScreen ? Tab.MACHINES : null,
                parent, staffHand(parent), "reclaimer", PANEL_WIDTH, 270, 270, 160);
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
        if (personal && selectedOwner == null && !owners.isEmpty()) selectedOwner = owners.get(0);
        var owned = byOwner.getOrDefault(selectedOwner, List.of());
        if (!owned.isEmpty()) selectedName = owned.get(0).ownerName();
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

    @Override protected void refreshStaffSettings() { requested = false; }
    @Override protected void initContent() {
        if (staffTab() == Tab.MACHINES && !requested && minecraft.getConnection() != null) {
            requested = true;
            Network.requestPersonalReclaimer();
        }
        visibleRows = fittedRowCount();
        scroll = Mth.clamp(scroll, 0, Math.max(0, rowCount() - visibleRows));
        layoutBody(Ui.column(listCard(), footerRow()).gap(Ui.t().gap));
    }

    /** Rows that fit the body: both card paddings, the caption and the footer row are reserved. */
    private int fittedRowCount() {
        int reserved = Ui.t().cardPad * 2 + LABEL_HEIGHT + Ui.t().gap + BUTTON_HEIGHT + Ui.t().gap;
        return Math.max(1, (contentHeight() - reserved) / (ROW_HEIGHT + ROW_GAP));
    }

    // ------------------------------------------------------------------ 声明式页面
    /** The page title doubles as the card caption; the rows below it stay paged by one screen. */
    private UiNode listCard() {
        Flex rows = Ui.column().gap(ROW_GAP);
        switch (page) {
            case PLAYERS -> addPlayerRows(rows);
            case TYPES -> rows.add(typeRow());
            case TARGETS -> addTargetRows(rows);
        }
        if (rowCount() == 0) {
            rows.add(Ui.row(Ui.caption(Ui.tr("gui.useless_stretcher.reclaimer.empty")).width(0).grow())
                    .height(ROW_HEIGHT));
        }
        return Ui.section(this::subtitle, rows);
    }

    private void addPlayerRows(Flex rows) {
        for (int row = 0; row < visibleRows; row++) {
            Flex line = Ui.row().gap(ROW_GAP).height(GRID_ROW_HEIGHT);
            boolean filled = false;
            for (int column = 0; column < 2; column++) {
                int index = (scroll + row) * 2 + column;
                if (index < owners.size()) {
                    line.add(playerNode(index));
                    filled = true;
                } else {
                    // An empty cell keeps the two columns equally wide.
                    line.add(Ui.spacer().width(0).grow());
                }
            }
            if (filled) rows.add(line);
        }
    }

    private UiNode playerNode(int index) {
        UUID owner = owners.get(index);
        var owned = byOwner.getOrDefault(owner, List.of());
        String name = owned.isEmpty() ? owner.toString() : owned.get(0).ownerName();
        int count = owned.size();
        SelectableAE2Button button = new SelectableAE2Button(0, 0, 100, GRID_ROW_HEIGHT,
                Component.literal(name + " (" + count + ")"), ignored -> {
                    selectedOwner = owner;
                    selectedName = name;
                    navigate(Page.TYPES);
                });
        button.setTooltip(Tooltip.create(Component.literal(name + " (" + owner + ") ")
                .append(ButtonHelp.text("player"))));
        return flexible(button, GRID_ROW_HEIGHT);
    }

    /** Both categories stay one tap apart and show their count in the message. */
    private UiNode typeRow() {
        var owned = byOwner.getOrDefault(selectedOwner, List.of());
        Flex line = Ui.row().gap(ROW_GAP).height(GRID_ROW_HEIGHT);
        for (int i = 0; i < 2; i++) {
            boolean ranges = i == 1;
            long count = owned.stream().filter(entry -> entry.range() == ranges).count();
            SelectableAE2Button button = new SelectableAE2Button(0, 0, 100, GRID_ROW_HEIGHT,
                    Component.translatable("gui.useless_stretcher.reclaimer.category_count", category(ranges), count),
                    ignored -> {
                        range = ranges;
                        targets = byOwner.getOrDefault(selectedOwner, List.of()).stream()
                                .filter(entry -> entry.range() == range).toList();
                        navigate(Page.TARGETS);
                    });
            line.add(flexible(button, GRID_ROW_HEIGHT));
        }
        return line;
    }

    private void addTargetRows(Flex rows) {
        for (int i = 0; i < visibleRows && scroll + i < targets.size(); i++) {
            rows.add(targetRow(targets.get(scroll + i)));
        }
    }

    /** Label and location share the row with the reclaim action; the button keeps the full help. */
    private UiNode targetRow(Network.ReclaimerEntry entry) {
        SelectableAE2Button reclaim = new SelectableAE2Button(0, 0, RECLAIM_WIDTH, BUTTON_HEIGHT,
                Component.translatable("gui.useless_stretcher.range.reclaim"), ignored ->
                minecraft.setScreen(new StretcherConfirmScreen(confirmed -> {
                    minecraft.setScreen(this);
                    if (confirmed) Network.reclaimTarget(entry.range(), entry.id(), personal);
                }, Component.translatable("gui.useless_stretcher.range.reclaim"),
                        Component.literal(entry.label() + " · " + entry.dimension() + " " + entry.pos().toShortString()))));
        reclaim.setTooltip(Tooltip.create(Component.literal(entry.ownerName() + " (" + entry.owner() + ") · "
                + entry.label() + " · " + entry.dimension() + " " + entry.pos().toShortString())));
        Flex text = Ui.column(
                Ui.body(Ui.lit(entry.label())),
                Ui.caption(Ui.bindText(() -> entry.dimension() + " " + entry.pos().toShortString()))).gap(1);
        return Ui.row(text.width(0).grow(), widgetNode(reclaim).width(RECLAIM_WIDTH))
                .gap(6).height(ROW_HEIGHT);
    }

    private UiNode footerRow() {
        SelectableAE2Button refresh = new SelectableAE2Button(0, 0, FOOTER_BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("gui.useless_stretcher.reclaimer.refresh"), ignored -> {
                    if (personal) Network.requestPersonalReclaimer(); else Network.requestReclaimer();
                });
        SelectableAE2Button back = new SelectableAE2Button(0, 0, FOOTER_BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("gui.useless_stretcher.back"), ignored -> onClose());
        return Ui.row(Ui.caption(this::pageText), Ui.spacer(),
                widgetNode(refresh).width(FOOTER_BUTTON_WIDTH),
                widgetNode(back).width(FOOTER_BUTTON_WIDTH)).gap(ROW_GAP).height(BUTTON_HEIGHT);
    }

    /** Native widget embedded by the shared layout: no width of its own, the row spreads it evenly. */
    private UiNode flexible(SelectableAE2Button widget, int height) {
        return widgetNode(widget).width(0).grow().height(height);
    }

    // ------------------------------------------------------------------ 文案
    private Component subtitle() {
        String name = personal ? Component.translatable("gui.useless_stretcher.reclaimer.mine").getString() : selectedName;
        return page == Page.PLAYERS
                ? Component.translatable("gui.useless_stretcher.reclaimer.players", owners.size())
                : Component.literal(name + (page == Page.TARGETS ? " > " + category(range).getString() : ""));
    }

    /** Paging hint in the footer; hidden when everything already fits on this page. */
    private Component pageText() {
        if (rowCount() <= visibleRows) return Component.empty();
        return Component.literal((scroll + 1) + "-" + Math.min(rowCount(), scroll + visibleRows) + " / " + rowCount());
    }

    // ------------------------------------------------------------------ 输入
    /** Cards, rows and captions are painted by the shared Apple host; nothing is drawn by hand. */
    @Override protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override protected boolean scrollContent(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (rowCount() <= visibleRows) return super.scrollContent(mouseX, mouseY, deltaX, deltaY);
        int next = Mth.clamp(scroll - (int) Math.signum(deltaY), 0, Math.max(0, rowCount() - visibleRows));
        if (next != scroll) { scroll = next; rebuildWidgets(); }
        return true;
    }

    @Override public void onClose() {
        if (page == Page.TARGETS) navigate(Page.TYPES);
        else if (page == Page.TYPES && !personal) navigate(Page.PLAYERS);
        else returnToPage(parent);
    }
    @Override public boolean isPauseScreen() { return false; }
}
