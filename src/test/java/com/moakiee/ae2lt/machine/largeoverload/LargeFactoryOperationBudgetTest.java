package com.moakiee.ae2lt.machine.largeoverload;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LargeFactoryOperationBudgetTest {
    @Test
    void completedWorkDoesNotRestoreSameTickCapacityAcrossHatches() {
        var budget = new LargeFactoryOperationBudget(1024);
        try (var active = budget.reserve(10, 800, 1)) {
            active.commit(800);
        }
        try (var passive = budget.reserve(10, 800, 1)) {
            assertEquals(224, passive.copies());
            passive.commit(224);
        }
        assertEquals(0, budget.remainingOperations(10));
        assertEquals(1024, budget.remainingOperations(11));
    }

    @Test
    void sourceMultiplicityLimitsFourTimesPatternsTo256Copies() {
        var budget = new LargeFactoryOperationBudget(LargeFactoryOperationBudget.FIRMAMENT_OPERATIONS_PER_TICK);
        try (var request = budget.reserve(1, Long.MAX_VALUE, 4)) {
            assertEquals(256, request.copies());
            assertEquals(1024, request.sourceOperations());
            request.commit(256);
        }
        assertEquals(0, budget.remainingOperations(1));
    }

    @Test
    void reentrantReservationCannotSpendAnAlreadyReservedOperation() {
        var budget = new LargeFactoryOperationBudget(10);
        try (var first = budget.reserve(1, 7, 1); var reentrant = budget.reserve(1, 7, 1)) {
            assertEquals(3, reentrant.copies());
            first.commit(7);
            reentrant.commit(3);
        }
        assertEquals(0, budget.remainingOperations(1));
    }

    @Test
    void partialAcceptanceAndAbortedPaymentsOnlyReturnUnconsumedAllowance() {
        var budget = new LargeFactoryOperationBudget(400);
        try (var first = budget.reserve(1, 100, 4)) {
            first.commit(24);
        }
        assertEquals(304, budget.remainingOperations(1));
        try (var cancelled = budget.reserve(1, 100, 4)) {
            assertEquals(76, cancelled.copies());
        }
        assertEquals(304, budget.remainingOperations(1));
    }

    @Test
    void escapedReservationsAndTimeTravelCannotResetTheTickAllowance() {
        var budget = new LargeFactoryOperationBudget(1024);
        try (var reservation = budget.reserve(100, 1, 1)) {
            assertThrows(IllegalStateException.class, () -> budget.remainingOperations(101));
            assertThrows(IllegalArgumentException.class, () -> budget.remainingOperations(99));
            reservation.commit(1);
            assertThrows(IllegalStateException.class, () -> reservation.commit(1));
        }
        assertEquals(1023, budget.remainingOperations(100));
        assertEquals(1024, budget.remainingOperations(101));
    }

    @Test
    void hugePatternMultipliersAndLongCapacityNeverOverflow() {
        var budget = new LargeFactoryOperationBudget(Long.MAX_VALUE);
        try (var reservation = budget.reserve(1, Long.MAX_VALUE, Long.MAX_VALUE)) {
            assertEquals(1, reservation.copies());
            reservation.commit(1);
        }
        assertEquals(0, budget.remainingOperations(1));
        var small = new LargeFactoryOperationBudget(1024);
        try (var reservation = small.reserve(1, Long.MAX_VALUE, Long.MAX_VALUE)) {
            assertEquals(0, reservation.copies());
        }
        assertEquals(1024, small.remainingOperations(1));
    }

    @Test
    void invalidCompletionDoesNotLoseTheReservationAndCloseIsIdempotent() {
        var budget = new LargeFactoryOperationBudget(10);
        var reservation = budget.reserve(1, 2, 3);
        assertThrows(IllegalArgumentException.class, () -> reservation.commit(3));
        assertEquals(4, budget.remainingOperations(1));
        reservation.close();
        reservation.close();
        assertEquals(10, budget.remainingOperations(1));
        assertThrows(IllegalArgumentException.class, () -> budget.reserve(1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> budget.reserve(1, 1, 0));
    }
}
