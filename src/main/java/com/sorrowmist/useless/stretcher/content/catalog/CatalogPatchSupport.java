package com.sorrowmist.useless.stretcher.content.catalog;

import net.neoforged.fml.ModList;

public final class CatalogPatchSupport {
    private CatalogPatchSupport() {}
    public static boolean enabled() {
        var list = ModList.get();
        return list != null && list.getModContainerById("useless_mod")
                .map(c -> verifiedVersion(c.getModInfo().getVersion().toString())).orElse(false);
    }
    public static boolean verifiedVersion(String version) {
        return "1.21.1-2.4.6".equals(version) || "1.21.1-2.4.6.2".equals(version)
                || "1.21.1-2.4.6.5".equals(version) || "1.21.1-2.4.6.6".equals(version);
    }
    public static boolean jeiPublicationEnabled() {
        var list = ModList.get();
        return enabled() && list.getModContainerById("jei")
                .map(c -> "19.57.0.451".equals(c.getModInfo().getVersion().toString())
                        || "19.70.0.460".equals(c.getModInfo().getVersion().toString())
                        || "19.70.0.469".equals(c.getModInfo().getVersion().toString())).orElse(false);
    }
}
