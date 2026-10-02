package com.sorrowmist.useless.stretcher.guidetest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.client.EntityAccelerationConfigScreen;
import com.sorrowmist.useless.stretcher.client.ReclaimerScreen;
import com.sorrowmist.useless.stretcher.client.StretcherConfigScreen;
import com.sorrowmist.useless.stretcher.client.WondrousStaffConfigScreen;
import com.sorrowmist.useless.stretcher.client.render.WondrousStaffAccelerationRenderer;
import com.sorrowmist.useless.stretcher.network.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

final class Ui152Checks {
    private static int stage, frames;
    private static ReclaimerScreen reclaimer;
    private static final UUID ALPHA = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ZETA = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final List<Network.ReclaimerEntry> ENTRIES = List.of(
            entry(ZETA, "Zeta_ID", false, "熔炉 x64"), entry(ALPHA, "Alpha_ID", true, "主工厂范围 x16"),
            entry(ALPHA, "Alpha_ID", false, "高炉 x1024"));
    private static final String[] NAMES = {"config-category", "reclaimer-players", "reclaimer-types", "reclaimer-permanent",
            "reclaimer-empty", "reclaimer-ranges", "reclaimer-return", "reclaimer-personal", "staff-config",
            "entity-settings", "progress-colors", "range-settings", "range-edit", "range-history",
            "summon-settings", "reclaim-confirm", "staff-expanded"};

    static boolean tick(Minecraft client) throws Exception {
        if (frames == 0) {
            switch (stage) {
                case 0 -> client.setScreen(new StretcherConfigScreen(null));
                case 1 -> {
                    reclaimer = new ReclaimerScreen(null, ENTRIES, false);
                    client.setScreen(reclaimer);
                    check(buttons(reclaimer).getFirst().getMessage().getString().startsWith("Alpha_ID"), "player ID sort");
                }
                case 2 -> {
                    buttons(reclaimer).getFirst().onPress();
                    check(buttons(reclaimer).getFirst().getMessage().getString().startsWith(tr("permanent")), "second-level categories");
                }
                case 3 -> {
                    buttons(reclaimer).getFirst().onPress();
                    var targets = (List<?>) field(reclaimer, "targets");
                    check(targets.size() == 1 && ((Network.ReclaimerEntry) targets.getFirst()).owner().equals(ALPHA)
                            && !((Network.ReclaimerEntry) targets.getFirst()).range(), "filters by player and permanent kind");
                }
                case 4 -> {
                    reclaimer.update(ENTRIES.stream().filter(e -> !e.owner().equals(ALPHA) || e.range()).toList());
                    check(((List<?>) field(reclaimer, "targets")).isEmpty()
                            && field(reclaimer, "page").toString().equals("TARGETS"), "removal retains empty current category");
                }
                case 5 -> {
                    reclaimer.onClose();
                    buttons(reclaimer).get(1).onPress();
                    var targets = (List<?>) field(reclaimer, "targets");
                    check(targets.size() == 1 && ((Network.ReclaimerEntry) targets.getFirst()).range(), "range category independent");
                }
                case 6 -> {
                    reclaimer.onClose();
                    reclaimer.onClose();
                    check(field(reclaimer, "page").toString().equals("PLAYERS"), "back navigation returns to players");
                }
                case 7 -> {
                    client.setScreen(new ReclaimerScreen(null, ENTRIES.stream().filter(e -> e.owner().equals(ALPHA)).toList(), true));
                    check(field(client.screen, "page").toString().equals("TYPES"), "personal screen skips unrelated owners");
                }
                case 8 -> client.setScreen(new WondrousStaffConfigScreen(InteractionHand.MAIN_HAND));
                case 9 -> {
                    client.setScreen(new EntityAccelerationConfigScreen(null, InteractionHand.MAIN_HAND));
                    check(buttons(client.screen).size() == 18, "two modes and fifteen timer multipliers");
                    check(buttons(client.screen).subList(0, 17).stream().allMatch(b -> b.getTooltip() != null), "all entity settings have hover help");
                }
                case 10 -> client.setScreen(new ProgressPreview());
                case 11 -> client.setScreen(new com.sorrowmist.useless.stretcher.client.RangeAccelerationConfigScreen(null, InteractionHand.MAIN_HAND));
                case 12 -> client.setScreen(new com.sorrowmist.useless.stretcher.client.RangeAccelerationHistoryEditScreen(null, rangeSummary()));
                case 13 -> {
                    var history = new com.sorrowmist.useless.stretcher.client.RangeAccelerationHistoryScreen(null);
                    var requested = history.getClass().getDeclaredField("requested");
                    requested.setAccessible(true);
                    requested.setBoolean(history, true);
                    client.setScreen(history);
                    history.onHistory(List.of(rangeSummary()));
                }
                case 14 -> client.setScreen(new com.sorrowmist.useless.stretcher.client.WondrousStaffSummonScreen(null, InteractionHand.MAIN_HAND));
                case 15 -> client.setScreen(new com.sorrowmist.useless.stretcher.client.gui.StretcherConfirmScreen(
                        ignored -> {}, Component.literal("回收确认"), Component.literal("测试机器，取消不会改变加速")));
                case 16 -> {
                    var staff = new WondrousStaffConfigScreen(InteractionHand.MAIN_HAND);
                    client.setScreen(staff);
                    for (var child : staff.children()) {
                        if (child instanceof net.minecraft.client.gui.components.PlainTextButton fold) { fold.onPress(); break; }
                    }
                }
            }
            WindowChecks.assertLayout(client.screen, NAMES[stage]);
            frames = 20;
            return false;
        }
        if (--frames != 0) return false;
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(client.gameDirectory.toPath().resolve("screenshots").resolve(NAMES[stage] + ".png"));
        }
        if (++stage == NAMES.length) {
            LogUtils.getLogger().info("UI 1.5.2 CHECKS PASSED: ID sort, categories, filtering, empty update, back, personal, tooltips, bounds and progress rendered");
            return true;
        }
        return false;
    }

    private static String tr(String key) { return Component.translatable("gui.useless_stretcher.reclaimer." + key).getString(); }
    private static com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData.Summary rangeSummary() {
        return new com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSavedData.Summary(UUID.randomUUID(),
                ResourceLocation.parse("minecraft:overworld"), new BlockPos(20, 64, 100), 0, true, 16, 3, 3, 3, 0, 0, 0, "测试工厂");
    }
    private static List<AbstractButton> buttons(Screen screen) {
        return screen.children().stream().filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast).toList();
    }
    private static Object field(Object value, String name) throws Exception {
        var field = value.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(value);
    }
    private static Network.ReclaimerEntry entry(UUID owner, String name, boolean range, String label) {
        return new Network.ReclaimerEntry(range, UUID.randomUUID(), owner, name, ResourceLocation.parse("minecraft:overworld"),
                new BlockPos(20, 64, 100), label);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static final class ProgressPreview extends Screen {
        private final Method draw;
        ProgressPreview() throws Exception {
            super(Component.literal("Progress preview"));
            draw = WondrousStaffAccelerationRenderer.class.getDeclaredMethod("drawProgressBar", PoseStack.class,
                    MultiBufferSource.class, float.class, float.class, float.class, float.class);
            draw.setAccessible(true);
        }
        @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
            graphics.fill(0, 0, width, height, 0xFF334455);
            graphics.drawString(font, "Progress: cyan / gold; dark track only on empty section", 10, 12, 0xFFFFFFFF);
            for (int i = 0; i < 4; i++) {
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(width / 2.0, 22 + i * 46, 100);
                pose.scale(3, 3, 3);
                try {
                    draw.invoke(null, pose, graphics.bufferSource(), i / 3.0F, i == 3 ? 1F : 0.2F,
                            i == 3 ? 0.65F : 0.85F, i == 3 ? 0.05F : 1F);
                } catch (Exception failure) { throw new RuntimeException(failure); }
                graphics.flush();
                pose.popPose();
            }
        }
        @Override public void renderBackground(GuiGraphics g, int x, int y, float p) {}
    }
}
