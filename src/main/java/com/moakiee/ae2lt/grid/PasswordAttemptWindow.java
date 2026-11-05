package com.moakiee.ae2lt.grid;

import java.util.ArrayDeque;

/** A bounded, monotonic rolling window shared across all frequencies for one player. */
public final class PasswordAttemptWindow {
    private static final long WINDOW_NANOS = 1_000_000_000L;
    private static final int MAX_ATTEMPTS = 5;
    private final ArrayDeque<Long> attempts = new ArrayDeque<>(MAX_ATTEMPTS);

    public boolean tryAcquire(long nowNanos) {
        while (!attempts.isEmpty() && nowNanos - attempts.getFirst() >= WINDOW_NANOS) {
            attempts.removeFirst();
        }
        if (attempts.size() >= MAX_ATTEMPTS) return false;
        attempts.addLast(nowNanos);
        return true;
    }
}
