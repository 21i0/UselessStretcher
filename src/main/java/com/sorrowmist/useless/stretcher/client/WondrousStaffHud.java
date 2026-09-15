package com.sorrowmist.useless.stretcher.client;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAcceleration;
import com.sorrowmist.useless.stretcher.content.entity.WondrousStaffAccelerationEntity;
import com.sorrowmist.useless.stretcher.content.range.RangeAccelerationSettings;
import com.sorrowmist.useless.stretcher.init.ModItems;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.joml.Vector3f;

import java.util.List;

/**
 * HUD progress bars for the wondrous staff's acceleration:
 * <ul>
 *   <li>World-time acceleration renders its bar on the sun / moon.</li>
 *   <li>Machine / animal acceleration renders a small bar above each target, so the player
 *       can see at a glance how much longer the acceleration lasts.</li>
 * </ul>
 */
@EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class WondrousStaffHud {
    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(UselessStretcherMod.MODID, "wondrous_staff_hud");

    private static final int BAR_WIDTH = 168;
    private static final int BAR_HEIGHT = 8;
    private static final int COLOR_BG = 0xCC101010;
    private static final int COLOR_BORDER = 0xFFFFFFFF;
    private static final int COLOR_TIME_FILL = 0xFF64D8FF;
    private static final int COLOR_PERMANENT_FILL = 0xFFFFB300;

    private WondrousStaffHud() {
    }

    @SubscribeEvent
    public static void registerLayer(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER_ID, WondrousStaffHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;
        int w = g.guiWidth();
        int h = g.guiHeight();
        Font font = mc.font;

        renderTimeBar(g, mc, player, font, w, h);
        renderFilterMarkingStatus(g, player, font, w, h);
    }

    private static void renderFilterMarkingStatus(GuiGraphics graphics, Player player,
                                                   Font font, int width, int height) {
        ItemStack staff = filterMarkingStaff(player);
        if (staff.isEmpty()) return;
        boolean sleepList = RangeAccelerationSettings.markSleepList(staff);
        boolean whitelist = sleepList
                ? RangeAccelerationSettings.sleepWhitelistMode(staff)
                : RangeAccelerationSettings.whitelistMode(staff);
        Component listType = Component.translatable(listNameKey(sleepList, whitelist));
        Component status = Component.translatable("gui.useless_stretcher.range.marking_status", listType);
        int color = whitelist ? 0x62E795 : 0xF06A7A;
        // Keep this persistent mode indicator clear of vanilla's action-bar message line.
        graphics.drawCenteredString(font, status, width / 2, Math.max(8, height - 92), color);
    }

    private static String listNameKey(boolean sleepList, boolean whitelist) {
        if (sleepList) return whitelist
                ? "gui.useless_stretcher.range.sleep_whitelist"
                : "gui.useless_stretcher.range.sleep_blacklist";
        return whitelist
                ? "gui.useless_stretcher.range.acceleration_whitelist"
                : "gui.useless_stretcher.range.acceleration_blacklist";
    }

    private static ItemStack filterMarkingStaff(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(ModItems.WONDROUS_STAFF.get())
                && RangeAccelerationSettings.filterMarkingMode(main)) return main;
        ItemStack off = player.getOffhandItem();
        return off.is(ModItems.WONDROUS_STAFF.get())
                && RangeAccelerationSettings.filterMarkingMode(off) ? off : ItemStack.EMPTY;
    }

    // ---------------------------------------------------------------------
    // Sun / moon time acceleration
    // ---------------------------------------------------------------------

    private static void renderTimeBar(GuiGraphics g, Minecraft mc, Player player, Font font, int w, int h) {
        List<WondrousStaffAccelerationEntity> timeAccels = mc.level.getEntitiesOfClass(
                WondrousStaffAccelerationEntity.class,
                new AABB(player.blockPosition()).inflate(96.0D),
                WondrousStaffAccelerationEntity::isTimeMode);
        WondrousStaffAccelerationEntity accel = timeAccels.stream().findFirst().orElse(null);
        if (accel == null) return;

        int speed = accel.getSpeed();
        boolean permanent = accel.isPermanent();
        int remaining = accel.getRemainingTime();

        Component title;
        int fillColor;
        float fraction;
        if (permanent) {
            title = Component.literal("\u65F6\u95F4\u52A0\u901F x" + speed + " \u221E");
            fillColor = COLOR_PERMANENT_FILL;
            fraction = 1.0F;
        } else {
            int seconds = Math.max(0, remaining) / 20;
            title = Component.literal("\u65F6\u95F4\u52A0\u901F x" + speed + " \u00B7 " + seconds + "s");
            fillColor = COLOR_TIME_FILL;
            fraction = Mth.clamp(remaining / (float) WondrousStaffAcceleration.DEFAULT_DURATION_TICKS, 0.0F, 1.0F);
        }

        Vec3 sunDir = WondrousStaffAcceleration.sunDirection(mc.level);
        ScreenPos pos = projectCelestial(sunDir, mc, w, h);
        if (pos == null) {
            pos = projectCelestial(sunDir.scale(-1.0D), mc, w, h);
        }

        int cx = pos == null ? w / 2 : pos.x();
        int cy = pos == null ? 10 : pos.y() - 26;
        drawBar(g, font, cx, cy, BAR_WIDTH, BAR_HEIGHT, fraction, fillColor, title);
    }

    private static void drawBar(GuiGraphics g, Font font, int cx, int cy, int width, int height,
                                float fraction, int fillColor, Component title) {
        int barX = cx - width / 2;
        g.fill(barX - 1, cy - 1, barX + width + 1, cy + height + 1, COLOR_BORDER);
        g.fill(barX, cy, barX + width, cy + height, COLOR_BG);
        int fillW = (int) (width * fraction);
        if (fillW > 0) {
            g.fill(barX, cy, barX + fillW, cy + height, fillColor);
        }
        g.drawCenteredString(font, title, cx, cy + height + 2, 0xFFFFFFFF);
    }

    /** Projects a world direction (e.g. the sun) onto the GUI; null when behind the camera. */
    private static ScreenPos projectCelestial(Vec3 worldDir, Minecraft mc, int w, int h) {
        Camera camera = mc.gameRenderer.getMainCamera();
        return project(worldDir, camera, w, h);
    }

    private static ScreenPos project(Vec3 dir, Camera camera, int w, int h) {
        Vector3f look = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();

        double fwd = dir.x * look.x() + dir.y * look.y() + dir.z * look.z();
        if (fwd <= 0.02D) return null;
        double upC = dir.x * up.x() + dir.y * up.y() + dir.z * up.z();
        double leftC = dir.x * left.x() + dir.y * left.y() + dir.z * left.z();

        double fov = 70.0D;
        double scale = (h / 2.0D) / Math.tan(Math.toRadians(fov / 2.0D));

        int sx = (int) Math.round(w / 2.0D - leftC / fwd * scale);
        int sy = (int) Math.round(h / 2.0D - upC / fwd * scale);
        return new ScreenPos(sx, sy);
    }

    private record ScreenPos(int x, int y) {
    }
}
