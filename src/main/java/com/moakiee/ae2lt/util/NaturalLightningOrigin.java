package com.moakiee.ae2lt.util;

/** Per-bolt origin synchronized through vanilla entity metadata on Forge 1.20.1. */
public interface NaturalLightningOrigin {
    boolean ae2lt$isNaturalWeatherLightning();

    void ae2lt$setNaturalWeatherLightning(boolean natural);
}
