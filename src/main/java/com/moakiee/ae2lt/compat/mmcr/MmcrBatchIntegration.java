package com.moakiee.ae2lt.compat.mmcr;

import appeng.api.crafting.IPatternDetails;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;

/** Optional class-loading boundary; MMCR API types stay inside the separately loaded bindings. */
public final class MmcrBatchIntegration {
    private static volatile boolean enabled;

    private MmcrBatchIntegration() { }

    public static synchronized void initialize() {
        if (enabled || ModList.get() == null || !ModList.get().isLoaded("mmcr")) return;
        try {
            enabled = MmcrApiBindings.initialize();
        } catch (RuntimeException | LinkageError unavailable) {
            LogUtils.getLogger().warn("AE2LT disabled MMCR batch integration: "
                    + "the installed MMCR does not provide AE2 Processing API v1", unavailable);
        }
    }

    public static boolean isBatchPattern(IPatternDetails pattern) {
        return enabled && MmcrApiBindings.isBatchPattern(pattern);
    }
}
