package com.moakiee.ae2lt.crafting.runtime;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.me.key.LightningKey;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** The same lifecycle contract for primitive-long and exact receipts, independent of CPU ledgers. */
class CraftingExecutionContractTest {
    private static final AEKey KEY = LightningKey.HIGH_VOLTAGE;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deliveryWaitsForAccountingAndCannotReenterOrRepeat(boolean exact) {
        var state = new CraftingExecutionState();
        assertTrue(state.tryEnter());
        var received = new LinkedHashMap<AEKey, BigInteger>();
        var expected = exact ? BigInteger.TEN.pow(100) : BigInteger.valueOf(17);
        var transaction = transaction(exact, (key, amount) -> {
            assertFalse(state.tryEnter());
            received.merge(key, amount, BigInteger::add);
        }, (key, amount) -> fail("unexpected fallback"));
        enqueue(transaction, exact, KEY, expected);
        assertTrue(received.isEmpty());
        transaction.accounted();
        assertTrue(received.isEmpty(), "accounting must finish before delivery starts");
        transaction.close();
        transaction.close();
        assertEquals(Map.of(KEY, expected), received);
        assertFalse(transaction.enqueueExact(KEY, BigInteger.ONE));
        assertThrows(IllegalStateException.class, transaction::outputSink);
        state.leave();
        assertTrue(state.tryEnter());
        state.leave();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void abortedAccountingRetainsEveryPhysicalReturn(boolean exact) {
        var retained = new LinkedHashMap<AEKey, BigInteger>();
        var expected = exact ? BigInteger.TEN.pow(100) : BigInteger.valueOf(17);
        try (var transaction = transaction(exact, (key, n) -> fail("unsettled job advanced"),
                (key, n) -> retained.merge(key, n, BigInteger::add))) {
            enqueue(transaction, exact, KEY, expected);
            enqueue(transaction, exact, LightningKey.EXTREME_HIGH_VOLTAGE, expected);
            // No accounting confirmation: models a throwing transfer or settlement.
        }
        assertEquals(Map.of(KEY, expected, LightningKey.EXTREME_HIGH_VOLTAGE, expected), retained);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedReceiverRetainsLaterOutputsWithoutRepeatingTransferredOwnership(boolean exact) {
        var retained = new LinkedHashMap<AEKey, BigInteger>();
        var first = new LinkedHashMap<AEKey, BigInteger>();
        var expected = exact ? BigInteger.TEN.pow(100) : BigInteger.valueOf(17);
        var transaction = transaction(exact, (key, n) -> {
            first.put(key, n); // Ownership is secured before a downstream callback throws.
            throw new IllegalStateException("downstream callback");
        }, (key, n) -> retained.put(key, n));
        enqueue(transaction, exact, KEY, expected);
        enqueue(transaction, exact, LightningKey.EXTREME_HIGH_VOLTAGE, expected);
        transaction.accounted();
        assertThrows(IllegalStateException.class, transaction::close);
        transaction.close();
        assertEquals(1, first.size());
        assertEquals(1, retained.size());
        first.forEach((key, n) -> assertFalse(retained.containsKey(key)));
        first.putAll(retained);
        assertEquals(Map.of(KEY, expected, LightningKey.EXTREME_HIGH_VOLTAGE, expected), first);
    }

    @Test
    void physicalTickBudgetSurvivesRepeatedVisitsAndKeepsUnboundedCopiesOutOfWorkCount() {
        var budget = new CraftingTickBudget();
        assertTrue(budget.begin(10, 2, 5));
        budget.consume(1, 3);
        assertFalse(budget.begin(10, 2, 5));
        assertEquals(1, budget.remainingOperations());
        assertEquals(2, budget.remainingCopies());
        assertThrows(IllegalStateException.class, () -> budget.consume(1, 3));
        budget.consume(1, 2);
        assertFalse(budget.tryOperation());
        assertEquals(2, budget.usedOperations(10));
        assertEquals(0, budget.usedOperations(11));
        assertTrue(budget.begin(11, 2, Long.MAX_VALUE));
        budget.consume(1, Long.MAX_VALUE);
        budget.consume(1, Long.MAX_VALUE);
        assertEquals(0, budget.remainingOperations());
        assertEquals(Long.MAX_VALUE, budget.remainingCopies());
    }

    @Test
    void storageDeliveryBlocksExecutionAndRecursiveDrainWithOrWithoutAnActiveRun() {
        var state = new CraftingExecutionState();
        for (boolean insideRun : new boolean[]{false, true}) {
            if (insideRun) assertTrue(state.tryEnter());
            assertTrue(state.tryEnterDelivery());
            assertFalse(state.tryEnterDelivery());
            assertFalse(state.tryEnter());
            state.leaveDelivery();
            if (insideRun) {
                assertFalse(state.tryEnter());
                state.leave();
            }
            assertFalse(state.busy());
        }
    }

    private static CraftingDispatch transaction(boolean exact,
            java.util.function.BiConsumer<AEKey, BigInteger> receiver,
            java.util.function.BiConsumer<AEKey, BigInteger> fallback) {
        return exact ? CraftingDispatch.exact(receiver, fallback)
                : new CraftingDispatch((key, n) -> receiver.accept(key, BigInteger.valueOf(n)),
                        (key, n) -> fallback.accept(key, BigInteger.valueOf(n)));
    }

    private static void enqueue(CraftingDispatch transaction, boolean exact, AEKey key, BigInteger n) {
        if (exact) assertTrue(transaction.enqueueExact(key, n));
        else {
            var outputs = new KeyCounter();
            outputs.add(key, n.longValueExact());
            assertTrue(transaction.outputSink().enqueue(outputs));
        }
    }
}
