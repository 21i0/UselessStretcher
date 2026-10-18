package com.sorrowmist.useless.stretcher.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import com.sorrowmist.useless.stretcher.client.gui.FloatingScreen;
import com.sorrowmist.useless.stretcher.client.gui.SelectableAE2Button;
import com.sorrowmist.useless.stretcher.client.gui.StretcherScreenStyle;
import com.sorrowmist.useless.stretcher.client.search.LocalizedSearchIndex;
import com.sorrowmist.useless.stretcher.network.CapsuleNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Uses the same floating window and localized search index as the myriad catalogue. */
public final class DimensionCapsuleScreen extends FloatingScreen {
    private record Entry(String id, String mod, String name, List<String> dimensions, LocalizedSearchIndex.Document search) { }
    private record Row(String mod, int index) { boolean header() { return index < 0; } }
    private final List<Entry> entries;
    private final List<Row> rows = new ArrayList<>();
    private final Set<String> expanded = new LinkedHashSet<>();
    private final boolean offhand;
    private final LocalizedSearchIndex searchIndex = LocalizedSearchIndex.current();
    private long searchRevision;
    private EditBox search;
    private int selected = -1, dimension, side, scroll, ticks, instanceId;
    private int left, top, listWidth, listBottom, visibleRows, mapX, mapY, mapSize;
    private float angle, zoom = 1;
    private boolean rotating;
    private CapsuleNetwork.State state;
    private ResourceLocation mapTexture;
    private SelectableAE2Button enter, generate, adjacent, dimensionButton, instanceButton;

    public DimensionCapsuleScreen(List<String> wire, boolean offhand) {
        super(Component.translatable("gui.useless_stretcher.dimension_capsule.title"), "dimension_capsule", 640, 355, 310, 255);
        this.offhand = offhand;
        List<Entry> parsed = new ArrayList<>();
        for (String value : wire) {
            String[] parts = value.split("\\|", -1);
            if (parts.length < 4) continue;
            String id = parts[0] + ":" + parts[1];
            String key = "structure." + parts[0] + "." + parts[1].replace('/', '.');
            String name = net.minecraft.locale.Language.getInstance().has(key) ? Component.translatable(key).getString()
                    : parts[1].replace('_', ' ').replace('/', ' ');
            List<String> dimensions = parts[3].isEmpty() ? List.of() : List.of(parts[3].split(","));
            parsed.add(new Entry(id, parts[0], name, dimensions, searchIndex.document(name, id, parts[0])));
        }
        entries = List.copyOf(parsed);
        if (!entries.isEmpty()) { selected = 0; expanded.add(entries.getFirst().mod()); }
        rebuildRows("");
    }

    @Override protected void initContent() {
        setContentExtent(contentHeight());
        left = contentLeft(); top = contentTop();
        listWidth = Math.max(115, contentWidth() * 44 / 100);
        int footer = top + contentHeight() - 64;
        visibleRows = Math.max(1, (footer - top - 28) / 18);
        listBottom = top + 28 + visibleRows * 18;
        mapX = left + listWidth + 12;
        mapSize = Math.max(24, Math.min(contentWidth() - listWidth - 20, contentHeight() - 142));
        mapY = top + 22;
        if (search == null) {
            search = new EditBox(font, left, top, contentWidth(), 18, label("search"));
            search.setHint(label("search"));
            search.setMaxLength(128);
            search.setResponder(value -> { rebuildRows(value); scroll = 0; });
        }
        search.setX(left); search.setY(top); search.setWidth(contentWidth()); addRenderableWidget(search);
        int gap = 4, third = (contentWidth() - gap * 2) / 3;
        int col2 = left + third + gap, col3 = col2 + third + gap;
        int lastWidth = contentWidth() - (third + gap) * 2;
        adjacent = addRenderableWidget(new SelectableAE2Button(left, footer, contentWidth(), 18, label("adjacent"), b -> request(3)));
        dimensionButton = addRenderableWidget(new SelectableAE2Button(left, footer + 22, third, 18, label("dimension"), b -> {
            if (selected < 0 || entry().dimensions.isEmpty()) return;
            dimension = (dimension + 1) % entry().dimensions.size(); instanceId = 0; clearMap(); request(0); updateButtons();
        }));
        var rotate = addRenderableWidget(new SelectableAE2Button(col2, footer + 22, third, 18, label("rotate"), b -> {
            side = (side + 1) & 3; angle = side * 90.0F;
        }));
        rotate.setTooltip(Tooltip.create(label("map_help")));
        instanceButton = addRenderableWidget(new SelectableAE2Button(col3, footer + 22, lastWidth,
                18, instanceLabel(), b -> cycleInstance()));
        generate = addRenderableWidget(new SelectableAE2Button(left, footer + 44, third, 18, label("generate"), b -> request(1)));
        enter = addRenderableWidget(new SelectableAE2Button(col2, footer + 44, contentWidth() - third - gap,
                18, label("enter"), b -> request(2)));
        updateButtons();
    }
    private Component label(String suffix) { return Component.translatable("gui.useless_stretcher.dimension_capsule." + suffix); }
    private Entry entry() { return entries.get(selected); }
    private String target() { return selected < 0 || entry().dimensions.isEmpty() ? "" : entry().dimensions.get(dimension); }
    private void request(int action) {
        if (selected < 0 || target().isEmpty() || minecraft.getConnection() == null) return;
        CapsuleNetwork.request(entry().id, target(), action, side, offhand, instanceId);
    }
    private Component instanceLabel() {
        return state == null || state.instanceCount() == 0 ? label("instance.none")
                : Component.translatable("gui.useless_stretcher.dimension_capsule.instance",
                instanceId + 1, state.instanceCount());
    }
    private void cycleInstance() {
        if (state == null || state.instanceCount() < 2) return;
        instanceId = (instanceId + 1) % state.instanceCount();
        clearMap(); request(0); updateButtons();
    }
    @Override public void tick() {
        super.tick();
        if (++ticks % 20 == 0) request(0);
        if (searchRevision != searchIndex.revision()) { searchRevision = searchIndex.revision(); rebuildRows(search.getValue()); }
    }
    public void accept(CapsuleNetwork.State payload) {
        if (selected < 0 || !payload.id().equals(entry().id) || !payload.dimension().equals(target())) return;
        state = payload;
        instanceId = payload.instanceId();
        if (payload.pixels().length == 4096) {
            if (mapTexture != null) minecraft.getTextureManager().release(mapTexture);
            NativeImage image = new NativeImage(64, 64, false);
            for (int i = 0; i < 4096; i++) {
                int argb = payload.pixels()[i];
                image.setPixelRGBA(i % 64, i / 64, argb & 0xFF00FF00 | (argb >> 16 & 255) | (argb & 255) << 16);
            }
            mapTexture = minecraft.getTextureManager().register("capsule_map", new DynamicTexture(image));
        }
        updateButtons();
    }
    private void updateButtons() {
        if (generate == null) return;
        generate.active = selected >= 0 && !target().isEmpty() && (state == null || state.phase().equals("empty")
                || state.phase().equals("paused") || state.phase().equals("queue_full")
                || java.util.Set.of("failed", "below_world", "too_large", "planning_timeout", "planning_memory",
                        "allocation_conflict", "timeout", "source_environment_unavailable", "source_dimension_missing").contains(state.phase()));
        enter.active = state != null && state.phase().equals("ready");
        adjacent.active = state != null && state.phase().equals("ready");
        dimensionButton.active = selected >= 0 && entry().dimensions.size() > 1;
        instanceButton.active = state != null && state.instanceCount() > 1;
        instanceButton.setMessage(instanceLabel());
        dimensionButton.setTooltip(Tooltip.create(Component.literal(target())));
    }
    private void rebuildRows(String query) {
        rows.clear();
        var filter = LocalizedSearchIndex.Query.parse(query);
        String previous = "";
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (!filter.matches(e.search)) continue;
            if (!previous.equals(e.mod)) { rows.add(new Row(e.mod, -1)); previous = e.mod; }
            if (!filter.empty() || expanded.contains(e.mod)) rows.add(new Row(e.mod, i));
        }
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - visibleRows));
    }
    @Override protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partial) {
        StretcherScreenStyle.drawInset(g, left, top + 24, left + listWidth, listBottom + 2);
        for (int row = 0; row < visibleRows && scroll + row < rows.size(); row++) {
            Row r = rows.get(scroll + row); int y = top + 28 + row * 18;
            if (r.header()) {
                String modName = ModList.get().getModContainerById(r.mod).map(c -> c.getModInfo().getDisplayName()).orElse(r.mod);
                g.fill(left + 2, y, left + listWidth - 5, y + 17, StretcherScreenStyle.SLOT_COLOR);
                g.drawString(font, font.plainSubstrByWidth((expanded.contains(r.mod) ? "- " : "+ ") + modName, listWidth - 14),
                        left + 5, y + 4, StretcherScreenStyle.TEXT_COLOR, false);
            } else {
                if (selected == r.index) g.fill(left + 2, y, left + listWidth - 5, y + 17, 0x55689082);
                g.drawString(font, font.plainSubstrByWidth(entries.get(r.index).name, listWidth - 17), left + 11, y + 4,
                        StretcherScreenStyle.TEXT_COLOR, false);
            }
        }
        if (rows.size() > visibleRows) {
            int h = listBottom - top - 28, thumb = Math.max(12, h * visibleRows / rows.size());
            int y = top + 28 + (h - thumb) * scroll / Math.max(1, rows.size() - visibleRows);
            g.fill(left + listWidth - 4, y, left + listWidth - 2, y + thumb, StretcherScreenStyle.ACTIVE_COLOR);
        }
        g.fill(mapX, mapY, mapX + mapSize, mapY + mapSize, 0xFF171B20);
        if (mapTexture != null) {
            g.enableScissor(mapX, mapY, mapX + mapSize, mapY + mapSize);
            var pose = g.pose(); pose.pushPose();
            pose.translate(mapX + mapSize / 2.0F, mapY + mapSize / 2.0F, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(angle)); pose.scale(zoom, zoom, 1);
            g.blit(mapTexture, -mapSize / 2, -mapSize / 2, 0, 0, mapSize, mapSize, mapSize, mapSize);
            // Entrance marker follows the chosen edge on the actual map.
            int ex = side == 1 ? mapSize / 2 - 4 : side == 3 ? -mapSize / 2 : -2;
            int ez = side == 2 ? mapSize / 2 - 4 : side == 0 ? -mapSize / 2 : -2;
            g.fill(ex, ez, ex + 4, ez + 4, 0xFFFFDE55); pose.popPose(); g.disableScissor();
            g.enableScissor(floatingWindow().bodyLeft(), floatingWindow().bodyTop(), floatingWindow().bodyLeft() + floatingWindow().bodyWidth(),
                    floatingWindow().bodyTop() + floatingWindow().bodyHeight());
        }
        renderProgress(g);
        if (state != null && state.sx() > 0) g.drawString(font,
                Component.translatable("gui.useless_stretcher.dimension_capsule.size", state.sx(), state.sy(), state.sz()),
                mapX, mapY + mapSize + 24, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        if (state != null && state.instanceCount() > 0) g.drawString(font,
                Component.translatable("gui.useless_stretcher.dimension_capsule.origin", state.originX(), state.originZ()),
                mapX, mapY + mapSize + 36, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
        String dim = target();
        g.drawString(font, font.plainSubstrByWidth(dim, Math.max(1, contentWidth() - listWidth - 15)), mapX,
                mapY + mapSize + 48, StretcherScreenStyle.SUBTLE_TEXT_COLOR, false);
    }
    private void renderProgress(GuiGraphics g) {
        String phase = state == null ? "empty" : state.phase();
        int progress = state == null ? 0 : Mth.clamp(state.progress(), 0, 100);
        boolean working = switch (phase) {
            case "planning", "cleaning", "placing", "bedrock", "trimming", "generating" -> true;
            default -> false;
        };
        boolean planning = phase.equals("planning");
        int y = mapY + mapSize + 4;
        Component phaseLabel = label("phase." + phase);
        if (!net.minecraft.locale.Language.getInstance().has("gui.useless_stretcher.dimension_capsule.phase." + phase))
            phaseLabel = label("phase.failed");
        String suffix = working ? (planning ? " " : "  " + progress + "% ") + ".".repeat(1 + ticks / 8 % 3)
                : phase.equals("ready") ? "  100%" : phase.equals("paused") ? "  " + progress + "%" : "";
        int suffixWidth = font.width(suffix);
        g.drawString(font, font.plainSubstrByWidth(phaseLabel.getString(), Math.max(1, mapSize - suffixWidth - 2)),
                mapX, y, StretcherScreenStyle.TEXT_COLOR, false);
        g.drawString(font, suffix, mapX + mapSize - suffixWidth, y, StretcherScreenStyle.TEXT_COLOR, false);
        // Reserve this row in every phase so the layout stays still while the task advances.
        int barY = y + 11, inside = Math.max(1, mapSize - 2);
        g.fill(mapX, barY, mapX + mapSize, barY + 6, 0xFF555B70);
        g.fill(mapX + 1, barY + 1, mapX + mapSize - 1, barY + 5, 0xFF222733);
        if (planning) {
            int length = Math.max(3, inside / 4);
            int travel = Math.max(1, inside - length);
            int phaseTick = ticks % 80;
            int offset = travel * Math.min(phaseTick, 80 - phaseTick) / 40;
            g.fill(mapX + 1 + offset, barY + 1, mapX + 1 + offset + length, barY + 5, 0xFF7098B5);
        } else if (progress > 0) {
            g.fill(mapX + 1, barY + 1, mapX + 1 + inside * progress / 100, barY + 5, 0xFF7098B5);
        }
    }
    @Override protected void renderContentTooltip(GuiGraphics g, int x, int y, float partial) {
        if (x >= left && x < left + listWidth && y >= top + 28 && y < listBottom) {
            int i = scroll + (y - top - 28) / 18;
            if (i < rows.size() && !rows.get(i).header())
                g.renderTooltip(font, Component.literal(entries.get(rows.get(i).index).id), x, y);
        }
        if (x >= mapX && x < mapX + mapSize && y >= mapY && y < mapY + mapSize)
            g.renderTooltip(font, font.split(label("map_help"), 180), x, y);
    }
    @Override protected boolean clickContent(double x, double y, int button) {
        if (super.clickContent(x, y, button)) return true;
        if (button == 0 && x >= mapX && x < mapX + mapSize && y >= mapY && y < mapY + mapSize) { rotating = true; return true; }
        if (button != 0 || x < left || x >= left + listWidth || y < top + 28 || y >= listBottom) return false;
        int i = scroll + (int)(y - top - 28) / 18;
        if (i >= rows.size()) return true;
        Row row = rows.get(i);
        if (row.header()) { if (!expanded.remove(row.mod)) expanded.add(row.mod); rebuildRows(search.getValue()); }
        else { selected = row.index; dimension = 0; instanceId = 0; clearMap(); request(0); updateButtons(); }
        return true;
    }
    @Override protected boolean dragContent(double x, double y, int button, double dx, double dy) {
        if (!rotating || button != 0) return false;
        angle += (float)dx; side = Math.floorMod(Math.round(-angle / 90.0F), 4); return true;
    }
    @Override protected boolean releaseContent(double x, double y, int button) { rotating = false; return false; }
    @Override protected boolean scrollContent(double x, double y, double dx, double dy) {
        if (x >= mapX && x < mapX + mapSize && y >= mapY && y < mapY + mapSize) { zoom = Mth.clamp(zoom + (float)dy * 0.1F, 0.5F, 4); return true; }
        if (x < left + listWidth) { scroll = Mth.clamp(scroll - (int)Math.signum(dy) * 3, 0, Math.max(0, rows.size() - visibleRows)); return true; }
        return super.scrollContent(x, y, dx, dy);
    }
    private void clearMap() { state = null; if (mapTexture != null && minecraft != null) minecraft.getTextureManager().release(mapTexture); mapTexture = null; }
    @Override public void removed() { clearMap(); super.removed(); }
}
