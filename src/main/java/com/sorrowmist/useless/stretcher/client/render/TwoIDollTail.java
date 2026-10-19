package com.sorrowmist.useless.stretcher.client.render;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Adapted Winefox bind geometry; attribution and asset license in TWO_I_TAIL_CREDITS.txt. */
public final class TwoIDollTail {
    private static final ResourceLocation FUR = ResourceLocation.fromNamespaceAndPath(
            "useless_stretcher", "textures/entity/two_i_tail.png");
    private record Point(float x, float y, float z, float u, float v) {
        Point(float x,float y,float z) { this(x,y,z,0,0); }
    }
    private record Face(Point a, Point b, Point c, Point d, float nx, float ny, float nz,
                        boolean marking, int tint) { }
    private record Bone(Point pivot, List<Face> faces, List<Point> corners) { }
    private static final Bone[] BONES = loadGeometry();
    private static final List<Face> RIBBON = createRibbon();
    private static final Point[] RIBBON_CORNERS = RIBBON.stream()
            .flatMap(f -> java.util.stream.Stream.of(f.a, f.b, f.c, f.d)).distinct().toArray(Point[]::new);
    private final float[] pitch = new float[7], yaw = new float[7];
    private final Matrix4f matrix = new Matrix4f(), ribbonMatrix = new Matrix4f();
    private float rootY, rootRoll, rootPitch, rootYaw, heightScale, cachedBottom;
    private float ribbonPitch;
    private float tipX, tipY, tipZ;

    private static Bone[] loadGeometry() {
        try (var stream = TwoIDollTail.class.getResourceAsStream(
                "/assets/useless_stretcher/models/entity/two_i_tail.json")) {
            if (stream == null) throw new IllegalStateException("Missing 2i tail geometry");
            var source = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("bones");
            Bone[] bones = new Bone[source.size()];
            for (int i = 0; i < bones.length; i++) {
                var bone = source.get(i).getAsJsonObject();
                var pivot = bone.getAsJsonArray("pivot");
                List<Face> faces = new ArrayList<>();
                List<Point> corners = new ArrayList<>();
                for (var cube : bone.getAsJsonArray("cubes")) {
                    Point[] p = new Point[8];
                    for (int j = 0; j < 8; j++) {
                        var v = cube.getAsJsonArray().get(j).getAsJsonArray();
                        float x=v.get(0).getAsFloat();
                        float y=v.get(1).getAsFloat(),z=v.get(2).getAsFloat();
                        p[j] = furPoint(x,y,z,pivot.get(1).getAsFloat(),pivot.get(2).getAsFloat());
                        corners.add(p[j]);
                    }
                    // Reversed X and Y preserve handedness. Winding here follows the converted corners.
                    face(faces,p,0,1,5,4,false,0xFFFFFF);
                    face(faces,p,2,6,7,3,false,0xFFFFFF);
                    face(faces,p,0,4,6,2,false,0xFFFFFF);
                    face(faces,p,1,3,7,5,false,0xFFFFFF);
                    face(faces,p,0,2,3,1,false,0xFFFFFF);
                    face(faces,p,4,5,7,6,false,0xFFFFFF);
                }
                bones[i] = new Bone(new Point(pivot.get(0).getAsFloat(),pivot.get(1).getAsFloat(),pivot.get(2).getAsFloat()),
                        List.copyOf(faces),List.copyOf(corners));
            }
            return bones;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load 2i tail geometry", e);
        }
    }

    private static Point furPoint(float x,float y,float z,float py,float pz) {
        // Follow the bind curve for continuous UVs across overlapping cubes.
        float[] cy={0,2.5F,3.7F,2.4F,-.5F,-3.4F,-6};
        float[] cz={0,1.9F,4.7F,7.4F,9.5F,10.1F,9.6F};
        float gy=y+py,gz=z+pz,best=Float.MAX_VALUE,arc=0,along=0,radial=0,total=0;
        for(int i=0;i<cy.length-1;i++) {
            float dy=cy[i+1]-cy[i],dz=cz[i+1]-cz[i],length=Mth.sqrt(dy*dy+dz*dz);
            float t=Mth.clamp(((gy-cy[i])*dy+(gz-cz[i])*dz)/(length*length),0,1);
            float ey=gy-cy[i]-t*dy,ez=gz-cz[i]-t*dz,dist=ey*ey+ez*ez;
            if(dist<best) { best=dist;along=arc+t*length;radial=(ey*dz-ez*dy)/length; }
            arc+=length;total=arc;
        }
        float u=.5F+(float)Math.atan2(x,radial)/Mth.TWO_PI;
        return new Point(x,y,z,u,Mth.clamp(along/total,0,1)*.74F);
    }

    private static List<Face> createRibbon() {
        List<Face> mesh = new ArrayList<>();
        // Folded satin loops have a pinched center and a raised crease, not cuboid ears.
        for (int side=-1;side<=1;side+=2) {
            Point a=new Point(side*.22F,-.05F,.29F),b=new Point(side*1.72F,-.86F,.10F);
            Point c=new Point(side*2.05F,-.57F,.03F),d=new Point(side*1.84F,.77F,.08F);
            Point e=new Point(side*.22F,.16F,.29F),crease=new Point(side*1.12F,.01F,.54F);
            triangle(mesh,a,b,crease,0xDDEAFF);triangle(mesh,b,c,crease,0xBDD6F5);
            triangle(mesh,c,d,crease,0x99BBE9);triangle(mesh,d,e,crease,0xABC9F0);
            triangle(mesh,e,a,crease,0xD2E3FA);
            // Two broad ribbons, with a small V cut into each end.
            Point t0=new Point(side*.24F,.20F,.13F),t1=new Point(side*.83F,.26F,.11F);
            Point t2=new Point(side*1.14F,2.22F,.18F),t3=new Point(side*.73F,1.94F,.24F);
            Point t4=new Point(side*.50F,2.30F,.16F),t5=new Point(side*.39F,1.10F,.34F);
            triangle(mesh,t0,t1,t5,0xD9E8FC);triangle(mesh,t1,t2,t5,0xAAC8F0);
            triangle(mesh,t2,t3,t5,0x91B5E8);triangle(mesh,t3,t4,t5,0xBDD6F5);
            triangle(mesh,t4,t0,t5,0xC9DEF8);
        }
        box(mesh,0,.02F,.40F,.58F,.62F,.38F,0,0,0,0xB1CDF2);
        box(mesh,0,.15F,.64F,.19F,.36F,.08F,0,0,.20F,0xD9BB7D);
        return List.copyOf(mesh);
    }

    private static void triangle(List<Face> out,Point a,Point b,Point c,int tint) {
        face(out,new Point[]{a,b,c},0,1,2,2,false,tint);
    }

    private static void box(List<Face> out,float x,float y,float z,float w,float h,float d,
                            float rx,float ry,float rz,int tint) {
        Matrix4f m = new Matrix4f().translation(x,y,z).rotateZ(rz).rotateY(ry).rotateX(rx);
        Point[] p = new Point[8];
        for (int i=0;i<8;i++) {
            Vector3f v = new Vector3f((i&1)==0?-w/2:w/2,(i&2)==0?-h/2:h/2,(i&4)==0?-d/2:d/2).mulPosition(m);
            p[i] = new Point(v.x,v.y,v.z);
        }
        face(out,p,0,1,5,4,false,tint); face(out,p,2,6,7,3,false,tint);
        face(out,p,0,4,6,2,false,tint); face(out,p,1,3,7,5,false,tint);
        face(out,p,0,2,3,1,false,tint); face(out,p,4,5,7,6,false,tint);
    }

    private static void face(List<Face> out,Point[] p,int a,int b,int c,int d,boolean marked,int tint) {
        Vector3f n = new Vector3f(p[b].x-p[a].x,p[b].y-p[a].y,p[b].z-p[a].z)
                .cross(new Vector3f(p[c].x-p[a].x,p[c].y-p[a].y,p[c].z-p[a].z)).normalize();
        out.add(new Face(p[a],p[b],p[c],p[d],n.x,n.y,n.z,marked,tint));
    }

    public void pose(boolean seated,float bodyY,float bodyRoll,float time,float stride,float moving,TwoIDollCarryMotion.Pose motion) {
        rootY = bodyY+5.6F;
        rootRoll = bodyRoll;
        heightScale = seated?.58F:.90F;
        rootPitch = seated?.28F:-.18F;
        rootYaw = seated?.77F:.38F;
        float speed = Math.max(moving,motion.speed());
        for (int i=0;i<7;i++) {
            float delay=i*.62F, weight=1F/(i+1);
            pitch[i] = Mth.sin(time*.055F-delay)*.014F+Mth.sin(time*.38F-delay)*speed*.026F
                    +motion.pitch()*.56F*weight+motion.airborne()*.045F*weight-motion.falling()*.10F*weight;
            yaw[i] = Mth.sin(time*.063F-delay)*.025F+Mth.sin(stride*.92F-delay)*moving*.035F
                    +Mth.sin(time*.35F-delay)*motion.speed()*.035F+motion.roll()*.85F*weight;
        }
        ribbonPitch = seated?-.02F:-.06F;
        // Cache support-plane clearance once per pose. Bounds use eight corners per cube,
        // rather than repeatedly inspecting four vertices of every face during rendering.
        cachedBottom=calculateBottom();
        for (int i=0;i<24 && cachedBottom>TwoIDollRenderer.FEET_Y-.16F;i++) {
            rootPitch+=.035F;
            cachedBottom=calculateBottom();
        }
    }

    private void startMatrix() {
        matrix.translation(0,rootY,2.15F).rotateZ(rootRoll).scale(1,heightScale,1)
                .rotateY(rootYaw).rotateX(rootPitch);
    }
    private void boneMatrix(int i) {
        Point p= BONES[i].pivot;
        if (i>0) {
            Point last=BONES[i-1].pivot;
            matrix.translate(p.x-last.x,p.y-last.y,p.z-last.z);
        }
        matrix.rotateY(yaw[i]).rotateX(pitch[i]);
    }
    private float calculateBottom() {
        startMatrix();float max=rootY;
        for (int i=0;i<7;i++) {
            boneMatrix(i);
            for (Point p:BONES[i].corners) max=Math.max(max,y(p));
            if (i==5) {
                ribbonMatrix.set(matrix);
                matrix.translate(0,-.15F,1.65F).rotateX(ribbonPitch).scale(1,1.35F,1);
                for (Point p:RIBBON_CORNERS) max=Math.max(max,y(p));
                matrix.set(ribbonMatrix);
            }
        }
        tipX=matrix.m30();tipY=matrix.m31();tipZ=matrix.m32();
        return max;
    }
    private float y(Point p) { return matrix.m01()*p.x+matrix.m11()*p.y+matrix.m21()*p.z+matrix.m31(); }
    public float bottom() { return cachedBottom; }
    public net.minecraft.world.phys.Vec3 tip() { return new net.minecraft.world.phys.Vec3(tipX,tipY,tipZ); }
    public float tipYaw() { float v=rootYaw;for(float a:yaw)v+=a;return v; }
    public float tipPitch() { float v=rootPitch;for(float a:pitch)v+=a;return v; }

    public void render(PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        pose.pushPose();
        pose.translate(0,rootY/16,2.15F/16);pose.mulPose(Axis.ZP.rotation(rootRoll));
        pose.scale(1,heightScale,1);pose.mulPose(Axis.YP.rotation(rootYaw));pose.mulPose(Axis.XP.rotation(rootPitch));
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(FUR));
        for (int i=0;i<7;i++) {
            Point p=BONES[i].pivot;
            if (i>0) { Point last=BONES[i-1].pivot;pose.translate((p.x-last.x)/16,(p.y-last.y)/16,(p.z-last.z)/16); }
            pose.mulPose(Axis.YP.rotation(yaw[i]));pose.mulPose(Axis.XP.rotation(pitch[i]));
            for (Face f:BONES[i].faces) renderFace(out,pose,f,light,overlay,false);
            if (i==5) {
                pose.pushPose();pose.translate(0,-.15F/16,1.65F/16);pose.mulPose(Axis.XP.rotation(ribbonPitch));
                pose.scale(1,1.35F,1);
                for(Face f:RIBBON)renderFace(out,pose,f,light,overlay,true);
                pose.popPose();
            }
        }
        pose.popPose();
    }
    private static void renderFace(VertexConsumer out,PoseStack pose,Face f,int light,int overlay,boolean ribbon) {
        if(ribbon) {
            vertex(out,pose,f.a,.1F,.80F,f,light,overlay);vertex(out,pose,f.b,.1F,.95F,f,light,overlay);
            vertex(out,pose,f.c,.3F,.95F,f,light,overlay);vertex(out,pose,f.d,.3F,.80F,f,light,overlay);
        } else {
            float lo=Math.min(Math.min(f.a.u,f.b.u),Math.min(f.c.u,f.d.u));
            float hi=Math.max(Math.max(f.a.u,f.b.u),Math.max(f.c.u,f.d.u));
            boolean seam=hi-lo>.5F;
            furVertex(out,pose,f.a,seam,f,light,overlay);furVertex(out,pose,f.b,seam,f,light,overlay);
            furVertex(out,pose,f.c,seam,f,light,overlay);furVertex(out,pose,f.d,seam,f,light,overlay);
        }
    }
    private static void furVertex(VertexConsumer out,PoseStack pose,Point p,boolean seam,Face f,int light,int overlay) {
        vertex(out,pose,p,seam&&p.u<.5F?p.u+1:p.u,p.v,f,light,overlay);
    }
    private static void vertex(VertexConsumer out,PoseStack pose,Point p,float u,float v,Face f,int light,int overlay) {
        out.addVertex(pose.last(),p.x/16,p.y/16,p.z/16).setColor(f.tint>>16&255,f.tint>>8&255,f.tint&255,255)
                .setUv(u,v).setOverlay(overlay).setLight(light).setNormal(pose.last(),f.nx,f.ny,f.nz);
    }
}
