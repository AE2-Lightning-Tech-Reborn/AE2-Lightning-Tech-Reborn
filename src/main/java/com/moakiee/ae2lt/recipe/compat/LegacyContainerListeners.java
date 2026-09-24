package com.moakiee.ae2lt.recipe.compat;

/** Compatibility hook for the SimpleContainer callbacks removed in 26.1. */
public interface LegacyContainerListeners {
    void ae2lt$addChangeListener(Runnable listener);
}
