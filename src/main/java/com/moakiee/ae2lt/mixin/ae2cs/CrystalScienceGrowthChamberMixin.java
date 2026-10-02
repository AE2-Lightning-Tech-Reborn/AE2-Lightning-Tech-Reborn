package com.moakiee.ae2lt.mixin.ae2cs;

import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.integration.ae2cs.OverclockPass;
import com.moakiee.ae2lt.integration.ae2cs.CrystalScienceOverclockIntegration;

import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.inventories.InternalInventory;
import appeng.blockentity.ServerTickingBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The growth chamber has a work interval instead of a recipe energy budget. */
@Mixin(targets = "io.github.lounode.ae2cs.common.block.entity.CrystalGrowthChamberBlockEntity", remap = false)
public abstract class CrystalScienceGrowthChamberMixin implements OverclockPass {
    @Unique
    private boolean ae2lt$extraPass;

    @Unique
    private int ae2lt$lastCapacityCards = -1;

    @Shadow(remap = false)
    public abstract InternalInventory getInternalInventory();

    @Override
    public boolean ae2lt$isExtraPass() {
        return ae2lt$extraPass;
    }

    @Unique
    private int ae2lt$cardCount() {
        return ((IUpgradeableObject) this).getUpgrades()
                .getInstalledUpgrades(ModItems.OVERLOAD_PARALLEL_CARD.get());
    }

    @Inject(method = "getWorkInterval", at = @At("RETURN"), cancellable = true)
    private void ae2lt$workEveryTick(CallbackInfoReturnable<Integer> cir) {
        if (ae2lt$cardCount() > 0) {
            cir.setReturnValue(1);
        }
    }

    @Inject(method = "serverTick", at = @At("HEAD"))
    private void ae2lt$updateEnergyCapacity(CallbackInfo ci) {
        if (!ae2lt$extraPass) {
            int cards = ae2lt$cardCount();
            if (cards != ae2lt$lastCapacityCards && CrystalScienceOverclockIntegration.scaleEnergyCapacity(
                    (AENetworkedBlockEntity) (Object) this, cards)) {
                ae2lt$lastCapacityCards = cards;
            }
        }
    }

    // Nearby blocks cannot change between passes of the same server tick.
    @Inject(method = "updateGrowthNum", at = @At("HEAD"), cancellable = true)
    private void ae2lt$skipRepeatedNeighborScan(CallbackInfo ci) {
        if (ae2lt$extraPass) {
            ci.cancel();
        }
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private void ae2lt$growFourTimes(CallbackInfo ci) {
        if (ae2lt$extraPass || getInternalInventory().isEmpty()) {
            return;
        }
        int cards = ae2lt$cardCount();
        if (cards <= 0) return;
        int operations = cards >= 2 ? 64 : 8;
        BlockEntity machine = (BlockEntity) (Object) this;
        if (machine.getLevel() == null || machine.getLevel().isClientSide()) {
            return;
        }
        ae2lt$extraPass = true;
        try {
            for (int i = 1; i < operations; i++) {
                double powerBefore = ((IAEPowerStorage) this).getAECurrentPower();
                ((ServerTickingBlockEntity) this).serverTick();
                if (((IAEPowerStorage) this).getAECurrentPower() == powerBefore) {
                    break;
                }
            }
        } finally {
            ae2lt$extraPass = false;
        }
    }
}
