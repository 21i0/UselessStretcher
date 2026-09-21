package com.sorrowmist.useless.stretcher.screen;

import appeng.client.gui.widgets.AE2Button;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.content.mold.MoldCatalog;
import com.sorrowmist.useless.stretcher.menu.OmniversalMyriadMenu;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OmniversalMyriadScreen extends AbstractContainerScreen<OmniversalMyriadMenu> {
    private static final int ROW_HEIGHT = 18;
    private static final int LIST_TOP = 50;
    private static final int LIST_BOTTOM = 226;

    private final BlockPos pos;
    private Map<String, List<MoldCatalog.MoldEntry>> catalog = Map.of();
    private final Set<ResourceLocation> enabled = new LinkedHashSet<>();
    private final Set<ResourceLocation> patternMolds = new LinkedHashSet<>();
    private final Set<String> expandedMods = new LinkedHashSet<>();
    private ResourceLocation lastToggled;
    private ResourceLocation lastPatternToggled;
    private boolean lastToggleOn;
    private boolean lastPatternToggleOn;
    private int patternsCount;
    private boolean aeBound;
    private int scroll;
    private EditBox search;
    private MoldCatalog.Builder catalogBuilder;
    private List<Row> rowCache;
    private String fetchProgress = "";
    private final List<String> receivedEnabled = new ArrayList<>();
    private final List<String> receivedPatterns = new ArrayList<>();
    private int expectedPart;

    public OmniversalMyriadScreen(OmniversalMyriadMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.pos = menu.getPos();
        this.imageWidth = 300;
        this.imageHeight = 264;
    }

    public boolean matches(BlockPos target) {
        return pos.equals(target);
    }

    public void onState(List<String> enabledMolds, List<String> patternMolds, int patternCount, boolean aeBound,
                        String progress, int part, int parts) {
        if (part == -1 && parts == 0) {
            patternsCount = patternCount;
            this.aeBound = aeBound;
            fetchProgress = progress;
            return;
        }
        if (part == 0) {
            receivedEnabled.clear();
            receivedPatterns.clear();
            expectedPart = 0;
        }
        if (part != expectedPart || parts <= 0 || part >= parts) return;
        expectedPart++;
        receivedEnabled.addAll(enabledMolds);
        receivedPatterns.addAll(patternMolds);
        if (part + 1 < parts) return;
        enabled.clear();
        for (String id : receivedEnabled) {
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed != null) enabled.add(parsed);
        }
        this.patternMolds.clear();
        for (String id : receivedPatterns) {
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed != null) this.patternMolds.add(parsed);
        }
        patternsCount = patternCount;
        this.aeBound = aeBound;
        this.fetchProgress = progress;
        receivedEnabled.clear();
        receivedPatterns.clear();
    }

    @Override
    protected void init() {
        super.init();
        this.catalogBuilder = MoldCatalog.start(Minecraft.getInstance().level);
        this.catalog = catalogBuilder.result();
        this.rowCache = null;

        this.search = new EditBox(this.font, leftPos + 8, topPos + 25, 192, 18, Component.empty());
        this.search.setMaxLength(64);
        this.search.setResponder(value -> { rowCache = null; scroll = 0; });
        this.search.setHint(Component.translatable("gui.useless_stretcher.search_hint"));
        this.search.setTextColor(StretcherScreenStyle.TEXT_COLOR);
        this.search.setTextColorUneditable(StretcherScreenStyle.MUTED_TEXT_COLOR);
        addRenderableWidget(search);
        addRenderableWidget(new AE2Button(leftPos + 206, topPos + 25, 86, 18,
                Component.translatable("gui.useless_stretcher.clear"), ignored -> {
                    patternMolds.clear();
                    lastPatternToggled = null;
                    Network.clearPatterns(pos);
                }));
        Network.requestState(pos);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (catalogBuilder != null && !catalogBuilder.done() && catalogBuilder.advance()) {
            catalog = catalogBuilder.result();
            rowCache = null;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        StretcherScreenStyle.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        graphics.drawString(this.font, title, leftPos + 8, topPos + 8,
                StretcherScreenStyle.TEXT_COLOR, false);
        StretcherScreenStyle.drawInset(graphics, leftPos + 7, topPos + LIST_TOP - 1,
                leftPos + imageWidth - 7, topPos + LIST_BOTTOM + 1);

        graphics.drawString(this.font,
                Component.translatable("gui.useless_stretcher.patterns", patternsCount),
                leftPos + 8, topPos + LIST_BOTTOM + 7, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        graphics.drawString(this.font,
                Component.translatable(aeBound ? "gui.useless_stretcher.ae_bound" : "gui.useless_stretcher.ae_unbound"),
                leftPos + 174, topPos + LIST_BOTTOM + 7,
                aeBound ? StretcherScreenStyle.SUCCESS_COLOR : StretcherScreenStyle.MUTED_TEXT_COLOR, false);

        if (catalogBuilder != null && !catalogBuilder.done()) {
            graphics.drawString(font, Component.translatable("gui.useless_stretcher.catalog_loading",
                    catalogBuilder.progress()), leftPos + 12, topPos + LIST_TOP + 8,
                    StretcherScreenStyle.TEXT_COLOR, false);
        }
        if (!fetchProgress.isEmpty()) {
            String key = "failed".equals(fetchProgress) ? "gui.useless_stretcher.fetch_failed"
                    : "partial".equals(fetchProgress) ? "gui.useless_stretcher.fetch_partial"
                    : fetchProgress.startsWith("index:") ? "gui.useless_stretcher.fetch_index"
                    : "select".equals(fetchProgress) ? "gui.useless_stretcher.fetch_select"
                    : "gui.useless_stretcher.fetch_progress";
            String value = fetchProgress.startsWith("index:") ? fetchProgress.substring(6) : fetchProgress;
            graphics.drawString(font, Component.translatable(key, value), leftPos + 8,
                    topPos + LIST_BOTTOM + 21, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        }

        List<Row> rows = visibleRows();
        int maxScroll = Math.max(0, rows.size() * ROW_HEIGHT - (LIST_BOTTOM - LIST_TOP));
        if (scroll > maxScroll) scroll = maxScroll;

        graphics.enableScissor(leftPos + 8, topPos + LIST_TOP, leftPos + imageWidth - 8, topPos + LIST_BOTTOM);
        try {
            int index = 0;
            for (Row row : rows) {
                int y = topPos + LIST_TOP + index * ROW_HEIGHT - scroll;
                if (y + ROW_HEIGHT < topPos + LIST_TOP || y > topPos + LIST_BOTTOM) {
                    index++;
                    continue;
                }
                if (row instanceof HeaderRow header) {
                    renderHeader(graphics, header, y, mouseX, mouseY);
                } else if (row instanceof MoldRow mold) {
                    renderMold(graphics, mold, y, mouseX, mouseY);
                }
                index++;
            }
        } finally {
            graphics.disableScissor();
        }

        if (maxScroll > 0) {
            int trackX = leftPos + imageWidth - 11;
            int trackHeight = LIST_BOTTOM - LIST_TOP - 4;
            int thumbHeight = Math.max(18, trackHeight * (LIST_BOTTOM - LIST_TOP)
                    / Math.max(LIST_BOTTOM - LIST_TOP, rows.size() * ROW_HEIGHT));
            int thumbY = topPos + LIST_TOP + 2 + (trackHeight - thumbHeight) * scroll / maxScroll;
            graphics.fill(trackX, topPos + LIST_TOP + 2, trackX + 2, topPos + LIST_BOTTOM - 2,
                    StretcherScreenStyle.SLOT_SHADOW_COLOR);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight,
                    StretcherScreenStyle.ACTIVE_COLOR);
        }
    }

    private void renderHeader(GuiGraphics graphics, HeaderRow header, int y, int mouseX, int mouseY) {
        boolean expanded = expandedMods.contains(header.sourceId());
        int rowLeft = leftPos + 9;
        int rowRight = leftPos + imageWidth - 12;
        boolean hovered = mouseX >= rowLeft && mouseX < rowRight && mouseY >= y && mouseY < y + ROW_HEIGHT;
        graphics.fill(rowLeft, y, rowRight, y + ROW_HEIGHT - 1,
                hovered ? StretcherScreenStyle.HIGHLIGHT_COLOR : StretcherScreenStyle.SLOT_COLOR);
        int checkX = rowRight - 62;
        String headerName = this.font.plainSubstrByWidth(
                (expanded ? "- " : "+ ") + header.sourceId(), checkX - rowLeft - 8);
        graphics.drawString(this.font, headerName,
                rowLeft + 4, y + 5, StretcherScreenStyle.TEXT_COLOR, false);
        drawCheckbox(graphics, checkX, y + 3, allEnabled(header.entries()));

        int px = rowRight - 44;
        int py = y + 2;
        boolean allFetched = allPatternsFetched(header.entries());
        boolean hoverPat = mouseX >= px && mouseX <= px + 40 && mouseY >= py && mouseY <= py + 14;
        drawPatternButton(graphics, px, py, 40, allFetched, hoverPat);
    }

    private void renderMold(GuiGraphics graphics, MoldRow mold, int y, int mouseX, int mouseY) {
        boolean on = enabled.contains(mold.entry().id());
        boolean fetched = patternMolds.contains(mold.entry().id());
        String query = search == null ? "" : search.getValue().trim();
        boolean highlighted = !query.isEmpty() && matches(mold.entry(), query);
        int rowLeft = leftPos + 9;
        int rowRight = leftPos + imageWidth - 12;
        boolean hovered = mouseX >= rowLeft && mouseX < rowRight && mouseY >= y && mouseY < y + ROW_HEIGHT;
        int background = highlighted ? 0xFFFFE3A0
                : hovered ? StretcherScreenStyle.HIGHLIGHT_COLOR : StretcherScreenStyle.PANEL_COLOR;
        graphics.fill(rowLeft, y, rowRight, y + ROW_HEIGHT - 1, background);
        drawCheckbox(graphics, rowLeft + 11, y + 3, on);
        String name = this.font.plainSubstrByWidth(mold.entry().displayName(), rowRight - rowLeft - 76);
        graphics.drawString(this.font, name, rowLeft + 27, y + 5,
                on ? StretcherScreenStyle.TEXT_COLOR : StretcherScreenStyle.MUTED_TEXT_COLOR, false);

        int px = rowRight - 44;
        int py = y + 2;
        boolean hoverPat = mouseX >= px && mouseX <= px + 40 && mouseY >= py && mouseY <= py + 14;
        drawPatternButton(graphics, px, py, 40, fetched, hoverPat);
    }

    private void drawCheckbox(GuiGraphics graphics, int x, int y, boolean checked) {
        graphics.fill(x, y, x + 11, y + 11, StretcherScreenStyle.SLOT_SHADOW_COLOR);
        graphics.fill(x + 1, y + 1, x + 10, y + 10,
                checked ? StretcherScreenStyle.SUCCESS_COLOR : StretcherScreenStyle.HIGHLIGHT_COLOR);
        if (checked) graphics.drawString(font, "x", x + 3, y + 2, 0xFFFFFFFF, false);
    }

    private void drawPatternButton(GuiGraphics graphics, int x, int y, int width,
                                   boolean fetched, boolean hovered) {
        graphics.fill(x, y, x + width, y + 14, StretcherScreenStyle.SLOT_SHADOW_COLOR);
        int color = fetched ? StretcherScreenStyle.WARNING_COLOR
                : hovered ? StretcherScreenStyle.ACTIVE_COLOR : StretcherScreenStyle.SUBTLE_TEXT_COLOR;
        graphics.fill(x + 1, y + 1, x + width - 1, y + 13, color);
        Component label = Component.translatable(fetched
                ? "gui.useless_stretcher.pattern_remove_short"
                : "gui.useless_stretcher.pattern_get_short");
        graphics.drawCenteredString(font, label, x + width / 2, y + 3, 0xFFFFFFFF);
    }

    private boolean allEnabled(List<MoldCatalog.MoldEntry> entries) {
        for (MoldCatalog.MoldEntry entry : entries) {
            if (!enabled.contains(entry.id())) return false;
        }
        return true;
    }

    private boolean allPatternsFetched(List<MoldCatalog.MoldEntry> entries) {
        if (entries.isEmpty()) return false;
        for (MoldCatalog.MoldEntry entry : entries) {
            if (!patternMolds.contains(entry.id())) return false;
        }
        return true;
    }

    private List<Row> visibleRows() {
        if (rowCache != null) return rowCache;
        String query = search == null ? "" : search.getValue().trim();
        boolean searching = !query.isEmpty();
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, List<MoldCatalog.MoldEntry>> entry : catalog.entrySet()) {
            String modId = entry.getKey();
            List<MoldCatalog.MoldEntry> filtered = new ArrayList<>();
            for (MoldCatalog.MoldEntry mold : entry.getValue()) {
                if (matches(mold, query)) filtered.add(mold);
            }
            if (filtered.isEmpty()) continue;
            rows.add(new HeaderRow(modId, filtered));
            if (searching || expandedMods.contains(modId)) {
                for (MoldCatalog.MoldEntry mold : filtered) rows.add(new MoldRow(mold));
            }
        }
        rowCache = List.copyOf(rows);
        return rowCache;
    }

    private static boolean matches(MoldCatalog.MoldEntry mold, String query) {
        if (query == null || query.isEmpty()) return true;
        if (query.startsWith("@")) {
            String mod = query.substring(1).toLowerCase(java.util.Locale.ROOT);
            return mold.sourceId().toLowerCase(java.util.Locale.ROOT).contains(mod);
        }
        String lower = query.toLowerCase(java.util.Locale.ROOT);
        return mold.displayName().toLowerCase(java.util.Locale.ROOT).contains(lower)
                || mold.sourceId().toLowerCase(java.util.Locale.ROOT).contains(lower);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            List<Row> rows = visibleRows();
            int index = 0;
            for (Row row : rows) {
                int y = topPos + LIST_TOP + index * ROW_HEIGHT - scroll;
                if (mouseY >= topPos + LIST_TOP && mouseY < topPos + LIST_BOTTOM
                        && mouseX >= leftPos + 9 && mouseX <= leftPos + imageWidth - 12
                        && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                    handleClick(row, mouseX, mouseY);
                    return true;
                }
                index++;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleClick(Row row, double mouseX, double mouseY) {
        if (row instanceof HeaderRow header) {
            int rowRight = leftPos + imageWidth - 12;
            boolean hitPattern = mouseX >= rowRight - 44;
            boolean hitToggle = mouseX >= rowRight - 66 && !hitPattern;
            if (hitPattern) {
                setModPatterns(header);
            } else if (hitToggle) {
                boolean all = allEnabled(header.entries());
                for (MoldCatalog.MoldEntry entry : header.entries()) {
                    if (all) enabled.remove(entry.id());
                    else enabled.add(entry.id());
                }
                pushEnabled();
            } else {
                if (!expandedMods.remove(header.sourceId())) expandedMods.add(header.sourceId());
                rowCache = null;
            }
        } else if (row instanceof MoldRow mold) {
            int rowRight = leftPos + imageWidth - 12;
            boolean hitPattern = mouseX >= rowRight - 44;
            if (hitPattern) {
                ResourceLocation id = mold.entry().id();
                boolean ctrl = Screen.hasControlDown();
                if (Screen.hasShiftDown() && lastPatternToggled != null) {
                    setPatternRange(lastPatternToggled, id, lastPatternToggleOn);
                } else {
                    boolean fetch = !patternMolds.contains(id);
                    setPatternSelection(List.of(id), fetch);
                    if (!ctrl) {
                        lastPatternToggled = id;
                        lastPatternToggleOn = fetch;
                    }
                }
                return;
            }

            boolean ctrl = Screen.hasControlDown();
            boolean shift = Screen.hasShiftDown();
            boolean turnOn = !enabled.contains(mold.entry().id());

            if (shift && lastToggled != null) {
                setEnabledRange(lastToggled, mold.entry().id(), lastToggleOn);
            } else {
                if (turnOn) enabled.add(mold.entry().id());
                else enabled.remove(mold.entry().id());
                if (!ctrl) {
                    lastToggled = mold.entry().id();
                    lastToggleOn = turnOn;
                }
            }
            pushEnabled();
        }
    }

    private void setEnabledRange(ResourceLocation anchor, ResourceLocation target, boolean turnOn) {
        for (ResourceLocation id : rangeIds(anchor, target)) {
            if (turnOn) enabled.add(id);
            else enabled.remove(id);
        }
    }

    private void pushEnabled() {
        List<String> ids = enabled.stream().map(ResourceLocation::toString).toList();
        Network.setEnabled(pos, ids);
    }

    private void setModPatterns(HeaderRow header) {
        List<ResourceLocation> ids = header.entries().stream().map(MoldCatalog.MoldEntry::id).toList();
        if (ids.isEmpty()) return;
        boolean fetch = !allPatternsFetched(header.entries());
        setPatternSelection(ids, fetch);
    }

    private void setPatternRange(ResourceLocation anchor, ResourceLocation target, boolean fetch) {
        setPatternSelection(rangeIds(anchor, target), fetch);
    }

    private List<ResourceLocation> rangeIds(ResourceLocation anchor, ResourceLocation target) {
        List<ResourceLocation> visibleMolds = visibleRows().stream()
                .filter(MoldRow.class::isInstance)
                .map(MoldRow.class::cast)
                .map(row -> row.entry().id())
                .toList();
        int a = -1;
        int b = -1;
        for (int i = 0; i < visibleMolds.size(); i++) {
            ResourceLocation id = visibleMolds.get(i);
            if (id.equals(anchor)) a = i;
            if (id.equals(target)) b = i;
        }
        if (a < 0 || b < 0) return List.of();
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return List.copyOf(visibleMolds.subList(lo, hi + 1));
    }

    private void setPatternSelection(List<ResourceLocation> ids, boolean fetch) {
        if (ids.isEmpty()) return;
        if (fetch) patternMolds.addAll(ids);
        else patternMolds.removeAll(ids);
        Network.setPatterns(pos, ids.stream().map(ResourceLocation::toString).toList(), fetch);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<Row> rows = visibleRows();
        int maxScroll = Math.max(0, rows.size() * ROW_HEIGHT - (LIST_BOTTOM - LIST_TOP));
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) scrollY * 18));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.isFocused()) {
            if (search.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            // Consume character keys while typing so keybindings (e.g. "E" opening the inventory)
            // don't fire; the actual character still arrives via charTyped.
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (search.isFocused() && search.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private sealed interface Row permits HeaderRow, MoldRow {
    }

    private record HeaderRow(String sourceId, List<MoldCatalog.MoldEntry> entries) implements Row {
    }

    private record MoldRow(MoldCatalog.MoldEntry entry) implements Row {
    }
}
