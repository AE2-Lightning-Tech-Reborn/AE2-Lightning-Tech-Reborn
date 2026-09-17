package com.moakiee.ae2lt.compat.neoeco;

import org.jetbrains.annotations.Nullable;

import net.neoforged.fml.ModList;

import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapter;

/** Keeps NeoECO API types outside the class-loading path when the optional mod is absent. */
public final class NeoEcoFastPathCompat {
    private static final String ADAPTER_CLASS =
            "com.moakiee.ae2lt.compat.neoeco.NeoEcoFastPathBatchAdapter";

    private NeoEcoFastPathCompat() {
    }

    @Nullable
    public static BatchProviderAdapter createAdapter() {
        // Plain unit tests do not initialize the NeoForge mod list.
        var modList = ModList.get();
        if (modList == null || !modList.isLoaded("neoecoae")) {
            return null;
        }
        try {
            return (BatchProviderAdapter) Class.forName(ADAPTER_CLASS)
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            appeng.core.AELog.warn(
                    "[ae2lt] NeoECO FastPath API is unavailable; Tianshu will use ordinary provider dispatch. %s",
                    unavailable);
            return null;
        }
    }
}
