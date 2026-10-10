package com.moakiee.ae2lt.mixin.ae2cs;

import com.moakiee.ae2lt.integration.ae2cs.ScaledEnergyCapacity;
import com.moakiee.ae2lt.integration.ae2cs.CrystalScienceOverclockIntegration;

import appeng.me.energy.StoredEnergyAmount;

import net.minecraft.nbt.CompoundTag;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scales CS's actual stored-energy capacity, including its normal grid refill. */
@org.spongepowered.asm.mixin.Pseudo
@Mixin(targets = "io.github.lounode.ae2cs.common.machine.component.EnergyComponent", remap = false)
public abstract class CrystalScienceEnergyCapacityMixin implements ScaledEnergyCapacity {
    @Shadow @Final private StoredEnergyAmount storedEnergy;

    @Unique private double ae2lt$baseCapacity;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2lt$rememberBaseCapacity(CallbackInfo ci) {
        ae2lt$baseCapacity = storedEnergy.getMaximum();
    }

    @Inject(method = "readNbt", at = @At("HEAD"))
    private void ae2lt$preserveLargeStoredEnergy(CompoundTag tag, CallbackInfo ci) {
        // CS loads energy before its upgrade slots. Keep saved power until the first server tick
        // can establish the capacity from the restored card count.
        double saved = tag.getDouble("stored_energy");
        if (saved > ae2lt$baseCapacity) {
            storedEnergy.setMaximum(Math.min(saved, ae2lt$baseCapacity * 64));
        }
    }

    @Override
    public void ae2lt$setParallelCards(int cards) {
        double capacity = ae2lt$baseCapacity * CrystalScienceOverclockIntegration.operationMultiplier(cards);
        if (storedEnergy.getMaximum() != capacity) {
            storedEnergy.setMaximum(capacity);
        }
    }
}
