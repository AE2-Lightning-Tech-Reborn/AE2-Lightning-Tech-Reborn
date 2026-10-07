package com.moakiee.ae2lt.machine.largeoverload;

import java.util.Objects;
import java.util.Optional;

import com.moakiee.ae2lt.me.key.LightningKey;

/** Exact per-source-operation cost. Plans are quotes, never proof of a successful network withdrawal. */
public record LargeFactoryLightningCost(long highVoltage, long extremeHighVoltage) {
    public static final int HIGH_PER_EXTREME = 4;
    public static final LargeFactoryLightningCost FIRMAMENT = new LargeFactoryLightningCost(0, 1);

    public LargeFactoryLightningCost {
        if (highVoltage < 0 || extremeHighVoltage < 0 || (highVoltage == 0 && extremeHighVoltage == 0)) {
            throw new IllegalArgumentException("Factory source operations require a positive lightning cost");
        }
    }

    public static LargeFactoryLightningCost ordinary(LightningKey.Tier sourceTier, long sourceCost) {
        Objects.requireNonNull(sourceTier, "sourceTier");
        if (sourceCost < 0) throw new IllegalArgumentException("Negative source lightning cost");
        if (sourceCost == 0) return new LargeFactoryLightningCost(1, 0);
        return sourceTier == LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                ? new LargeFactoryLightningCost(0, sourceCost) : new LargeFactoryLightningCost(sourceCost, 0);
    }

    public record Payment(long highVoltage, long extremeHighVoltage) {
        public Payment {
            if (highVoltage < 0 || extremeHighVoltage < 0) throw new IllegalArgumentException("Negative payment");
        }
    }

    /**
     * Use native EHV first, then compensate its integral shortfall at 4:1.
     * HV demand cannot use EHV. Overflow is unaffordable, not saturated to a smaller bill.
     */
    public Optional<Payment> plan(long sourceOperations, long availableHigh, long availableExtreme) {
        if (sourceOperations < 0 || availableHigh < 0 || availableExtreme < 0) {
            throw new IllegalArgumentException("Negative operation count or lightning availability");
        }
        try {
            long high = Math.multiplyExact(highVoltage, sourceOperations);
            long extreme = Math.multiplyExact(extremeHighVoltage, sourceOperations);
            long useExtreme = Math.min(extreme, availableExtreme);
            long compensate = Math.multiplyExact(extreme - useExtreme, HIGH_PER_EXTREME);
            long useHigh = Math.addExact(high, compensate);
            return useHigh <= availableHigh ? Optional.of(new Payment(useHigh, useExtreme)) : Optional.empty();
        } catch (ArithmeticException overflow) {
            return Optional.empty();
        }
    }

    /** Solve h*n + 4*max(e*n - availableExtreme, 0) <= availableHigh directly. */
    public long maxPayableOperations(long requested, long availableHigh, long availableExtreme) {
        if (requested < 0 || availableHigh < 0 || availableExtreme < 0) {
            throw new IllegalArgumentException("Negative operation count or lightning availability");
        }
        if (extremeHighVoltage == 0) return Math.min(requested, availableHigh / highVoltage);
        // plan() must still represent the full EHV source cost before substitution.
        long bound = Math.min(requested, Long.MAX_VALUE / extremeHighVoltage);
        if (highVoltage == 0) {
            long converted = availableHigh / HIGH_PER_EXTREME;
            long total = converted > Long.MAX_VALUE - availableExtreme ? Long.MAX_VALUE : availableExtreme + converted;
            return Math.min(bound, total / extremeHighVoltage);
        }
        bound = Math.min(bound, availableHigh / highVoltage);
        if (extremeHighVoltage <= (Long.MAX_VALUE - highVoltage) / HIGH_PER_EXTREME
                && availableExtreme <= (Long.MAX_VALUE - availableHigh) / HIGH_PER_EXTREME) {
            return Math.min(bound, (availableHigh + HIGH_PER_EXTREME * availableExtreme)
                    / (highVoltage + HIGH_PER_EXTREME * extremeHighVoltage));
        }
        // Rare oversized mixed costs use one exact division, never a count-sized loop or repeated plans.
        var numerator = java.math.BigInteger.valueOf(availableExtreme).shiftLeft(2).add(java.math.BigInteger.valueOf(availableHigh));
        var denominator = java.math.BigInteger.valueOf(extremeHighVoltage).shiftLeft(2).add(java.math.BigInteger.valueOf(highVoltage));
        return numerator.divide(denominator).min(java.math.BigInteger.valueOf(bound)).longValueExact();
    }
}
