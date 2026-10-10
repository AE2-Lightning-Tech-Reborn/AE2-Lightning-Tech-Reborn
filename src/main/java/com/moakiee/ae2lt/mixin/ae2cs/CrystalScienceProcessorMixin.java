package com.moakiee.ae2lt.mixin.ae2cs;

import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.integration.ae2cs.OverclockPass;
import com.moakiee.ae2lt.integration.ae2cs.CrystalScienceOverclockIntegration;

import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.blockentity.ServerTickingBlockEntity;
import appeng.blockentity.grid.AENetworkBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reuses each CS processor's own recipe, power, fluid, and output checks per operation. */
@org.spongepowered.asm.mixin.Pseudo
@Mixin(targets = {
        "io.github.lounode.ae2cs.common.block.entity.CircuitEtcherBlockEntity",
        "io.github.lounode.ae2cs.common.block.entity.CrystalPulverizerBlockEntity",
        "io.github.lounode.ae2cs.common.block.entity.CrystalAggregatorBlockEntity",
        "io.github.lounode.ae2cs.common.block.entity.EntropyVariationReactionChamberBlockEntity"
}, remap = false)
public abstract class CrystalScienceProcessorMixin implements OverclockPass {
    @Unique
    private boolean ae2lt$extraPass;

    @Unique
    private int ae2lt$lastCapacityCards = -1;

    @Shadow(remap = false)
    public abstract int getActiveRecipeEnergyCost();

    @Shadow(remap = false)
    public abstract int getRecipeProgress();

    @Override
    public boolean ae2lt$isExtraPass() {
        return ae2lt$extraPass;
    }

    @Unique
    private int ae2lt$cardCount() {
        return ((IUpgradeableObject) this).getUpgrades()
                .getInstalledUpgrades(ModItems.OVERLOAD_PARALLEL_CARD.get());
    }

    @Inject(method = "getEnergyPerTick", at = @At("RETURN"), cancellable = true)
    private void ae2lt$finishRecipeInOnePass(CallbackInfoReturnable<Double> cir) {
        if (ae2lt$cardCount() > 0) {
            cir.setReturnValue(Math.max(cir.getReturnValueD(), getActiveRecipeEnergyCost()));
        }
    }

    @Inject(method = "serverTick", at = @At("HEAD"))
    private void ae2lt$updateEnergyCapacity(CallbackInfo ci) {
        if (!ae2lt$extraPass) {
            int cards = ae2lt$cardCount();
            if (cards != ae2lt$lastCapacityCards && CrystalScienceOverclockIntegration.scaleEnergyCapacity(
                    (AENetworkBlockEntity) (Object) this, cards)) {
                ae2lt$lastCapacityCards = cards;
            }
        }
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private void ae2lt$runExtraOperations(CallbackInfo ci) {
        if (ae2lt$extraPass || getActiveRecipeEnergyCost() <= 0) {
            return;
        }
        int cards = ae2lt$cardCount();
        if (cards <= 0) return;
        int operations = CrystalScienceOverclockIntegration.operationMultiplier(cards);
        BlockEntity machine = (BlockEntity) (Object) this;
        if (machine.getLevel() == null || machine.getLevel().isClientSide()) {
            return;
        }

        ae2lt$extraPass = true;
        try {
            for (int i = 1; i < operations; i++) {
                double powerBefore = ((IAEPowerStorage) this).getAECurrentPower();
                int progressBefore = getRecipeProgress();
                ((ServerTickingBlockEntity) this).serverTick();
                if (getActiveRecipeEnergyCost() <= 0) {
                    break;
                }
                if (getRecipeProgress() == progressBefore
                        && ((IAEPowerStorage) this).getAECurrentPower() == powerBefore) {
                    break;
                }
            }
        } finally {
            ae2lt$extraPass = false;
        }
    }
}
