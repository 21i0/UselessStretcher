package com.sorrowmist.useless.stretcher.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEntity;
import com.sorrowmist.useless.stretcher.content.entity.TwoIDollEnvironment;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** One plush-doll rig, standing as a companion or sitting as an animated item. */
@OnlyIn(Dist.CLIENT)
public final class TwoIDollRenderer extends MobRenderer<TwoIDollEntity, TwoIDollRenderer.Model> {
    /** Standard 64x64 Steve skin UVs are deliberately identical in every form. */
    public static final ResourceLocation TEXTURE =
            new ResourceLocation("useless_stretcher", "textures/entity/two_i.png");
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation("useless_stretcher", "two_i_doll"), "main");
    public static final float RENDER_SCALE = 0.62F;
    public static final float FEET_Y = 24.0F;

    public TwoIDollRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(LAYER)), 0.18F);
        addLayer(new net.minecraft.client.renderer.entity.layers.RenderLayer<TwoIDollEntity, Model>(this) {
            @Override
            public void render(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light,
                               TwoIDollEntity doll, float swing, float amount, float partial, float age,
                               float yaw, float pitch) {
                getParentModel().renderAccessories(pose, buffers, light,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            }
        });
    }

    @Override
    protected void scale(TwoIDollEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(RENDER_SCALE, RENDER_SCALE, RENDER_SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(TwoIDollEntity entity) {
        return TwoIDollSkin.texture(model.blink());
    }

    /**
     * Keep Steve's UV-sized cubes, not Steve's proportions or locomotion. Short
     * plush limbs are scaled at the bones so future standard skins still fit;
     * sitting and standing share these exact bones and skin-layer transforms.
     */
    public static final class Model extends PlayerModel<TwoIDollEntity> {
        // Poppet reference: approximately 9.5 px head / 9 px torso / 7 px width.
        // Keep original Steve UVs, with one set of longer plush limbs in every form.
        private static final float HEAD_SIZE = 1.18F;
        private static final float BODY_HEIGHT = 0.75F;
        private static final float LEG_HEIGHT = 0.50F;
        private static final float LEG_DEPTH = 0.75F;
        private static final float HIP_Y = FEET_Y - 12.0F * LEG_HEIGHT;
        private static final float NECK_Y = HIP_Y - 12.0F * BODY_HEIGHT;
        private final ModelPart[] resetParts;

        public static LayerDefinition createLayer() {
            var mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
            var umbrella = mesh.getRoot().addOrReplaceChild("umbrella",
                    CubeListBuilder.create().texOffs(0, 0)
                            .addBox(-0.18F, -19.55F, -0.18F, 0.36F, 21.1F, 0.36F)
                            .addBox(-0.45F, -20.1F, -0.45F, 0.9F, 0.65F, 0.9F)
                            .addBox(-0.40F, -0.65F, -0.40F, 0.8F, 2.1F, 0.8F)
                            .addBox(-0.30F, 1.20F, -1.35F, 0.6F, 0.65F, 1.7F)
                            .addBox(-0.30F, 0.55F, -1.55F, 0.6F, 1.1F, 0.6F), PartPose.ZERO);
            // The shaft stays just outside the head, while the canopy center
            // shifts back over the doll instead of wasting half the fabric.
            umbrella.addOrReplaceChild("canopy", CubeListBuilder.create(), PartPose.offset(2.25F, -20, 0));
            mesh.getRoot().addOrReplaceChild("nose_drop", CubeListBuilder.create().texOffs(48, 0)
                    .addBox(-0.32F, -1.7F, -4.56F, 0.64F, 1.5F, 0.45F), PartPose.ZERO);
            return LayerDefinition.create(mesh, 64, 64);
        }

        private final ModelPart umbrella;
        private final ModelPart noseDrop;
        private final TwoIDollTail tail = new TwoIDollTail();
        private float tailStride, tailMoving;
        private TwoIDollCarryMotion.Pose carryMotion = TwoIDollCarryMotion.Pose.STILL;
        private float blink;
        private boolean seated;
        private boolean sharedUmbrella;
        private boolean umbrellaHolder = true;
        private static final ResourceLocation ACCESSORIES = new ResourceLocation(
                "useless_stretcher", "textures/entity/two_i_accessories.png");

        public Model(ModelPart root) {
            super(root, false);
            umbrella = root.getChild("umbrella");
            noseDrop = root.getChild("nose_drop");
            // Dedicated doll pivots; only UV geometry is shared with Steve.
            initialPivot(head, 0, NECK_Y, 0);
            initialPivot(body, 0, NECK_Y, 0);
            initialPivot(leftArm, 4.15F, NECK_Y + 1.4F, 0);
            initialPivot(rightArm, -4.15F, NECK_Y + 1.4F, 0);
            initialPivot(leftLeg, 1.8F, HIP_Y, 0);
            initialPivot(rightLeg, -1.8F, HIP_Y, 0);
            resetParts = root.getAllParts().toArray(ModelPart[]::new);
        }

        private static void initialPivot(ModelPart part, float x, float y, float z) {
            PartPose pose = PartPose.offset(x, y, z);
            part.setInitialPose(pose);
            part.loadPose(pose);
        }

        @Override
        public void setupAnim(TwoIDollEntity entity, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch) {
            setupStanding(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch,
                    entity.getPoseAction(), entity.getPoseActionProgress(ageInTicks - entity.tickCount));
            setSharedUmbrella(entity.hasSharedUmbrella(), entity.isSharedUmbrellaHolder());
            applyTailMotion(TwoIDollCarryMotion.sample(entity, ageInTicks - entity.tickCount), ageInTicks);
            applyWeather(TwoIDollWeather.sample(entity, entity.level(), entity.blockPosition(),
                    ageInTicks - entity.tickCount, entity.getUUID().hashCode()), ageInTicks);
        }

        /** Also used by the isolated client fixture: no fake entity/world needed. */
        public void setupStanding(float limbSwing, float limbSwingAmount, float time,
                                  float headYaw, float headPitch, int action, float progress) {
            setupStanding(limbSwing, limbSwingAmount, time, headYaw, headPitch, action, progress,
                    TwoIDollEntity.ACTION_NONE, 0.0F);
        }

        public void setupStanding(float limbSwing, float limbSwingAmount, float time,
                                  float headYaw, float headPitch, int action, float progress,
                                  int environmentAction, float environmentProgress) {
            resetDoll();
            float moving = Mth.clamp(limbSwingAmount * 2.4F, 0, 1);
            tailStride = limbSwing;
            tailMoving = moving;
            float still = 1 - moving;
            float phase = limbSwing * 0.92F;
            float step = Mth.sin(phase);
            float bounce = Mth.abs(step) * moving * 0.16F;
            float breath = Mth.sin(time * 0.09F) * still;
            moveTorso(-bounce + breath * 0.045F);
            head.yRot = Mth.clamp(headYaw * Mth.DEG_TO_RAD, -0.72F, 0.72F);
            head.xRot = Mth.clamp(headPitch * Mth.DEG_TO_RAD, -0.38F, 0.42F);
            head.zRot = Mth.sin(time * 0.033F) * 0.035F * still;
            body.zRot = step * moving * 0.022F;
            head.x += step * moving * 0.07F;
            leftArm.xRot = -step * moving * 0.38F;
            rightArm.xRot = step * moving * 0.38F;
            leftArm.zRot = -0.07F - breath * 0.015F;
            rightArm.zRot = 0.07F + breath * 0.015F;
            leftLeg.xRot = step * moving * 0.57F;
            rightLeg.xRot = -step * moving * 0.57F;
            // Plant the supporting foot exactly at y=24; only the swinging foot
            // lifts. A vanilla 12-pixel stride made the old tiny model float.
            plantFoot(leftLeg, Mth.clamp(-step, 0, 1) * moving * 0.65F);
            plantFoot(rightLeg, Mth.clamp(step, 0, 1) * moving * 0.65F);

            float gesture = Mth.sin(Mth.clamp(progress, 0, 1) * Mth.PI);
            switch (action) {
                case TwoIDollEntity.ACTION_GREETING -> {
                    rightArm.xRot = Mth.lerp(gesture, rightArm.xRot, -2.45F);
                    rightArm.zRot += (0.45F + Mth.sin(time * 0.32F) * 0.20F) * gesture;
                    head.zRot -= 0.12F * gesture;
                }
                case TwoIDollEntity.ACTION_CURIOUS -> head.zRot += 0.25F * gesture;
                case TwoIDollEntity.ACTION_PATTED -> {
                    leftArm.xRot = Mth.lerp(gesture, leftArm.xRot, -2.65F);
                    leftArm.yRot = 0.24F * gesture;
                    leftArm.zRot -= 0.28F * gesture;
                    head.xRot -= 0.12F * gesture;
                    head.zRot += 0.10F * gesture;
                    head.xScale = head.zScale = HEAD_SIZE * (1 + 0.055F * gesture);
                    head.yScale = HEAD_SIZE * (1 - 0.10F * gesture);
                    moveTorso(0.30F * gesture);
                }
                case TwoIDollEntity.ACTION_RECEIVE_GIFT -> {
                    leftArm.xRot = rightArm.xRot = -1.18F * gesture;
                    leftArm.yRot = 0.25F * gesture;
                    rightArm.yRot = -0.25F * gesture;
                    head.xRot += 0.18F * gesture;
                }
                case TwoIDollEntity.ACTION_INVITE -> {
                    leftArm.zRot -= 0.72F * gesture;
                    rightArm.zRot += 0.72F * gesture;
                    leftArm.xRot = rightArm.xRot = -0.55F * gesture;
                    head.zRot += 0.12F * gesture;
                }
                case TwoIDollEntity.ACTION_SWING_FEET -> {
                    // A standing companion rocks onto one foot, never hangs both
                    // feet in mid-air like the seated decoration's foot-swing.
                    leftLeg.xRot += Mth.sin(time * 0.19F) * 0.18F * still * gesture;
                    plantFoot(leftLeg, 0.15F * still * gesture);
                }
                case TwoIDollEntity.ACTION_STRETCH -> {
                    leftArm.zRot -= 2.1F * gesture;
                    rightArm.zRot += 2.1F * gesture;
                    head.xRot -= 0.18F * gesture;
                }
                case TwoIDollEntity.ACTION_DUST -> {
                    rightArm.xRot = -0.7F * gesture;
                    rightArm.yRot = -0.65F * gesture;
                    rightArm.zRot += Mth.sin(time * 0.22F) * 0.15F * gesture;
                    head.xRot += 0.16F * gesture;
                }
                case TwoIDollEntity.ACTION_SHY -> {
                    leftArm.xRot = rightArm.xRot = -2.35F * gesture;
                    head.zRot += 0.18F * gesture;
                }
                default -> { }
            }

            // Small head/shoulder motions keep the doll alive between set-piece gestures.
            float glance = Mth.sin(time * 0.021F) * 0.11F + Mth.sin(time * 0.047F) * 0.045F;
            head.yRot += glance * still;
            body.zRot += Mth.sin(time * 0.031F) * 0.018F * still;
            blink = blinkEnvelope(time);

            applyEnvironment(environmentAction, environmentProgress, time);
            if (action == TwoIDollEntity.ACTION_PATTED) {
                head.xScale = head.zScale = HEAD_SIZE * (1.0F + 0.055F * gesture);
                head.yScale = HEAD_SIZE * (1.0F - 0.10F * gesture);
            }
            copySkinLayers();
        }

        private float blinkEnvelope(float time) {
            int cycle = Math.floorDiv((int)Math.floor(time), 83);
            float phase = time - cycle * 83.0F;
            // Two-frame blink with an occasional quick double blink.
            float first = Mth.clamp(1.0F - Math.abs(phase - 42.0F) / 3.0F, 0, 1);
            float second = cycle % 7 == 0 ? Mth.clamp(1.0F - Math.abs(phase - 49.0F) / 2.0F, 0, 1) : 0;
            return Math.max(first, second);
        }

        private void applyEnvironment(int action, float progress, float time) {
            int flags = action == TwoIDollEntity.ACTION_UMBRELLA ? TwoIDollEnvironment.RAIN
                    : action == TwoIDollEntity.ACTION_SNEEZE ? TwoIDollEnvironment.COLD
                    : action == TwoIDollEntity.ACTION_SHIVER ? TwoIDollEnvironment.THUNDER : 0;
            applyWeather(new TwoIDollWeather.Pose(flags, action == TwoIDollEntity.ACTION_UMBRELLA ? progress : 0,
                    action == TwoIDollEntity.ACTION_SNEEZE ? progress : -1), time);
        }

        public void applyWeather(TwoIDollWeather.Pose weather, float time) {
            float open = Mth.sin(weather.open() * Mth.HALF_PI);
            umbrella.visible = open > 0.01F && (!sharedUmbrella || umbrellaHolder);
            var canopy = umbrella.getChild("canopy");
            float sharedScale = sharedUmbrella ? 1.35F : 1.0F;
            canopy.xScale = canopy.zScale = Math.max(0.05F, open * sharedScale);
            canopy.yScale = 0.65F + open * 0.35F;
            noseDrop.visible = (weather.flags() & TwoIDollEnvironment.COLD) != 0;
            if (umbrella.visible) {
                // Reserve this hand for the umbrella in every idle/action/falling pose.
                rightArm.xRot = -0.50F;
                rightArm.yRot = 0;
                // Keep the grip just outside the head while bringing the canopy
                // over this doll instead of leaving half of it behind the body.
                rightArm.zRot = 1.04F;
                head.yRot += Mth.sin(time * 0.09F) * 0.05F * open;
            }
            if (noseDrop.visible) {
                noseDrop.yScale = 0.55F + (Mth.sin(time * 0.07F) + 1) * 0.22F;
                if (weather.sneeze() >= 0) {
                    float sneeze = Mth.sin(weather.sneeze() * Mth.PI);
                    head.xRot += 0.42F * sneeze;
                    head.y += 0.22F * sneeze;
                    leftArm.xRot = Mth.lerp(sneeze, leftArm.xRot, -1.35F);
                    leftArm.zRot -= 0.18F * sneeze;
                    blink = Math.max(blink, sneeze);
                }
            }
            if ((weather.flags() & TwoIDollEnvironment.THUNDER) != 0) {
                float shiver = Mth.sin(time * 0.95F) * 0.055F;
                body.zRot += shiver;
                head.zRot += shiver * 1.3F;
                leftArm.xRot = -0.6F + shiver;
                if (!umbrella.visible) rightArm.xRot = -0.6F - shiver;
                head.xRot += 0.10F;
            }
            if (seated) {
                float bottom = 12.25F * BODY_HEIGHT * Mth.cos(body.zRot)
                        + 4.25F * body.xScale * Math.abs(Mth.sin(body.zRot));
                moveTorso(FEET_Y - bottom - body.y);
            }
            if (umbrella.visible) {
                // Keep the mast outside the head's whole animated volume, not just its neutral pose.
                head.yRot = Mth.clamp(head.yRot, -0.40F, 0.40F);
                head.zRot = Mth.clamp(head.zRot, -0.07F, 0.07F);
                head.xRot = Mth.clamp(head.xRot, -0.30F, 0.40F);
                float handX = -rightArm.xScale;
                float handY = 10 * rightArm.yScale;
                // Match the precise quaternion rotation used by ModelPart; lookup-table trigonometry drifts.
                float reachY = handY * (float)Math.cos(rightArm.xRot);
                float rollCos = (float)Math.cos(rightArm.zRot), rollSin = (float)Math.sin(rightArm.zRot);
                umbrella.setPos(rightArm.x + handX * rollCos - reachY * rollSin,
                        rightArm.y + handX * rollSin + reachY * rollCos,
                        rightArm.z + handY * (float)Math.sin(rightArm.xRot));
            }
            updateTail(time);
            copySkinLayers();
        }

        public void setSharedUmbrella(boolean shared, boolean holder) {
            sharedUmbrella = shared;
            umbrellaHolder = holder;
        }

        public float blink() { return blink; }
        public boolean noseVisible() { return noseDrop.visible; }
        public void renderAccessories(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers,
                                      int light, int overlay) {
            tail.render(pose, buffers, light, overlay);
            var vertices = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(ACCESSORIES));
            umbrella.render(pose, vertices, light, overlay);
            if (noseDrop.visible) {
                pose.pushPose();
                head.translateAndRotate(pose);
                noseDrop.render(pose, vertices, light, overlay);
                pose.popPose();
            }
            if (umbrella.visible) {
                pose.pushPose();
                umbrella.translateAndRotate(pose);
                umbrella.getChild("canopy").translateAndRotate(pose);
                TwoIUmbrellaCanopy.render(pose, buffers, light, overlay);
                pose.popPose();
            }
        }

        public boolean umbrellaVisible() {
            return umbrella.visible;
        }

        private static void plantFoot(ModelPart leg, float lift) {
            leg.y = FEET_Y - soleDepth(leg.xRot) - lift;
        }

        private static float soleDepth(float angle) {
            return 12.0F * LEG_HEIGHT * Mth.cos(angle) + 2.0F * LEG_DEPTH * Mth.abs(Mth.sin(angle));
        }

        /** Bottom of the bare skin's foot, in model coordinates, for regression checks. */
        public float footBottom(ModelPart leg) {
            return leg.y + soleDepth(leg.xRot);
        }

        private void resetDoll() {
            for (ModelPart part : resetParts) part.resetPose();
            young = riding = crouching = false;
            attackTime = swimAmount = 0;
            head.xScale = head.yScale = head.zScale = HEAD_SIZE;
            umbrella.visible = false;
            noseDrop.visible = false;
            blink = 0;
            carryMotion = TwoIDollCarryMotion.Pose.STILL;
            tailStride = tailMoving = 0;
            seated = false;
            body.xScale = 0.875F;
            body.yScale = BODY_HEIGHT;
            body.zScale = 1.125F;
            leftArm.xScale = rightArm.xScale = 0.75F;
            leftArm.yScale = rightArm.yScale = 0.50F;
            leftArm.zScale = rightArm.zScale = 0.75F;
            leftLeg.xScale = rightLeg.xScale = 0.75F;
            leftLeg.yScale = rightLeg.yScale = LEG_HEIGHT;
            leftLeg.zScale = rightLeg.zScale = LEG_DEPTH;
        }

        private void moveTorso(float dy) {
            head.y += dy;
            body.y += dy;
            leftArm.y += dy;
            rightArm.y += dy;
        }

        /** Twelve softly blended idle gestures, exclusively client-side. */
        public void setupDecoration(float time) {
            resetDoll();
            seated = true;
            // Fold the very same short legs forward and lower the torso. The
            // seated bottom stays on the block top, without a second doll mesh.
            // The torso's jacket bottom is the seat contact, independent of the feet.
            // Rotating upright legs and aligning only their toes leaves the rump floating.
            float seatedHip = FEET_Y - 2.25F * LEG_DEPTH;
            moveTorso(FEET_Y - 0.25F * BODY_HEIGHT - HIP_Y);
            leftLeg.y = rightLeg.y = seatedHip;
            leftLeg.z = rightLeg.z = -1.6F;
            leftLeg.x = 2.60F;
            rightLeg.x = -2.60F;
            leftLeg.xRot = rightLeg.xRot = -Mth.HALF_PI;
            // The foot points toward -Z. Negative yaw opens the left leg toward
            // +X; the old opposite signs folded both feet into each other.
            leftLeg.yRot = -0.24F;
            rightLeg.yRot = 0.24F;
            float breath = Mth.sin(time * 0.08F);
            head.zRot = Mth.sin(time * 0.029F) * 0.04F;
            head.y += breath * 0.04F;
            leftArm.xRot = rightArm.xRot = -0.13F;
            leftArm.zRot = -0.10F;
            rightArm.zRot = 0.10F;

            int slot = Math.floorDiv((int)time, 200);
            float progress = (time - slot * 200.0F) / 120.0F;
            float envelope = progress < 1 ? Mth.sin(progress * Mth.PI) : 0;
            switch (Math.floorMod(slot, 12)) {
                case 0 -> {
                    head.yRot = Mth.sin(progress * Mth.TWO_PI) * 0.4F * envelope;
                    head.xRot = -0.10F * envelope;
                }
                case 1 -> {
                    leftLeg.xRot += Mth.sin(time * 0.20F) * 0.17F * envelope;
                    rightLeg.xRot -= Mth.sin(time * 0.20F) * 0.17F * envelope;
                }
                case 2 -> {
                    rightArm.xRot = -2.40F * envelope;
                    rightArm.zRot += (0.36F + Mth.sin(time * 0.28F) * 0.20F) * envelope;
                    head.zRot += 0.12F * envelope;
                }
                case 3 -> {
                    leftArm.zRot -= 2.25F * envelope;
                    rightArm.zRot += 2.25F * envelope;
                    head.xRot -= 0.20F * envelope;
                }
                case 4 -> {
                    head.xRot += (0.28F + Mth.sin(time * 0.08F) * 0.09F) * envelope;
                    head.zRot += 0.13F * envelope;
                    head.y += 0.13F * envelope;
                }
                case 5 -> {
                    rightArm.xRot = -0.80F * envelope;
                    rightArm.yRot = -0.65F * envelope;
                    rightArm.zRot += Mth.sin(time * 0.19F) * 0.17F * envelope;
                    head.xRot += 0.16F * envelope;
                }
                case 6 -> {
                    head.zRot += 0.27F * envelope;
                    leftLeg.xRot += Mth.sin(time * 0.16F) * 0.08F * envelope;
                }
                case 7 -> {
                    leftArm.xRot = rightArm.xRot = -2.55F * envelope;
                    leftArm.yRot = 0.20F * envelope;
                    rightArm.yRot = -0.20F * envelope;
                    head.xRot += 0.13F * envelope;
                }
                case 8 -> {
                    head.zRot += 0.08F * envelope;
                    head.yRot -= 0.28F * envelope;
                    leftArm.xRot -= 0.22F * envelope;
                    rightArm.xRot += 0.22F * envelope;
                }
                case 9 -> {
                    head.xRot += 0.10F * envelope;
                    head.yRot += Mth.sin(time * 0.21F) * 0.20F * envelope;
                    leftArm.zRot -= 0.35F * envelope;
                    rightArm.zRot -= 0.18F * envelope;
                }
                case 10 -> {
                    head.zRot -= 0.12F * envelope;
                    leftArm.xRot = -0.72F * envelope;
                    rightArm.xRot = -0.42F * envelope;
                    head.xRot -= 0.16F * envelope;
                }
                case 11 -> {
                    leftLeg.xRot += 0.22F * envelope;
                    rightLeg.xRot -= 0.22F * envelope;
                    head.zRot += 0.18F * envelope;
                }
            }
            head.yRot += Mth.sin(time * 0.021F) * 0.10F + Mth.sin(time * 0.047F) * 0.04F;
            blink = blinkEnvelope(time);
            // Seated swinging feet may lift, but never clip through the shelf.
            seatLeg(leftLeg);
            seatLeg(rightLeg);
            updateTail(time);
            copySkinLayers();
        }

        private static void seatLeg(ModelPart leg) {
            float cosine = Mth.cos(leg.xRot);
            float bottom = (cosine >= 0 ? 12.25F : -0.25F) * LEG_HEIGHT * cosine
                    + 2.25F * LEG_DEPTH * Math.abs(Mth.sin(leg.xRot));
            leg.y = FEET_Y - bottom;
        }

        public void applyCarriedMotion(TwoIDollCarryMotion.Pose motion, float time) {
            applyTailMotion(motion, time);
            head.yRot += Mth.sin(time * 0.17F) * 0.32F * motion.airborne();
            head.xRot -= 0.15F * motion.airborne();
            head.zRot += 0.08F * Mth.sin(time * 0.12F) * motion.airborne();
            leftArm.xRot = Mth.lerp(motion.falling(), leftArm.xRot, -2.85F);
            rightArm.xRot = Mth.lerp(motion.falling(), rightArm.xRot, -2.85F);
            leftArm.zRot = Mth.lerp(motion.falling(), leftArm.zRot, 0.45F);
            rightArm.zRot = Mth.lerp(motion.falling(), rightArm.zRot, -0.45F);
            copySkinLayers();
        }

        public void applyTailMotion(TwoIDollCarryMotion.Pose motion, float time) {
            carryMotion = motion;
            updateTail(time);
        }

        private void updateTail(float time) {
            tail.pose(seated, body.y, body.zRot, time, tailStride, tailMoving, carryMotion);
        }

        public float tailYaw() { return tail.tipYaw(); }
        public float tailPitch() { return tail.tipPitch(); }
        public float tailBottom() { return tail.bottom(); }
        public net.minecraft.world.phys.Vec3 tailTip() { return tail.tip(); }
        public net.minecraft.world.phys.Vec3 tailAttachment() { return tail.attachment(); }

        private void copySkinLayers() {
            hat.copyFrom(head);
            jacket.copyFrom(body);
            leftSleeve.copyFrom(leftArm);
            rightSleeve.copyFrom(rightArm);
            leftPants.copyFrom(leftLeg);
            rightPants.copyFrom(rightLeg);
        }
    }
}
