package com.moakiee.ae2lt.logic.energy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Already converted raw AE, owned by the same node claim as its FE escrow. */
public final class AeEnergyBalance {
    private BigDecimal stored;
    private long pendingFeWithdrawal;
    private final Runnable changed;

    AeEnergyBalance(String saved, Runnable changed) {
        this(saved, 0, changed);
    }
    AeEnergyBalance(String saved, long pendingFeWithdrawal, Runnable changed) {
        stored = saved.isEmpty() ? BigDecimal.ZERO : new BigDecimal(saved);
        if (stored.signum() < 0 || !Double.isFinite(stored.doubleValue()) || pendingFeWithdrawal < 0) throw new IllegalArgumentException("Invalid AE balance");
        this.pendingFeWithdrawal = pendingFeWithdrawal;
        this.changed = changed;
    }
    public String saved() { return stored.toPlainString(); }
    public double available() { return floorDouble(stored); }
    /** Unacknowledged extraction upper bound, not an owned or spendable FE balance. */
    public long pendingFeWithdrawal() { return pendingFeWithdrawal; }
    void beginWithdrawal(long amount) {
        if (amount <= 0 || pendingFeWithdrawal != 0) throw new IllegalStateException("Invalid or unresolved FE withdrawal");
        pendingFeWithdrawal = amount; changed.run();
    }
    void completeWithdrawal(EnergyEscrow fe, long received) {
        if (received < 0 || received > pendingFeWithdrawal) throw new IllegalArgumentException("Invalid FE withdrawal receipt");
        fe.credit(received);
        pendingFeWithdrawal = 0; changed.run();
    }
    long neededFe(double rawRequest, double aePerFe) {
        requirePositiveFinite(rawRequest); requirePositiveFinite(aePerFe);
        var missing = BigDecimal.valueOf(rawRequest).subtract(stored).max(BigDecimal.ZERO);
        return missing.divide(BigDecimal.valueOf(aePerFe), 0, RoundingMode.CEILING)
                .min(BigDecimal.valueOf(Long.MAX_VALUE)).longValueExact();
    }
    double simulate(double rawRequest, long extraFe, double aePerFe) {
        requirePositiveFinite(rawRequest); requirePositiveFinite(aePerFe);
        if (extraFe < 0) throw new IllegalArgumentException("Negative FE");
        return floorDouble(stored.add(BigDecimal.valueOf(aePerFe).multiply(BigDecimal.valueOf(extraFe)))
                .min(BigDecimal.valueOf(rawRequest)));
    }
    void convert(EnergyEscrow fe, long amount, double aePerFe) {
        requirePositiveFinite(aePerFe);
        if (amount < 0 || amount > fe.available()) throw new IllegalArgumentException("Unowned FE conversion");
        if (amount == 0) return;
        var next = stored.add(BigDecimal.valueOf(aePerFe).multiply(BigDecimal.valueOf(amount)));
        if (!Double.isFinite(next.doubleValue())) throw new IllegalArgumentException("AE balance overflow");
        fe.debit(amount);
        stored = next;
        changed.run();
    }
    double extract(double rawRequest) {
        requirePositiveFinite(rawRequest);
        double paid = floorDouble(stored.min(BigDecimal.valueOf(rawRequest)));
        stored = stored.subtract(BigDecimal.valueOf(paid));
        if (paid > 0) changed.run();
        return paid;
    }
    private static double floorDouble(BigDecimal value) {
        double result = value.doubleValue();
        if (BigDecimal.valueOf(result).compareTo(value) > 0) result = Math.nextDown(result);
        return Math.max(0, result);
    }
    private static void requirePositiveFinite(double amount) {
        if (!(amount > 0) || !Double.isFinite(amount)) throw new IllegalArgumentException("Invalid AE amount or conversion rate");
    }
}
