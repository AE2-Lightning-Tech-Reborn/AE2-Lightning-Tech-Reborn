package com.moakiee.ae2lt.machine.overloadfactory;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

import com.moakiee.ae2lt.util.MixinReflectionSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

public final class NotifyingFluidTank extends FluidTank {
    private final Runnable changeListener;
    private boolean exporting;
    private boolean savedDuringExport;

    public NotifyingFluidTank(int capacity, Runnable changeListener) {
        this(capacity, fluidStack -> true, changeListener);
    }

    public NotifyingFluidTank(int capacity, Predicate<FluidStack> validator, Runnable changeListener) {
        super(capacity, validator);
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return exporting ? 0 : super.fill(resource, action);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return exporting ? FluidStack.EMPTY : super.drain(resource, action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return exporting ? FluidStack.EMPTY : super.drain(maxDrain, action);
    }

    @Override
    public void setFluid(FluidStack stack) {
        requireMutable();
        super.setFluid(stack);
    }

    @Override
    public FluidTank setCapacity(int capacity) {
        requireMutable();
        return super.setCapacity(capacity);
    }

    @Override
    public FluidTank setValidator(Predicate<FluidStack> validator) {
        requireMutable();
        return super.setValidator(validator);
    }

    @Override
    public FluidTank readFromNBT(CompoundTag nbt) {
        requireMutable();
        return super.readFromNBT(nbt);
    }

    @Override
    public CompoundTag writeToNBT(CompoundTag nbt) {
        if (exporting) savedDuringExport = true;
        return super.writeToNBT(nbt);
    }

    /**
     * Detaches and reserves the current fluid before invoking foreign storage. Only a
     * confirmed rejection is restored, with its tags intact and its space reserved.
     * The return value is the retired amount, including an unknown receipt: a receiver
     * may already have credited the offer before throwing or returning a malformed count.
     * Such an attempt cannot safely be replayed; losslessness requires an honest receiver.
     */
    public long exportFluid(ToLongFunction<FluidStack> insertion) {
        Objects.requireNonNull(insertion, "insertion");
        if (exporting || fluid.isEmpty()) return 0;
        // Do not let a previously obtained live FluidStack alias mutate the reserved offer.
        FluidStack original = fluid.copy();
        // Until a valid receipt arrives, even a fatal error leaves ownership uncertain.
        long retired = original.getAmount();
        exporting = true;
        savedDuringExport = false;
        fluid = FluidStack.EMPTY;
        try {
            try {
                long receipt = insertion.applyAsLong(original.copy());
                if (receipt < 0 || receipt > original.getAmount()) {
                    throw new IllegalStateException("Storage returned invalid inserted amount: " + receipt);
                }
                retired = receipt;
            } catch (RuntimeException | LinkageError uncertain) {
                retired = original.getAmount();
                MixinReflectionSupport.logReflectionFailure("export machine fluid to "
                        + insertion.getClass().getName() + " (receipt unknown; attempt will not be retried)", uncertain);
            }
            return retired;
        } finally {
            if (retired == original.getAmount()) {
                fluid = FluidStack.EMPTY;
            } else {
                fluid = original.copy();
                fluid.setAmount(original.getAmount() - (int) retired);
            }
            exporting = false;
            // A callback snapshot omitted the reservation; persist any restored remainder.
            if (retired > 0 || savedDuringExport) onContentsChanged();
        }
    }

    private void requireMutable() {
        if (exporting) throw new IllegalStateException("Fluid tank mutation during external export");
    }

    @Override
    protected void onContentsChanged() {
        changeListener.run();
    }
}

