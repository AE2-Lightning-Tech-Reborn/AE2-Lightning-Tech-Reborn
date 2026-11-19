package com.moakiee.ae2lt.logic.energy;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EnergyBoundaryTest {
    @Test void randomTopologyAndReservationsMatchIndependentComponentModel() {
        var random = new Random(0xFE20261021L);
        for (int round = 0; round < 40; round++) {
            var budgets = new EnergyTickBudget[32];
            int[] group = new int[32], visits = new int[32];
            long[] spent = new long[32];
            for (int i = 0; i < 32; i++) { budgets[i] = new EnergyTickBudget(round); group[i] = i; }
            for (int step = 0; step < 1000; step++) {
                int a = random.nextInt(32), b = random.nextInt(32);
                if (random.nextInt(4) == 0) {
                    budgets[a].merge(budgets[b]);
                    int from = group[b], to = group[a];
                    for (int i = 0; i < 32; i++) if (group[i] == from) group[i] = to;
                } else {
                    long limit = random.nextInt(10000), request = random.nextInt(12000);
                    long reserve = random.nextInt(12000);
                    int visitLimit = 1 + random.nextInt(100), reservedVisits = random.nextInt(3);
                    long used = 0; int seen = 0;
                    for (int i = 0; i < 32; i++) if (group[i] == group[a]) { used += spent[i]; seen += visits[i]; }
                    long remaining = Math.max(0, limit - used);
                    long expected = seen >= Math.max(0, visitLimit - reservedVisits) ? 0
                            : Math.min(request, Math.max(0, remaining - reserve));
                    assertEquals(expected, budgets[a].preview(request, limit, visitLimit, reserve, reservedVisits));
                    assertEquals(expected, budgets[a].reserve(request, limit, visitLimit, reserve, reservedVisits));
                    spent[a] += expected;
                    if (seen < Math.max(0, visitLimit - reservedVisits)) visits[a]++;
                }
            }
        }
    }

    @Test void mergingNearLongMaximumSaturatesWithoutReopeningBudget() {
        var a = new EnergyTickBudget(1); var b = new EnergyTickBudget(1);
        assertEquals(Long.MAX_VALUE - 10, a.reserve(Long.MAX_VALUE - 10, Long.MAX_VALUE, 100, 0, 0));
        assertEquals(100, b.reserve(100, Long.MAX_VALUE, 100, 0, 0));
        a.merge(b); b.merge(a);
        assertEquals(0, a.reserve(Long.MAX_VALUE, Long.MAX_VALUE, 100, 0, 0));
        assertEquals(0, b.preview(Long.MAX_VALUE, Long.MAX_VALUE, 100, 0, 0));
    }

    @Test void oneVisitBudgetBelongsToMaintenanceWhenEnabled() {
        var b = new EnergyTickBudget(1);
        assertEquals(0, b.reserve(1, 100, 1, 20, 1));
        assertEquals(100, b.reserve(100, 100, 1, 0, 0));
        assertEquals(0, b.reserve(1, 100, 1, 0, 0));
    }

    @Test void configReductionCannotUndoSpentBudgetAndNextTickRecovers() {
        var service = new WirelessEnergyGridService();
        assertEquals(80, service.reserve(1, 80, 100, 10));
        assertEquals(0, service.reserve(1, 100, 40, 10));
        assertEquals(20, service.reserve(1, 100, 100, 10));
        assertEquals(0, service.reserve(2, 100, 0, 10));
        assertEquals(100, service.reserve(3, 100, 100, 10));
    }

    @Test void invalidReceiptsLeaveEscrowAndDirtyFlagUntouched() {
        var dirty = new AtomicInteger();
        var e = new EnergyEscrow(30, 70, dirty::incrementAndGet);
        for (long[] receipt : new long[][] {{-1, 0}, {71, 0}, {70, -1}, {70, 71}}) {
            assertThrows(IllegalArgumentException.class, () -> e.settle(receipt[0], receipt[1]));
            assertEquals(30, e.available()); assertEquals(70, e.uncertain());
        }
        assertEquals(0, dirty.get());
        e.settle(70, 0);
        assertEquals(100, e.available()); assertEquals(0, e.uncertain());
    }

    @Test void longMaximumEscrowConservesPartialReceiptWithoutOverflow() {
        var e = new EnergyEscrow(Long.MAX_VALUE, 0, () -> {});
        long offered = e.debit(Long.MAX_VALUE); e.quarantine(offered);
        assertThrows(IllegalArgumentException.class, () -> e.credit(1));
        e.settle(offered, 17);
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), BigInteger.valueOf(e.available()).add(BigInteger.valueOf(17)));
        assertEquals(17, e.room());
    }

    @Test void withdrawalRejectsOverReceiptAndRetainsUnresolvedJournal() {
        var ae = new AeEnergyBalance("0.375", () -> {}); var fe = new EnergyEscrow(0, 0, () -> {});
        ae.beginWithdrawal(7);
        assertThrows(IllegalArgumentException.class, () -> ae.completeWithdrawal(fe, 8));
        assertThrows(IllegalArgumentException.class, () -> ae.completeWithdrawal(fe, -1));
        assertEquals(7, ae.pendingFeWithdrawal()); assertEquals(0, fe.available());
        assertEquals("0.375", ae.saved());
        ae.completeWithdrawal(fe, 3);
        assertEquals(0, ae.pendingFeWithdrawal()); assertEquals(3, fe.available());
    }

    @Test void conversionOverflowDoesNotDebitOwnedFe() {
        var fe = new EnergyEscrow(2, 0, () -> {});
        var ae = new AeEnergyBalance(Double.toString(Double.MAX_VALUE), () -> {});
        assertThrows(IllegalArgumentException.class, () -> ae.convert(fe, 1, Double.MAX_VALUE));
        assertEquals(2, fe.available()); assertEquals(Double.MAX_VALUE, ae.available());
    }

    @Test void subnormalPaymentsNeverOverdrawAndPreserveDecimalRemainder() {
        var ae = new AeEnergyBalance("1.0E-323", () -> {});
        BigDecimal paid = BigDecimal.ZERO;
        for (int i = 0; i < 10; i++) paid = paid.add(BigDecimal.valueOf(ae.extract(Double.MIN_VALUE)));
        assertEquals(0, paid.add(new BigDecimal(ae.saved())).compareTo(new BigDecimal("1.0E-323")));
        assertTrue(ae.available() >= 0);
    }

    @Test void enormousRequestsClampFeNeedAndInvalidNumbersDoNotMutateBalance() {
        var ae = new AeEnergyBalance("0.25", () -> {});
        assertEquals(Long.MAX_VALUE, ae.neededFe(Double.MAX_VALUE, Double.MIN_VALUE));
        for (double value : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> ae.extract(value));
            assertThrows(IllegalArgumentException.class, () -> ae.neededFe(1, value));
        }
        assertEquals("0.25", ae.saved());
    }
}
