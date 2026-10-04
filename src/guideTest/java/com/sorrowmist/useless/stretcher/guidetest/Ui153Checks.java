package com.sorrowmist.useless.stretcher.guidetest;

import appeng.api.stacks.AEItemKey;
import com.sorrowmist.useless.core.component.UComponents;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.client.WondrousStaffApotheosisScreen;
import com.sorrowmist.useless.stretcher.content.apotheosis.ApotheosisTableData;
import com.sorrowmist.useless.stretcher.network.ApotheosisNetwork;
import com.sorrowmist.useless.stretcher.network.Network;
import com.sorrowmist.useless.stretcher.screen.MyriadPatternRepositoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Real mouse events catch the repository's former swallowed-widget clicks. */
final class Ui153Checks {
    private static int stage, frames;

    static boolean tick(Minecraft client) throws Exception {
        if (frames == 0) {
            if (stage < 2) {
                var screen = new MyriadPatternRepositoryScreen(null, BlockPos.ZERO, InteractionHand.MAIN_HAND);
                client.setScreen(screen);
                List<AEItemKey> patterns = new ArrayList<>();
                var fixture = NbtIo.readCompressed(client.gameDirectory.toPath().toAbsolutePath().normalize().resolveSibling("regression-server")
                        .resolve("omniversal-ui-fixture.nbt"), NbtAccounter.unlimitedHeap());
                var entries = fixture.getList("patterns", 10);
                var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
                for (int i = 0; i < entries.size(); i++) {
                    var stack = ItemStack.parseOptional(registries, entries.getCompound(i));
                    check(stack.is(com.sorrowmist.useless.init.ModItems.OMNIVERSAL_PATTERN.get()), "fixture is actual fetched omniversal item");
                    patterns.add(AEItemKey.of(stack));
                }
                check(patterns.size() == 64, "real fetch fixture fills a whole page");
                screen.onPage(new Network.PatternPagePayload(BlockPos.ZERO, 0, 2, true, true, false, patterns));
                var stack = patterns.getFirst().toStack(1);
                var metadata = stack.get(UComponents.OMNIVERSAL_PATTERN_DATA.get());
                var tooltip = stack.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.ADVANCED)
                        .stream().map(net.minecraft.network.chat.Component::getString).toList();
                check(tooltip.stream().anyMatch(line -> line.contains(metadata.recipeId().toString())), "native tooltip includes actual recipe ID");
                check(tooltip.stream().anyMatch(line -> line.contains(metadata.displayMold().orElseThrow().getDisplayName().getString())),
                        "native tooltip includes real required mold");
                var outputMethod = screen.getClass().getDeclaredMethod("output", int.class);
                outputMethod.setAccessible(true);
                var output = (ItemStack) outputMethod.invoke(screen, 0);
                check(!output.isEmpty() && !output.is(stack.getItem()), "Shift display resolves actual recipe output");
                LogUtils.getLogger().info("UI actual omniversal tooltip: {}", tooltip);
                if (stage == 1) screen.resize(client, 320, 240);
                WindowChecks.assertLayout(screen, "pattern grid " + stage);
                int x = number(screen, "gridLeft") + 8, y = number(screen, "gridTop") + 8;
                check(screen.mouseClicked(x, y, 0), "left starts freehand selection");
                screen.mouseDragged(x + 54, y, 0, 54, 0);
                screen.mouseReleased(x + 54, y, 0);
                check(selection(screen).size() == 4, "fast left drag selects all crossed cells");
                screen.mouseClicked(x, y, 0);
                screen.mouseReleased(x, y, 0);
                check(selection(screen).size() == 4, "left drag never toggles selected cells off");
                screen.mouseClicked(x + 18, y, 1);
                screen.mouseDragged(x + 36, y, 1, 18, 0);
                screen.mouseReleased(x + 36, y, 1);
                check(selection(screen).size() == 2, "right drag removes selected cells");
                click(screen, (AbstractButton) field(screen, "selectButton"));
                check(selection(screen).size() == 64, "actual click reaches select-page widget");
                click(screen, (AbstractButton) field(screen, "selectButton"));
                check(selection(screen).isEmpty(), "actual click reaches clear-page widget");
                screen.mouseClicked(x, y, 0);
                screen.mouseDragged(x + 54, y + 36, 0, 54, 36);
                screen.mouseReleased(x + 54, y + 36, 0);
                check(((AbstractButton) field(screen, "deleteButton")).active, "delete enables after selection");
                if (stage == 1) {
                    for (int i = 0; i < 30; i++) screen.mouseScrolled(x, y, 0, -1);
                    check(number(screen, "scrollRow") + number(screen, "rows") >=
                            (64 + number(screen, "columns") - 1) / number(screen, "columns"), "all 64 entries reachable");
                    var searchBox = (net.minecraft.client.gui.components.EditBox) field(screen, "search");
                    searchBox.setValue("tieding");
                    int query = number(screen, "queryId");
                    check(!screen.matches(new Network.PatternPagePayload(BlockPos.ZERO, 0, 1, false, true, false,
                            List.of(), query - 1, "")), "late responses cannot overwrite current search");
                    screen.onPage(new Network.PatternPagePayload(BlockPos.ZERO, 0, 1, true, true, false,
                            patterns.subList(0, 8), query, ""));
                    check(searchBox.getValue().equals("tieding"), "search retained through page rebuild");
                    x = number(screen, "gridLeft") + 8; y = number(screen, "gridTop") + 8;
                    screen.mouseClicked(x, y, 0);
                    screen.mouseDragged(x + 54, y, 0, 54, 0);
                    screen.mouseReleased(x + 54, y, 0);
                    check(selection(screen).size() == 4, "left paint remains intact after search");
                    screen.mouseClicked(x, y, 1);
                    screen.mouseReleased(x, y, 1);
                    check(selection(screen).size() == 3, "right erase remains intact after search");
                    check(((AbstractButton) field(screen, "deleteButton")).active, "filtered selection is deletable");
                    WindowChecks.assertLayout(screen, "filtered repository");
                }
            } else {
                var screen = new WondrousStaffApotheosisScreen(null, InteractionHand.MAIN_HAND);
                client.setScreen(screen);
                screen.acceptState(new ApotheosisNetwork.SettingsPayload(false, true, "pinnacle",
                        new ApotheosisTableData.Boost(42, 35, 28, 7)));
                screen.resize(client, 320, 240);
                check(number(screen, "eterna") == 42 && number(screen, "clues") == 7,
                        "resize retains acknowledged preset");
                check(field(screen, "selectedTier").equals("pinnacle"), "tier acknowledgment displayed");
                WindowChecks.assertLayout(screen, "apotheosis retained preset");
            }
            client.setScreen(new Preview(client.screen));
            frames = 12;
            return false;
        }
        if (--frames > 0) return false;
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(client.gameDirectory.toPath().resolve("screenshots/ui153-" + stage + ".png"));
        }
        if (++stage == 3) {
            LogUtils.getLogger().info("UI 1.5.3 CHECKS PASSED: actual fetched omniversal patterns, native recipe/mold tooltip, Shift outputs, AE grid, freehand selection, buttons, narrow reachability, Apotheosis retained state");
            return true;
        }
        return false;
    }

    private static void click(Screen screen, AbstractButton button) {
        check(screen.mouseClicked(button.getX() + 3, button.getY() + 3, 0), "widget receives click");
        screen.mouseReleased(button.getX() + 3, button.getY() + 3, 0);
    }
    private static Set<?> selection(Object screen) throws Exception { return (Set<?>) field(screen, "selected"); }
    private static int number(Object screen, String key) throws Exception { return (int) field(screen, key); }
    private static Object field(Object screen, String key) throws Exception {
        var field = screen.getClass().getDeclaredField(key);
        field.setAccessible(true);
        return field.get(screen);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final class Preview extends Screen {
        private final Screen target;
        private Preview(Screen target) { super(net.minecraft.network.chat.Component.empty()); this.target = target; }
        @Override public void render(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, float partial) {
            graphics.fill(0, 0, width, height, 0xFF181B22);
            target.renderWithTooltip(graphics, -10000, -10000, partial);
        }
        @Override public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, float partial) { }
    }
}
