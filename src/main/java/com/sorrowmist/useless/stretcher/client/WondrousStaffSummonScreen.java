package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
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
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Searchable, mod-grouped entity selector for the staff summon action. */
public final class WondrousStaffSummonScreen extends Screen {
    private static final int PANEL_WIDTH = 390;
    private static final int PANEL_HEIGHT = 252;
    private static final int LIST_TOP = 57;
    private static final int LIST_BOTTOM = 210;
    private static final int ROW_HEIGHT = 18;
    private static final int MAX_VISIBLE_ROWS = (LIST_BOTTOM - LIST_TOP) / ROW_HEIGHT;

    private final Screen parent;
    private final InteractionHand hand;
    private final List<Entry> entries;
    private final Set<ResourceLocation> selected = new HashSet<>();
    private final Set<String> expandedMods = new LinkedHashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private EditBox searchBox;
    private SelectableAE2Button enabledButton;
    private SelectableAE2Button summonActionButton;
    private int panelLeft;
    private int panelTop;
    private int scroll;

    public WondrousStaffSummonScreen(Screen parent, InteractionHand hand) {
        super(Component.translatable("gui.useless_stretcher.staff_summon.title"));
        this.parent = parent;
        this.hand = hand;
        this.entries = SummonCatalog.entries();
        rebuildRows("");
    }

    @Override
    protected void init() {
        super.init();
        int panelWidth = Math.min(PANEL_WIDTH, width - 12);
        panelLeft = (width - panelWidth) / 2;
        panelTop = Math.max(6, (height - PANEL_HEIGHT) / 2);

        searchBox = addRenderableWidget(new EditBox(font, panelLeft + 9, panelTop + 35,
                panelWidth - 18, 18, Component.translatable("gui.useless_stretcher.staff_summon.search")));
        searchBox.setValue(searchBox.getValue());
        searchBox.setResponder(value -> {
            rebuildRows(value);
            scroll = 0;
        });
        searchBox.setHint(Component.translatable("gui.useless_stretcher.staff_summon.search"));

        enabledButton = addRenderableWidget(new SelectableAE2Button(
                panelLeft + panelWidth - 105, panelTop + 8, 96, 18,
                enabledMessage(), ignored -> toggleEnabled()));
        addRenderableWidget(new SelectableAE2Button(
                panelLeft + 9, panelTop + 218, 110, 18,
                Component.translatable("gui.useless_stretcher.staff_summon.select_visible"),
                ignored -> selectVisible()));
        addRenderableWidget(new SelectableAE2Button(
                panelLeft + 122, panelTop + 218, 110, 18,
                Component.translatable("gui.useless_stretcher.staff_summon.clear"),
                ignored -> selected.clear()));
        summonActionButton = addRenderableWidget(new SelectableAE2Button(
                panelLeft + 235, panelTop + 218, 82, 18,
                summonActionMessage(),
                ignored -> summonSelected()));
        addRenderableWidget(new SelectableAE2Button(
                panelLeft + panelWidth - 68, panelTop + 218, 59, 18,
                Component.translatable("gui.useless_stretcher.back"), ignored -> onClose()));
        enabledButton.setSelected(isEnabled());
        enabledButton.active = StretcherConfig.enableStaffSummon();
        summonActionButton.active = StretcherConfig.enableStaffSummon();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x33000000);
        int panelWidth = Math.min(PANEL_WIDTH, width - 12);
        StretcherScreenStyle.drawPanel(graphics, panelLeft, panelTop, panelWidth, PANEL_HEIGHT);
        graphics.drawString(font, title, panelLeft + 9, panelTop + 10,
                StretcherScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable("gui.useless_stretcher.staff_summon.selected",
                        selected.size(), com.sorrowmist.useless.stretcher.content.entity.WondrousStaffSummoning.MAX_SELECTION),
                panelLeft + 9, panelTop + 24, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        StretcherScreenStyle.drawInset(graphics, panelLeft + 8, panelTop + 55,
                panelLeft + panelWidth - 8, panelTop + 212);
        renderRows(graphics, mouseX, mouseY, panelWidth);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY, int panelWidth) {
        int first = Math.min(scroll, Math.max(0, rows.size() - MAX_VISIBLE_ROWS));
        for (int visible = 0; visible < MAX_VISIBLE_ROWS && first + visible < rows.size(); visible++) {
            Row row = rows.get(first + visible);
            int y = panelTop + LIST_TOP + visible * ROW_HEIGHT;
            if (row.header()) {
                boolean expanded = expandedMods.contains(row.modId());
                graphics.fill(panelLeft + 10, y, panelLeft + panelWidth - 10, y + ROW_HEIGHT,
                        StretcherScreenStyle.SLOT_COLOR);
                graphics.drawString(font, Component.literal((expanded ? "- " : "+ ") + row.modId()),
                        panelLeft + 14, y + 4, StretcherScreenStyle.TEXT_COLOR, false);
                continue;
            }
            Entry entry = row.entry();
            boolean checked = selected.contains(entry.id());
            if (isRowHovered(mouseX, mouseY, y, panelWidth)) {
                graphics.fill(panelLeft + 10, y, panelLeft + panelWidth - 10, y + ROW_HEIGHT,
                        0x22517497);
            }
            graphics.drawString(font, checked ? "[x]" : "[ ]", panelLeft + 13, y + 4,
                    checked ? StretcherScreenStyle.SUCCESS_COLOR : StretcherScreenStyle.MUTED_TEXT_COLOR, false);
            String label = font.plainSubstrByWidth(entry.name(), panelWidth - 73);
            graphics.drawString(font, label, panelLeft + 36, y + 4,
                    StretcherScreenStyle.TEXT_COLOR, false);
        }
        int total = Math.max(1, rows.size() - MAX_VISIBLE_ROWS);
        if (rows.size() > MAX_VISIBLE_ROWS) {
            int trackTop = panelTop + LIST_TOP;
            int trackHeight = LIST_BOTTOM - LIST_TOP;
            int thumbHeight = Math.max(18, trackHeight * MAX_VISIBLE_ROWS / rows.size());
            int thumbTop = trackTop + (trackHeight - thumbHeight) * first / total;
            graphics.fill(panelLeft + panelWidth - 14, trackTop, panelLeft + panelWidth - 10,
                    trackTop + trackHeight, StretcherScreenStyle.SLOT_SHADOW_COLOR);
            graphics.fill(panelLeft + panelWidth - 14, thumbTop, panelLeft + panelWidth - 10,
                    thumbTop + thumbHeight, StretcherScreenStyle.ACTIVE_COLOR);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        int row = (int) ((mouseY - (panelTop + LIST_TOP)) / ROW_HEIGHT);
        int first = Math.min(scroll, Math.max(0, rows.size() - MAX_VISIBLE_ROWS));
        int index = first + row;
        if (row >= 0 && row < MAX_VISIBLE_ROWS && index >= 0 && index < rows.size()) {
            Row clicked = rows.get(index);
            if (clicked.header()) {
                if (!expandedMods.remove(clicked.modId())) expandedMods.add(clicked.modId());
                rebuildRows(searchBox == null ? "" : searchBox.getValue());
                scroll = Math.min(scroll, Math.max(0, rows.size() - MAX_VISIBLE_ROWS));
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
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (mouseX >= panelLeft + 8 && mouseX <= panelLeft + PANEL_WIDTH - 8
                && mouseY >= panelTop + LIST_TOP && mouseY <= panelTop + LIST_BOTTOM) {
            int max = Math.max(0, rows.size() - MAX_VISIBLE_ROWS);
            scroll = net.minecraft.util.Mth.clamp(scroll - (int) Math.signum(deltaY), 0, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
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
        int first = Math.min(scroll, Math.max(0, rows.size() - MAX_VISIBLE_ROWS));
        for (int i = 0; i < MAX_VISIBLE_ROWS && first + i < rows.size(); i++) {
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
            return Component.translatable("gui.useless_stretcher.staff_config.summon_disabled");
        }
        return Component.translatable("gui.useless_stretcher.staff_summon.mode",
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
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String lastMod = null;
        for (Entry entry : entries) {
            if (!normalized.isEmpty() && !entry.searchText().contains(normalized)) continue;
            if (!entry.modId().equals(lastMod)) {
                rows.add(new Row(true, entry));
                lastMod = entry.modId();
            }
            if (!normalized.isEmpty() || expandedMods.contains(entry.modId())) {
                rows.add(new Row(false, entry));
            }
        }
    }

    private boolean isRowHovered(double mouseX, double mouseY, int y, int panelWidth) {
        return mouseX >= panelLeft + 10 && mouseX <= panelLeft + panelWidth - 10
                && mouseY >= y && mouseY < y + ROW_HEIGHT;
    }

    private record Row(boolean header, Entry entry) {
        private String modId() {
            return entry.modId();
        }
    }

    private record Entry(ResourceLocation id, String modId, String name, String searchText) {
    }

    /** Client-only registry catalog; it never constructs entity instances during indexing. */
    private static final class SummonCatalog {
        private static List<Entry> cached;

        private static List<Entry> entries() {
            if (cached != null) return cached;
            List<Entry> result = new ArrayList<>();
            for (var entry : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
                EntityType<?> type = entry.getValue();
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (!type.canSummon() || type.getCategory() == MobCategory.MISC) continue;
                String translated = type.getDescription().getString();
                String path = id.getPath().replace('_', ' ');
                String search = (translated + " " + path + " " + id + " " + id.getNamespace())
                        .toLowerCase(Locale.ROOT);
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
