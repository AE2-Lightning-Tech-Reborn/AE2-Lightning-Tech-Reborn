package com.moakiee.ae2lt.util;

import net.neoforged.neoforge.energy.IEnergyStorage;

/** Long inventory totals, independently of the legacy API's int transfer amounts. */
public interface LongEnergyStorage extends IEnergyStorage {
    long getStoredEnergyLong();

    long getCapacityLong();
}
