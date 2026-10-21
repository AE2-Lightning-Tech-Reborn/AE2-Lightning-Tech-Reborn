package com.moakiee.ae2lt.crafting.big;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import net.minecraft.world.level.Level;

/**
 * Exact physical handover to an ECO FX lane. Thunderbolt 2.0.3-beta2 has no
 * {@code prepareExact}/{@code ExactBatch}, so counts that do not fit the long batch path stay on
 * the normal dispatcher until a core that exposes that API is paired.
 */
final class NeoEcoBigIntegerCompat {
    private NeoEcoBigIntegerCompat() {}

    static boolean dispatch(BigCraftingJob job, BigProviderRecipe recipe, IPatternDetails pattern,
            ICraftingProvider provider, Level level, IEnergyService energy, Runnable changed) {
        return false;
    }
}
