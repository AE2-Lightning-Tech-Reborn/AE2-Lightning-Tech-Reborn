package com.moakiee.ae2lt.client.core.veil;

/** A guarded boundary for an optional Veil backend. */
public final class VeilCoreEffectShaders {
    private VeilCoreEffectShaders() {
    }

    public static boolean isApiCompatible() {
        // The 1.21 Veil shader-state bridge was removed with Minecraft's 26.1 pipeline rewrite.
        return false;
    }
}
