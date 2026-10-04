package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.FloatingScreen;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.client.search.LocalizedSearchIndex;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffSummoning;
import com.sorrowmist.useless.stretcher.config.StretcherConfig;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Searchable, mod-grouped entity selector for the staff summon action. */
public final class WondrousStaffSummonScreen extends FloatingScreen {
    private static final int PANEL_WIDTH = 390;
    private static final int PANEL_HEIGHT = 252;
    private static final int LIST_TOP = 45;
    private static final int ROW_HEIGHT = 18;

    private final Screen parent;
    private final InteractionHand hand;
    private List<Entry> entries;
    private LocalizedSearchIndex searchIndex;
    private long searchRevision;
    private int searchRefreshTicks;
    private final Set<ResourceLocation> selected = new HashSet<>();
    private final Set<String> expandedMods = new LinkedHashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private EditBox searchBox;
    private SelectableAE2Button enabledButton;
    private SelectableAE2Button summonActionButton;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int visibleRows;
    private int listBottom;
    private int footerTop;
    private int footerColumns;
    private int scroll;

    public WondrousStaffSummonScreen(Screen parent, InteractionHand hand) {
        super(Component.translatable("gui.useless_stretcher.staff_summon.title"),
                "summon", PANEL_WIDTH, PANEL_HEIGHT + 22, 270, 190);
        this.parent = parent;
        this.hand = hand;
        this.entries = SummonCatalog.entries();
        this.searchIndex = LocalizedSearchIndex.current();
        this.searchRevision = searchIndex.revision();
        rebuildRows("");
    }

    @Override
    protected void initContent() {
        setContentExtent(contentHeight());
        panelWidth = contentWidth();
        panelLeft = contentLeft();
        panelTop = contentTop();
        footerColumns = panelWidth >= 355 ? 5 : 3;
        int footerRows = (5 + footerColumns - 1) / footerColumns;
        footerTop = panelTop + contentHeight() - footerRows * 22;
        visibleRows = Math.max(1, (footerTop - panelTop - LIST_TOP - 4) / ROW_HEIGHT);
        listBottom = panelTop + LIST_TOP + visibleRows * ROW_HEIGHT;
        scroll = Math.min(scroll, Math.max(0, rows.size() - visibleRows));
        if (searchBox == null) {
            searchBox = new EditBox(font, panelLeft + 2, panelTop + 22,
                    panelWidth - 4, 18, Component.translatable("gui.useless_stretcher.staff_summon.search"));
            searchBox.setResponder(value -> {
                rebuildRows(value);
                scroll = 0;
            });
            searchBox.setHint(Component.translatable("gui.useless_stretcher.staff_summon.search"));
        }
        searchBox.setX(panelLeft + 2);
        searchBox.setY(panelTop + 22);
        searchBox.setWidth(panelWidth - 4);
        addRenderableWidget(searchBox);

        enabledButton = addRenderableWidget(new SelectableAE2Button(
                panelLeft + panelWidth - 106, panelTop, 104, 18,
                enabledMessage(), ignored -> toggleEnabled()));
        addFooterButton(0,
                Component.translatable("gui.useless_stretcher.staff_summon.select_visible"),
                ignored -> selectVisible());
        addFooterButton(1,
                Component.translatable("gui.useless_stretcher.staff_summon.clear"),
                ignored -> selected.clear());
        summonActionButton = addFooterButton(2,
                summonActionMessage(),
                ignored -> summonSelected());
        addFooterButton(3,
                Component.translatable("gui.useless_stretcher.staff_summon.recall"),
                ignored -> Network.recallWondrousStaff(hand));
        addFooterButton(4, Component.translatable("gui.useless_stretcher.back"), ignored -> onClose());
        enabledButton.setSelected(isEnabled());
        enabledButton.active = StretcherConfig.enableStaffSummon();
        summonActionButton.active = enabledButton.active;
    }

    private SelectableAE2Button addFooterButton(int index, Component label, net.minecraft.client.gui.components.Button.OnPress action) {
        int row = index / footerColumns;
        int columns = Math.min(footerColumns, 5 - row * footerColumns);
        int column = index % footerColumns;
        int buttonWidth = (panelWidth - (columns - 1) * 4) / columns;
        return addRenderableWidget(new SelectableAE2Button(panelLeft + column * (buttonWidth + 4),
                footerTop + row * 22, buttonWidth, 18, label, action));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String selection = Component.translatable("gui.useless_stretcher.staff_summon.selected",
                selected.size(), com.sorrowmist.useless.stretcher.content.entity.WondrousStaffSummoning.MAX_SELECTION).getString();
        graphics.drawString(font, font.plainSubstrByWidth(selection, panelWidth - 112),
                panelLeft + 2, panelTop + 5, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        StretcherScreenStyle.drawInset(graphics, panelLeft, panelTop + LIST_TOP - 2,
                panelLeft + panelWidth, listBottom + 2);
        renderRows(graphics, mouseX, mouseY, panelWidth);
    }

    @Override
    protected void renderContentTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int first = Math.min(scroll, Math.max(0, rows.size() - visibleRows));
        int rowIndex = (mouseY - panelTop - LIST_TOP) / ROW_HEIGHT;
        if (mouseY >= panelTop + LIST_TOP && rowIndex >= 0 && rowIndex < visibleRows
                && first + rowIndex < rows.size() && mouseX >= panelLeft + 2 && mouseX < panelLeft + panelWidth - 8) {
            Row row = rows.get(first + rowIndex);
            Component help = com.sorrowmist.useless.stretcher.client.gui.ButtonHelp.text(row.header() ? "fold_mod" : "select_entity");
            if (!row.header()) help = Component.literal(row.entry().name() + " (" + row.entry().id() + ") ").append(help);
            graphics.renderTooltip(font, font.split(help, 260), mouseX, mouseY);
        }
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY, int panelWidth) {
        int first = Math.min(scroll, Math.max(0, rows.size() - visibleRows));
        for (int visible = 0; visible < visibleRows && first + visible < rows.size(); visible++) {
            Row row = rows.get(first + visible);
            int y = panelTop + LIST_TOP + visible * ROW_HEIGHT;
            if (row.header()) {
                boolean expanded = expandedMods.contains(row.modId());
                graphics.fill(panelLeft + 2, y, panelLeft + panelWidth - 8, y + ROW_HEIGHT,
                        StretcherScreenStyle.SLOT_COLOR);
                graphics.drawString(font, font.plainSubstrByWidth((expanded ? "- " : "+ ") + row.modId(), panelWidth - 16),
                        panelLeft + 6, y + 4, StretcherScreenStyle.TEXT_COLOR, false);
                continue;
            }
            Entry entry = row.entry();
            boolean checked = selected.contains(entry.id());
            if (isRowHovered(mouseX, mouseY, y, panelWidth)) {
                graphics.fill(panelLeft + 2, y, panelLeft + panelWidth - 8, y + ROW_HEIGHT,
                        0x22517497);
            }
            graphics.drawString(font, checked ? "[x]" : "[ ]", panelLeft + 5, y + 4,
                    checked ? StretcherScreenStyle.SUCCESS_COLOR : StretcherScreenStyle.MUTED_TEXT_COLOR, false);
            String label = font.plainSubstrByWidth(entry.name(), panelWidth - 41);
            graphics.drawString(font, label, panelLeft + 28, y + 4,
                    StretcherScreenStyle.TEXT_COLOR, false);
        }
        int total = Math.max(1, rows.size() - visibleRows);
        if (rows.size() > visibleRows) {
            int trackTop = panelTop + LIST_TOP;
            int trackHeight = visibleRows * ROW_HEIGHT;
            int thumbHeight = Math.max(18, trackHeight * visibleRows / rows.size());
            int thumbTop = trackTop + (trackHeight - thumbHeight) * first / total;
            graphics.fill(panelLeft + panelWidth - 6, trackTop, panelLeft + panelWidth - 2,
                    trackTop + trackHeight, StretcherScreenStyle.SLOT_SHADOW_COLOR);
            graphics.fill(panelLeft + panelWidth - 6, thumbTop, panelLeft + panelWidth - 2,
                    thumbTop + thumbHeight, StretcherScreenStyle.ACTIVE_COLOR);
        }
    }

    @Override
    protected boolean clickContent(double mouseX, double mouseY, int button) {
        if (super.clickContent(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        if (mouseX < panelLeft + 2 || mouseX >= panelLeft + panelWidth - 8
                || mouseY < panelTop + LIST_TOP || mouseY >= listBottom) return false;
        int row = (int) ((mouseY - (panelTop + LIST_TOP)) / ROW_HEIGHT);
        int first = Math.min(scroll, Math.max(0, rows.size() - visibleRows));
        int index = first + row;
        if (row >= 0 && row < visibleRows && index >= 0 && index < rows.size()) {
            Row clicked = rows.get(index);
            if (clicked.header()) {
                if (!expandedMods.remove(clicked.modId())) expandedMods.add(clicked.modId());
                rebuildRows(searchBox == null ? "" : searchBox.getValue());
                scroll = Math.min(scroll, Math.max(0, rows.size() - visibleRows));
                return true;
            }
            if (!selected.remove(clicked.entry().id()) && selected.size() <
                    com.sorrowmist.useless.stretcher.content.entity.WondrousStaffSummoning.MAX_SELECTION) {
                selected.add(clicked.entry().id());
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean scrollContent(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (mouseX >= panelLeft && mouseX <= panelLeft + panelWidth
                && mouseY >= panelTop + LIST_TOP && mouseY < listBottom) {
            int max = Math.max(0, rows.size() - visibleRows);
            scroll = net.minecraft.util.Mth.clamp(scroll - (int) Math.signum(deltaY), 0, max);
            return true;
        }
        return super.scrollContent(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void toggleEnabled() {
        if (!StretcherConfig.enableStaffSummon()) return;
        ItemStack staff = currentStaff();
        if (!staff.is(ModItems.WONDROUS_STAFF.get())) return;
        boolean enabled = !isEnabled();
        staff.set(StretcherComponents.WONDROUS_STAFF_SUMMON_ENABLED.get(), enabled);
        enabledButton.setSelected(enabled);
        enabledButton.setMessage(enabledMessage());
        summonActionButton.active = StretcherConfig.enableStaffSummon();
        summonActionButton.setMessage(summonActionMessage());
        Network.sendWondrousStaffFeatures(enabled,
                WondrousStaffAcceleration.isLootRefreshEnabled(staff), hand);
    }

    @Override
    public void tick() {
        super.tick();
        if (searchIndex != LocalizedSearchIndex.current()) {
            searchIndex = LocalizedSearchIndex.current();
            entries = SummonCatalog.entries();
            rebuildRows(searchBox == null ? "" : searchBox.getValue());
            searchRevision = searchIndex.revision();
        } else if (++searchRefreshTicks % 5 == 0 && searchRevision != searchIndex.revision()) {
            searchRevision = searchIndex.revision();
            if (searchBox != null && !searchBox.getValue().isBlank()) rebuildRows(searchBox.getValue());
        }
        enabledButton.active = StretcherConfig.enableStaffSummon();
        enabledButton.setSelected(isEnabled());
        enabledButton.setMessage(enabledMessage());
        summonActionButton.active = enabledButton.active;
        summonActionButton.setMessage(summonActionMessage());
    }

    private void summonSelected() {
        if (!StretcherConfig.enableStaffSummon() || selected.isEmpty()) return;
        ItemStack staff = currentStaff();
        if (!staff.is(ModItems.WONDROUS_STAFF.get())) return;
        if (!isEnabled()) {
            staff.set(StretcherComponents.WONDROUS_STAFF_SUMMON_ENABLED.get(), true);
            enabledButton.setSelected(true);
            enabledButton.setMessage(enabledMessage());
            Network.sendWondrousStaffFeatures(true,
                    WondrousStaffAcceleration.isLootRefreshEnabled(staff), hand);
        }
        Network.sendWondrousStaffSummon(selected.stream().map(ResourceLocation::toString).toList(), hand);
    }

    private void selectVisible() {
        int first = Math.min(scroll, Math.max(0, rows.size() - visibleRows));
        for (int i = 0; i < visibleRows && first + i < rows.size(); i++) {
            Row row = rows.get(first + i);
            if (!row.header() && selected.size() <
                    com.sorrowmist.useless.stretcher.content.entity.WondrousStaffSummoning.MAX_SELECTION) {
                selected.add(row.entry().id());
            }
        }
    }

    private boolean isEnabled() {
        return StretcherConfig.enableStaffSummon()
                && WondrousStaffAcceleration.isSummonEnabled(currentStaff());
    }

    private ItemStack currentStaff() {
        return minecraft == null || minecraft.player == null
                ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand);
    }

    private Component enabledMessage() {
        if (!StretcherConfig.enableStaffSummon()) {
            return Component.translatable("gui.useless_stretcher.staff_summon.mode_short",
                    Component.translatable("gui.useless_stretcher.staff_config.disabled_short"));
        }
        return Component.translatable("gui.useless_stretcher.staff_summon.mode_short",
                Component.translatable(isEnabled() ? "gui.useless_stretcher.staff_config.on"
                        : "gui.useless_stretcher.staff_config.off"));
    }

    private Component summonActionMessage() {
        return Component.translatable(StretcherConfig.enableStaffSummon()
                ? "gui.useless_stretcher.staff_summon.summon"
                : "gui.useless_stretcher.staff_summon.disabled");
    }

    private void rebuildRows(String query) {
        rows.clear();
        var normalized = LocalizedSearchIndex.Query.parse(query);
        String lastMod = null;
        for (Entry entry : entries) {
            if (!normalized.matches(entry.searchText())) continue;
            if (!entry.modId().equals(lastMod)) {
                rows.add(new Row(true, entry));
                lastMod = entry.modId();
            }
            if (!normalized.empty() || expandedMods.contains(entry.modId())) {
                rows.add(new Row(false, entry));
            }
        }
    }

    private boolean isRowHovered(double mouseX, double mouseY, int y, int panelWidth) {
        return mouseX >= panelLeft + 2 && mouseX < panelLeft + panelWidth - 8
                && mouseY >= y && mouseY < y + ROW_HEIGHT;
    }

    private record Row(boolean header, Entry entry) {
        private String modId() {
            return entry.modId();
        }
    }

    private record Entry(ResourceLocation id, String modId, String name, LocalizedSearchIndex.Document searchText) {
    }

    /** Client-only registry catalog; it never constructs entity instances during indexing. */
    private static final class SummonCatalog {
        private static List<Entry> cached;
        private static LocalizedSearchIndex searchIndex;

        private static List<Entry> entries() {
            LocalizedSearchIndex current = LocalizedSearchIndex.current();
            if (cached != null && searchIndex == current) return cached;
            searchIndex = current;
            List<Entry> result = new ArrayList<>();
            for (var entry : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
                EntityType<?> type = entry.getValue();
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (!WondrousStaffSummoning.canSummonType(type)) continue;
                String translated = type.getDescription().getString();
                var search = searchIndex.document(translated, id.toString(), id.getNamespace());
                result.add(new Entry(id, id.getNamespace(), translated, search));
            }
            result.sort(Comparator.comparing(Entry::modId)
                    .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(entry -> entry.id().toString()));
            cached = List.copyOf(result);
            return cached;
        }
    }
}
