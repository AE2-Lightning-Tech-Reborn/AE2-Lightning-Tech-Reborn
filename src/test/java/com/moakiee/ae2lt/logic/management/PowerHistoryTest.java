package com.moakiee.ae2lt.logic.management;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PowerHistoryTest {
    @Test void constantReceiptsStayExactThroughWrapsQueriesAndIrregularTransitions() {
        for (long amount : new long[]{4096, 9_007_199_254_740_993L, Long.MAX_VALUE}) {
            var history = new PowerHistory();
            var receipts = new java.util.HashMap<Long, Long>();
            for (long now = -2000; now <= 2000; now++) {
                history.record(now, amount);
                receipts.put(now, amount);
                if (now % 37 == 0) assertMatchesReceipts(history, receipts, now);
            }
            // Add to the incomplete tail of a long constant run, saturating before conversion to double.
            history.record(2000, 123);
            receipts.compute(2000L, (tick, value) -> Math.min(Long.MAX_VALUE - value, 123) + value);
            assertMatchesReceipts(history, receipts, 2000);
            assertMatchesReceipts(history, receipts, 2001);
            // A gap and a new receipt must keep the old completed ticks and count missing ticks as zero.
            history.record(2077, 17);
            receipts.put(2077L, 17L);
            assertMatchesReceipts(history, receipts, 2078);
            assertMatchesReceipts(history, receipts, 3200);
            assertMatchesReceipts(history, receipts, 3278);
            assertArrayEquals(new double[5], history.rates(-4000));
            history.record(-4000, 91);
            assertArrayEquals(new double[]{91, 91 / 20.0, .91, 91 / 600.0, 91 / 1200.0}, history.rates(-3999));
        }
    }

    @Test void constantRunExpiresEvenWhenIdleQueriesKeepAdvancingTheClock() {
        var history = new PowerHistory();
        var receipts = new java.util.HashMap<Long, Long>();
        for (long now = 0; now < 2000; now++) {
            history.record(now, 7);
            receipts.put(now, 7L);
        }
        for (long now = 2000; now <= 3201; now++) assertMatchesReceipts(history, receipts, now);
        history.record(3201, 19);
        receipts.put(3201L, 19L);
        assertMatchesReceipts(history, receipts, 3202);
    }

    @Test void queryOnlyGapThenChangedReceiptKeepsTheLivePartOfAConstantRun() {
        var history = new PowerHistory();
        var receipts = new java.util.HashMap<Long, Long>();
        for (long now = 0; now < 3000; now++) {
            history.record(now, 9_007_199_254_740_993L);
            receipts.put(now, 9_007_199_254_740_993L);
        }
        assertMatchesReceipts(history, receipts, 3071);
        history.record(3080, 13);
        receipts.put(3080L, 13L);
        assertMatchesReceipts(history, receipts, 3080);
        assertMatchesReceipts(history, receipts, 3081);
        assertMatchesReceipts(history, receipts, 4199);
    }

    private static void assertMatchesReceipts(PowerHistory history, java.util.Map<Long, Long> receipts, long now) {
        double[] expected = new double[5];
        double sum = 0;
        int window = 0;
        for (int age = 1; age <= 1200; age++) {
            sum += receipts.getOrDefault(now - age, 0L);
            if (age == PowerHistory.WINDOWS[window]) expected[window++] = sum / age;
        }
        assertArrayEquals(expected, history.rates(now), "tick=" + now);
    }

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

    @Test void sparseReceiptsAndRepeatedQueriesMatchOracleAcrossNegativeTicksAndResets() {
        var history = new PowerHistory();
        var receipts = new java.util.HashMap<Long, Long>();
        var random = new java.util.Random(52719);
        long now = -4000;
        for (int step = 0; step < 2500; step++) {
            if (step == 1250) {
                now = -8000;
                receipts.clear();
            }
            now += step % 19 == 0 ? 1201 : random.nextInt(80);
            long amount = random.nextInt(100_000);
            history.record(now, amount);
            receipts.merge(now, amount, Long::sum);
            double[] expected = new double[5];
            for (int w = 0; w < expected.length; w++) {
                long sum = 0;
                for (long tick = now - PowerHistory.WINDOWS[w]; tick < now; tick++)
                    sum += receipts.getOrDefault(tick, 0L);
                expected[w] = sum / (double) PowerHistory.WINDOWS[w];
            }
            assertArrayEquals(expected, history.rates(now), "tick=" + now);
            assertArrayEquals(expected, history.rates(now), "repeated tick=" + now);
        }
    }
}
