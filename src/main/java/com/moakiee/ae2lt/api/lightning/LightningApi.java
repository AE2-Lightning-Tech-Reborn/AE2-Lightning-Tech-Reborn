package com.moakiee.ae2lt.api.lightning;

import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.me.GridLightningEnergyHandler;
import com.moakiee.ae2lt.me.key.LightningKey;
import java.util.Objects;

/** Server-thread helpers for an addon's own AE node. Handles resolve the current grid on each call. */
public final class LightningApi {
    private LightningApi() {}

    public static ILightningEnergyHandler forHost(IActionHost host) {
        return forHost(host, IActionSource.ofMachine(host));
    }

    public static ILightningEnergyHandler forHost(IActionHost host, IActionSource source) {
        return new GridLightningEnergyHandler(host, source);
    }

    /** For callers already using AE2 storage. This does not check connectivity or permissions. */
    public static AEKey keyOf(LightningTier tier) {
        return LightningKey.of(Objects.requireNonNull(tier));
    }
}
