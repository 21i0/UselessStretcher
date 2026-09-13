package com.sorrowmist.useless.stretcher.mixin;

import net.neoforged.fml.ModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Selects the legacy capacity patches only for Useless Mod versions before 2.3.7. */
public final class UselessStretcherMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> LEGACY_4096_MIXINS = Set.of(
            "ConfigManagerMixin", "RecoverableItemStackHandlerMixin", "ItemStackHandlerMixin",
            "PassiveCraftingHatchBlockEntityMixin", "PassiveCraftingHatchMenuMixin",
            "PassiveCraftingStatusPacketMixin", "PagedRecoverableMenuMixin",
            "PagedRecoverableMenuPageViewMixin", "PagedRecoverableScreenMixin",
            "PatternAssemblyScreenMixin", "PassiveCraftingHatchScreenMixin",
            "OreGeneratorMenuMixin", "OreGeneratorScreenMixin");

    private static Boolean native4096;

    @Override
    public void onLoad(String mixinPackage) {
        if (native4096 == null) native4096 = detectNative4096Support();
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!Boolean.TRUE.equals(native4096)) return true;
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        return !LEGACY_4096_MIXINS.contains(simpleName);
    }

    private static boolean detectNative4096Support() {
        try {
            String version = ModList.get().getModContainerById("useless_mod")
                    .map(container -> container.getModInfo().getVersion().toString()).orElse("");
            return compareVersion(extractModVersion(version), List.of(2, 3, 7)) >= 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String extractModVersion(String value) {
        int dash = value.lastIndexOf('-');
        return dash >= 0 ? value.substring(dash + 1) : value;
    }

    private static int compareVersion(String actual, List<Integer> expected) {
        String[] parts = actual.split("[.-]");
        for (int i = 0; i < expected.size(); i++) {
            int actualPart = 0;
            if (i < parts.length) {
                String digits = parts[i].replaceAll("[^0-9].*", "");
                if (!digits.isEmpty()) {
                    try { actualPart = Integer.parseInt(digits); }
                    catch (NumberFormatException ignored) { return -1; }
                }
            }
            int result = Integer.compare(actualPart, expected.get(i));
            if (result != 0) return result;
        }
        return 0;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass,
                                    String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass,
                                     String mixinClassName, IMixinInfo mixinInfo) { }
}
