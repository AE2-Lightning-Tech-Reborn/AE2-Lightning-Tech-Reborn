package com.moakiee.ae2lt.logic;

import it.unimi.dsi.fastutil.objects.Object2LongMap;

final class OverloadedAmountMath {
    private OverloadedAmountMath() {
    }

    static long mergeReportedAndSimulatedAmount(long reported, long simulated, long cap) {
        long visible = Math.max(Math.max(0, reported), Math.max(0, simulated));
        return capVisibleAmount(visible, cap);
    }

    static long capVisibleAmount(long available, long cap) {
        if (cap <= 0) return 0;
        long nonNegativeAvailable = Math.max(0, available);
        return cap == Long.MAX_VALUE
                ? nonNegativeAvailable
                : Math.min(nonNegativeAvailable, cap);
    }

    static long saturatingAdd(long a, long b) {
        return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b;
    }

    static <K> void mergeSharedExposure(
            Object2LongMap<K> capByKey,
            Object2LongMap<K> amountByKey,
            K key,
            long cap,
            long available) {
        capByKey.put(key, saturatingAdd(capByKey.getLong(key), cap));
        amountByKey.put(key, Math.max(amountByKey.getLong(key), Math.max(0, available)));
    }
}
