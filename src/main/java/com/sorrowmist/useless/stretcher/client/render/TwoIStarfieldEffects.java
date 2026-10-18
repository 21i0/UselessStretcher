package com.sorrowmist.useless.stretcher.client.render;

import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.content.entity.TwoIStarfieldData;
import com.sorrowmist.useless.stretcher.dimension.world.TwoIStarfieldChunkGenerator;
import com.sorrowmist.useless.stretcher.dimension.world.TwoIStarfieldDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.BlockPos;
import org.joml.Vector3f;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

/** Vanilla sky/particles only: one show-state packet, no new textures, entity ticks or particle broadcasts. */
public final class TwoIStarfieldEffects extends DimensionSpecialEffects {
    private static BlockPos showCenter;
    private static long showEnd;
    private static final DustParticleOptions BLUE = new DustParticleOptions(new Vector3f(0.35F, 0.72F, 1.0F), 1.2F);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.76F, 0.44F, 1.0F), 1.2F);

    public static void startShow(BlockPos center, long end) {
        showCenter = center.immutable();
        showEnd = end;
    }
    public TwoIStarfieldEffects() {
        super(Float.NaN, false, SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return new Vec3(0.045D, 0.055D, 0.105D);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTick) {
        return null;
    }

    @EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void register(RegisterDimensionSpecialEffectsEvent event) {
            event.register(TwoIStarfieldDimensions.KEY.location(), new TwoIStarfieldEffects());
        }
    }

    @EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT)
    public static final class Ambience {
        private Ambience() {
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || !mc.level.dimension().equals(TwoIStarfieldDimensions.KEY)) {
                showCenter = null;
                showEnd = 0L;
                return;
            }
            if (mc.isPaused()) return;
            ParticleStatus quality = mc.options.particles().get();
            if (quality == ParticleStatus.MINIMAL) return;
            int interval = quality == ParticleStatus.DECREASED ? 10 : 5;
            long time = mc.level.getGameTime();
            animateShow(mc, time, quality);
            if (time % interval != 0) return;
            var random = mc.level.random;
            double x = mc.player.getX();
            double z = mc.player.getZ();
            double y = Math.max(TwoIStarfieldChunkGenerator.SURFACE_Y, mc.player.getY());
            // A few soft motes and falling stars; ordinary particle settings cap the work further.
            mc.level.addParticle(ParticleTypes.END_ROD, x + random.nextDouble() * 30.0D - 15.0D,
                    y + 10.0D + random.nextDouble() * 10.0D, z + random.nextDouble() * 30.0D - 15.0D,
                    0.005D, -0.018D, 0.0D);
            mc.level.addParticle(ParticleTypes.ENCHANT, x + random.nextDouble() * 14.0D - 7.0D,
                    y + random.nextDouble() * 2.0D + 0.1D, z + random.nextDouble() * 14.0D - 7.0D,
                    0.0D, 0.025D, 0.0D);
            double centerX = Math.rint((x - 8.5D) / TwoIStarfieldData.AREA_SPACING)
                    * TwoIStarfieldData.AREA_SPACING + 8.5D;
            double centerZ = Math.rint((z - 8.5D) / TwoIStarfieldData.AREA_SPACING)
                    * TwoIStarfieldData.AREA_SPACING + 8.5D;
            if (Math.abs(x - centerX) < 24.0D && Math.abs(z - centerZ) < 24.0D) {
                double angle = time * Math.PI / 100.0D;
                mc.level.addParticle(ParticleTypes.END_ROD, centerX + Math.cos(angle) * 5.0D,
                        TwoIStarfieldChunkGenerator.SURFACE_Y + 0.18D, centerZ + Math.sin(angle) * 5.0D,
                        0.0D, 0.012D, 0.0D);
            }
            if (time % 200L == 0L) {
                double starX = x + random.nextDouble() * 20.0D - 10.0D;
                double starZ = z + random.nextDouble() * 20.0D - 10.0D;
                for (int i = 0; i < 6; i++) {
                    mc.level.addParticle(ParticleTypes.END_ROD, starX + i * 0.5D, y + 21.0D + i * 0.2D,
                            starZ + i * 0.25D, -0.3D, -0.12D, -0.15D);
                }
            }
        }

        private static void animateShow(Minecraft mc, long time, ParticleStatus quality) {
            if (showCenter == null || time >= showEnd) return;
            int interval = quality == ParticleStatus.DECREASED ? 8 : 4;
            if (time % interval != 0) return;
            double t = (600L - (showEnd - time)) / 20.0D;
            double x = showCenter.getX() + 0.5D;
            double y = showCenter.getY();
            double z = showCenter.getZ() + 0.5D;
            // Act 1: twin spirals unfurl upward. Act 2: constellations and a crown. Finale: falling stars.
            if (t < 8.0D) {
                for (int i = 0; i < 8; i++) {
                    double angle = t * 2.4D + i * Math.PI / 4.0D;
                    double height = 4.0D + t * 1.4D;
                    double radius = 2.0D + t * 0.5D;
                    mc.level.addParticle(i % 2 == 0 ? BLUE : VIOLET,
                            x + Math.cos(angle) * radius, y + height, z + Math.sin(angle) * radius,
                            0.0D, 0.015D, 0.0D);
                }
            } else if (t < 22.0D) {
                for (int i = 0; i < 10; i++) {
                    double angle = i * Math.PI / 5.0D + t * 0.15D;
                    double radius = i % 2 == 0 ? 9.0D : 4.5D;
                    mc.level.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : BLUE,
                            x + Math.cos(angle) * radius, y + 16.0D + Math.sin(t) * 0.5D,
                            z + Math.sin(angle) * radius, 0.0D, 0.0D, 0.0D);
                }
            } else {
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4.0D + t * 0.4D;
                    mc.level.addParticle(ParticleTypes.END_ROD, x + Math.cos(angle) * 9.0D,
                            y + 18.0D - (t - 22.0D) * 0.8D, z + Math.sin(angle) * 9.0D,
                            -Math.cos(angle) * 0.05D, -0.05D, -Math.sin(angle) * 0.05D);
                }
            }
        }
    }
}
