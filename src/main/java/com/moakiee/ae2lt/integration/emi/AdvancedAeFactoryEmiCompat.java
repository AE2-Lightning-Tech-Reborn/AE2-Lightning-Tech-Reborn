package com.moakiee.ae2lt.integration.emi;

import com.moakiee.ae2lt.registry.ModBlocks;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import net.pedroksl.advanced_ae.xmod.emi.recipes.EMIReactionChamberRecipe;

final class AdvancedAeFactoryEmiCompat {
    private AdvancedAeFactoryEmiCompat() {
    }

    static void registerWorkstation(EmiRegistry registry) {
        registry.addWorkstation(EMIReactionChamberRecipe.CATEGORY,
                EmiStack.of(ModBlocks.OVERLOAD_PROCESSING_FACTORY.get()));
    }
}
