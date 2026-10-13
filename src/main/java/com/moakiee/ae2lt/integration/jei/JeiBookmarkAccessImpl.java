package com.moakiee.ae2lt.integration.jei;

import java.util.List;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.runtime.IJeiRuntime;

final class JeiBookmarkAccessImpl {
    private static final Logger LOG = LogUtils.getLogger();
    private static IJeiRuntime runtime;
    private static JeiBookmarkWriter writer;
    private static EaepBookmarkAdapter eaep;
    private static boolean eaepResolved;

    private JeiBookmarkAccessImpl() {
    }

    static void setRuntime(IJeiRuntime newRuntime) {
        clearRuntime();
        runtime = newRuntime;
        try {
            writer = JeiBookmarkWriter.resolve(newRuntime, ITypedIngredient.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            LOG.warn("Direct JEI missing-material bookmarks are unavailable for this runtime", failure);
        }
    }

    static void clearRuntime() {
        runtime = null;
        writer = null;
        eaep = null;
        eaepResolved = false;
    }

    static boolean isAvailable(boolean eaepLoaded) {
        return runtime != null && (eaep(eaepLoaded) != null || writer != null);
    }

    static void addMissingToBookmarks(List<? extends AEKey> keys, boolean eaepLoaded) {
        if (runtime == null) {
            return;
        }
        var preferred = eaep(eaepLoaded);
        for (var key : keys) {
            if (!(key instanceof AEItemKey) && !(key instanceof AEFluidKey)) {
                continue;
            }
            if (preferred != null) {
                try {
                    preferred.add(key);
                    continue;
                } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                    eaep = null;
                    preferred = null;
                    LOG.warn("EAEP could not bookmark missing materials; falling back to JEI", failure);
                }
            }
            if (writer == null) {
                return;
            }
            try {
                var ingredient = key instanceof AEItemKey item
                        ? runtime.getIngredientManager().createTypedIngredient(
                                VanillaTypes.ITEM_STACK, item.toStack(), false)
                        : runtime.getIngredientManager().createTypedIngredient(
                                NeoForgeTypes.FLUID_STACK, ((AEFluidKey) key).toStack(1000), false);
                if (ingredient.isPresent()) {
                    writer.add(ingredient.get());
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                writer = null;
                LOG.warn("JEI could not bookmark missing materials; disabling direct bookmarks", failure);
                return;
            }
        }
    }

    private static EaepBookmarkAdapter eaep(boolean loaded) {
        if (!loaded) {
            return null;
        }
        if (!eaepResolved) {
            eaepResolved = true;
            try {
                eaep = EaepBookmarkAdapter.resolve(Class.forName(
                        "com.extendedae_plus.compat.JeiRuntimeCompat"));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                LOG.warn("EAEP bookmark adapter is unavailable; using direct JEI bookmarks", failure);
            }
        }
        if (eaep != null) {
            try {
                return eaep.isAvailable() ? eaep : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                eaep = null;
                LOG.warn("EAEP bookmark runtime is unavailable; using direct JEI bookmarks", failure);
            }
        }
        return null;
    }
}
