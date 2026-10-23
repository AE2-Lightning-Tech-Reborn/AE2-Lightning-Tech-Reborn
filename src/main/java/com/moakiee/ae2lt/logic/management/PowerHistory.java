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
        } else if (now > lastTick) {
            int start = Math.floorMod(lastTick, ticks.length) + 1;
            int end = Math.floorMod(now, ticks.length) + 1;
            if (start > end) {
                Arrays.fill(ticks, start, ticks.length, 0);
                Arrays.fill(ticks, 0, end, 0);
            } else {
                Arrays.fill(ticks, start, end, 0);
            }
        }
        lastTick = now;
    }

    /** Excludes the current, incomplete server tick so opening order cannot bias the 1t sample. */
    public double[] rates(long now) {
        advance(now);
        double sum = 0;
        double[] result = new double[WINDOWS.length];
        int slot = Math.floorMod(now, ticks.length);
        int age = 0;
        // Walk contiguous spans newest-first, preserving addition order without per-sample modulo.
        for (int window = 0; window < WINDOWS.length; window++) {
            int remaining = WINDOWS[window] - age;
            while (remaining > 0) {
                if (slot == 0) slot = ticks.length;
                int end = Math.max(0, slot - remaining);
                for (int i = slot - 1; i >= end; i--) sum += ticks[i];
                remaining -= slot - end;
                slot = end;
            }
            age = WINDOWS[window];
            result[window] = sum / age;
        }
        return result;
    }
}
