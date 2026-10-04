package com.sorrowmist.useless.stretcher.screen;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AEProcessingPattern;
import com.sorrowmist.useless.stretcher.client.gui.FloatingScreen;
import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.client.search.RegistrySearchCatalog;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Chest-style, bounded server-backed view of the library linked to the held stretcher. */
public final class MyriadPatternRepositoryScreen extends FloatingScreen {
    private static final int SLOT = 18;
    private static final java.util.concurrent.atomic.AtomicInteger QUERY_IDS = new java.util.concurrent.atomic.AtomicInteger();
    private final Screen parent;
    private final BlockPos pos;
    private final InteractionHand hand;
    private final List<AEItemKey> patterns = new ArrayList<>();
    private final List<ItemStack> stacks = new ArrayList<>();
    private final List<ItemStack> outputs = new ArrayList<>();
    private final Set<Integer> selected = new LinkedHashSet<>();
    private int page, pages = 1;
    private boolean aeBound;
    private int left, top, gridLeft, gridTop, columns, rows, scrollRow;
    private boolean dragging, dragValue, pending;
    private int lastDrag = -1, dragButton;
    private SelectableAE2Button deleteButton, previousButton, nextButton, selectButton;
    private EditBox search;
    private RegistrySearchCatalog searchCatalog;
    private RegistrySearchCatalog.Masks searchMasks = new RegistrySearchCatalog.Masks(new byte[0], new byte[0]);
    private String maskQuery = "", searchProgress = "";
    private int queryId, searchDelay = -1, targetPage;
    private boolean awaitingPinyin;

    public MyriadPatternRepositoryScreen(Screen parent, BlockPos pos, InteractionHand hand) {
        super(Component.translatable("gui.useless_stretcher.pattern_repository.title"),
                "myriad_patterns", 310, 270, 240, 165);
        this.parent = parent;
        this.pos = pos;
        this.hand = hand;
    }

    public boolean matches(Network.PatternPagePayload payload) {
        return payload.item() && payload.offhand() == (hand == InteractionHand.OFF_HAND)
                && pos.equals(payload.pos()) && payload.requestId() == queryId;
    }

    public void onPage(Network.PatternPagePayload payload) {
        if (!matches(payload)) return;
        if (!payload.progress().isEmpty()) {
            searchProgress = payload.progress();
            if ("changed".equals(searchProgress)) queueSearch(0, 0);
            else pending = !"failed".equals(searchProgress);
            updateButtons();
            return;
        }
        searchProgress = "";
        page = payload.page(); pages = Math.max(1, payload.pages()); aeBound = payload.aeBound();
        patterns.clear(); patterns.addAll(payload.patterns());
        stacks.clear(); outputs.clear();
        for (AEItemKey key : patterns) { stacks.add(key.toStack(1)); outputs.add(null); }
        selected.clear(); dragging = false; lastDrag = -1;
        scrollRow = 0; pending = false;
        rebuildWidgets();
    }

    @Override protected void initContent() {
        left = contentLeft(); top = contentTop();
        int width = contentWidth();
        columns = Math.max(1, (width - 8) / SLOT);
        rows = Math.max(1, (contentHeight() - 87) / SLOT);
        gridLeft = left + (width - columns * SLOT - 6) / 2;
        gridTop = top + 48;
        if (search == null) {
            search = new EditBox(font, left, top + 26, width, 18,
                    Component.translatable("gui.useless_stretcher.search_hint"));
            search.setMaxLength(128);
            search.setHint(Component.translatable("gui.useless_stretcher.search_hint"));
            search.setResponder(value -> { maskQuery = null; awaitingPinyin = false; queueSearch(0, 5); });
        }
        search.setX(left); search.setY(top + 26); search.setWidth(width);
        addRenderableWidget(search);
        scrollRow = Mth.clamp(scrollRow, 0, maxScrollRow());
        int footerY = top + contentHeight() - 21;
        button(left, footerY, 42, "back", this::onClose);
        selectButton = button(left + 45, footerY, 53, "pattern_repository.select_page", () -> {
            if (selected.size() == patterns.size()) selected.clear();
            else for (int i = 0; i < patterns.size(); i++) selected.add(i);
            updateButtons();
        });
        deleteButton = button(left + 101, footerY, 58, "pattern_repository.delete", () -> {
            if (selected.isEmpty() || pending) return;
            List<AEItemKey> remove = selected.stream().map(patterns::get).toList();
            pending = true;
            queueSearch(0, 0);
            updateButtons();
            Network.deleteStretcherPatterns(hand, remove);
        });
        previousButton = addRenderableWidget(new SelectableAE2Button(left + width - 40, footerY, 18, 18,
                Component.literal("<"), ignored -> requestPage(page - 1)));
        nextButton = addRenderableWidget(new SelectableAE2Button(left + width - 19, footerY, 18, 18,
                Component.literal(">"), ignored -> requestPage(page + 1)));
        setContentExtent(contentHeight());
        updateButtons();
    }

    private SelectableAE2Button button(int x, int y, int width, String key, Runnable action) {
        SelectableAE2Button button = addRenderableWidget(new SelectableAE2Button(x, y, width, 18,
                Component.translatable("gui.useless_stretcher." + key), ignored -> action.run()));
        button.setTooltip(Tooltip.create(Component.translatable("help.useless_stretcher." + key)));
        return button;
    }

    private void requestPage(int target) {
        if (pending || target < 0 || target >= pages) return;
        queueSearch(target, 0);
    }

    private void queueSearch(int target, int delay) {
        queryId = QUERY_IDS.incrementAndGet();
        targetPage = target;
        searchDelay = delay;
        pending = true;
        selected.clear(); dragging = false; lastDrag = -1;
        searchProgress = "";
        updateButtons();
    }

    @Override public void tick() {
        super.tick();
        if (minecraft.getConnection() == null) return;
        String text = search == null ? "" : search.getValue().trim();
        if (!text.isEmpty()) {
            var current = RegistrySearchCatalog.current();
            if (searchCatalog != current) {
                searchCatalog = current;
                maskQuery = null;
                if (searchDelay < 0) queueSearch(0, 5);
            }
            searchCatalog.advance();
            if (awaitingPinyin && searchCatalog.ready() && !searchCatalog.transliterating()) {
                awaitingPinyin = false;
                maskQuery = null;
                queueSearch(0, 0);
            }
        }
        if (searchDelay < 0 || (searchDelay > 0 && --searchDelay > 0)) return;
        if (!text.isEmpty() && !searchCatalog.ready()) return;
        if (!text.equals(maskQuery)) {
            searchMasks = text.isEmpty() ? new RegistrySearchCatalog.Masks(new byte[0], new byte[0])
                    : searchCatalog.match(text);
            maskQuery = text;
            awaitingPinyin = !text.isEmpty() && searchCatalog.transliterating();
        }
        searchDelay = -1;
        Network.requestStretcherPatternPage(hand, targetPage, queryId, text, searchMasks.items(), searchMasks.fluids());
    }

    private void updateButtons() {
        if (deleteButton == null) return;
        deleteButton.active = !pending && !selected.isEmpty();
        selectButton.active = !pending && !patterns.isEmpty();
        previousButton.active = !pending && page > 0;
        nextButton.active = !pending && page + 1 < pages;
    }

    @Override protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(font, Component.translatable("gui.useless_stretcher.pattern_repository.count",
                patterns.size(), page + 1, pages), left, top + 2, StretcherScreenStyle.TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable(aeBound ? "gui.useless_stretcher.pattern_repository.connected"
                : "gui.useless_stretcher.pattern_repository.disconnected"), left, top + 14,
                aeBound ? StretcherScreenStyle.SUCCESS_COLOR : StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        int hover = slotAt(mouseX, mouseY);
        for (int cell = 0; cell < columns * rows; cell++) {
            int x = gridLeft + cell % columns * SLOT, y = gridTop + cell / columns * SLOT;
            graphics.fill(x, y, x + SLOT, y + SLOT, StretcherScreenStyle.HIGHLIGHT_COLOR);
            graphics.fill(x, y, x + SLOT - 1, y + SLOT - 1, StretcherScreenStyle.TEXT_COLOR);
            graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, StretcherScreenStyle.SLOT_COLOR);
            int i = scrollRow * columns + cell;
            if (i >= patterns.size()) continue;
            if (selected.contains(i)) graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xFF5A96BE);
            ItemStack display = Screen.hasShiftDown() ? output(i) : stacks.get(i);
            graphics.renderItem(display, x + 1, y + 1);
            graphics.renderItemDecorations(font, display, x + 1, y + 1);
            if (i == hover) graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0x40FFFFFF);
        }
        if (maxScrollRow() > 0) {
            int x = gridLeft + columns * SLOT + 2, height = rows * SLOT;
            graphics.fill(x, gridTop, x + 4, gridTop + height, 0xFF373737);
            int thumbHeight = Math.max(12, height * rows / (maxScrollRow() + rows));
            int thumbTop = gridTop + (height - thumbHeight) * scrollRow / maxScrollRow();
            graphics.fill(x, thumbTop, x + 4, thumbTop + thumbHeight, 0xFFBABABA);
        }
        Component status = Component.translatable("failed".equals(searchProgress) ? "gui.useless_stretcher.fetch_failed"
                : pending ? "gui.useless_stretcher.pattern_repository.waiting"
                : "gui.useless_stretcher.pattern_repository.selected", selected.size());
        if (pending && !searchProgress.isEmpty()) status = status.copy().append(" " + searchProgress);
        graphics.drawString(font, status,
                left, top + contentHeight() - 33, StretcherScreenStyle.TEXT_COLOR, false);
        if (patterns.isEmpty() && !pending) graphics.drawCenteredString(font,
                Component.translatable("gui.useless_stretcher.pattern_repository.empty"),
                gridLeft + columns * SLOT / 2, gridTop + 8, 0xFFFFFFFF);
    }

    private ItemStack output(int index) {
        ItemStack cached = outputs.get(index);
        if (cached != null) return cached;
        ItemStack result = stacks.get(index);
        try {
            // Omniversal patterns already contain display outputs; do not resolve recipes to draw an icon.
            var details = result.has(AEComponents.ENCODED_PROCESSING_PATTERN)
                    ? new AEProcessingPattern(patterns.get(index))
                    : minecraft.level == null ? null : PatternDetailsHelper.decodePattern(result, minecraft.level);
            if (details != null) {
                var primary = details.getPrimaryOutput();
                result = primary.what().wrapForDisplayOrFilter();
                if (primary.what() instanceof AEItemKey)
                    result.setCount((int) Math.min(99, primary.amount()));
            }
        } catch (RuntimeException ignored) {
            // Missing recipes must not prevent viewing or deleting their original pattern.
        }
        outputs.set(index, result);
        return result;
    }

    @Override protected void renderContentTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int index = slotAt(mouseX, mouseY);
        // Preserve AE and Useless Mod's own materials/molds tooltip, including Shift behavior.
        if (index >= 0 && !dragging) graphics.renderTooltip(font, stacks.get(index), mouseX, mouseY);
    }

    private int slotAt(double x, double y) {
        if (x < gridLeft || y < gridTop || x >= gridLeft + columns * SLOT || y >= gridTop + rows * SLOT) return -1;
        int index = (scrollRow + (int) ((y - gridTop) / SLOT)) * columns + (int) ((x - gridLeft) / SLOT);
        return index < patterns.size() ? index : -1;
    }

    @Override protected boolean clickContent(double x, double y, int button) {
        if (super.clickContent(x, y, button)) return true;
        int index = slotAt(x, y);
        if ((button != 0 && button != 1) || index < 0 || pending) return false;
        // FTB Chunks freehand semantics: left paints selection, right erases it.
        dragging = true; dragButton = button; dragValue = button == 0; lastDrag = index;
        select(index);
        return true;
    }

    private void select(int index) {
        if (dragValue) selected.add(index); else selected.remove(index);
        updateButtons();
    }

    @Override protected boolean dragContent(double x, double y, int button, double dx, double dy) {
        if (!dragging || button != dragButton) return false;
        int index = slotAt(x, y);
        if (index >= 0) {
            // Interpolate missed pointer events so fast drags do not leave holes.
            int fromX = lastDrag % columns, fromY = lastDrag / columns;
            int toX = index % columns, toY = index / columns;
            int steps = Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY));
            for (int step = 0; step <= steps; step++) {
                float t = steps == 0 ? 0 : step / (float) steps;
                select(Math.round(fromY + (toY - fromY) * t) * columns + Math.round(fromX + (toX - fromX) * t));
            }
            lastDrag = index;
        }
        return true;
    }

    @Override protected boolean releaseContent(double x, double y, int button) {
        if (button == dragButton && dragging) { dragging = false; lastDrag = -1; return true; }
        return false;
    }

    private int maxScrollRow() { return Math.max(0, (patterns.size() + columns - 1) / columns - rows); }

    @Override protected boolean scrollContent(double x, double y, double dx, double dy) {
        int next = Mth.clamp(scrollRow - (int) Math.signum(dy), 0, maxScrollRow());
        if (next == scrollRow) return super.scrollContent(x, y, dx, dy);
        scrollRow = next; dragging = false;
        return true;
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
