package com.sorrowmist.useless.stretcher.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** A cached eight-panel fabric dome with scalloped piping, underside ribs and little stars. */
public final class TwoIUmbrellaCanopy {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(
            "useless_stretcher", "textures/entity/two_i_umbrella.png");
    private static final float SECTOR = (float) Math.PI / 4;
    /** Radius in model pixels. 18.5 keeps the full doll under one canopy. */
    public static final float CANOPY_RADIUS = 18.5F;
    private static final int[] FABRIC = {0xF7F0E5, 0xADC9EE};
    private static final List<Quad> MESH = build();
    private record Point(float x, float y, float z) { }
    private record Quad(Point a, Point b, Point c, Point d, float nx, float ny, float nz, int color) { }

    private TwoIUmbrellaCanopy() { }

    private static Point surface(float radius, float angle, float offset) {
        float fraction = angle / SECTOR - (float) Math.floor(angle / SECTOR);
        float within = (fraction - 0.5F) * SECTOR;
        float edge = CANOPY_RADIUS * (float) Math.cos(SECTOR / 2) / (float) Math.cos(within);
        float y = -0.45F + 4.9F * (float) Math.pow(radius, 1.65)
                + 0.38F * (float) Math.sin(fraction * Math.PI) * radius * radius;
        return new Point(edge * radius * (float) Math.cos(angle), y + offset,
                edge * radius * (float) Math.sin(angle));
    }

    private static List<Quad> build() {
        List<Quad> mesh = new ArrayList<>();
        for (int panel = 0; panel < 8; panel++) {
            for (int arc = 0; arc < 4; arc++) {
                float a = (panel + arc / 4F) * SECTOR, b = (panel + (arc + 1) / 4F) * SECTOR;
                for (int ring = 0; ring < 4; ring++) {
                    float inner = ring / 4F, outer = (ring + 1) / 4F;
                    quad(mesh, surface(inner, a, 0), surface(inner, b, 0),
                            surface(outer, b, 0), surface(outer, a, 0), FABRIC[panel % 2]);
                }
                quad(mesh, surface(0.962F, a, -0.035F), surface(0.962F, b, -0.035F),
                        surface(1, b, -0.035F), surface(1, a, -0.035F), 0xF4F7FF);
            }
            // Thin ribs sit on the underside, terminating underneath the fabric edge.
            float angle = panel * SECTOR;
            for (int ring = 0; ring < 4; ring++) {
                float inner = Math.max(0.02F, ring / 4F), outer = (ring + 1) / 4F * 0.99F;
                quad(mesh, surface(inner, angle - 0.006F, 0.08F), surface(inner, angle + 0.006F, 0.08F),
                        surface(outer, angle + 0.006F, 0.08F), surface(outer, angle - 0.006F, 0.08F), 0x46516B);
            }
            // A four-point stitched star follows the dome instead of floating above it.
            float middle = (panel + 0.5F) * SECTOR;
            Point center = surface(0.71F, middle, -0.045F);
            for (int point = 0; point < 8; point++) {
                Point a = starPoint(middle, point), b = starPoint(middle, (point + 1) % 8);
                quad(mesh, center, a, b, b, 0xD9BF82);
            }
        }
        return List.copyOf(mesh);
    }

    private static Point starPoint(float angle, int point) {
        float phase = point * SECTOR;
        float length = point % 2 == 0 ? 1.0F : 0.32F;
        return surface(0.71F + (float) Math.cos(phase) * length * 0.075F,
                angle + (float) Math.sin(phase) * length * 0.095F, -0.045F);
    }

    private static void quad(List<Quad> mesh, Point a, Point b, Point c, Point d, int color) {
        float ux = c.x - a.x, uy = c.y - a.y, uz = c.z - a.z;
        float vx = d.x - b.x, vy = d.y - b.y, vz = d.z - b.z;
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float length = Math.max(0.00001F, (float) Math.sqrt(nx * nx + ny * ny + nz * nz));
        // Model coordinates point down. Give the outer fabric an upward normal.
        if (ny > 0) length = -length;
        mesh.add(new Quad(a, b, c, d, nx / length, ny / length, nz / length, color));
    }

    public static void render(PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        for (Quad quad : MESH) {
            vertex(vertices, pose.last(), quad.a, quad, light, overlay);
            vertex(vertices, pose.last(), quad.b, quad, light, overlay);
            vertex(vertices, pose.last(), quad.c, quad, light, overlay);
            vertex(vertices, pose.last(), quad.d, quad, light, overlay);
        }
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, Point p, Quad quad, int light, int overlay) {
        vertices.addVertex(pose, p.x / 16, p.y / 16, p.z / 16)
                .setColor(quad.color >> 16 & 255, quad.color >> 8 & 255, quad.color & 255, 255)
                .setUv(0.5F, 0.5F).setOverlay(overlay).setLight(light)
                .setNormal(pose, quad.nx, quad.ny, quad.nz);
    }
}
