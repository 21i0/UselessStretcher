package com.sorrowmist.useless.stretcher.screen;

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
    private static final int LIST_TOP = 32;
    private static final int LIST_BOTTOM = 208;

    private final BlockPos pos;
    private Map<String, List<MoldCatalog.MoldEntry>> catalog = Map.of();
    private List<MoldCatalog.MoldEntry> flatMolds = List.of();
    private final Set<ResourceLocation> enabled = new LinkedHashSet<>();
    private final Set<ResourceLocation> patternMolds = new LinkedHashSet<>();
    private final Set<String> expandedMods = new LinkedHashSet<>();
    private ResourceLocation lastToggled;
    private ResourceLocation lastPatternToggled;
    private int patternsCount;
    private boolean aeBound;
    private int scroll;
    private EditBox search;

    public OmniversalMyriadScreen(OmniversalMyriadMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.pos = menu.getPos();
        this.imageWidth = 256;
        this.imageHeight = 224;
    }

    public boolean matches(BlockPos target) {
        return pos.equals(target);
    }

    public void onState(List<String> enabledMolds, List<String> patternMolds, int patternCount, boolean aeBound) {
        enabled.clear();
        for (String id : enabledMolds) {
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed != null) enabled.add(parsed);
        }
        this.patternMolds.clear();
        for (String id : patternMolds) {
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed != null) this.patternMolds.add(parsed);
        }
        patternsCount = patternCount;
        this.aeBound = aeBound;
    }

    @Override
    protected void init() {
        super.init();
        this.catalog = MoldCatalog.getAllMolds(Minecraft.getInstance().level);
        List<MoldCatalog.MoldEntry> flat = new ArrayList<>();
        for (List<MoldCatalog.MoldEntry> list : catalog.values()) flat.addAll(list);
        this.flatMolds = List.copyOf(flat);

        this.search = new EditBox(this.font, leftPos + 8, topPos + 8, 158, 16, Component.empty());
        this.search.setMaxLength(64);
        this.search.setHint(Component.translatable("gui.useless_stretcher.search_hint"));
        addRenderableWidget(search);
        Network.requestState(pos);
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
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xC0101010);
        graphics.fill(leftPos + 7, topPos + 7, leftPos + imageWidth - 7, topPos + imageHeight - 7, 0xFF15151F);

        // Clear-all button.
        int clearX = leftPos + 174;
        int clearY = topPos + 8;
        boolean hoverClear = mouseX >= clearX && mouseX <= clearX + 74 && mouseY >= clearY && mouseY <= clearY + 16;
        graphics.fill(clearX, clearY, clearX + 74, clearY + 16, hoverClear ? 0xFF8B3A3A : 0xFF4A2020);
        graphics.drawString(this.font, Component.translatable("gui.useless_stretcher.clear"),
                clearX + 12, clearY + 4, 0xFFFFFF, false);

        graphics.drawString(this.font,
                Component.translatable("gui.useless_stretcher.patterns", patternsCount),
                leftPos + 8, topPos + LIST_BOTTOM + 2, 0x888888, false);
        graphics.drawString(this.font,
                Component.translatable(aeBound ? "gui.useless_stretcher.ae_bound" : "gui.useless_stretcher.ae_unbound"),
                leftPos + 140, topPos + LIST_BOTTOM + 2, aeBound ? 0x55FF55 : 0x888888, false);

        List<Row> rows = visibleRows();
        int maxScroll = Math.max(0, rows.size() * ROW_HEIGHT - (LIST_BOTTOM - LIST_TOP));
        if (scroll > maxScroll) scroll = maxScroll;

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
    }

    private void renderHeader(GuiGraphics graphics, HeaderRow header, int y, int mouseX, int mouseY) {
        boolean expanded = expandedMods.contains(header.sourceId());
        int color = 0xE0E0E0;
        graphics.fill(leftPos + 8, y, leftPos + 248, y + ROW_HEIGHT - 1, 0xFF303048);
        graphics.drawString(this.font, (expanded ? "▼ " : "▶ ") + header.sourceId(),
                leftPos + 12, y + 5, color, false);

        String label = allEnabled(header.entries()) ? "☑" : "☐";
        graphics.drawString(this.font, label, leftPos + 200, y + 5, 0x55FF55, false);

        int px = leftPos + 216;
        int py = y + 2;
        boolean allFetched = allPatternsFetched(header.entries());
        boolean hoverPat = mouseX >= px && mouseX <= px + 30 && mouseY >= py && mouseY <= py + 14;
        graphics.fill(px, py, px + 30, py + 14, allFetched ? 0xFF7A5A10 : (hoverPat ? 0xFF2A4A6A : 0xFF1F3240));
        graphics.drawString(this.font, allFetched ? "样✓" : "样", px + 4, py + 3,
                allFetched ? 0xFFFFD75E : 0x66B2FF, false);
    }

    private void renderMold(GuiGraphics graphics, MoldRow mold, int y, int mouseX, int mouseY) {
        boolean on = enabled.contains(mold.entry().id());
        boolean fetched = patternMolds.contains(mold.entry().id());
        String query = search == null ? "" : search.getValue().trim();
        boolean highlighted = !query.isEmpty() && matches(mold.entry(), query);
        if (highlighted) {
            graphics.fill(leftPos + 8, y, leftPos + 248, y + ROW_HEIGHT - 1, 0xFF6A5A10);
        } else {
            graphics.fill(leftPos + 8, y, leftPos + 248, y + ROW_HEIGHT - 1, 0x28FFFFFF);
        }
        graphics.drawString(this.font, (on ? "☑ " : "☐ ") + mold.entry().displayName(),
                leftPos + 20, y + 5, highlighted ? 0xFFFFD75E : (on ? 0xFFFFFF : 0xA0A0A0), false);

        int px = leftPos + 210;
        int py = y + 2;
        boolean hoverPat = mouseX >= px && mouseX <= px + 36 && mouseY >= py && mouseY <= py + 14;
        graphics.fill(px, py, px + 36, py + 14, fetched ? 0xFF7A5A10 : (hoverPat ? 0xFF2A4A6A : 0xFF1F3240));
        graphics.drawString(this.font, fetched ? "样✓" : "样", px + 5, py + 3,
                fetched ? 0xFFFFD75E : 0x66B2FF, false);
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
        return rows;
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
            int clearX = leftPos + 174;
            int clearY = topPos + 8;
            if (mouseX >= clearX && mouseX <= clearX + 74 && mouseY >= clearY && mouseY <= clearY + 16) {
                patternMolds.clear();
                Network.clearPatterns(pos);
                return true;
            }

            List<Row> rows = visibleRows();
            int index = 0;
            for (Row row : rows) {
                int y = topPos + LIST_TOP + index * ROW_HEIGHT - scroll;
                if (mouseX >= leftPos + 8 && mouseX <= leftPos + 248
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
            boolean hitPattern = mouseX >= leftPos + 216;
            boolean hitToggle = mouseX >= leftPos + 196 && !hitPattern;
            if (hitPattern) {
                toggleModPatterns(header);
            } else if (hitToggle) {
                boolean all = allEnabled(header.entries());
                for (MoldCatalog.MoldEntry entry : header.entries()) {
                    if (all) enabled.remove(entry.id());
                    else enabled.add(entry.id());
                }
                pushEnabled();
            } else {
                if (!expandedMods.remove(header.sourceId())) expandedMods.add(header.sourceId());
            }
        } else if (row instanceof MoldRow mold) {
            boolean hitPattern = mouseX >= leftPos + 210;
            if (hitPattern) {
                if (Screen.hasShiftDown() && lastPatternToggled != null) {
                    togglePatternRange(lastPatternToggled, mold.entry().id());
                } else {
                    Network.togglePatterns(pos, mold.entry().id().toString());
                    lastPatternToggled = mold.entry().id();
                }
                return;
            }

            boolean ctrl = Screen.hasControlDown();
            boolean shift = Screen.hasShiftDown();
            boolean turnOn = !enabled.contains(mold.entry().id());

            if (shift && lastToggled != null) {
                toggleRange(lastToggled, mold.entry().id(), turnOn);
            } else {
                if (turnOn) enabled.add(mold.entry().id());
                else enabled.remove(mold.entry().id());
                if (!ctrl) lastToggled = mold.entry().id();
            }
            pushEnabled();
        }
    }

    private void toggleRange(ResourceLocation anchor, ResourceLocation target, boolean turnOn) {
        int a = -1;
        int b = -1;
        for (int i = 0; i < flatMolds.size(); i++) {
            ResourceLocation id = flatMolds.get(i).id();
            if (id.equals(anchor)) a = i;
            if (id.equals(target)) b = i;
        }
        if (a < 0 || b < 0) return;
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        for (int i = lo; i <= hi; i++) {
            ResourceLocation id = flatMolds.get(i).id();
            if (turnOn) enabled.add(id);
            else enabled.remove(id);
        }
    }

    private void pushEnabled() {
        List<String> ids = enabled.stream().map(ResourceLocation::toString).toList();
        Network.setEnabled(pos, ids);
    }

    private void toggleModPatterns(HeaderRow header) {
        List<String> ids = header.entries().stream().map(e -> e.id().toString()).toList();
        if (ids.isEmpty()) return;
        Network.toggleModPatterns(pos, ids);
    }

    private void togglePatternRange(ResourceLocation anchor, ResourceLocation target) {
        int a = -1;
        int b = -1;
        for (int i = 0; i < flatMolds.size(); i++) {
            ResourceLocation id = flatMolds.get(i).id();
            if (id.equals(anchor)) a = i;
            if (id.equals(target)) b = i;
        }
        if (a < 0 || b < 0) return;
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        List<String> ids = new ArrayList<>();
        for (int i = lo; i <= hi; i++) {
            ids.add(flatMolds.get(i).id().toString());
        }
        if (!ids.isEmpty()) Network.toggleModPatterns(pos, ids);
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
