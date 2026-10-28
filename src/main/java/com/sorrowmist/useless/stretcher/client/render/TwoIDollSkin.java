package com.sorrowmist.useless.stretcher.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import com.sorrowmist.useless.stretcher.util.RateLimitedLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;

/** One client texture for every doll form, rebuilt only when resources reload. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class TwoIDollSkin {
    private static final ResourceLocation NORMALIZED = new ResourceLocation(
            UselessStretcherMod.MODID, "dynamic/two_i_normalized_skin");
    private static ResourceLocation texture = TwoIDollRenderer.TEXTURE;
    private static final ResourceLocation HALF = new ResourceLocation(
            UselessStretcherMod.MODID, "dynamic/two_i_half_blink");
    private static final ResourceLocation CLOSED = new ResourceLocation(
            UselessStretcherMod.MODID, "dynamic/two_i_closed_eyes");
    private static boolean blinkReady;

    private TwoIDollSkin() { }

    public static ResourceLocation texture() {
        return texture;
    }

    public static ResourceLocation texture(float blink) {
        return !blinkReady || blink < 0.25F ? texture : blink < 0.65F ? HALF : CLOSED;
    }

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<NativeImage>() {
            @Override
            protected NativeImage prepare(ResourceManager resources, ProfilerFiller profiler) {
                try (var input = resources.open(TwoIDollRenderer.TEXTURE);
                     NativeImage source = NativeImage.read(input)) {
                    return normalizedCopy(source);
                } catch (Exception failure) {
                    RateLimitedLogger.warn(LogUtils.getLogger(), "Could not prepare the 2i skin; using the resource directly", failure);
                    return null;
                }
            }

            @Override
            protected void apply(NativeImage image, ResourceManager resources, ProfilerFiller profiler) {
                TwoIDollHandRenderer.reload();
                var textures = Minecraft.getInstance().getTextureManager();
                textures.release(NORMALIZED);
                textures.release(HALF);
                textures.release(CLOSED);
                blinkReady = false;
                texture = TwoIDollRenderer.TEXTURE;
                if (image != null) {
                    textures.register(HALF, new DynamicTexture(blinkCopy(image, false)));
                    textures.register(CLOSED, new DynamicTexture(blinkCopy(image, true)));
                    textures.register(NORMALIZED, new DynamicTexture(image));
                    texture = NORMALIZED;
                    blinkReady = true;
                }
            }
        });
    }

    /** Original PNG is untouched. Standard and integer-scale HD Steve/Alex skins are accepted. */
    public static NativeImage normalizedCopy(NativeImage source) {
        NativeImage copy = new NativeImage(source.getWidth(), source.getHeight(), false);
        copy.copyFrom(source);
        if (usesSlimArms(source)) {
            int scale = source.getWidth() / 64;
            remapArm(source, copy, 40, 16, scale, true);
            remapArm(source, copy, 32, 48, scale, true);
            remapArm(source, copy, 40, 32, scale, false);
            remapArm(source, copy, 48, 48, scale, false);
        }
        return copy;
    }

    /** Artist's three-row gold eyes: close the iris as well as the white, without painting over hair. */
    public static NativeImage blinkCopy(NativeImage skin, boolean closed) {
        NativeImage copy = new NativeImage(skin.getWidth(), skin.getHeight(), false);
        copy.copyFrom(skin);
        if (skin.getWidth() % 64 != 0 || skin.getHeight() != skin.getWidth()) return copy;
        int scale = skin.getWidth() / 64;
        for (int eye : new int[]{9, 13}) for (int px = 0; px < 2 * scale; px++) {
            int x = eye * scale + px;
            int complexion = skin.getPixelRGBA(11 * scale, 15 * scale);
            int lash = skin.getPixelRGBA(8 * scale, 8 * scale);
            for (int py = 0; py < 3 * scale; py++) {
                int y = 12 * scale + py;
                int color = closed ? (py == 2 * scale ? lash : complexion)
                        : (py < scale ? complexion : skin.getPixelRGBA(x, y));
                copy.setPixelRGBA(x, y, color);
                // An opaque face overlay must close too, while transparent hair pixels stay transparent.
                if (py >= scale && (skin.getPixelRGBA(x + 32 * scale, y) >>> 24) != 0)
                    copy.setPixelRGBA(x + 32 * scale, y, color);
            }
        }
        return copy;
    }

    public static boolean usesSlimArms(NativeImage skin) {
        if (skin.getWidth() < 64 || skin.getWidth() % 64 != 0 || skin.getHeight() != skin.getWidth()) return false;
        int scale = skin.getWidth() / 64;
        // Alex reserves the last two arm columns. Check both base arms and
        // confirm their three-pixel palms exist, not an entirely missing skin.
        return allTransparent(skin, 54, 20, 2, 12, scale)
                && allTransparent(skin, 46, 52, 2, 12, scale)
                && !allTransparent(skin, 47, 16, 3, 4, scale)
                && !allTransparent(skin, 39, 48, 3, 4, scale);
    }

    private static boolean allTransparent(NativeImage image, int x, int y, int width, int height, int scale) {
        for (int py = y * scale; py < (y + height) * scale; py++) {
            for (int px = x * scale; px < (x + width) * scale; px++) {
                if ((image.getPixelRGBA(px, py) >>> 24) != 0) return false;
            }
        }
        return true;
    }

    private static void remapArm(NativeImage source, NativeImage target, int x, int y, int scale, boolean base) {
        // Unfolded vanilla cube faces: top, palm, side, front, side, back.
        face(source, target, x + 4, y, 3, 4, x + 4, y, 4, 4, scale, base);
        face(source, target, x + 7, y, 3, 4, x + 8, y, 4, 4, scale, base);
        face(source, target, x, y + 4, 4, 12, x, y + 4, 4, 12, scale, base);
        face(source, target, x + 4, y + 4, 3, 12, x + 4, y + 4, 4, 12, scale, base);
        face(source, target, x + 7, y + 4, 4, 12, x + 8, y + 4, 4, 12, scale, base);
        face(source, target, x + 11, y + 4, 3, 12, x + 12, y + 4, 4, 12, scale, base);
    }

    private static void face(NativeImage source, NativeImage target, int sx, int sy, int sw, int sh,
                             int dx, int dy, int dw, int dh, int scale, boolean opaqueBase) {
        for (int y = 0; y < dh * scale; y++) for (int x = 0; x < dw * scale; x++) {
            int color = source.getPixelRGBA(sx * scale + x * sw / dw, sy * scale + y * sh / dh);
            target.setPixelRGBA(dx * scale + x, dy * scale + y, opaqueBase ? color | 0xFF000000 : color);
        }
    }
}
