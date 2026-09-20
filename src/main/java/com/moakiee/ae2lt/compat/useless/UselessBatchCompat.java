package com.moakiee.ae2lt.compat.useless;

import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderResolver;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** Isolates optional API classes, including installations of Useless predating the API. */
public final class UselessBatchCompat {
    private UselessBatchCompat() {
    }

    public static @Nullable BatchProviderResolver createAdapter() {
        var modList = ModList.get();
        if (modList == null || !modList.isLoaded("useless_mod")) return null;
        return loadAdapter(UselessBatchCompat.class.getClassLoader());
    }

    static @Nullable BatchProviderResolver loadAdapter(ClassLoader loader) {
        try {
            return new UselessBatchAdapter(new UselessBatchApi(loader));
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            appeng.core.AELog.warn(
                    "[ae2lt] Useless batch API is unavailable; using ordinary provider dispatch. %s",
                    unavailable);
            return null;
        }
    }
}
