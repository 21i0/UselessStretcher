package com.sorrowmist.useless.stretcher.guidetest;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.UselessStretcherMod;
import guideme.Guides;
import guideme.PageAnchor;
import guideme.color.SymbolicColor;
import guideme.document.block.LytNode;
import guideme.document.block.LytVisitor;
import guideme.document.flow.LytFlowContent;
import guideme.indices.ItemIndex;
import guideme.internal.screen.GuideNavigation;
import guideme.internal.screen.GuideScreen;
import guideme.scene.LytItemImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.Files;
import java.util.List;

/** Loads and renders the actual resource-pack guide, then exits the isolated preview client. */
@EventBusSubscriber(modid = UselessStretcherMod.MODID, value = Dist.CLIENT)
public final class GuideChecks {
    private static final ResourceLocation GUIDE_ID = ResourceLocation.fromNamespaceAndPath("ae2", "guide");
    private static final List<String> PAGES = List.of(
            "useless_stretcher/index.md", "useless_stretcher/myriad.md",
            "useless_stretcher/stretcher.md", "useless_stretcher/staff.md",
            "useless_stretcher/range.md", "useless_stretcher/recipes.md",
            "useless_stretcher/extras.md");
    private static int ticks;
    private static int page;
    private static int waitFrames;
    private static boolean checked;
    private static boolean finished;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("useless_stretcher.guide_test") || finished) return;
        var client = Minecraft.getInstance();
        try {
            check(++ticks < 2400, "guide preview timed out");
            if (client.getOverlay() != null || client.screen == null) return;
            var guide = Guides.getById(GUIDE_ID);
            if (guide == null) return;
            if (!checked) {
                for (String path : PAGES) {
                    var content = guide.getPage(pageId(path));
                    check(content != null, "page compiled: " + path);
                    var document = content.document();
                    check(!document.getTextContent().contains("PARSING ERROR"), "markdown parsed: " + path);
                    int[] images = {0};
                    document.visit(new LytVisitor() {
                        public Result beforeNode(LytNode node) {
                            check(node.getStyle().color() != SymbolicColor.ERROR_TEXT, "no block error: " + path);
                            if (node instanceof LytItemImage image) {
                                check(!image.getItem().isEmpty(), "image item resolves: " + path);
                                images[0]++;
                            }
                            return Result.CONTINUE;
                        }
                        public Result beforeFlowContent(LytFlowContent content) {
                            // Recipe widgets legitimately use GuideME's in-game-only placeholder in preview.
                            // Recipe widgets are intentionally unresolved until a real player screen is open.
                            return Result.CONTINUE;
                        }
                    });
                    check(images[0] > 0 || path.endsWith("recipes.md") || path.endsWith("extras.md"),
                            "each page has an item image: " + path);
                }
                var index = guide.getIndex(ItemIndex.class);
                check(index.get(ResourceLocation.fromNamespaceAndPath("useless_stretcher", "omniversal_myriad")) != null, "myriad tooltip entry");
                check(index.get(ResourceLocation.fromNamespaceAndPath("useless_stretcher", "useless_stretcher")) != null, "stretcher tooltip entry");
                check(index.get(ResourceLocation.fromNamespaceAndPath("useless_stretcher", "wondrous_staff")) != null, "staff tooltip entry");
                checked = true;
            }
            if (waitFrames == 0) {
                String path = PAGES.get(page);
                GuideNavigation.navigateTo(guide, PageAnchor.page(pageId(path)));
                waitFrames = 20;
                return;
            }
            if (--waitFrames != 0) return;
            check(client.screen instanceof GuideScreen, "actual GuideME screen opened");
            var screenshots = client.gameDirectory.toPath().resolve("screenshots");
            Files.createDirectories(screenshots);
            String path = PAGES.get(page);
            try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                String name = path.substring(path.lastIndexOf('/') + 1, path.length() - 3);
                image.writeToFile(screenshots.resolve("guide-" + name + ".png"));
            }
            if (++page == PAGES.size()) {
                LogUtils.getLogger().info("GUIDE CHECKS PASSED: AE2 guide pages compiled/rendered, images and three tooltip entries resolved");
                finished = true;
                client.stop();
            }
        } catch (Throwable failure) {
            LogUtils.getLogger().error("GUIDE CHECKS FAILED", failure);
            finished = true;
            client.stop();
        }
    }

    private static ResourceLocation pageId(String path) {
        return ResourceLocation.fromNamespaceAndPath("ae2", path);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
