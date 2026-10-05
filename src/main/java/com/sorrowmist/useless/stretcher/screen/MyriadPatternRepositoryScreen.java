package com.sorrowmist.useless.stretcher.screen;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.client.render.PatternSlotRenderer;
import com.sorrowmist.useless.stretcher.content.mold.PatternOutputs;
import com.sorrowmist.useless.stretcher.content.mold.PatternSearchIndex;
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
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.BitSet;
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
    private final List<GenericStack> outputs = new ArrayList<>();
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
    private int pendingTicks;
    private int pageCapacity;
    private int resizeAnchor = -1;
    private boolean trimming;
    private PatternSearchIndex.Mode searchMode = PatternSearchIndex.Mode.OUTPUT;
    private final BitSet duplicates = new BitSet();
    private boolean markDuplicates, manualSelection, duplicateMenuOpen;
    private PatternSearchIndex.Keep keep = PatternSearchIndex.Keep.MOST;
    private final BitSet keepMost = new BitSet(), keepLeast = new BitSet();
    private long selectionRevision;
    private int duplicateCount;
    private SelectableAE2Button modeButton, trimButton, duplicateButton, duplicateMenuButton, keepMostButton, keepLeastButton;

    public MyriadPatternRepositoryScreen(Screen parent, BlockPos pos, InteractionHand hand) {
        super(Component.translatable("gui.useless_stretcher.pattern_repository.title"),
                "myriad_patterns", 310, 270, 240, 185);
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
        pendingTicks = 0;
        if (!payload.progress().isEmpty()) {
            searchProgress = payload.progress();
            if ("changed".equals(searchProgress)) {
                if (trimming && minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable(
                        "gui.useless_stretcher.pattern_repository.changed"), false);
                trimming = false;
                queueSearch(refreshPage(), 0);
            }
            else if (searchProgress.startsWith("trimmed:") || searchProgress.startsWith("deduplicated:")) {
                trimming = false;
                boolean deduplicated = searchProgress.startsWith("deduplicated:");
                if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable(
                        "gui.useless_stretcher.pattern_repository." + (deduplicated ? "deduplicated" : "trimmed"),
                        searchProgress.substring(deduplicated ? 13 : 8)), false);
                queueSearch(refreshPage(), 0);
            }
            else {
                pending = !"failed".equals(searchProgress);
                if (!pending) trimming = false;
            }
            updateButtons();
            return;
        }
        searchProgress = "";
        int previousPage = page;
        page = payload.page(); targetPage = page; pages = Math.max(1, payload.pages()); aeBound = payload.aeBound();
        patterns.clear(); patterns.addAll(payload.patterns());
        duplicates.clear(); duplicates.or(BitSet.valueOf(payload.duplicates()));
        selectionRevision = payload.selection().revision();
        duplicateCount = payload.selection().count();
        keepMost.clear(); keepMost.or(BitSet.valueOf(payload.selection().keepMost()));
        keepLeast.clear(); keepLeast.or(BitSet.valueOf(payload.selection().keepLeast()));
        stacks.clear(); outputs.clear();
        for (AEItemKey key : patterns) {
            stacks.add(key.toStack(1));
            GenericStack primary = null;
            try { primary = PatternOutputs.primary(key); } catch (RuntimeException ignored) { }
            outputs.add(primary);
        }
        applyDuplicateSelection();
        dragging = false; lastDrag = -1;
        scrollRow = previousPage == page ? Mth.clamp(scrollRow, 0, maxScrollRow()) : 0;
        pending = false; trimming = false; searchDelay = -1; resizeAnchor = -1;
        rebuildWidgets();
    }

    @Override protected void initContent() {
        left = contentLeft(); top = contentTop();
        int width = contentWidth();
        int oldColumns = columns;
        columns = Math.max(1, (width - 8) / SLOT);
        rows = Math.max(1, (contentHeight() - 109 - (duplicateMenuOpen ? 22 : 0)) / SLOT);
        gridLeft = left + (width - columns * SLOT - 6) / 2;
        gridTop = top + (duplicateMenuOpen ? 92 : 70);
        if (search == null) {
            search = new EditBox(font, left, top + 26, width, 18,
                    Component.translatable("gui.useless_stretcher.search_hint"));
            search.setMaxLength(128);
            search.setResponder(value -> {
                maskQuery = null; awaitingPinyin = false;
                if (!trimming) queueSearch(0, 5);
            });
        }
        search.setHint(Component.translatable("gui.useless_stretcher.pattern_repository.hint." + searchMode.name().toLowerCase(java.util.Locale.ROOT)));
        search.setX(left); search.setY(top + 26); search.setWidth(Math.max(1, width - 46));
        addRenderableWidget(search);
        modeButton = button(left + width - 43, top + 26, 43,
                "pattern_repository.mode." + searchMode.name().toLowerCase(java.util.Locale.ROOT), () -> {
                    searchMode = searchMode.next();
                    queueSearch(0, 0);
                    rebuildWidgets();
                });
        modeButton.setTooltip(Tooltip.create(Component.translatable("help.useless_stretcher.pattern_repository.mode")));
        int actionWidth = (width - 3) / 2;
        trimButton = button(left, top + 48, actionWidth, "pattern_repository.trim", () -> {
            if (pending || patterns.isEmpty()) return;
            queueSearch(page, -1);
            trimming = true;
            updateButtons();
            Network.trimStretcherPatterns(hand, targetPage, queryId, pageCapacity);
        });
        duplicateButton = button(left + actionWidth + 3, top + 48, width - actionWidth - 24,
                "pattern_repository.duplicates." + (markDuplicates ? "on" : "off"), () -> {
                    markDuplicates = !markDuplicates;
                    manualSelection = false;
                    applyDuplicateSelection();
                    rebuildWidgets();
                });
        duplicateButton.setTooltip(Tooltip.create(Component.translatable("help.useless_stretcher.pattern_repository.duplicates")));
        duplicateMenuButton = addRenderableWidget(new SelectableAE2Button(left + width - 18, top + 48, 18, 18,
                Component.literal(duplicateMenuOpen ? "▲" : "▼"), ignored -> {
                    duplicateMenuOpen = !duplicateMenuOpen;
                    rebuildWidgets();
                }));
        duplicateMenuButton.setTooltip(Tooltip.create(Component.translatable("help.useless_stretcher.pattern_repository.keep")));
        if (duplicateMenuOpen) {
            keepMostButton = button(left, top + 70, actionWidth, "pattern_repository.keep.most",
                    () -> chooseKeep(PatternSearchIndex.Keep.MOST));
            keepLeastButton = button(left + actionWidth + 3, top + 70, width - actionWidth - 3,
                    "pattern_repository.keep.least", () -> chooseKeep(PatternSearchIndex.Keep.LEAST));
        }
        scrollRow = Mth.clamp(scrollRow, 0, maxScrollRow());
        int footerY = top + contentHeight() - 21;
        button(left, footerY, 42, "back", this::onClose);
        selectButton = button(left + 45, footerY, 53, "pattern_repository.select_page", () -> {
            manualSelection = true;
            if (selected.size() == patterns.size()) selected.clear();
            else for (int i = 0; i < patterns.size(); i++) selected.add(i);
            updateButtons();
        });
        deleteButton = button(left + 101, footerY, 58, "pattern_repository.delete", () -> {
            if (pending) return;
            if (autoSelectionActive()) {
                if (duplicateCount == 0) return;
                queueSearch(page, -1);
                trimming = true;
                updateButtons();
                Network.deduplicateStretcherPatterns(hand, targetPage, queryId, pageCapacity, keep, selectionRevision);
                return;
            }
            if (selected.isEmpty()) return;
            List<AEItemKey> remove = selected.stream().map(patterns::get).toList();
            refreshAfterDeletion();
            Network.deleteStretcherPatterns(hand, remove);
        });
        previousButton = addRenderableWidget(new SelectableAE2Button(left + width - 40, footerY, 18, 18,
                Component.literal("<"), ignored -> requestPage(page - 1)));
        nextButton = addRenderableWidget(new SelectableAE2Button(left + width - 19, footerY, 18, 18,
                Component.literal(">"), ignored -> requestPage(page + 1)));
        setContentExtent(contentHeight());
        updateButtons();
        // Expanding the policy row only changes the visible grid, never the server page or its anchor.
        int capacity = Math.min(Network.PatternPagePayload.MAX_PAGE, columns * Math.max(1, (contentHeight() - 109) / SLOT));
        if (pageCapacity != 0 && capacity != pageCapacity) {
            int anchor = resizeAnchor >= 0 ? resizeAnchor
                    : (pending ? targetPage : page) * pageCapacity + scrollRow * oldColumns;
            pageCapacity = capacity;
            if (!trimming) queueSearch(anchor / capacity, 3);
            resizeAnchor = anchor;
        } else {
            pageCapacity = capacity;
            if (queryId == 0 && patterns.isEmpty() && !pending) queueSearch(page, 0);
        }
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

    private int refreshPage() { return resizeAnchor < 0 ? targetPage : resizeAnchor / Math.max(1, pageCapacity); }

    private void refreshAfterDeletion() { queueSearch(page, 0); }

    private boolean autoSelectionActive() { return markDuplicates && !manualSelection && selectionRevision != 0; }

    private void applyDuplicateSelection() {
        selected.clear();
        if (!autoSelectionActive()) return;
        BitSet redundant = keep == PatternSearchIndex.Keep.MOST ? keepMost : keepLeast;
        for (int i = redundant.nextSetBit(0); i >= 0 && i < patterns.size(); i = redundant.nextSetBit(i + 1)) selected.add(i);
    }

    private void chooseKeep(PatternSearchIndex.Keep policy) {
        keep = policy;
        manualSelection = false;
        duplicateMenuOpen = false;
        applyDuplicateSelection();
        rebuildWidgets();
    }

    private void queueSearch(int target, int delay) {
        resizeAnchor = -1;
        queryId = QUERY_IDS.incrementAndGet();
        targetPage = target;
        searchDelay = delay;
        pending = true;
        pendingTicks = 0;
        selected.clear(); dragging = false; lastDrag = -1;
        searchProgress = "";
        updateButtons();
    }

    @Override public void tick() {
        super.tick();
        if (minecraft.getConnection() == null) return;
        if (floatingWindow().capturing()) return;
        // Watchdog: the server silently drops requests in a few cases (hand swapped mid-flight,
        // dimension change, cancel from another view). Re-query periodically instead of leaving
        // the footer buttons disabled forever, which looked like a broken delete key.
        if (pending && ++pendingTicks >= 100) {
            pendingTicks = 0;
            trimming = false;
            queueSearch(refreshPage(), 0);
            return;
        }
        if (trimming) return;
        String text = search == null ? "" : search.getValue().trim();
        if (!text.isEmpty()) {
            var current = RegistrySearchCatalog.current();
            if (searchCatalog != current) {
                searchCatalog = current;
                maskQuery = null;
                if (searchDelay < 0) queueSearch(pending ? targetPage : page, 5);
            }
            searchCatalog.advance();
            if (awaitingPinyin && searchCatalog.ready() && !searchCatalog.transliterating()) {
                awaitingPinyin = false;
                maskQuery = null;
                queueSearch(pending ? targetPage : page, 0);
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
        Network.requestStretcherPatternPage(hand, targetPage, queryId, text, searchMasks.items(), searchMasks.fluids(),
                pageCapacity, searchMode);
    }

    private void updateButtons() {
        if (deleteButton == null) return;
        boolean automatic = autoSelectionActive();
        deleteButton.active = !pending && (automatic ? duplicateCount > 0 : !selected.isEmpty());
        deleteButton.setMessage(Component.translatable("gui.useless_stretcher.pattern_repository." + (automatic ? "delete_duplicates" : "delete")));
        deleteButton.setTooltip(Tooltip.create(Component.translatable("help.useless_stretcher.pattern_repository."
                + (automatic ? "delete_duplicates" : "delete"))));
        selectButton.active = !pending && !patterns.isEmpty();
        previousButton.active = !pending && page > 0;
        nextButton.active = !pending && page + 1 < pages;
        if (trimButton != null) trimButton.active = !pending && !patterns.isEmpty();
        if (duplicateButton != null) duplicateButton.active = !pending;
        if (duplicateMenuButton != null) duplicateMenuButton.active = !pending;
        if (keepMostButton != null) keepMostButton.active = !pending && keep != PatternSearchIndex.Keep.MOST;
        if (keepLeastButton != null) keepLeastButton.active = !pending && keep != PatternSearchIndex.Keep.LEAST;
        if (modeButton != null) modeButton.active = !trimming;
        if (search != null) search.setEditable(!trimming);
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
            if (!PatternSlotRenderer.renderPattern(graphics, font, outputs.get(i), x + 1, y + 1, i))
                graphics.renderItem(new ItemStack(Items.BARRIER), x + 1, y + 1);
            if (i == hover) graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0x40FFFFFF);
        }
        if (markDuplicates) {
            graphics.flush();
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 170);
            for (int cell = 0; cell < columns * rows; cell++) {
                int i = scrollRow * columns + cell;
                if (i >= patterns.size() || !duplicates.get(i)) continue;
                int x = gridLeft + cell % columns * SLOT, y = gridTop + cell / columns * SLOT;
                graphics.fill(x + 1, y + 1, x + SLOT - 1, y + 2, 0xFFFFBE42);
                graphics.fill(x + 1, y + SLOT - 2, x + SLOT - 1, y + SLOT - 1, 0xFFFFBE42);
                graphics.fill(x + 1, y + 1, x + 2, y + SLOT - 1, 0xFFFFBE42);
                graphics.fill(x + SLOT - 2, y + 1, x + SLOT - 1, y + SLOT - 1, 0xFFFFBE42);
            }
            graphics.pose().popPose();
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
                : autoSelectionActive() ? "gui.useless_stretcher.pattern_repository.selected_duplicates"
                : "gui.useless_stretcher.pattern_repository.selected", autoSelectionActive() ? duplicateCount : selected.size());
        if (pending && !searchProgress.isEmpty()) {
            status = searchProgress.startsWith("trim:")
                    ? Component.translatable("gui.useless_stretcher.pattern_repository.trimming", searchProgress.substring(5))
                    : searchProgress.startsWith("dedup:")
                    ? Component.translatable("gui.useless_stretcher.pattern_repository.deduplicating", searchProgress.substring(6))
                    : status.copy().append(" " + searchProgress);
        }
        graphics.drawString(font, status,
                left, top + contentHeight() - 33, StretcherScreenStyle.TEXT_COLOR, false);
        if (patterns.isEmpty() && !pending) graphics.drawCenteredString(font,
                Component.translatable("gui.useless_stretcher.pattern_repository.empty"),
                gridLeft + columns * SLOT / 2, gridTop + 8, 0xFFFFFFFF);
    }

    @Override protected void renderContentTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int index = slotAt(mouseX, mouseY);
        // Preserve AE and Useless Mod's own materials/molds tooltip, including Shift behavior.
        if (index >= 0 && !dragging) {
            if (markDuplicates && duplicates.get(index)) {
                var lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, stacks.get(index)));
                lines.add(Component.translatable("gui.useless_stretcher.pattern_repository.duplicate_hint")
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
                lines.add(Component.translatable("gui.useless_stretcher.pattern_repository."
                        + ((keep == PatternSearchIndex.Keep.MOST ? keepMost : keepLeast).get(index) ? "redundant" : "retained"))
                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
                graphics.renderTooltip(font, lines, stacks.get(index).getTooltipImage(), mouseX, mouseY);
            } else graphics.renderTooltip(font, stacks.get(index), mouseX, mouseY);
        }
    }

    private int slotAt(double x, double y) {
        if (x < gridLeft || y < gridTop || x >= gridLeft + columns * SLOT || y >= gridTop + rows * SLOT) return -1;
        int index = (scrollRow + (int) ((y - gridTop) / SLOT)) * columns + (int) ((x - gridLeft) / SLOT);
        return index < patterns.size() ? index : -1;
    }

    @Override protected boolean clickContent(double x, double y, int button) {
        if (duplicateMenuOpen && (y < top + 48 || y >= top + 88)) {
            duplicateMenuOpen = false;
            rebuildWidgets();
            return true;
        }
        if (super.clickContent(x, y, button)) return true;
        int index = slotAt(x, y);
        if ((button != 0 && button != 1) || index < 0 || pending) return false;
        // FTB Chunks freehand semantics: left paints selection, right erases it.
        dragging = true; dragButton = button; dragValue = button == 0; lastDrag = index;
        select(index);
        return true;
    }

    private void select(int index) {
        manualSelection = true;
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

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && duplicateMenuOpen) {
            duplicateMenuOpen = false;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
