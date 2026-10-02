package com.moakiee.ae2lt.integration.jei;

import java.util.List;

import appeng.api.stacks.AEKey;
import net.neoforged.fml.ModList;

public final class JeiBookmarkAccess {
    private JeiBookmarkAccess() {
    }

    public static boolean isAvailable() {
        var mods = ModList.get();
        return mods != null && mods.isLoaded("jei")
                && JeiBookmarkAccessImpl.isAvailable(mods.isLoaded("extendedae_plus"));
    }

    public static void addMissingToBookmarks(List<? extends AEKey> keys) {
        var mods = ModList.get();
        if (mods != null && mods.isLoaded("jei")) {
            JeiBookmarkAccessImpl.addMissingToBookmarks(keys, mods.isLoaded("extendedae_plus"));
        }
    }
}
