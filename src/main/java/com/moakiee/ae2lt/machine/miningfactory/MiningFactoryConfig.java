package com.moakiee.ae2lt.machine.miningfactory;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class MiningFactoryConfig {
    private static ModConfigSpec.IntValue samples;
    private static ModConfigSpec.IntValue toolEnergy;
    private static ModConfigSpec.IntValue planeEnergy;

    private MiningFactoryConfig() {}

    public static void define(ModConfigSpec.Builder builder) {
        builder.push("miningFactory");
        samples = builder.comment("Maximum independent loot rolls per batch. Each batch takes five ticks and rolls only on completion. Parallel capacity follows the Overload Processing Factory matrix settings. Each roll represents an equal-sized group (remainder distributed across groups). Set at least the matrix parallel capacity for independent rolls.")
                .defineInRange("lootSamplesPerTick", 8, 1, 1024);
        toolEnergy = builder.comment("FE consumed per input block when using a durability tool.")
                .defineInRange("toolEnergyPerBlock", 256, 1, 500000);
        planeEnergy = builder.comment("Additional FE per input block when using an annihilation plane instead of a durability tool.")
                .defineInRange("planeExtraEnergyPerBlock", 768, 0, 500000);
        builder.pop();
    }

    public static int samples() { return samples.get(); }
    public static int energyPerBlock(boolean plane) { return toolEnergy.get() + (plane ? planeEnergy.get() : 0); }
}
