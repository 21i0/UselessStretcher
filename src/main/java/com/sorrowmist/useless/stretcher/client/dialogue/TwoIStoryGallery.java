package com.sorrowmist.useless.stretcher.client.dialogue;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** The seventeen original images supplied for the 2i visual novel, with source aspect ratios. */
public final class TwoIStoryGallery {
    public record Picture(String file, int width, int height, int category) {
        public ResourceLocation texture() {
            return ResourceLocation.fromNamespaceAndPath("useless_stretcher", "textures/dialogue/" + file + ".png");
        }

        public String titleKey() {
            return "gui.useless_stretcher.two_i.vn.picture." + file.replace('/', '.');
        }
    }

    private static final List<Picture> PICTURES = List.of(
            new Picture("cg/first_meet", 836, 471, 1),
            new Picture("cg/friends", 836, 471, 1),
            new Picture("cg/starfield_invite", 836, 471, 1),
            new Picture("expressions/calm", 512, 768, 2),
            new Picture("expressions/smile", 512, 768, 2),
            new Picture("expressions/happy", 512, 768, 2),
            new Picture("expressions/shy", 512, 768, 2),
            new Picture("expressions/shy_alt", 512, 768, 2),
            new Picture("expressions/surprised", 512, 768, 2),
            new Picture("expressions/confused", 512, 768, 2),
            new Picture("expressions/tsundere", 512, 768, 2),
            new Picture("expressions/determined", 512, 768, 2),
            new Picture("expressions/guilty", 512, 768, 2),
            new Picture("portrait/2i_portrait", 627, 627, 3),
            new Picture("ui/dialogue_frame", 1086, 362, 3),
            new Picture("ui/nameplate", 1086, 362, 3),
            new Picture("ui/bow", 627, 627, 3));

    private TwoIStoryGallery() { }

    public static List<Picture> pictures(int category) {
        return category == 0 ? PICTURES : PICTURES.stream().filter(picture -> picture.category() == category).toList();
    }
}
