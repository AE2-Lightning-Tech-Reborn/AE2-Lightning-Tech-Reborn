package com.moakiee.ae2lt.logic.energy;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Provisional, server-configurable engineering limits. */
public final class WirelessEnergySettings {
    public static ModConfigSpec.BooleanValue runningNetworkFeFallback;
    public static ModConfigSpec.IntValue networkFe, targetFe, networkTargets, globalVisits, endpointLimit, reserveFe;
    public static void define(ModConfigSpec.Builder builder) {
        builder.push("wirelessControllerEnergy");
        runningNetworkFeFallback = builder.comment("Experimental: supplement AE requests from FE only while an advanced-controller network is already running. Never cold-starts a grid.")
                .define("runningNetworkFeFallback", false);
        networkFe = builder.comment("Maximum FE offered by all advanced controllers on one grid each tick; 0 disables output.")
                .defineInRange("networkFePerTick", 65536, 0, Integer.MAX_VALUE);
        targetFe = builder.defineInRange("targetFePerTick", 4096, 0, Integer.MAX_VALUE);
        networkTargets = builder.defineInRange("networkTargetsPerTick", 32, 1, 1024);
        globalVisits = builder.defineInRange("globalEndpointVisitsPerTick", 128, 1, 8192);
        endpointLimit = builder.defineInRange("endpointsPerFrequency", 256, 1, 8192);
        reserveFe = builder.comment("FE left in ME storage by this output path. Does not power AE or constrain other exporters.")
                .defineInRange("networkFeReserve", 10000, 0, Integer.MAX_VALUE);
        builder.pop();
    }
    private WirelessEnergySettings() {}
}
