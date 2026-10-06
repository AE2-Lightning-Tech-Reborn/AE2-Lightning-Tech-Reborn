package com.moakiee.ae2lt.api.lightning;

import static org.junit.jupiter.api.Assertions.*;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;

class LightningPaymentTest {
    static final class Storage implements ILightningEnergyHandler {
        final EnumMap<LightningTier, Long> stored = new EnumMap<>(LightningTier.class);
        long refundLimit = Long.MAX_VALUE;
        long ehvCommitLimit = Long.MAX_VALUE;
        int writes;
        Storage() { for (var tier : LightningTier.values()) stored.put(tier, 20L); }
        public long getStored(LightningTier tier) { return stored.get(tier); }
        public long extract(LightningTier tier, long amount, boolean simulate) {
            long taken = Math.min(amount, getStored(tier));
            if (!simulate) {
                if (tier == LightningTier.EXTREME_HIGH_VOLTAGE) taken = Math.min(taken, ehvCommitLimit);
                stored.put(tier, getStored(tier) - taken); writes++;
            }
            return taken;
        }
        public long insert(LightningTier tier, long amount, boolean simulate) {
            long accepted = Math.min(amount, refundLimit);
            if (!simulate) { stored.put(tier, getStored(tier) + accepted); writes++; }
            return accepted;
        }
    }

    @Test void preflightFailureNeverMutatesAndNegativeCostsAreRejected() {
        var storage = new Storage();
        assertFalse(LightningPayment.pay(storage, 21, 0).paid());
        assertThrows(IllegalArgumentException.class, () -> LightningPayment.pay(storage, -1, 0));
        assertEquals(0, storage.writes);
    }

    @Test void partialSecondTierReturnsExactDebtForEachVoltage() {
        var storage = new Storage(); storage.ehvCommitLimit = 3; storage.refundLimit = 2;
        var result = LightningPayment.pay(storage, 10, 10);
        assertEquals(new LightningPayment.Result(false, 8, 1), result);
        assertEquals(20, storage.getStored(LightningTier.HIGH_VOLTAGE) + result.refundHv());
        assertEquals(20, storage.getStored(LightningTier.EXTREME_HIGH_VOLTAGE) + result.refundEhv());
    }

    @Test void successfulPaymentDebitsBothTiersAndZeroCostNeedsNoWrites() {
        var storage = new Storage();
        assertTrue(LightningPayment.pay(storage, 0, 0).paid());
        assertEquals(0, storage.writes);
        assertTrue(LightningPayment.pay(storage, 4, 7).paid());
        assertEquals(16, storage.getStored(LightningTier.HIGH_VOLTAGE));
        assertEquals(13, storage.getStored(LightningTier.EXTREME_HIGH_VOLTAGE));
    }
}
