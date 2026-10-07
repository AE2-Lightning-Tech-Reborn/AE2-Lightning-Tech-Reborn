package com.moakiee.ae2lt.machine.largeoverload;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class LargeFactoryConfig {
    private static final List<ModConfigSpec.LongValue> OPERATIONS = new ArrayList<>();
    private static final List<ModConfigSpec.LongValue> THROUGHPUT = new ArrayList<>();
    private static final List<ModConfigSpec.LongValue> CAPACITY = new ArrayList<>();
    private static ModConfigSpec.IntValue energyMultiplier;
    private static ModConfigSpec.IntValue processSlots;
    private static ModConfigSpec.IntValue crystalSlots;
    private LargeFactoryConfig() { }

    public static void define(ModConfigSpec.Builder builder) {
        builder.push("largeOverloadFactory");
        energyMultiplier = builder.comment("FE multiplier per source operation; firmament mode always uses zero processing FE.")
                .defineInRange("energyMultiplier", 2, 1, 1024);
        processSlots = builder.comment("Slots in newly placed process-core hatches; existing inventories keep their saved size.")
                .defineInRange("processCoreSlots", 9, 8, 36);
        crystalSlots = builder.comment("Slots in newly placed crystal hatches; existing inventories keep their saved size.")
                .defineInRange("crystalSlots", 9, 1, 36);
        for (int tier = 0; tier < 4; tier++) {
            builder.push("tier" + (tier + 1));
            OPERATIONS.add(builder.comment("Cumulative source operations per server tick, shared by every hatch.")
                    .defineInRange("operationsPerTick", 16_384L << (tier * 2), 1, 1L << 40));
            THROUGHPUT.add(builder.comment("Total processing FE per tick; adding energy hatches does not increase this.")
                    .defineInRange("energyPerTick", 16_777_216L << (tier * 2), 1, 1L << 50));
            CAPACITY.add(builder.comment("Capacity of the factory's external FE buffer.")
                    .defineInRange("energyCapacity", 167_772_160L << (tier * 2), 1, 1L << 50));
            builder.pop();
        }
        builder.pop();
    }
    public static int processSlots() { return processSlots.get(); }
    public static int crystalSlots() { return crystalSlots.get(); }
    public static int tier(LargeFactoryComponent core) {
        return switch (core) { case CORE_T2 -> 1; case CORE_T3 -> 2; case CORE_T4 -> 3; default -> 0; };
    }
    public static long operations(LargeFactoryComponent core) {
        return core == LargeFactoryComponent.FIRMAMENT_CORE ? 1024 : OPERATIONS.get(tier(core)).get();
    }
    public static long throughput(LargeFactoryComponent core) { return THROUGHPUT.get(tier(core)).get(); }
    public static long capacity(LargeFactoryComponent core) { return CAPACITY.get(tier(core)).get(); }
    public static long energy(LargeFactoryRecipe recipe) {
        return recipe.process() == LargeFactoryRecipeAccess.Process.FIRMAMENT ? 0
                : Math.multiplyExact(recipe.energy(), energyMultiplier.get());
    }
}
