package com.moakiee.ae2lt.logic.management;

import java.util.Arrays;

/** Exact server-tick windows. Empty ticks count as zero, including before the first receipt. */
public final class PowerHistory {
    public static final int[] WINDOWS = {1, 20, 100, 600, 1200};
    private final long[] ticks = new long[1201];
    private long lastTick = Long.MIN_VALUE;

    public void record(long tick, long accepted) {
        if (accepted <= 0) return;
        advance(tick);
        int slot = Math.floorMod(tick, ticks.length);
        ticks[slot] = Math.min(Long.MAX_VALUE - ticks[slot], accepted) + ticks[slot];
    }

    private void advance(long now) {
        if (lastTick == Long.MIN_VALUE || now < lastTick || now - lastTick >= ticks.length) {
            Arrays.fill(ticks, 0);
        } else {
            for (long t = lastTick + 1; t <= now; t++) ticks[Math.floorMod(t, ticks.length)] = 0;
        }
        lastTick = now;
    }

    /** Excludes the current, incomplete server tick so opening order cannot bias the 1t sample. */
    public double[] rates(long now) {
        advance(now);
        double sum = 0;
        double[] result = new double[WINDOWS.length];
        int window = 0;
        for (int age = 1; age <= 1200; age++) {
            sum += ticks[Math.floorMod(now - age, ticks.length)];
            if (age == WINDOWS[window]) result[window++] = sum / age;
        }
        return result;
    }
}
