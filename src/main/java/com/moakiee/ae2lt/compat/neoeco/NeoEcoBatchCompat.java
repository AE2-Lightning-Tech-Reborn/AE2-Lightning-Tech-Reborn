package com.moakiee.ae2lt.compat.neoeco;

import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderResolver;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** Absence and older API shapes leave the ordinary provider path available. */
public final class NeoEcoBatchCompat {
    private NeoEcoBatchCompat() {}

    public static @Nullable BatchProviderResolver createAdapter() {
        var mods = ModList.get();
        if (mods == null || !mods.isLoaded("neoecoae")) return null;
        return loadAdapter(NeoEcoBatchCompat.class.getClassLoader());
    }

    static @Nullable BatchProviderResolver loadAdapter(ClassLoader loader) {
        try { return new NeoEcoBatchAdapter(new NeoEcoBatchApi(loader)); }
        catch (ReflectiveOperationException | LinkageError unavailable) {
            appeng.core.AELog.warn("[ae2lt] NeoECO allocated batch API unavailable; using ordinary dispatch. %s", unavailable);
            return null;
        }
    }
}
