package com.sorrowmist.useless.stretcher.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/** Bounded cosmetic springs sampled once per client tick, shared by both hands and head. */
public final class TwoIDollCarryMotion {
    private static final Map<LivingEntity, Sample> SAMPLES = new WeakHashMap<>();
    private TwoIDollCarryMotion() { }

    public record Pose(float lift, float pitch, float roll, float airborne, float falling, float speed,
                       float tailPitch, float tailYaw) {
        public Pose(float lift, float pitch, float roll, float airborne, float falling, float speed) {
            this(lift, pitch, roll, airborne, falling, speed, pitch * 2, roll * 2);
        }
        public Pose(float lift, float pitch, float roll, float airborne, float falling) {
            this(lift, pitch, roll, airborne, falling, 0);
        }
        public static final Pose STILL = new Pose(0, 0, 0, 0, 0);
        public void apply(PoseStack stack) {
            stack.translate(0, lift, 0);
            stack.mulPose(Axis.XP.rotation(pitch));
            stack.mulPose(Axis.ZP.rotation(roll));
        }
        public Pose blend(Pose next, float t) {
            return new Pose(Mth.lerp(t, lift, next.lift), Mth.lerp(t, pitch, next.pitch),
                    Mth.lerp(t, roll, next.roll), Mth.lerp(t, airborne, next.airborne), Mth.lerp(t, falling, next.falling),
                    Mth.lerp(t, speed, next.speed), Mth.lerp(t, tailPitch, next.tailPitch),
                    Mth.lerp(t, tailYaw, next.tailYaw));
        }
    }

    /** Pure tick simulation also exercised by the client motion regression. */
    public static final class Spring {
        private Pose previous = Pose.STILL, current = Pose.STILL;
        private float velocity, lastForward, lastSide, lastVertical, tailPitchVelocity, tailYawVelocity;
        private int airTicks;
        public void step(float forward, float sideways, float vertical, boolean airborne, float fallDistance) {
            step(forward, sideways, vertical, airborne, fallDistance, 0);
        }
        public void step(float forward, float sideways, float vertical, boolean airborne, float fallDistance, float turn) {
            previous = current;
            airTicks = airborne ? airTicks + 1 : 0;
            float falling = airborne ? Mth.clamp(Math.max((fallDistance - 2) / 5,
                    airTicks > 10 ? (-vertical - 0.35F) * 1.4F : 0), 0, 1) : 0;
            float excitement = Mth.lerp(0.3F, current.airborne, airborne ? 1 : 0);
            float arms = Mth.lerp(0.3F, current.falling, falling);
            float target = airborne ? 0.035F + 0.08F * arms : 0;
            velocity = (velocity + (target - current.lift) * 0.25F
                    + Mth.clamp(lastVertical - vertical, -0.15F, 0.15F) * 0.05F) * 0.66F;
            float lift = Mth.clamp(current.lift + velocity, 0, 0.16F);
            if (lift == 0) velocity = Math.max(0, -velocity * 0.2F);
            float pitch = Mth.lerp(0.28F, current.pitch, Mth.clamp(
                    (lastForward - forward) * 0.7F - forward * 0.15F, -0.16F, 0.16F));
            float roll = Mth.lerp(0.28F, current.roll, Mth.clamp(
                    (sideways - lastSide) * 0.7F + sideways * 0.15F, -0.14F, 0.14F));
            float speed = Mth.lerp(0.25F, current.speed,
                    Mth.clamp((float)Math.sqrt(forward * forward + sideways * sideways) * 3, 0, 1));
            // Separate tail springs: visible secondary motion never feeds back into the player's arms.
            float tailPitchTarget = Mth.clamp(-vertical * .65F + (lastVertical - vertical) * 1.4F
                    + forward * .35F + (lastForward - forward) * .8F, -.65F, .65F);
            float tailYawTarget = Mth.clamp(-turn * .035F + sideways * .4F
                    + (sideways - lastSide) * 1.2F, -.70F, .70F);
            tailPitchVelocity = (tailPitchVelocity + (tailPitchTarget - current.tailPitch) * .16F) * .78F;
            tailYawVelocity = (tailYawVelocity + (tailYawTarget - current.tailYaw) * .14F) * .80F;
            current = new Pose(lift, pitch, roll, excitement, arms, speed,
                    Mth.clamp(current.tailPitch + tailPitchVelocity, -.8F, .8F),
                    Mth.clamp(current.tailYaw + tailYawVelocity, -.85F, .85F));
            lastForward = forward; lastSide = sideways; lastVertical = vertical;
        }
        public Pose pose(float partialTick) { return previous.blend(current, Mth.clamp(partialTick, 0, 1)); }
    }

    private static final class Sample {
        Spring spring = new Spring();
        int tick = Integer.MIN_VALUE;
        double x, y, z;
        float distance, yaw;
    }

    public static Pose sample(LivingEntity carrier, float partialTick) {
        Sample sample = SAMPLES.computeIfAbsent(carrier, ignored -> new Sample());
        if (sample.tick != carrier.tickCount) {
            double dx = carrier.getX() - sample.x, dy = carrier.getY() - sample.y, dz = carrier.getZ() - sample.z;
            boolean reset = carrier.tickCount - (long)sample.tick != 1 || dx * dx + dy * dy + dz * dz > 16;
            if (reset) {
                sample.spring = new Spring(); sample.distance = 0; dx = dy = dz = 0;
            }
            boolean airborne = !carrier.onGround() && !carrier.isInWater() && !carrier.isInLava()
                    && !carrier.onClimbable() && !carrier.isPassenger() && !carrier.isFallFlying()
                    && !(carrier instanceof net.minecraft.world.entity.player.Player player && player.getAbilities().flying);
            sample.distance = airborne ? sample.distance + (float)Math.max(0, -dy) : 0;
            float yaw = carrier.yBodyRot * Mth.DEG_TO_RAD;
            float forward = (float)(dz * Math.cos(yaw) - dx * Math.sin(yaw));
            float sideways = (float)(dx * Math.cos(yaw) + dz * Math.sin(yaw));
            sample.spring.step(forward, sideways, (float)dy, airborne, sample.distance,
                    reset ? 0 : Mth.wrapDegrees(carrier.yBodyRot - sample.yaw));
            sample.yaw = carrier.yBodyRot;
            sample.tick = carrier.tickCount;
            sample.x = carrier.getX(); sample.y = carrier.getY(); sample.z = carrier.getZ();
        }
        return sample.spring.pose(partialTick);
    }
}
