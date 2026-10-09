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
        assertEquals(1, history.execute(nonce, "first", calls::incrementAndGet, result -> false));
        assertEquals(1, history.execute(nonce, "first", calls::incrementAndGet, result -> false));
        assertEquals(1, calls.get());
        assertTrue(history.conflicts(nonce, "other"));
        assertThrows(IllegalArgumentException.class,
                () -> history.execute(nonce, "other", calls::incrementAndGet, result -> false));
    }

    @Test
    void temporaryRejectionCanRecoverWithSameNonce() {
        var history = new SubmissionHistory<String, Integer>(2);
        var nonce = UUID.randomUUID();
        assertEquals(0, history.execute(nonce, "request", () -> 0, result -> result == 0));
        assertEquals(4, history.execute(nonce, "request", () -> 4, result -> result == 0));
        assertEquals(4, history.execute(nonce, "request", () -> fail("duplicate dispatch"), result -> false));
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
                }, result -> false));
        assertEquals(nonce, failure.nonce());
        assertSame(failure, assertThrows(IndeterminateSubmissionException.class,
                () -> history.execute(nonce, "request", () -> moved.addAndGet(5), result -> false)));
        assertThrows(IllegalStateException.class,
                () -> history.execute(UUID.randomUUID(), "other", () -> moved.addAndGet(5), result -> false));
        assertEquals(5, moved.get());
    }

    @Test
    void reentrantSubmissionNeverDispatchesTwice() {
        var history = new SubmissionHistory<String, Integer>(2);
        var nonce = UUID.randomUUID();
        assertThrows(IndeterminateSubmissionException.class, () -> history.execute(nonce, "request",
                () -> history.execute(nonce, "request", () -> fail("reentrant dispatch"), result -> false), result -> false));
    }

    @Test
    void completedHistoryIsBounded() {
        var history = new SubmissionHistory<String, Integer>(1);
        var first = UUID.randomUUID();
        history.execute(first, "one", () -> 1, result -> false);
        history.execute(UUID.randomUUID(), "two", () -> 2, result -> false);
        assertEquals(3, history.execute(first, "one", () -> 3, result -> false));
    }
}
