package com.moakiee.ae2lt.logic.energy;

import java.util.function.LongSupplier;

/** Cooperative deadline: bounds work between callbacks and always allows one delivery attempt. */
final class WirelessEnergyWorkBudget {
    private final LongSupplier clock;
    private final long started, nanos;
    private boolean dispatched;

    WirelessEnergyWorkBudget(double millis) { this(millis, System::nanoTime); }

    WirelessEnergyWorkBudget(double millis, LongSupplier clock) {
        this.clock = clock;
        started = clock.getAsLong();
        nanos = (long) (millis * 1_000_000);
    }

    boolean hasTime() { return clock.getAsLong() - started < nanos; }
    boolean canDispatch() { return !dispatched || hasTime(); }
    void dispatched() { dispatched = true; }
}
