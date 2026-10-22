package com.moakiee.ae2lt.grid;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PasswordAttemptWindowTest {
    @Test
    void sixthAttemptIsRejectedAndTheExactSecondBoundaryExpires() {
        var window = new PasswordAttemptWindow();
        for (int i = 0; i < 5; i++) assertTrue(window.tryAcquire(100));
        assertFalse(window.tryAcquire(100));
        assertFalse(window.tryAcquire(1_000_000_099L));
        assertTrue(window.tryAcquire(1_000_000_100L));
    }

    @Test
    void staggeredAttemptsUseARollingWindowRatherThanFixedBuckets() {
        var window = new PasswordAttemptWindow();
        for (int i = 0; i < 5; i++) assertTrue(window.tryAcquire(i * 200_000_000L));
        assertFalse(window.tryAcquire(999_999_999L));
        assertTrue(window.tryAcquire(1_000_000_000L));
        assertFalse(window.tryAcquire(1_199_999_999L));
        assertTrue(window.tryAcquire(1_200_000_000L));
    }

    @Test
    void sessionsAreIndependentAndMatchAnUnboundedHistoryOracle() {
        var random = new Random(875234);
        var windows = new PasswordAttemptWindow[] {new PasswordAttemptWindow(), new PasswordAttemptWindow()};
        var history = new ArrayList<ArrayList<Long>>();
        history.add(new ArrayList<>());
        history.add(new ArrayList<>());
        long now = Long.MAX_VALUE - 2_000_000_000L;
        for (int i = 0; i < 5000; i++) {
            now += random.nextInt(100_000_000);
            int player = random.nextInt(2);
            long time = now;
            boolean expected = history.get(player).stream().filter(t -> time - t < 1_000_000_000L).count() < 5;
            assertEquals(expected, windows[player].tryAcquire(now));
            if (expected) history.get(player).add(now);
        }
    }
}
