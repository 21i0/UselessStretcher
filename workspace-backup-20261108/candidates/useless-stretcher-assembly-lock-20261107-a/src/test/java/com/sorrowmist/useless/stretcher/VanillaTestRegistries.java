package com.sorrowmist.useless.stretcher;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;

import java.util.List;

import static org.mockito.Mockito.*;

/** Initializes vanilla codecs only, without a client, server, world or mod launch. */
public final class VanillaTestRegistries {
    private static boolean initialized;

    private VanillaTestRegistries() {}

    public static synchronized void initialize() {
        if (initialized) return;
        // Patched FeatureFlags reads the mod file list during vanilla bootstrap.
        // Standalone JUnit has no loader, so supply the empty list for this scope.
        LoadingModList empty = mock(LoadingModList.class);
        when(empty.getModFiles()).thenReturn(List.of());
        try (var loading = mockStatic(LoadingModList.class)) {
            loading.when(LoadingModList::get).thenReturn(empty);
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
        initialized = true;
    }
}
