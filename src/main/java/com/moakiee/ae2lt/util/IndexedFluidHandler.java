package com.moakiee.ae2lt.util;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Per-tank operations with the same access restrictions as the exposed capability. */
public interface IndexedFluidHandler extends IFluidHandler {
    int fillTank(int tank, FluidStack resource, FluidAction action);

    FluidStack drainTank(int tank, FluidStack resource, FluidAction action);
}
