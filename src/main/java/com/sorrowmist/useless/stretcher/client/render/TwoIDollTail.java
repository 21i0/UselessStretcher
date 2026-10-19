package com.sorrowmist.useless.stretcher.client.render;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
    private record Point(float x, float y, float z, float u, float v, int a, int b, float weight) {
        Point(float x,float y,float z) { this(x,y,z,0,0,0,0,0); }
    }
    private record Face(Point a, Point b, Point c, Point d, float nx, float ny, float nz,
                        boolean marking, int tint) { }
    private record Bone(Point pivot, List<Face> faces) { }
    private static final Bone[] BONES = loadGeometry();
    private static final Point[] SUPPORT = java.util.Arrays.stream(BONES).flatMap(b->b.faces.stream())
            .flatMap(f->java.util.stream.Stream.of(f.a,f.b,f.c,f.d)).distinct().toArray(Point[]::new);
    private static final List<Face> RIBBON = createRibbon();
    private static final Point[] RIBBON_CORNERS = RIBBON.stream()
            .flatMap(f -> java.util.stream.Stream.of(f.a, f.b, f.c, f.d)).distinct().toArray(Point[]::new);
    private final float[] pitch = new float[7], yaw = new float[7];
    private final Matrix4f matrix = new Matrix4f(), ribbonMatrix = new Matrix4f();
    private final Matrix4f attachment = new Matrix4f();
    private final Matrix4f[] skin = java.util.stream.IntStream.range(0,7).mapToObj(i->new Matrix4f()).toArray(Matrix4f[]::new);
    private float rootY, rootRoll, rootPitch, rootYaw, heightScale, cachedBottom;
    private float ribbonPitch;
    private float ribbonRoll;
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
                for (var cube : bone.getAsJsonArray("cubes")) {
                    Point[] p = new Point[8];
                    for (int j = 0; j < 8; j++) {
                        var v = cube.getAsJsonArray().get(j).getAsJsonArray();
                        float x=v.get(0).getAsFloat();
                        float y=v.get(1).getAsFloat(),z=v.get(2).getAsFloat();
                        p[j] = furPoint(x,y,z,pivot.get(1).getAsFloat(),pivot.get(2).getAsFloat());
                    }
                    // Reversed X and Y preserve handedness. Winding here follows the converted corners.
                    face(faces,p,0,1,5,4,false,0xFFFFFF);
                    face(faces,p,2,6,7,3,false,0xFFFFFF);
                    face(faces,p,0,4,6,2,false,0xFFFFFF);
                    face(faces,p,1,3,7,5,false,0xFFFFFF);
                    face(faces,p,0,2,3,1,false,0xFFFFFF);
                    face(faces,p,4,5,7,6,false,0xFFFFFF);
                }
                // Subdivide broad fur panels before skinning; the Winefox silhouette remains,
                // but the bulky middle can bend instead of rotating as one rigid block.
                List<Face> smooth = new ArrayList<>();
                for (Face f : faces) subdivide(smooth, f, i == 2 ? 4 : 2);
                bones[i] = new Bone(new Point(pivot.get(0).getAsFloat(),pivot.get(1).getAsFloat(),pivot.get(2).getAsFloat()),
                        List.copyOf(smooth));
            }
            return bones;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load 2i tail geometry", e);
        }
    }

    private static Point mix(Point a, Point b, float t) {
        float bu=b.u;
        if (bu-a.u>.5F) bu-=1;
        if (a.u-bu>.5F) bu+=1;
        return weightedPoint(Mth.lerp(t,a.x,b.x),Mth.lerp(t,a.y,b.y),Mth.lerp(t,a.z,b.z),
                Mth.lerp(t,a.u,bu),Mth.lerp(t,a.v,b.v));
    }
    private static void subdivide(List<Face> out, Face f, int n) {
        for(int y=0;y<n;y++) for(int x=0;x<n;x++) {
            Point l0=mix(f.a,f.d,y/(float)n),r0=mix(f.b,f.c,y/(float)n);
            Point l1=mix(f.a,f.d,(y+1F)/n),r1=mix(f.b,f.c,(y+1F)/n);
            out.add(new Face(mix(l0,r0,x/(float)n),mix(l0,r0,(x+1F)/n),
                    mix(l1,r1,(x+1F)/n),mix(l1,r1,x/(float)n),f.nx,f.ny,f.nz,false,f.tint));
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
        return weightedPoint(x,gy,gz,u,Mth.clamp(along/total,0,1)*.74F);
    }

    private static Point weightedPoint(float x,float y,float z,float u,float v) {
        float[] cy={0,2.47358F,2.19175F,-1.71243F,-2.6044F,-4.33778F,-5.90146F};
        float[] cz={0,1.69704F,3.7538F,9.9476F,10.16067F,10.00902F,9.58112F};
        float best=Float.MAX_VALUE,weight=0;int a=0;
        for(int i=0;i<6;i++) {
            float dy=cy[i+1]-cy[i],dz=cz[i+1]-cz[i];
            float t=Mth.clamp(((y-cy[i])*dy+(z-cz[i])*dz)/(dy*dy+dz*dz),0,1);
            float ey=y-cy[i]-t*dy,ez=z-cz[i]-t*dz,dist=ey*ey+ez*ez;
            if(dist<best){best=dist;a=i;weight=t*t*(3-2*t);}
        }
        return new Point(x,y,z,u,v,a,a+1,weight);
    }

    private static List<Face> createRibbon() {
        List<Face> mesh = new ArrayList<>();
        // Rounded, shallow folded loops, narrow knot, two clean swallow-tail streamers.
        for (int side=-1;side<=1;side+=2) {
            float[] xs={.16F,.55F,1.15F,1.65F,1.83F}, heights={.14F,.38F,.60F,.62F,.43F};
            for(int i=0;i<4;i++)for(int j=0;j<8;j++) {
                Point[] p=new Point[4];
                for(int k=0;k<4;k++) {
                    int ring=i+(k==1||k==2?1:0), edge=j+(k>=2?1:0);
                    float angle=edge*Mth.TWO_PI/8;
                    p[k]=new Point(side*xs[ring],-.12F*xs[ring]+Mth.cos(angle)*heights[ring],
                            .22F+Mth.sin(angle)*(.10F+heights[ring]*.28F));
                }
                int tint=j==1||j==2?0xE6EFFC:j<4?0xC8DDF8:j==4||j==7?0xA4C2EC:0xB6D0F2;
                face(mesh,p,0,1,2,3,false,tint);
            }
            // Two broad ribbons, with a small V cut into each end.
            Point t0=new Point(side*.24F,.20F,.13F),t1=new Point(side*.83F,.26F,.11F);
            Point t2=new Point(side*1.04F,2.02F,.18F),t3=new Point(side*.73F,1.72F,.24F);
            Point t4=new Point(side*.50F,2.10F,.16F),t5=new Point(side*.39F,1.10F,.34F);
            triangle(mesh,t0,t1,t5,0xD9E8FC);triangle(mesh,t1,t2,t5,0xAAC8F0);
            triangle(mesh,t2,t3,t5,0x91B5E8);triangle(mesh,t3,t4,t5,0xBDD6F5);
            triangle(mesh,t4,t0,t5,0xC9DEF8);
        }
        box(mesh,0,.02F,.32F,.48F,.52F,.38F,0,0,0,0xC2D9F6);
        box(mesh,0,.05F,.55F,.24F,.24F,.07F,0,0,Mth.PI/4,0xDECBA3);
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
        rootY = bodyY+5.8F;
        rootRoll = bodyRoll;
        heightScale = seated?.72F:.90F;
        rootPitch = seated?.32F:-.18F;
        // Zero-mean idle motion, with a relaxed pause between alternating curious sweeps.
        float cycle=time/240F, phase=cycle-(float)Math.floor(cycle);
        float gesture=phase<.60F?Mth.sin(phase/.60F*Mth.PI):0;
        float side=Math.floorMod((int)Math.floor(cycle),2)==0?1:-1;
        rootYaw = Mth.sin(time*.032F)*.10F + side*gesture*.95F;
        float speed = Math.max(moving,motion.speed());
        for (int i=0;i<7;i++) {
            float delay=i*.48F, weight=(.5F+i*.16F)/7;
            pitch[i] = Mth.sin(time*.055F-delay)*(.022F+i*.004F)
                    +Mth.sin(time*.29F-delay)*speed*.055F
                    +motion.tailPitch()*weight*1.8F+motion.airborne()*.05F-motion.falling()*.065F
                    +gesture*Mth.sin(time*.08F-delay)*.035F;
            yaw[i] = Mth.sin(time*.043F-delay)*(.025F+i*.008F)
                    +Mth.sin(stride*.92F-delay)*moving*.055F
                    +Mth.sin(time*.29F-delay)*motion.speed()*.055F+motion.tailYaw()*weight*2
                    +side*gesture*Mth.sin(phase*5-delay)*.055F
                    +(i>3?Mth.sin(time*.35F-delay)*gesture*.055F:0);
        }
        ribbonPitch = -.10F + Mth.sin(time*.09F-2)*.09F + motion.tailPitch()*.28F;
        ribbonRoll = Mth.sin(time*.07F-2)*.08F + motion.tailYaw()*.35F;
        // Bounds share the same skinned vertices as the visible mesh, cached once per pose.
        cachedBottom=calculateBottom();
    }

    private void startMatrix() {
        // The base sits inside the jacket, and never translates to solve ground clearance.
        attachment.translation(0,rootY,1.25F).rotateZ(rootRoll).scale(1,heightScale,1);
        matrix.set(attachment).rotateY(rootYaw).rotateX(rootPitch);
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
        startMatrix();
        for (int i=0;i<7;i++) {
            boneMatrix(i);
            Point pivot=BONES[i].pivot;
            skin[i].set(matrix).translate(-pivot.x,-pivot.y,-pivot.z);
            if (i==5) {
                ribbonMatrix.set(matrix).translate(0,-.15F,1.5F).rotateX(ribbonPitch).rotateZ(ribbonRoll);
            }
        }
        tipX=matrix.m30();tipY=supportedY(matrix.m31());tipZ=matrix.m32();
        float max=rootY;
        // Every subdivided panel corner participates, including bends between original cube corners.
        for(Point p:SUPPORT) max=Math.max(max,skinY(p));
        for(Point p:RIBBON_CORNERS)max=Math.max(max,supportedY(ribbonMatrix.m01()*p.x+ribbonMatrix.m11()*p.y+ribbonMatrix.m21()*p.z+ribbonMatrix.m31()));
        return max;
    }
    private float skinY(Point p) {
        Matrix4f a=skin[p.a],b=skin[p.b];
        float skinned=Mth.lerp(p.weight,a.m01()*p.x+a.m11()*p.y+a.m21()*p.z+a.m31(),b.m01()*p.x+b.m11()*p.y+b.m21()*p.z+b.m31());
        return supportedY(Mth.lerp(rootWeight(p),attachment.m01()*p.x+attachment.m11()*p.y+attachment.m21()*p.z+attachment.m31(),skinned));
    }
    private static float supportedY(float y) {
        // Soft contact compresses only the underside, never stretches the whole tail or moves its root.
        float start=TwoIDollRenderer.FEET_Y-.72F;
        return y<=start?y:start+.5F*(y-start)/(.5F+y-start);
    }
    private static float rootWeight(Point p) {
        float t=Mth.clamp(p.z/3.2F,0,1);
        return t*t*(3-2*t);
    }
    public net.minecraft.world.phys.Vec3 attachment() {
        return new net.minecraft.world.phys.Vec3(attachment.m30(),attachment.m31(),attachment.m32());
    }
    public float bottom() { return cachedBottom; }
    public net.minecraft.world.phys.Vec3 tip() { return new net.minecraft.world.phys.Vec3(tipX,tipY,tipZ); }
    public float tipYaw() { float v=rootYaw;for(float a:yaw)v+=a;return v; }
    public float tipPitch() { float v=rootPitch;for(float a:pitch)v+=a;return v; }

    public void render(PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(FUR));
        for (int i=0;i<7;i++) {
            for (Face f:BONES[i].faces) renderFace(out,pose,f,light,overlay,false);
            if (i==5) {
                for(Face f:RIBBON)renderFace(out,pose,f,light,overlay,true);
            }
        }
    }
    private void renderFace(VertexConsumer out,PoseStack pose,Face f,int light,int overlay,boolean ribbon) {
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
    private void furVertex(VertexConsumer out,PoseStack pose,Point p,boolean seam,Face f,int light,int overlay) {
        Matrix4f a=skin[p.a],b=skin[p.b];float t=p.weight;
        float x=Mth.lerp(t,a.m00()*p.x+a.m10()*p.y+a.m20()*p.z+a.m30(),b.m00()*p.x+b.m10()*p.y+b.m20()*p.z+b.m30());
        float y=skinY(p);
        float z=Mth.lerp(t,a.m02()*p.x+a.m12()*p.y+a.m22()*p.z+a.m32(),b.m02()*p.x+b.m12()*p.y+b.m22()*p.z+b.m32());
        float rootWeight=rootWeight(p);
        x=Mth.lerp(rootWeight,attachment.m00()*p.x+attachment.m10()*p.y+attachment.m20()*p.z+attachment.m30(),x);
        z=Mth.lerp(rootWeight,attachment.m02()*p.x+attachment.m12()*p.y+attachment.m22()*p.z+attachment.m32(),z);
        float nx=Mth.lerp(t,a.m00()*f.nx+a.m10()*f.ny+a.m20()*f.nz,b.m00()*f.nx+b.m10()*f.ny+b.m20()*f.nz);
        float ny=Mth.lerp(t,a.m01()*f.nx+a.m11()*f.ny+a.m21()*f.nz,b.m01()*f.nx+b.m11()*f.ny+b.m21()*f.nz);
        float nz=Mth.lerp(t,a.m02()*f.nx+a.m12()*f.ny+a.m22()*f.nz,b.m02()*f.nx+b.m12()*f.ny+b.m22()*f.nz);
        nx=Mth.lerp(rootWeight,attachment.m00()*f.nx+attachment.m10()*f.ny+attachment.m20()*f.nz,nx);
        ny=Mth.lerp(rootWeight,attachment.m01()*f.nx+attachment.m11()*f.ny+attachment.m21()*f.nz,ny);
        nz=Mth.lerp(rootWeight,attachment.m02()*f.nx+attachment.m12()*f.ny+attachment.m22()*f.nz,nz);
        out.addVertex(pose.last(),x/16,y/16,z/16).setColor(255,255,255,255)
                .setUv(seam&&p.u<.5F?p.u+1:p.u,p.v).setOverlay(overlay).setLight(light).setNormal(pose.last(),nx,ny,nz);
    }
    private void vertex(VertexConsumer out,PoseStack pose,Point p,float u,float v,Face f,int light,int overlay) {
        float x=ribbonMatrix.m00()*p.x+ribbonMatrix.m10()*p.y+ribbonMatrix.m20()*p.z+ribbonMatrix.m30();
        float y=supportedY(ribbonMatrix.m01()*p.x+ribbonMatrix.m11()*p.y+ribbonMatrix.m21()*p.z+ribbonMatrix.m31());
        float z=ribbonMatrix.m02()*p.x+ribbonMatrix.m12()*p.y+ribbonMatrix.m22()*p.z+ribbonMatrix.m32();
        float nx=ribbonMatrix.m00()*f.nx+ribbonMatrix.m10()*f.ny+ribbonMatrix.m20()*f.nz;
        float ny=ribbonMatrix.m01()*f.nx+ribbonMatrix.m11()*f.ny+ribbonMatrix.m21()*f.nz;
        float nz=ribbonMatrix.m02()*f.nx+ribbonMatrix.m12()*f.ny+ribbonMatrix.m22()*f.nz;
        out.addVertex(pose.last(),x/16,y/16,z/16).setColor(f.tint>>16&255,f.tint>>8&255,f.tint&255,255)
                .setUv(u,v).setOverlay(overlay).setLight(light).setNormal(pose.last(),nx,ny,nz);
    }
}
