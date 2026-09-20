package com.moakiee.ae2lt.logic.energy;

/** Per-machine hysteresis for network FE refill; no energy is held outside the machine. */
public final class MachineRechargeController {
    private boolean recharging;

    public boolean shouldRecharge(long stored, long capacity, long nextTickDemand, long maxTransfer) {
        if (capacity <= 0L || stored >= capacity) {
            recharging = false;
            return false;
        }
        if (stored >= highWatermark(capacity)) {
            recharging = false;
        }
        // A speed/configuration change or a large recipe must never wait for the low watermark.
        // When the configured transfer cannot outpace consumption, skipping even
        // one refill would needlessly drain the existing reserve sooner.
        if (stored <= capacity / 4L || stored < nextTickDemand || nextTickDemand >= maxTransfer) {
            recharging = true;
        }
        return recharging;
    }

    public void afterRecharge(long stored, long capacity) {
        // Observe the result before this tick consumes FE, otherwise high-throughput
        // machines could remain in refill mode forever after reaching the high watermark.
        if (stored >= highWatermark(capacity)) {
            recharging = false;
        }
    }

    private static long highWatermark(long capacity) {
        return capacity - capacity / 10L;
    }
}
