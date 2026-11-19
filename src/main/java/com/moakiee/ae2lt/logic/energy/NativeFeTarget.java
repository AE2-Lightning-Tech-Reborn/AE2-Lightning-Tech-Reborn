package com.moakiee.ae2lt.logic.energy;

import java.util.OptionalLong;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Native FE is the only observation backend admitted so far. Other backends remain unknown. */
record NativeFeTarget(IEnergyStorage target) implements TargetAccess {
    @Override public long simulateReceive(long amount) {
        return Math.max(0, target.receiveEnergy((int) Math.min(Integer.MAX_VALUE, Math.max(0, amount)), true));
    }
    @Override public long receive(long amount) {
        return Math.max(0, target.receiveEnergy((int) Math.min(Integer.MAX_VALUE, Math.max(0, amount)), false));
    }
    @Override public OptionalLong storedEnergyFe() {
        int value = target.getEnergyStored(), capacity = target.getMaxEnergyStored();
        return value < 0 || capacity <= 0 || value > capacity || value == Integer.MAX_VALUE
                ? OptionalLong.empty() : OptionalLong.of(value);
    }
}
