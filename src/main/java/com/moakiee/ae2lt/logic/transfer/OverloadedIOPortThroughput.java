package com.moakiee.ae2lt.logic.transfer;

/** Transfer budgets expressed in AE2 operations, before converting to a key's native units. */
public final class OverloadedIOPortThroughput {
    public static final long BASE_TRANSFER_OPERATIONS = 8_388_608L;
    public static final int BASE_ATTEMPTS = 4;
    public static final int MAX_MATRICES = 8;
    public static final int MAX_ATTEMPTS = 16;

    private OverloadedIOPortThroughput() {}

    public static int attemptLimit(int matrices) {
        return Math.min(MAX_ATTEMPTS, BASE_ATTEMPTS + 2 * boundedMatrices(matrices));
    }

    public static long operationCap(int matrices) {
        int count = boundedMatrices(matrices);
        // The eighth x32 step reaches 2^63, which is one above the signed long limit.
        return count == MAX_MATRICES ? Long.MAX_VALUE : BASE_TRANSFER_OPERATIONS << (5 * count);
    }

    public static long nativeAmountCap(long operations, int amountPerOperation) {
        int units = Math.max(1, amountPerOperation);
        return operations > Long.MAX_VALUE / units ? Long.MAX_VALUE : operations * units;
    }

    private static int boundedMatrices(int matrices) {
        return Math.max(0, Math.min(MAX_MATRICES, matrices));
    }
}
