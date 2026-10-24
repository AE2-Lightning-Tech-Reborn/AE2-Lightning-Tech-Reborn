package com.moakiee.ae2lt.logic.management;

import java.util.Arrays;

/** Exact server-tick windows. Empty ticks count as zero, including before the first receipt. */
public final class PowerHistory {
    public static final int[] WINDOWS = {1, 20, 100, 600, 1200};
    private static final int HISTORY_SIZE = 1201;
    private long[] ticks;
    private long lastTick = Long.MIN_VALUE;
    private int currentSlot;
    // A constant contiguous receipt run needs no ring. Irregular receipts materialize it exactly.
    private long uniformStart, uniformEnd, uniformAmount;

    public void record(long tick, long accepted) {
        if (accepted <= 0) return;
        advance(tick);
        if (ticks == null) {
            if (uniformAmount == 0) {
                uniformStart = uniformEnd = tick;
                uniformAmount = accepted;
                return;
            }
            if (tick - uniformEnd == 1 && accepted == uniformAmount) {
                uniformEnd = tick;
                return;
            }
            if (tick == uniformEnd && uniformStart == uniformEnd) {
                uniformAmount = Math.min(Long.MAX_VALUE - uniformAmount, accepted) + uniformAmount;
                return;
            }
            materialize(tick);
        }
        int slot = currentSlot;
        ticks[slot] = Math.min(Long.MAX_VALUE - ticks[slot], accepted) + ticks[slot];
    }

    private void materialize(long now) {
        ticks = new long[HISTORY_SIZE];
        int slot = Math.floorMod(uniformStart, HISTORY_SIZE);
        int count = (int) (uniformEnd - uniformStart) + 1;
        for (int i = 0; i < count; i++) {
            ticks[slot] = uniformAmount;
            if (++slot == HISTORY_SIZE) slot = 0;
        }
        currentSlot = Math.floorMod(now, HISTORY_SIZE);
        uniformAmount = 0;
    }

    private void advance(long now) {
        if (ticks == null) {
            if (lastTick == Long.MIN_VALUE || now < lastTick || now - lastTick >= HISTORY_SIZE
                    || (uniformAmount != 0 && now - uniformEnd >= HISTORY_SIZE)) {
                uniformAmount = 0;
            } else if (uniformAmount != 0 && now >= Long.MIN_VALUE + HISTORY_SIZE - 1) {
                // Keep at most the same 1201 ticks that the ring would retain.
                uniformStart = Math.max(uniformStart, now - (HISTORY_SIZE - 1));
            }
            lastTick = now;
            return;
        }
        if (lastTick == Long.MIN_VALUE || now < lastTick || now - lastTick >= ticks.length) {
            Arrays.fill(ticks, 0);
            currentSlot = Math.floorMod(now, ticks.length);
        } else if (now > lastTick) {
            if (now - lastTick == 1) {
                // Sustained receipts advance one slot; no division or range fill is needed.
                if (++currentSlot == ticks.length) currentSlot = 0;
                ticks[currentSlot] = 0;
            } else {
                int start = currentSlot + 1;
                currentSlot = Math.floorMod(now, ticks.length);
                int end = currentSlot + 1;
                if (start > end) {
                    Arrays.fill(ticks, start, ticks.length, 0);
                    Arrays.fill(ticks, 0, end, 0);
                } else {
                    Arrays.fill(ticks, start, end, 0);
                }
            }
        }
        lastTick = now;
    }

    /** Excludes the current, incomplete server tick so opening order cannot bias the 1t sample. */
    public double[] rates(long now) {
        advance(now);
        if (ticks == null) return uniformRates(now);
        double sum = 0;
        double[] result = new double[WINDOWS.length];
        int slot = currentSlot;
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

    private double[] uniformRates(long now) {
        double[] result = new double[WINDOWS.length];
        if (uniformAmount == 0) return result;
        int newest = (int) (now - uniformEnd);
        int oldest = (int) (now - uniformStart);
        int age = 0;
        double sum = 0;
        for (int window = 0; window < WINDOWS.length; window++) {
            int end = Math.min(WINDOWS[window], oldest);
            // Preserve the ring's newest-first double addition order, including values above 2^53.
            for (int i = Math.max(age + 1, newest); i <= end; i++) sum += uniformAmount;
            age = WINDOWS[window];
            result[window] = sum / age;
        }
        return result;
    }
}
