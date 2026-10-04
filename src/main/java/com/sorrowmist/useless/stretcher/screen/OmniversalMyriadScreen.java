package com.sorrowmist.useless.stretcher.screen;

import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.ButtonHelp;
import com.sorrowmist.useless.stretcher.client.gui.FloatingWindow;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.client.search.LocalizedSearchIndex;
import com.sorrowmist.useless.stretcher.content.mold.MoldCatalog;
import com.sorrowmist.useless.stretcher.menu.OmniversalMyriadMenu;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class OmniversalMyriadScreen extends AbstractContainerScreen<OmniversalMyriadMenu> {
    private static final int ROW_HEIGHT = 18;

    private final BlockPos pos;
    private final FloatingWindow window = new FloatingWindow("myriad", 310, 265, 260, 160);
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
    private SelectableAE2Button clearButton;
    private boolean initialized;
    private int listTop;
    private int listBottom;
    private boolean stackedFooter;
    private List<Row> rowCache;
    private final Map<MoldCatalog.MoldEntry, LocalizedSearchIndex.Document> searchDocuments = new IdentityHashMap<>();
    private LocalizedSearchIndex searchIndex;
    private long searchRevision = -1;
    private int searchRefreshTicks;
    private String fetchProgress = "";
    private String catalogProgress = "";
    private String catalogStatus = "checking";
    private boolean catalogLoading = true;
    private final Map<String, List<MoldCatalog.MoldEntry>> receivedCatalog = new TreeMap<>();
    private int expectedCatalogPart;
    private final List<String> receivedEnabled = new ArrayList<>();
    private final List<String> receivedPatterns = new ArrayList<>();
    private int expectedPart;

    public OmniversalMyriadScreen(OmniversalMyriadMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.pos = menu.getPos();
        this.imageWidth = 310;
        this.imageHeight = 265;
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

    public void onCatalog(Network.MoldCatalogPayload payload) {
        if (payload.parts() == 0) {
            catalogStatus = payload.status();
            catalogLoading = !"failed".equals(payload.status());
            catalogProgress = payload.progress();
            return;
        }
        if (payload.part() == 0) {
            receivedCatalog.clear();
            expectedCatalogPart = 0;
        }
        if (payload.part() != expectedCatalogPart || payload.parts() <= 0) return;
        expectedCatalogPart++;
        for (int i = 0; i < payload.itemIds().size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(payload.itemIds().get(i));
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            var item = BuiltInRegistries.ITEM.get(id);
            String source = payload.sourceIds().get(i);
            var stack = new net.minecraft.world.item.ItemStack(item);
            receivedCatalog.computeIfAbsent(source, ignored -> new ArrayList<>())
                    .add(new MoldCatalog.MoldEntry(source, id, stack));
        }
        if (payload.part() + 1 < payload.parts()) return;
        Map<String, List<MoldCatalog.MoldEntry>> completed = new TreeMap<>();
        receivedCatalog.forEach((source, entries) -> completed.put(source, List.copyOf(entries)));
        catalog = java.util.Collections.unmodifiableMap(completed);
        receivedCatalog.clear();
        rebuildSearchDocuments();
        catalogLoading = false;
        catalogProgress = "";
        catalogStatus = "done";
    }

    @Override
    protected void rebuildWidgets() {
        // Screen clears focus before init when the viewport changes. Reuse the same editor.
        var focused = getFocused();
        super.rebuildWidgets();
        if (focused == null || children().contains(focused)) setFocused(focused);
    }

    @Override
    protected void init() {
        boolean searchFocused = search != null && search.isFocused();
        super.init();
        window.init(width, height);
        if (!initialized) {
            this.rowCache = null;
            this.search = new EditBox(this.font, 0, 0, 192, 18, Component.empty());
            this.search.setMaxLength(64);
            this.search.setResponder(value -> { rowCache = null; scroll = 0; });
            this.search.setHint(Component.translatable("gui.useless_stretcher.search_hint"));
            this.search.setTextColor(StretcherScreenStyle.TEXT_COLOR);
            this.search.setTextColorUneditable(StretcherScreenStyle.MUTED_TEXT_COLOR);
            this.clearButton = new SelectableAE2Button(0, 0, 86, 18,
                    Component.translatable("gui.useless_stretcher.clear"), ignored -> {
                        patternMolds.clear();
                        lastPatternToggled = null;
                        Network.clearPatterns(pos);
                    });
            initialized = true;
            Network.requestMoldCatalog(pos);
            Network.requestState(pos);
        }
        applyWindowLayout();
        addRenderableWidget(search);
        addRenderableWidget(clearButton);
        if (searchFocused) setFocused(search);
        search.setFocused(searchFocused);
    }

    private void applyWindowLayout() {
        leftPos = window.left();
        topPos = window.top();
        imageWidth = window.width();
        imageHeight = window.height();
        int clearWidth = Math.min(76, Math.max(44, window.bodyWidth() / 4));
        search.setX(window.bodyLeft());
        search.setY(window.bodyTop());
        search.setWidth(Math.max(20, window.bodyWidth() - clearWidth - 6));
        clearButton.setX(window.bodyLeft() + window.bodyWidth() - clearWidth);
        clearButton.setY(window.bodyTop());
        clearButton.setWidth(clearWidth);
        updateListBounds();
        window.consumeLayoutChanged();
    }

    private void updateListBounds() {
        stackedFooter = font.width(patternCountText()) + font.width(bindingText()) + 14 > window.bodyWidth();
        listTop = window.bodyTop() + (catalogLoading || "failed".equals(catalogStatus) ? 48 : 24);
        listBottom = Math.max(listTop + ROW_HEIGHT,
                window.bodyTop() + window.bodyHeight() - (stackedFooter ? 50 : 36));
    }

    private Component patternCountText() {
        return Component.translatable("gui.useless_stretcher.patterns", patternsCount);
    }

    private Component bindingText() {
        return Component.translatable(aeBound ? "gui.useless_stretcher.ae_bound"
                : "gui.useless_stretcher.ae_unbound");
    }

    private int rowLeft() {
        return window.bodyLeft() + 2;
    }

    private int rowRight() {
        return window.bodyLeft() + window.bodyWidth() - 5;
    }

    private boolean containsList(double mouseX, double mouseY) {
        return mouseY >= listTop && mouseY < listBottom
                && mouseX >= rowLeft() && mouseX < rowRight();
    }

    private void drawClippedText(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), Math.max(0, maxWidth)),
                x, y, color, false);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (searchIndex != LocalizedSearchIndex.current()) rebuildSearchDocuments();
        if (++searchRefreshTicks % 5 == 0 && searchRevision != searchIndex.revision()) {
            searchRevision = searchIndex.revision();
            rowCache = null;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        if (!window.capturing() && containsList(mouseX, mouseY)) {
            int index = (mouseY - listTop + scroll) / ROW_HEIGHT;
            List<Row> rows = visibleRows();
            if (index >= 0 && index < rows.size()) {
                Row row = rows.get(index);
                boolean patterns = mouseX >= rowRight() - 44;
                String help = row instanceof HeaderRow
                        ? patterns ? "mod_patterns" : mouseX >= rowRight() - 66 ? "mod_molds" : "fold_mod"
                        : patterns ? "fetch_pattern" : "enable_mold";
                graphics.renderTooltip(font, font.split(ButtonHelp.text(help), 260), mouseX, mouseY);
            }
        }
        window.renderTooltip(graphics, font, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        updateListBounds();
        window.renderFrame(graphics, font, title, mouseX, mouseY);
        StretcherScreenStyle.drawInset(graphics, window.bodyLeft(), listTop - 1,
                window.bodyLeft() + window.bodyWidth(), listBottom + 1);

        int bindingWidth = Math.min(window.bodyWidth(), font.width(bindingText()));
        drawClippedText(graphics, patternCountText(), window.bodyLeft(), listBottom + 7,
                stackedFooter ? window.bodyWidth() : window.bodyWidth() - bindingWidth - 10,
                StretcherScreenStyle.SUBTLE_TEXT_COLOR);
        drawClippedText(graphics, bindingText(),
                stackedFooter ? window.bodyLeft() : window.bodyLeft() + window.bodyWidth() - bindingWidth,
                listBottom + (stackedFooter ? 21 : 7), window.bodyWidth(),
                aeBound ? StretcherScreenStyle.SUCCESS_COLOR : StretcherScreenStyle.MUTED_TEXT_COLOR);

        if (catalogLoading || "failed".equals(catalogStatus)) {
            String key = switch (catalogStatus) {
                case "waiting" -> "gui.useless_stretcher.catalog_waiting";
                case "failed" -> "gui.useless_stretcher.catalog_failed";
                case "checking" -> "gui.useless_stretcher.catalog_checking";
                default -> "gui.useless_stretcher.catalog_loading";
            };
            drawClippedText(graphics, Component.translatable(key,
                    catalogProgress), rowLeft() + 3, window.bodyTop() + 27,
                    rowRight() - rowLeft() - 6, StretcherScreenStyle.TEXT_COLOR);
            drawProgressBar(graphics, catalogProgress, window.bodyTop() + 39);
        }
        if (!fetchProgress.isEmpty()) {
            String key = "failed".equals(fetchProgress) ? "gui.useless_stretcher.fetch_failed"
                    : "partial".equals(fetchProgress) ? "gui.useless_stretcher.fetch_partial"
                    : fetchProgress.startsWith("index:") ? "gui.useless_stretcher.fetch_index"
                    : "select".equals(fetchProgress) ? "gui.useless_stretcher.fetch_select"
                    : "gui.useless_stretcher.fetch_progress";
            String value = fetchProgress.startsWith("index:") ? fetchProgress.substring(6) : fetchProgress;
            drawClippedText(graphics, Component.translatable(key, value), window.bodyLeft(),
                    listBottom + (stackedFooter ? 35 : 21), window.bodyWidth(),
                    StretcherScreenStyle.SUBTLE_TEXT_COLOR);
            drawProgressBar(graphics, value, listBottom + (stackedFooter ? 46 : 32));
        }

        List<Row> rows = visibleRows();
        int maxScroll = Math.max(0, rows.size() * ROW_HEIGHT - (listBottom - listTop));
        if (scroll > maxScroll) scroll = maxScroll;

        graphics.enableScissor(window.bodyLeft() + 1, listTop,
                window.bodyLeft() + window.bodyWidth() - 1, listBottom);
        try {
            int first = Math.max(0, scroll / ROW_HEIGHT);
            int end = Math.min(rows.size(), (scroll + listBottom - listTop + ROW_HEIGHT - 1) / ROW_HEIGHT);
            for (int index = first; index < end; index++) {
                Row row = rows.get(index);
                int y = listTop + index * ROW_HEIGHT - scroll;
                if (row instanceof HeaderRow header) {
                    renderHeader(graphics, header, y, mouseX, mouseY);
                } else if (row instanceof MoldRow mold) {
                    renderMold(graphics, mold, y, mouseX, mouseY);
                }
            }
        } finally {
            graphics.disableScissor();
        }

        if (maxScroll > 0) {
            int trackX = rowRight() + 1;
            int trackHeight = listBottom - listTop - 4;
            int thumbHeight = Math.min(trackHeight, Math.max(18, trackHeight * (listBottom - listTop)
                    / Math.max(listBottom - listTop, rows.size() * ROW_HEIGHT)));
            int thumbY = listTop + 2 + (trackHeight - thumbHeight) * scroll / maxScroll;
            graphics.fill(trackX, listTop + 2, trackX + 2, listBottom - 2,
                    StretcherScreenStyle.SLOT_SHADOW_COLOR);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight,
                    StretcherScreenStyle.ACTIVE_COLOR);
        }
    }

    private void drawProgressBar(GuiGraphics graphics, String value, int y) {
        int left = window.bodyLeft();
        int width = window.bodyWidth();
        graphics.fill(left, y, left + width, y + 3, StretcherScreenStyle.SLOT_SHADOW_COLOR);
        String[] values = value.split("/", 2);
        if (values.length != 2) return;
        try {
            long completed = Long.parseLong(values[0]);
            long total = Long.parseLong(values[1]);
            if (total <= 0) return;
            int filled = (int) (width * Math.clamp((double) completed / total, 0.0D, 1.0D));
            graphics.fill(left, y, left + filled, y + 3, StretcherScreenStyle.ACTIVE_COLOR);
        } catch (NumberFormatException ignored) {
            // Non-numeric statuses have a label but no misleading completion percentage.
        }
    }

    private void renderHeader(GuiGraphics graphics, HeaderRow header, int y, int mouseX, int mouseY) {
        boolean expanded = expandedMods.contains(header.sourceId());
        int rowLeft = rowLeft();
        int rowRight = rowRight();
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
        var query = LocalizedSearchIndex.Query.parse(search == null ? "" : search.getValue());
        boolean highlighted = !query.empty() && query.matches(searchDocuments.get(mold.entry()));
        int rowLeft = rowLeft();
        int rowRight = rowRight();
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
        if (searchIndex != LocalizedSearchIndex.current()) rebuildSearchDocuments();
        if (rowCache != null) return rowCache;
        var query = LocalizedSearchIndex.Query.parse(search == null ? "" : search.getValue());
        boolean searching = !query.empty();
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, List<MoldCatalog.MoldEntry>> entry : catalog.entrySet()) {
            String modId = entry.getKey();
            List<MoldCatalog.MoldEntry> filtered = new ArrayList<>();
            for (MoldCatalog.MoldEntry mold : entry.getValue()) {
                if (query.empty() || query.matches(searchDocuments.get(mold))) filtered.add(mold);
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

    private void rebuildSearchDocuments() {
        searchIndex = LocalizedSearchIndex.current();
        searchDocuments.clear();
        for (var molds : catalog.values()) {
            for (var mold : molds) {
                searchDocuments.put(mold, searchIndex.document(mold.displayName(), mold.id().toString(), mold.sourceId()));
            }
        }
        searchRevision = searchIndex.revision();
        rowCache = null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (window.mouseClicked(mouseX, mouseY, button)) {
            if (window.consumeLayoutChanged()) applyWindowLayout();
            if (window.consumeCloseRequested()) onClose();
            return true;
        }
        // This menu has no slots: frame/outside clicks must never reach container drop handling.
        if (!window.containsBody(mouseX, mouseY)) return true;
        if (button == 0 && containsList(mouseX, mouseY)) {
            List<Row> rows = visibleRows();
            int index = (int) (mouseY - listTop + scroll) / ROW_HEIGHT;
            if (index >= 0 && index < rows.size()) {
                handleClick(rows.get(index), mouseX, mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (window.mouseDragged(mouseX, mouseY, button)) {
            if (window.consumeLayoutChanged()) applyWindowLayout();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (window.mouseReleased(button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        window.save();
        super.removed();
    }

    private void handleClick(Row row, double mouseX, double mouseY) {
        if (row instanceof HeaderRow header) {
            int rowRight = rowRight();
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
            int rowRight = rowRight();
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
        if (!containsList(mouseX, mouseY) || window.capturing()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        List<Row> rows = visibleRows();
        int maxScroll = Math.max(0, rows.size() * ROW_HEIGHT - (listBottom - listTop));
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
