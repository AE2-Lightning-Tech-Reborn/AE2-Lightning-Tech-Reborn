package com.moakiee.ae2lt.crafting.big;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import com.moakiee.thunderbolt.compat.neoeco.NeoEcoFastPathCompat;
import java.math.BigDecimal;
import java.math.BigInteger;
import net.minecraft.world.level.Level;

/** Exact physical handover to an ECO FX lane; the CPU continues to own escrow and waiting. */
final class NeoEcoBigIntegerCompat {
    private NeoEcoBigIntegerCompat() {}

    static boolean dispatch(BigCraftingJob job, BigProviderRecipe recipe, IPatternDetails pattern,
            ICraftingProvider provider, Level level, IEnergyService energy, Runnable changed) {
        if (job.dispatchFailed || job.returning || !recipe.sharedInputs().isEmpty()) return false;
        BigInteger requested = job.remainingStepCopies();
        for (var input : recipe.inputs().entrySet())
            requested = requested.min(job.escrow.getOrDefault(input.getKey(), BigInteger.ZERO)
                    .divide(BigInteger.valueOf(input.getValue())));
        if (requested.signum() <= 0) return false;
        // Keep bounded transfers on the established long batch path. Exact transport is an opt-in
        // exception only when the count or a concrete material total cannot fit that contract.
        if (fitsLongBatch(recipe, requested)) return false;
        double singlePower = 0;
        for (var input : recipe.inputs().entrySet())
            singlePower += (double) input.getValue() / Math.max(1, input.getKey().getAmountPerOperation());
        if (!Double.isFinite(singlePower) || singlePower < 0) return false;
        var unitPower = BigDecimal.valueOf(singlePower);
        if (singlePower > 0) {
            var wantedExact = unitPower.multiply(new BigDecimal(requested));
            double wanted = Math.min(Double.MAX_VALUE, wantedExact.doubleValue());
            double available = energy.extractAEPower(wanted, Actionable.SIMULATE, PowerMultiplier.CONFIG);
            if (!Double.isFinite(available) || available <= 0) return false;
            if (available < wanted || wantedExact.compareTo(BigDecimal.valueOf(Double.MAX_VALUE)) > 0)
                requested = requested.min(new BigDecimal(available).divideToIntegralValue(unitPower).toBigInteger());
        }
        if (requested.signum() <= 0 || fitsLongBatch(recipe, requested)) return false;
        NeoEcoFastPathCompat.ExactBatch batch;
        try {
            batch = NeoEcoFastPathCompat.prepareExact(provider, pattern, recipe.template(),
                    requested, level, job.id);
        } catch (RuntimeException unavailable) {
            return false; // Preparation cannot transfer material ownership.
        }
        if (batch == null) return false;
        var offered = BigCraftingTransfer.of(recipe, batch.craftCount());
        job.reserve(offered);
        changed.run();
        try {
            boolean accepted = batch.submit();
            job.settle(offered, accepted ? offered : BigCraftingTransfer.of(recipe, BigInteger.ZERO));
            if (accepted)
                energy.extractAEPower(unitPower.multiply(new BigDecimal(batch.craftCount())).doubleValue(),
                        Actionable.MODULATE, PowerMultiplier.CONFIG);
            changed.run();
            return accepted;
        } catch (RuntimeException uncertain) {
            job.failDispatch();
            changed.run();
            appeng.core.AELog.warn("[ae2lt] Exact order %s stopped after indeterminate FX ownership: %s",
                    job.id, uncertain);
            return false;
        }
    }

    private static boolean fitsLongTotals(java.util.Map<appeng.api.stacks.AEKey, Long> units, long count) {
        for (long unit : units.values()) if (count > Long.MAX_VALUE / unit) return false;
        return true;
    }

    private static boolean fitsLongBatch(BigProviderRecipe recipe, BigInteger count) {
        return count.bitLength() <= 63 && fitsLongTotals(recipe.inputs(), count.longValueExact())
                && fitsLongTotals(recipe.outputs(), count.longValueExact());
    }
}
