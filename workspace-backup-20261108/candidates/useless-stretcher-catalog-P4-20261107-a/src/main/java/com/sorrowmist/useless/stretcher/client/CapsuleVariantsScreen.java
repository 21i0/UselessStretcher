package com.sorrowmist.useless.stretcher.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.sorrowmist.useless.stretcher.client.gui.FloatingScreen;
import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.client.search.LocalizedSearchIndex;
import com.sorrowmist.useless.stretcher.network.CapsuleVariantNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** An inspected plan is selected by its opaque server token, never recreated from a client seed. */
public final class CapsuleVariantsScreen extends FloatingScreen {
    private static final String PREFIX = "gui.useless_stretcher.dimension_capsule.variants.";
    private final DimensionCapsuleScreen parent;
    private final String structureId, dimension;
    private final boolean offhand;
    private final int parentInstanceId, side;
    private final List<Integer> rows = new ArrayList<>();
    private CapsuleVariantNetwork.State state;
    private String selected = "";
    private int ticks, scroll;
    private long searchRevision;
    private Layout layout;
    private EditBox filter;
    private SelectableAE2Button continueButton, pauseButton, generateButton;
    private DynamicTexture previewImage;
    private ResourceLocation previewTexture;
    private int[] previewPixels;

    /** Exposed for viewport regression checks; all controls stay in the floating body's bounds. */
    public record Layout(int left, int top, int width, int height, int listTop, int listWidth,
                         int listBottom, int visibleRows, int mapX, int mapY, int mapSize, int footer) {
        public static Layout calculate(int left, int top, int width, int height) {
            int safeWidth = Math.max(1, width), safeHeight = Math.max(158, height);
            int footer = top + safeHeight - 44;
            int listTop = top + 62;
            int availableRows = Math.max(1, (footer - 4 - listTop) / 18);
            int gap = Math.min(8, Math.max(0, safeWidth / 12));
            int listWidth = Math.max(1, Math.min(Math.max(1, safeWidth - gap - 16), Math.max(48, safeWidth * 42 / 100)));
            int mapX = left + listWidth + gap;
            int mapSize = Math.max(1, Math.min(Math.max(1, left + safeWidth - mapX), Math.max(1, footer - listTop - 44)));
            return new Layout(left, top, safeWidth, safeHeight, listTop, listWidth,
                    listTop + availableRows * 18, availableRows, mapX, listTop, mapSize, footer);
        }
    }

    public CapsuleVariantsScreen(DimensionCapsuleScreen parent, String structureId, String dimension,
                                 boolean offhand, int parentInstanceId, int side) {
        super(label("title"), "dimension_capsule_variants", 640, 355, 310, 255);
        this.parent = parent;
        this.structureId = structureId;
        this.dimension = dimension;
        this.offhand = offhand;
        this.parentInstanceId = parentInstanceId;
        this.side = side;
        shareWindow(parent.floatingWindow());
    }

    private static Component label(String suffix, Object... args) { return Component.translatable(PREFIX + suffix, args); }

    @Override protected void initContent() {
        // A short viewport uses the shared floating screen's vertical scrolling as a fallback.
        int extent = Math.max(158, contentHeight());
        setContentExtent(extent);
        layout = Layout.calculate(contentLeft(), contentTop(), contentWidth(), extent);
        if (filter == null) {
            filter = new EditBox(font, layout.left, layout.top, layout.width, 18, label("filter"));
            filter.setHint(label("filter"));
            filter.setMaxLength(128);
            filter.setResponder(value -> { rebuildRows(); scroll = 0; });
            filter.setTooltip(Tooltip.create(Component.literal(structureId + "\n" + dimension)));
        }
        filter.setX(layout.left); filter.setY(layout.top); filter.setWidth(layout.width);
        addRenderableWidget(filter);
        int gap = 4, third = Math.max(1, (layout.width - gap * 2) / 3);
        continueButton = addRenderableWidget(new SelectableAE2Button(layout.left, layout.footer, third, 18,
                label("continue"), b -> request(CapsuleVariantNetwork.CONTINUE)));
        pauseButton = addRenderableWidget(new SelectableAE2Button(layout.left + third + gap, layout.footer, third, 18,
                label("pause"), b -> request(CapsuleVariantNetwork.PAUSE)));
        addRenderableWidget(new SelectableAE2Button(layout.left + (third + gap) * 2, layout.footer,
                layout.width - (third + gap) * 2, 18, label("back"), b -> onClose()));
        generateButton = addRenderableWidget(new SelectableAE2Button(layout.left, layout.footer + 22, layout.width, 18,
                label("generate"), b -> request(CapsuleVariantNetwork.GENERATE)));
        generateButton.setTooltip(Tooltip.create(label("generate_help")));
        clampScroll();
        updateButtons();
        if (ticks == 0) request(CapsuleVariantNetwork.QUERY);
    }

    @Override public void tick() {
        super.tick();
        if (++ticks % 20 == 0) request(CapsuleVariantNetwork.QUERY);
        long revision = LocalizedSearchIndex.current().revision();
        if (searchRevision != revision) { searchRevision = revision; rebuildRows(); }
    }

    private void request(int action) {
        if (minecraft == null || minecraft.getConnection() == null) return;
        if (action == CapsuleVariantNetwork.GENERATE && selectedEntry() == null) return;
        CapsuleVariantNetwork.request(structureId, dimension, action,
                action == CapsuleVariantNetwork.QUERY || action == CapsuleVariantNetwork.GENERATE ? selected : "",
                offhand, parentInstanceId, side);
    }

    public void accept(CapsuleVariantNetwork.State payload) {
        if (!structureId.equals(payload.id()) || !dimension.equals(payload.dimension())) return;
        state = payload;
        if (payload.generatedInstanceId() >= 0) {
            parent.showVariantInstance(payload.generatedInstanceId());
            return;
        }
        if (selectedEntry() == null) {
            selected = payload.entries().stream().anyMatch(e -> e.candidateId().equals(payload.selectedCandidateId()))
                    ? payload.selectedCandidateId() : payload.entries().isEmpty() ? "" : payload.entries().getFirst().candidateId();
        }
        rebuildRows();
        if (selected.equals(payload.selectedCandidateId()) && payload.preview().length == 4096) {
            uploadPreview(payload.preview());
        } else clearPreview();
        updateButtons();
    }

    private void uploadPreview(int[] pixels) {
        if (Arrays.equals(previewPixels, pixels)) return;
        if (previewImage == null) {
            previewImage = new DynamicTexture(64, 64, false);
            previewTexture = minecraft.getTextureManager().register("capsule_candidate", previewImage);
        }
        NativeImage image = previewImage.getPixels();
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            image.setPixelRGBA(i % 64, i / 64, argb & 0xFF00FF00 | (argb >> 16 & 255) | (argb & 255) << 16);
        }
        previewImage.upload();
        previewPixels = pixels.clone();
    }

    private CapsuleVariantNetwork.Entry selectedEntry() {
        if (state != null) for (var entry : state.entries()) if (entry.candidateId().equals(selected)) return entry;
        return null;
    }

    private Component entryName(CapsuleVariantNetwork.Entry entry, int index) {
        String family = switch (entry.family()) {
            case "minecraft:bastion/treasure" -> "treasure";
            case "minecraft:bastion/units" -> "housing";
            case "minecraft:bastion/bridge" -> "bridge";
            case "minecraft:bastion/hoglin_stable" -> "hoglin_stable";
            default -> "";
        };
        return family.isEmpty() ? label("layout", index + 1) : label("named_layout", index + 1, label("family." + family));
    }

    private void rebuildRows() {
        rows.clear();
        if (state == null) return;
        var query = LocalizedSearchIndex.Query.parse(filter == null ? "" : filter.getValue());
        var search = LocalizedSearchIndex.current();
        for (int i = 0; i < state.entries().size(); i++) {
            var entry = state.entries().get(i);
            if (query.matches(search.document(entryName(entry, i).getString(), entry.template(), entry.family()))) rows.add(i);
        }
        clampScroll();
        // Filtering must never leave a hidden, different layout as the generation target.
        boolean selectedVisible = rows.stream().anyMatch(index -> state.entries().get(index).candidateId().equals(selected));
        if (!selectedVisible) {
            if (!rows.isEmpty()) select(rows.getFirst());
            else { selected = ""; clearPreview(); updateButtons(); }
        }
    }

    private void clampScroll() {
        if (layout != null) scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - layout.visibleRows));
    }

    private void updateButtons() {
        if (generateButton == null) return;
        String phase = state == null ? "idle" : state.phase();
        boolean searching = phase.equals("searching"), blocked = phase.equals("unavailable") || phase.equals("busy");
        continueButton.active = !searching && !blocked && !phase.equals("cache_full")
                && (state == null || state.entries().size() < CapsuleVariantNetwork.MAX_CANDIDATES);
        pauseButton.active = searching;
        generateButton.active = selectedEntry() != null && !phase.equals("stale") && !blocked;
    }

    @Override protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int left = layout.left, top = layout.top;
        String phase = state == null ? "idle" : state.phase();
        String key = PREFIX + "phase." + phase;
        Component status = net.minecraft.locale.Language.getInstance().has(key) ? Component.translatable(key) : label("phase.failed");
        g.drawString(font, font.plainSubstrByWidth(status.getString(), layout.width), left, top + 24, StretcherScreenStyle.TEXT_COLOR, false);
        int checked = state == null ? 0 : Math.max(0, state.checked() - state.roundStart());
        int limit = state == null ? 256 : state.roundLimit();
        int total = state == null ? 0 : state.checked();
        Component count = label("checked", checked, limit, total);
        g.drawString(font, font.plainSubstrByWidth(count.getString(), layout.width), left, top + 36, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        g.fill(left, top + 48, left + layout.width, top + 54, 0xFF555B70);
        g.fill(left + 1, top + 49, left + layout.width - 1, top + 53, 0xFF222733);
        int filled = (int) ((long) Math.max(0, layout.width - 2) * Mth.clamp(checked, 0, Math.max(1, limit)) / Math.max(1, limit));
        if (filled > 0) g.fill(left + 1, top + 49, left + 1 + filled, top + 53, 0xFF7098B5);

        StretcherScreenStyle.drawInset(g, left, layout.listTop - 2, left + layout.listWidth, layout.listBottom + 2);
        for (int row = 0; row < layout.visibleRows && row + scroll < rows.size(); row++) {
            int index = rows.get(row + scroll), y = layout.listTop + row * 18;
            var entry = state.entries().get(index);
            if (entry.candidateId().equals(selected)) g.fill(left + 2, y, left + layout.listWidth - 5, y + 17, 0x55689082);
            g.drawString(font, font.plainSubstrByWidth(entryName(entry, index).getString(), layout.listWidth - 12),
                    left + 5, y + 4, StretcherScreenStyle.TEXT_COLOR, false);
        }
        if (rows.isEmpty()) g.drawString(font, font.plainSubstrByWidth(label("empty").getString(), layout.listWidth - 10),
                left + 5, layout.listTop + 4, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        if (rows.size() > layout.visibleRows) {
            int height = layout.listBottom - layout.listTop, thumb = Math.max(12, height * layout.visibleRows / rows.size());
            int y = layout.listTop + (height - thumb) * scroll / Math.max(1, rows.size() - layout.visibleRows);
            g.fill(left + layout.listWidth - 4, y, left + layout.listWidth - 2, y + thumb, StretcherScreenStyle.ACTIVE_COLOR);
        }
        g.fill(layout.mapX, layout.mapY, layout.mapX + layout.mapSize, layout.mapY + layout.mapSize, 0xFF171B20);
        if (previewTexture != null) {
            g.enableScissor(layout.mapX, layout.mapY, layout.mapX + layout.mapSize, layout.mapY + layout.mapSize);
            try { g.blit(previewTexture, layout.mapX, layout.mapY, 0, 0, layout.mapSize, layout.mapSize, layout.mapSize, layout.mapSize); }
            finally { g.disableScissor(); }
        }
        int detailWidth = Math.max(1, layout.left + layout.width - layout.mapX);
        int detailY = layout.mapY + layout.mapSize + 4;
        g.drawString(font, font.plainSubstrByWidth(label("diagram").getString(), detailWidth),
                layout.mapX, detailY, StretcherScreenStyle.WARNING_COLOR, false);
        var entry = selectedEntry();
        if (entry != null) {
            g.drawString(font, font.plainSubstrByWidth(label("size", entry.sx(), entry.sy(), entry.sz(), entry.pieces()).getString(), detailWidth),
                    layout.mapX, detailY + 12, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
            g.drawString(font, font.plainSubstrByWidth(entry.template(), detailWidth),
                    layout.mapX, detailY + 24, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        }
    }

    @Override protected void renderContentTooltip(GuiGraphics g, int x, int y, float partial) {
        int index = rowAt(x, y);
        if (index >= 0) {
            var entry = state.entries().get(index);
            g.renderTooltip(font, List.of(entryName(entry, index).getVisualOrderText(), Component.literal(entry.template()).getVisualOrderText(),
                    label("size", entry.sx(), entry.sy(), entry.sz(), entry.pieces()).getVisualOrderText()), x, y);
        } else if (x >= layout.mapX && x < layout.left + layout.width && y >= layout.mapY && y < layout.footer) {
            var entry = selectedEntry();
            Component help = entry == null ? label("diagram_help") : label("diagram_help").copy().append("\n").append(entry.template());
            g.renderTooltip(font, font.split(help, 220), x, y);
        }
    }

    private int rowAt(double x, double y) {
        if (x < layout.left || x >= layout.left + layout.listWidth || y < layout.listTop || y >= layout.listBottom) return -1;
        int row = scroll + (int) (y - layout.listTop) / 18;
        return row >= 0 && row < rows.size() ? rows.get(row) : -1;
    }

    private void select(int index) {
        if (state == null || index < 0 || index >= state.entries().size()) return;
        String token = state.entries().get(index).candidateId();
        if (!selected.equals(token)) { selected = token; clearPreview(); request(CapsuleVariantNetwork.QUERY); }
        updateButtons();
    }

    @Override protected boolean clickContent(double x, double y, int button) {
        if (super.clickContent(x, y, button)) return true;
        int index = rowAt(x, y);
        if (button == 0 && index >= 0) { select(index); return true; }
        return false;
    }

    @Override protected boolean scrollContent(double x, double y, double dx, double dy) {
        if (x >= layout.left && x < layout.left + layout.listWidth && y >= layout.listTop && y < layout.listBottom) {
            scroll -= (int) Math.signum(dy) * 3; clampScroll(); return true;
        }
        return super.scrollContent(x, y, dx, dy);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!filter.isFocused() && !rows.isEmpty()
                && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN)) {
            int at = 0;
            for (int i = 0; i < rows.size(); i++) if (state.entries().get(rows.get(i)).candidateId().equals(selected)) { at = i; break; }
            int step = keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN ? layout.visibleRows : 1;
            int target = Mth.clamp(at + (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_PAGE_UP ? -step : step), 0, rows.size() - 1);
            select(rows.get(target));
            if (target < scroll) scroll = target;
            if (target >= scroll + layout.visibleRows) scroll = target - layout.visibleRows + 1;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void clearPreview() {
        if (previewTexture != null && minecraft != null) minecraft.getTextureManager().release(previewTexture);
        previewTexture = null; previewImage = null; previewPixels = null;
    }

    @Override public void onClose() { parent.returnFromVariants(); }
    @Override public void removed() { clearPreview(); super.removed(); }

    public String selectedCandidateId() { return selected; }
    public int candidateScroll() { return scroll; }
    public int visibleCandidateRows() { return layout == null ? 0 : layout.visibleRows; }
}
