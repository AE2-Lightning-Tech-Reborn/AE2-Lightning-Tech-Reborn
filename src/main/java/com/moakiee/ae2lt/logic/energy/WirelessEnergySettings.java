package com.moakiee.ae2lt.logic.energy;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side work budgets; wireless FE output follows the receiver's demand. */
public final class WirelessEnergySettings {
    public static ModConfigSpec.BooleanValue runningNetworkFeFallback;
    public static ModConfigSpec.IntValue networkFe, networkTargets, reserveFe;
    public static ModConfigSpec.DoubleValue tickBudgetMillis;
    public static void define(ModConfigSpec.Builder builder) {
        builder.push("wirelessControllerEnergy");
        runningNetworkFeFallback = builder.comment("Experimental: supplement AE requests from FE only while an advanced-controller network is already running. Never cold-starts a grid.")
                .define("runningNetworkFeFallback", false);
        networkFe = builder.comment("FE-to-AE fallback budget only. Does not limit wireless FE output; 0 disables fallback withdrawals.")
                .defineInRange("networkFePerTick", 65536, 0, Integer.MAX_VALUE);
        networkTargets = builder.comment("FE-to-AE fallback IO attempts per grid per tick only.")
                .defineInRange("networkTargetsPerTick", 32, 1, 1024);
        tickBudgetMillis = builder.comment("Soft global wireless FE work budget in milliseconds per server tick. Unfinished work resumes fairly next tick; a single mod callback cannot be interrupted.")
                .defineInRange("tickBudgetMillis", 2.0, 0.1, 10.0);
        reserveFe = builder.comment("FE left in ME storage by this output path. Does not power AE or constrain other exporters.")
                .defineInRange("networkFeReserve", 10000, 0, Integer.MAX_VALUE);
        builder.pop();
    }
    private WirelessEnergySettings() {}
}
