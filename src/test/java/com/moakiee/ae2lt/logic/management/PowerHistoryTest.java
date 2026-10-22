package com.moakiee.ae2lt.logic.management;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PowerHistoryTest {
    @Test void ignoresIncompleteTickAndUsesFullWindowIncludingIdleTicks() {
        var history = new PowerHistory();
        history.record(10, 1200);
        assertArrayEquals(new double[5], history.rates(10));
        assertArrayEquals(new double[]{1200, 60, 12, 2, 1}, history.rates(11));
        assertEquals(0, history.rates(12)[0]);
        assertEquals(1, history.rates(1210)[4]);
        assertArrayEquals(new double[5], history.rates(1211));
    }
    @Test void multipleReceiptsSameTickAndQueriesDoNotConsumeHistory() {
        var history = new PowerHistory();
        history.record(50, 15); history.record(50, 25);
        assertEquals(0, history.rates(50)[0]);
        history.record(50, 20);
        assertArrayEquals(new double[]{60, 3, .6, .1, .05}, history.rates(51));
        assertArrayEquals(history.rates(51), history.rates(51));
    }
    @Test void longIdleGapAndClockResetDoNotResurrectSamples() {
        var history = new PowerHistory();
        history.record(0, 500);
        assertArrayEquals(new double[5], history.rates(1_000_000));
        history.record(1_000_000, 10);
        assertEquals(10, history.rates(1_000_001)[0]);
        assertArrayEquals(new double[5], history.rates(0));
    }
    @Test void maximumReceiptsNeverWrapToNegativeRates() {
        var history = new PowerHistory();
        history.record(1, Long.MAX_VALUE); history.record(1, Long.MAX_VALUE);
        history.record(2, Long.MAX_VALUE);
        assertEquals((double)Long.MAX_VALUE, history.rates(3)[0]);
        assertEquals((double)Long.MAX_VALUE / 10, history.rates(3)[1], 1024);
    }
    @Test void exactRollingWindowsMatchIndependentNaiveOracleAcrossRingWraps() {
        var history = new PowerHistory();
        var random = new java.util.Random(912846);
        long[] actual = new long[4000];
        int[] windows = {1, 20, 100, 600, 1200};
        for (int now = 0; now < actual.length; now++) {
            if (random.nextBoolean()) {
                actual[now] = random.nextInt(100_000);
                history.record(now, actual[now]);
            }
            if (now % 13 != 0) continue;
            double[] rates = history.rates(now);
            for (int w = 0; w < windows.length; w++) {
                long sum = 0;
                for (int t = Math.max(0, now - windows[w]); t < now; t++) sum += actual[t];
                assertEquals(sum / (double) windows[w], rates[w], "tick=" + now + ", window=" + windows[w]);
            }
        }
    }
}
