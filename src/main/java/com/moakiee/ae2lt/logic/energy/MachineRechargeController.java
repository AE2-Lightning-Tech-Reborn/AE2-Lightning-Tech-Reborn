package com.moakiee.ae2lt.logic.energy;

/** Batches network FE refills without leaving idle or reloaded machines partially charged. */
public final class MachineRechargeController {
    private static final long TOP_UP_INTERVAL = 20L;
    private boolean recharging;
    private long lastAttemptTick = Long.MIN_VALUE;

    public boolean shouldRecharge(long stored, long capacity, long nextTickDemand, long maxTransfer, long gameTime) {
        if (capacity <= 0L || stored >= capacity) {
            recharging = false;
            lastAttemptTick = gameTime;
            return false;
        }
        // A speed/configuration change or a large recipe must never wait for the low watermark.
        // When the configured transfer cannot outpace consumption, skipping even
        // one refill would needlessly drain the existing reserve sooner.
        if (stored <= capacity / 4L || stored < nextTickDemand || nextTickDemand >= maxTransfer
                || lastAttemptTick == Long.MIN_VALUE || gameTime < lastAttemptTick
                || gameTime - lastAttemptTick >= TOP_UP_INTERVAL) {
            recharging = true;
        }
        if (recharging) lastAttemptTick = gameTime;
        return recharging;
    }

    public void afterRecharge(long stored, long capacity) {
        // Observe the result before this tick consumes FE, otherwise high-throughput
        // machines could remain in refill mode forever after reaching the high watermark.
        if (stored >= capacity) {
            recharging = false;
        }
    }
}
