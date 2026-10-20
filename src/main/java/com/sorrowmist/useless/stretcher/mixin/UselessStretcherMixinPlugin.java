package com.sorrowmist.useless.stretcher.mixin;

import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Only load integration mixins when their optional mods are present. */
public final class UselessStretcherMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) { }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".OccultismRitualClockMixin")) return isModLoadedEarly("occultism");
        if (mixinClassName.endsWith(".StaffRadiationShieldingMixin") || mixinClassName.endsWith(".StaffRadiationEntityMixin"))
            return isModLoadedEarly("mekanism");
        if (mixinClassName.endsWith(".TwoIArsRitualMixin") || mixinClassName.endsWith(".TwoIArsJarMixin"))
            return isModLoadedEarly("ars_nouveau");
        if (mixinClassName.endsWith(".TwoIBeeEncaseMixin") || mixinClassName.endsWith(".TwoIAmberMixin"))
            return isModLoadedEarly("productivebees");
        if (mixinClassName.endsWith(".ApotheosisEnchantingMenuMixin")) {
            return isModLoadedEarly("apotheosis");
        }
        if (mixinClassName.endsWith(".ApotheosisEnchantingStatsMixin")) {
            // Apotheosis 1.20.1 contains the enchanting module.
            return isModLoadedEarly("apotheosis");
        }
        return true;
    }

    private static boolean isModLoadedEarly(String modId) {
        try {
            var loadingList = FMLLoader.getLoadingModList();
            if (loadingList != null) {
                return loadingList.getMods().stream().anyMatch(mod -> modId.equals(mod.getModId()));
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Continue to the normal list when bootstrap has already completed.
        }
        try {
            var modList = net.minecraftforge.fml.ModList.get();
            return modList != null && modList.isLoaded(modId);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass,
                                    String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass,
                                     String mixinClassName, IMixinInfo mixinInfo) { }
}
