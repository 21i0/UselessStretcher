package com.sorrowmist.useless.stretcher.upstreamcheck;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.client.*;
import com.sorrowmist.useless.stretcher.client.gui.AE2RangeSlider;
import com.sorrowmist.useless.stretcher.client.gui.FloatingWindow;
import com.sorrowmist.useless.stretcher.network.CapsuleNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/** Offline real-widget/GL preview; never replaces a connected player's screen. */
@Mod.EventBusSubscriber(modid = "useless_stretcher", value = Dist.CLIENT)
public final class ForgeClientUpstreamChecks {
    private static StaffScreen staff;
    private static FloatingWindow shared;
    private static DimensionCapsuleScreen capsule;
    private static Object texture;
    private static int ticks, stage, wait, frames;
    private static boolean done;
    private static final int[] PIXELS = new int[4096];

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("stretcher.upstreamClientCheck") || done || event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        try {
            check(++ticks < 1800, "client checks timeout");
            if (client.getOverlay() != null || client.screen == null) return;
            if (wait > 0 && --wait > 0) return;
            Files.createDirectories(client.gameDirectory.toPath().resolve("screenshots"));
            if (stage == 0) {
                client.options.guiScale().set(1); client.resizeDisplay();
                staff = new WondrousStaffConfigScreen(InteractionHand.OFF_HAND); preview(client, staff, false);
                shared = staff.floatingWindow(); shared.reset();
                shared.setPreference("sidebarCollapsed", "false"); staff.resize(client, 640, 400);
                var fold = staff.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                        .filter(w -> w.getMessage().equals(Component.translatable("gui.useless_stretcher.staff_summon.expand"))
                                || w.getMessage().equals(Component.translatable("gui.useless_stretcher.staff_summon.collapse"))).findFirst().orElseThrow();
                if (fold.getMessage().equals(Component.translatable("gui.useless_stretcher.staff_summon.collapse"))) {
                    click(fold);
                    fold = staff.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                            .filter(w -> w.getMessage().equals(Component.translatable("gui.useless_stretcher.staff_summon.expand"))).findFirst().orElseThrow();
                }
                click(fold);
                check(shared.preference("extrasCollapsed", "true").equals("false"), "expanded extras preference");
            } else if (stage <= 7) {
                var tab = StaffScreen.Tab.values()[stage];
                if (tab == StaffScreen.Tab.APOTHEOSIS) staff = new WondrousStaffApotheosisScreen(staff, InteractionHand.OFF_HAND);
                else { staff.navigate(tab); staff = (StaffScreen) client.screen; }
                preview(client, staff, false);
                check(staff.staffTab() == tab && staff.floatingWindow() == shared, "tab shares original window: " + tab);
                if (tab == StaffScreen.Tab.FLUIDS) {
                    var sliders = staff.children().stream().filter(AE2RangeSlider.class::isInstance).map(AE2RangeSlider.class::cast).toList();
                    check(sliders.size() == 2, "radius/rate sliders");
                    for (var slider : sliders) {
                        double x = slider.getX() + 6, y = slider.getY() + 8;
                        check(staff.mouseClicked(x, y, 0), "slider press");
                        staff.mouseDragged(slider.getX() + slider.getWidth() - 4, y, 0, slider.getWidth() - 10, 0);
                        staff.mouseReleased(x, y, 0);
                    }
                    check(sliders.get(0).getMessage().getString().contains("16") && sliders.get(1).getMessage().getString().contains("64"), "slider drag reaches limits");
                }
            } else if (stage == 8) {
                staff.navigate(StaffScreen.Tab.SUMMON); staff = (StaffScreen) client.screen; preview(client, staff, false);
                var search = staff.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                search.setValue("minecraft");
                staff.navigate(StaffScreen.Tab.MAIN); staff = (StaffScreen) client.screen; preview(client, staff, false);
                staff.navigate(StaffScreen.Tab.SUMMON); staff = (StaffScreen) client.screen; preview(client, staff, false);
                check(staff.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                        .anyMatch(box -> box.getValue().equals("minecraft")), "search survives cached tab navigation");
                click((AbstractWidget) staff.children().get(0));
                check(staff.sidebarCollapsed(), "sidebar collapses"); shared.save();
                var path = FloatingWindow.class.getDeclaredField("loadedPath"); path.setAccessible(true); path.set(null, null);
                StaffScreen.open(InteractionHand.OFF_HAND); staff = (StaffScreen) client.screen; preview(client, staff, false);
                check(staff.staffTab() == StaffScreen.Tab.SUMMON && staff.sidebarCollapsed(), "disk reload restores active tab and sidebar");
                check(staff.floatingWindow().preference("extrasCollapsed", "true").equals("false"), "disk reload restores expanded extras");
                staff.keyPressed(GLFW.GLFW_KEY_TAB, 0, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT);
                staff = (StaffScreen) client.screen; preview(client, staff, true);
                check(staff.staffTab() == StaffScreen.Tab.FLUIDS, "Ctrl Shift Tab navigates backwards");
                check(staff.floatingWindow().left() + staff.floatingWindow().width() <= 320, "narrow viewport clamps shared window");
            } else {
                if (capsule == null) {
                    capsule = new DimensionCapsuleScreen(List.of("ae2|meteorite||useless_stretcher:capsule_overworld"), false);
                    client.setScreen(capsule); Arrays.fill(PIXELS, 0xFF667788);
                }
                PIXELS[frames % PIXELS.length] = 0xFF88AA55;
                String phase = new String[]{"planning", "cleaning", "placing", "bedrock", "trimming", "ready"}[Math.min(5, frames / 20)];
                capsule.accept(new CapsuleNetwork.State("ae2:meteorite", "useless_stretcher:capsule_overworld", 0, 1, 0, 0,
                        phase, Math.min(100, frames), PIXELS.clone(), 144, 17, 144));
                if (texture == null) texture = field(capsule, "mapImage");
                check(texture == field(capsule, "mapImage"), "changing map pixels reuse one GPU texture");
                var graphics = new GuiGraphics(client, client.renderBuffers().bufferSource());
                capsule.render(graphics, -1, -1, 0); graphics.flush();
                var clips = (Deque<?>) field(field(graphics, "scissorStack"), "stack");
                check(clips.isEmpty() && !GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), "no clip leaks into framebuffer clear");
                graphics.enableScissor(3, 5, 600, 380);
                capsule.render(graphics, -1, -1, 0); graphics.flush();
                check(clips.size() == 1, "embedded GUI retains exactly its own clip"); graphics.disableScissor();
                if (++frames >= 120) {
                    capsule.removed(); check(field(capsule, "mapTexture") == null && field(capsule, "mapImage") == null, "closing releases texture");
                    ForgeAppleUiChecks.validate(client);
                    LogUtils.getLogger().info("FORGE CLIENT UPSTREAM CHECKS PASSED: eight real staff pages, sliders, search retention, persisted tabs/sidebar/extras, Ctrl Tab, narrow viewport and 240 balanced capsule renders");
                    done = true; client.stop();
                }
                return;
            }
            for (var child : staff.children()) if (child instanceof AbstractWidget widget && widget.visible) {
                var window = staff.floatingWindow();
                check(widget.getWidth() > 0 && widget.getX() >= window.bodyLeft()
                        && widget.getX() + widget.getWidth() <= window.bodyLeft() + window.bodyWidth(), "widget within horizontal bounds");
                check(!widget.getMessage().getString().contains("gui.useless_stretcher."), "translated staff labels");
            }
            try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                image.writeToFile(client.gameDirectory.toPath().resolve("screenshots/staff-" + stage + ".png"));
            }
            stage++; wait = 12;
        } catch (Throwable failure) {
            done = true; LogUtils.getLogger().error("FORGE CLIENT UPSTREAM CHECKS FAILED", failure); client.stop();
        }
    }

    private static void preview(Minecraft client, StaffScreen screen, boolean narrow) {
        client.setScreen(new Preview(screen)); screen.resize(client, narrow ? 320 : 640, narrow ? 240 : 400);
    }
    private static void click(AbstractWidget widget) {
        check(staff.mouseClicked(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0, 0), "real widget click");
        staff.mouseReleased(widget.getX() + 2, widget.getY() + 2, 0);
    }
    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static final class Preview extends Screen {
        private final Screen target;
        Preview(Screen target) { super(Component.empty()); this.target = target; }
        @Override protected void init() { target.init(minecraft, width, height); }
        @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
            graphics.fill(0, 0, width, height, 0xFF181B22); target.render(graphics, x, y, partial);
        }
        @Override public void renderBackground(GuiGraphics graphics) { }
    }
}
