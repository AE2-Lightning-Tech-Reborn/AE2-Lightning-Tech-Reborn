package com.moakiee.ae2lt.machine.miningfactory;

import net.minecraftforge.common.ForgeConfigSpec;

public final class MiningFactoryConfig {
    private static ForgeConfigSpec.IntValue baseParallel;
    private static ForgeConfigSpec.IntValue parallelPerMatrix;
    private static ForgeConfigSpec.IntValue samples;
    private static ForgeConfigSpec.IntValue toolEnergy;
    private static ForgeConfigSpec.IntValue planeEnergy;

    private MiningFactoryConfig() {}

    public static void define(ForgeConfigSpec.Builder builder) {
        builder.push("miningFactory");
        baseParallel = builder.comment("Parallel capacity without matrices. Independent of the Overload Processing Factory settings.")
                .defineInRange("baseParallel", 8, 1, 4096);
        parallelPerMatrix = builder.comment("Parallel capacity per installed matrix, replacing baseParallel when matrices are installed. Up to eight matrices can be installed.")
                .defineInRange("parallelPerMatrix", 256, 1, Integer.MAX_VALUE / 8);
        samples = builder.comment("Maximum independent loot rolls per batch (despite the historical PerTick name). Each batch takes five ticks and rolls only on completion. Each roll represents an equal-sized group (remainder distributed across groups). Set at least the actual batch size for independent rolls; 2048 covers the default eight-matrix capacity.")
                .defineInRange("lootSamplesPerTick", 8, 1, 4096);
        toolEnergy = builder.comment("FE consumed per input block when using a durability tool.")
                .defineInRange("toolEnergyPerBlock", 256, 1, 500000);
        planeEnergy = builder.comment("Additional FE per input block when using an annihilation plane instead of a durability tool.")
                .defineInRange("planeExtraEnergyPerBlock", 768, 0, 500000);
        builder.pop();
    }

    public static int samples() { return samples.get(); }
    public static int parallelCapacity(int matrices) {
        return matrices <= 0 ? baseParallel.get()
                : Math.min(matrices, MiningFactoryInventory.MATRIX_SLOT_LIMIT) * parallelPerMatrix.get();
    }
    public static int energyPerBlock(boolean plane) { return toolEnergy.get() + (plane ? planeEnergy.get() : 0); }
}
