package com.moakiee.ae2lt.integration.ae2cs;

import com.moakiee.ae2lt.registry.ModItems;

import appeng.api.upgrades.Upgrades;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.blockentity.grid.AENetworkBlockEntity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

/** Registers the LT card only for AE2CS machines present in the installed version. */
public final class CrystalScienceOverclockIntegration {
    private static final String[] PROCESSORS = {
            "crystal_growth_chamber",
            "circuit_etcher",
            "crystal_pulverizer",
            "crystal_aggregator",
            "entropy_variation_reaction_chamber",
            // Added after AE2CS 1.2.1.
            "crystal_infuser",
            "pulse_centrifuge"
    };

    private CrystalScienceOverclockIntegration() {
    }

    public static void registerUpgrades() {
        if (!ModList.get().isLoaded("ae2cs")) {
            return;
        }
        for (String path : PROCESSORS) {
            BuiltInRegistries.BLOCK.getOptional(new ResourceLocation("ae2cs", path))
                    .ifPresent(block -> Upgrades.add(ModItems.OVERLOAD_PARALLEL_CARD.get(), block, 2));
        }
    }

    /** Called once per normal machine tick, before CS refills the expanded buffer. */
    public static boolean scaleEnergyCapacity(AENetworkBlockEntity machine, int cards) {
        var node = machine.getMainNode().getNode();
        if (node == null) return false;
        var storage = node.getService(IAEPowerStorage.class);
        if (storage instanceof ScaledEnergyCapacity scaled) {
            scaled.ae2lt$setParallelCards(cards);
            return true;
        }
        return false;
    }
}
