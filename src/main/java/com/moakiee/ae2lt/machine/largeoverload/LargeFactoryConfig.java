package com.moakiee.ae2lt.machine.largeoverload;

import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

public final class LargeFactoryConfig {
    private static final long[] DEFAULT_OPERATIONS = {1_024, 16_384, 262_144, 0};
    private static final List<ForgeConfigSpec.LongValue> OPERATIONS = new ArrayList<>();
    private static final List<ForgeConfigSpec.LongValue> CAPACITY = new ArrayList<>();
    private static ForgeConfigSpec.IntValue energyMultiplier;
    public static final int AUXILIARY_SLOTS = 36;
    private LargeFactoryConfig() { }

    public static void define(ForgeConfigSpec.Builder builder) {
        builder.push("largeOverloadFactory");
        energyMultiplier = builder.comment("FE multiplier per source operation; firmament mode always uses zero processing FE.")
                .defineInRange("energyMultiplier", 2, 1, 1024);
        for (int tier = 0; tier < 4; tier++) {
            builder.push("tier" + (tier + 1));
            OPERATIONS.add(builder.comment("Cumulative source operations per server tick, shared by every hatch. 0 removes the operation cap; resource and server work budgets still apply.")
                    .defineInRange("operationsPerTick", DEFAULT_OPERATIONS[tier], 0, 1L << 40));
            CAPACITY.add(builder.comment("Capacity of the factory's external FE buffer.")
                    .defineInRange("energyCapacity", 167_772_160L << (tier * 2), 1, 1L << 50));
            builder.pop();
        }
        builder.pop();
    }
    public static int processSlots() { return AUXILIARY_SLOTS; }
    public static int crystalSlots() { return AUXILIARY_SLOTS; }
    public static int tier(LargeFactoryComponent core) {
        return switch (core) { case CORE_T2 -> 1; case CORE_T3 -> 2; case CORE_T4 -> 3; default -> 0; };
    }
    public static long operations(LargeFactoryComponent core) {
        if (core == LargeFactoryComponent.FIRMAMENT_CORE) return LargeFactoryOperationBudget.FIRMAMENT_OPERATIONS_PER_TICK;
        long configured = OPERATIONS.get(tier(core)).get();
        return configured == 0 ? LargeFactoryOperationBudget.UNLIMITED : configured;
    }
    public static long capacity(LargeFactoryComponent core) { return CAPACITY.get(tier(core)).get(); }
    public static long energy(LargeFactoryRecipe recipe) {
        return recipe.process() == LargeFactoryRecipeAccess.Process.FIRMAMENT ? 0
                : Math.multiplyExact(recipe.energy(), energyMultiplier.get());
    }
}
