package com.moakiee.ae2lt.logic.batch;

import static org.junit.jupiter.api.Assertions.*;
import com.moakiee.ae2lt.api.crafting.IndeterminateSubmissionException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SubmissionHistoryTest {
    @Test
    void successfulReceiptPreventsDuplicateDispatchAndConflictingRequests() {
        var history = new SubmissionHistory<String, Integer>(2);
        var nonce = UUID.randomUUID();
        var calls = new AtomicInteger();
        assertEquals(1, history.execute(nonce, "first", calls::incrementAndGet, n -> false));
        assertEquals(1, history.execute(nonce, "first", calls::incrementAndGet, n -> false));
        assertEquals(1, calls.get());
        assertTrue(history.conflicts(nonce, "other"));
        assertThrows(IllegalArgumentException.class,
                () -> history.execute(nonce, "other", calls::incrementAndGet, n -> false));
    }

    @Test
    void temporaryRejectionCanRecoverWithSameNonce() {
        var history = new SubmissionHistory<String, Integer>(2);
        var nonce = UUID.randomUUID();
        assertEquals(0, history.execute(nonce, "request", () -> 0, n -> n == 0));
        assertEquals(4, history.execute(nonce, "request", () -> 4, n -> n == 0));
        assertEquals(4, history.execute(nonce, "request", () -> fail("duplicate dispatch"), n -> false));
    }

    @Test
    void exceptionAfterSideEffectCannotBecomeZeroAcceptanceOrBeReplayed() {
        var history = new SubmissionHistory<String, Integer>(1);
        var nonce = UUID.randomUUID();
        var moved = new AtomicInteger();
        var failure = assertThrows(IndeterminateSubmissionException.class,
                () -> history.execute(nonce, "request", () -> {
                    moved.addAndGet(5);
                    throw new ArithmeticException("after dispatch");
                }, n -> false));
        assertEquals(nonce, failure.nonce());
        assertSame(failure, assertThrows(IndeterminateSubmissionException.class,
                () -> history.execute(nonce, "request", () -> moved.addAndGet(5), n -> false)));
        assertThrows(IllegalStateException.class,
                () -> history.execute(UUID.randomUUID(), "other", () -> moved.addAndGet(5), n -> false));
        assertEquals(5, moved.get());
    }

    @Test
    void reentrantSubmissionNeverDispatchesTwice() {
        var history = new SubmissionHistory<String, Integer>(2);
        var nonce = UUID.randomUUID();
        assertThrows(IndeterminateSubmissionException.class, () -> history.execute(nonce, "request",
                () -> history.execute(nonce, "request", () -> fail("reentrant dispatch"), n -> false), n -> false));
    }

    @Test
    void completedHistoryIsBounded() {
        var history = new SubmissionHistory<String, Integer>(1);
        var first = UUID.randomUUID();
        history.execute(first, "one", () -> 1, n -> false);
        history.execute(UUID.randomUUID(), "two", () -> 2, n -> false);
        assertEquals(3, history.execute(first, "one", () -> 3, n -> false));
    }
}
