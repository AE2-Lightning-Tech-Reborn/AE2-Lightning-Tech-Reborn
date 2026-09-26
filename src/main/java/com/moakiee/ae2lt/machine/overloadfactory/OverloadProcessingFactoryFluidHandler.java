package com.moakiee.ae2lt.machine.overloadfactory;

import java.util.Objects;

import net.neoforged.neoforge.fluids.FluidStack;
import com.moakiee.ae2lt.util.IndexedFluidHandler;

public final class OverloadProcessingFactoryFluidHandler implements IndexedFluidHandler {
    private final NotifyingFluidTank inputTank;
    private final NotifyingFluidTank outputTank;

    public OverloadProcessingFactoryFluidHandler(NotifyingFluidTank inputTank, NotifyingFluidTank outputTank) {
        this.inputTank = Objects.requireNonNull(inputTank, "inputTank");
        this.outputTank = Objects.requireNonNull(outputTank, "outputTank");
    }

    @Override
    public int getTanks() {
        return 2;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return switch (tank) {
            case 0 -> inputTank.getFluid();
            case 1 -> outputTank.getFluid();
            default -> FluidStack.EMPTY;
        };
    }

    @Override
    public int getTankCapacity(int tank) {
        return switch (tank) {
            case 0 -> inputTank.getCapacity();
            case 1 -> outputTank.getCapacity();
            default -> 0;
        };
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tank == 0 && inputTank.isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return inputTank.fill(resource, action);
    }

    @Override
    public int fillTank(int tank, FluidStack resource, FluidAction action) {
        Objects.checkIndex(tank, getTanks());
        return tank == 0 ? inputTank.fill(resource, action) : 0;
    }

    @Override
    public FluidStack drainTank(int tank, FluidStack resource, FluidAction action) {
        Objects.checkIndex(tank, getTanks());
        return tank == 1 ? outputTank.drain(resource, action) : FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return outputTank.drain(resource, action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return outputTank.drain(maxDrain, action);
    }
}
