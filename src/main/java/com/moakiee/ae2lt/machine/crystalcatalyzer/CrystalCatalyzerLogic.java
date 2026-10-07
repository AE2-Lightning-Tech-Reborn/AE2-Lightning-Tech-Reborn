package com.moakiee.ae2lt.machine.crystalcatalyzer;

import java.util.Optional;
import appeng.api.networking.ticking.TickRateModulation;

import com.moakiee.ae2lt.blockentity.CrystalCatalyzerBlockEntity;
import com.moakiee.ae2lt.machine.common.AbstractGridRecipeMachineLogic;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerLockedRecipe;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipeCandidate;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.CrystalCatalyzerRecipeService;

/**
 * AE grid tick driver for the crystal catalyzer. No speed card support —
 * {@link #getMaxEnergyPerTickForSpeedCards} returns a single constant cap.
 */
public final class CrystalCatalyzerLogic extends AbstractGridRecipeMachineLogic<
        CrystalCatalyzerBlockEntity,
        CrystalCatalyzerLockedRecipe,
        CrystalCatalyzerRecipeCandidate> {

    private static final long MAX_ENERGY_PER_TICK = 200_000L;
    public static final int PIGMEE_PROCESS_TICKS = 5 * 20;

    private long lastPigmeeGameTime = Long.MIN_VALUE;
    private TickRateModulation lastPigmeeModulation = TickRateModulation.SLOWER;

    public CrystalCatalyzerLogic(CrystalCatalyzerBlockEntity host) {
        super(host);
    }

    public void tickStandalone() {
        if (host.isPigmeeVariant()) {
            tickMachine();
        }
    }

    @Override
    protected TickRateModulation tickMachine() {
        if (!host.isPigmeeVariant()) {
            return super.tickMachine();
        }
        var level = host.getLevel();
        if (host.isRemoved() || level == null || level.isClientSide()) {
            return TickRateModulation.SLEEP;
        }
        long gameTime = level.getGameTime();
        if (gameTime == lastPigmeeGameTime) {
            return lastPigmeeModulation;
        }
        lastPigmeeGameTime = gameTime;
        lastPigmeeModulation = super.tickMachine();
        return lastPigmeeModulation;
    }

    @Override
    protected int getMinProcessTicks() {
        return host.isPigmeeVariant() ? PIGMEE_PROCESS_TICKS : host.getMode().getMinProcessTicks();
    }

    @Override
    protected long getMaxEnergyPerTickForSpeedCards(int speedCards) {
        return MAX_ENERGY_PER_TICK;
    }

    @Override
    protected long getTotalEnergy(CrystalCatalyzerLockedRecipe lockedRecipe) {
        return host.isPigmeeVariant() ? 0L : lockedRecipe.totalEnergy();
    }

    @Override
    protected boolean shouldRechargeFromAppliedFlux() {
        return !host.isPigmeeVariant();
    }

    @Override
    protected void onEnergyFreeProcessingTick() {
        host.advanceEnergyFreeProcessingTick();
    }

    @Override
    protected Optional<CrystalCatalyzerRecipeCandidate> validateLockedRecipe(
            CrystalCatalyzerLockedRecipe lockedRecipe) {
        return CrystalCatalyzerRecipeService.findRecipeById(host.getLevel(), lockedRecipe.recipeId())
                .filter(candidate -> lockedRecipe.matchesFluidInput(candidate.recipe()))
                .filter(candidate -> !host.isPigmeeVariant()
                        || (candidate.recipe().isWaterRecipe()
                                && host.getInventory().getStackInSlot(CrystalCatalyzerInventory.SLOT_CATALYST).getCount()
                                        >= CrystalCatalyzerInventory.PIGMEE_CATALYST_SLOT_LIMIT))
                .filter(candidate -> candidate.recipe().mode() == host.getMode())
                .filter(candidate -> candidate.recipe().catalystMatches(
                        host.getInventory().getStackInSlot(CrystalCatalyzerInventory.SLOT_CATALYST)));
    }

    @Override
    protected boolean canAcceptOutputThisTick(CrystalCatalyzerLockedRecipe lockedRecipe) {
        var candidate = CrystalCatalyzerRecipeService.findRecipeById(host.getLevel(), lockedRecipe.recipeId());
        if (candidate.isEmpty() || !lockedRecipe.matchesFluidInput(candidate.get().recipe())
                || (host.isPigmeeVariant() && !candidate.get().recipe().isWaterRecipe())) {
            host.abortProcessing();
            return false;
        }
        return host.canAcceptLockedRecipeOutput(lockedRecipe)
                && host.canAdvanceLockedRecipe(lockedRecipe);
    }
}
