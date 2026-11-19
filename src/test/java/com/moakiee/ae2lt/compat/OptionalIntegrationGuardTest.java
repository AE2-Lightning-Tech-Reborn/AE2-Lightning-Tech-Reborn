package com.moakiee.ae2lt.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class OptionalIntegrationGuardTest {
    @Test
    void successfulOperationsKeepRunning() {
        var guard = new OptionalIntegrationGuard("test integration");
        var calls = new AtomicInteger();
        assertEquals(1, guard.get(calls::incrementAndGet, -1));
        assertEquals(2, guard.get(calls::incrementAndGet, -1));
        guard.run(calls::incrementAndGet);
        assertEquals(3, calls.get());
    }

    static Stream<Throwable> recoverableFailures() {
        return Stream.of(new NoClassDefFoundError("optional/IngredientStack$Item"),
                new NoSuchMethodError("optional recipe ABI changed"),
                new ExceptionInInitializerError("optional class initialization failed"),
                new ClassCastException("optional ingredient type changed"));
    }

    @ParameterizedTest
    @MethodSource("recoverableFailures")
    void failedIntegrationFallsBackWithoutRetryingButOthersRemainUsable(Throwable failure) {
        var guard = new OptionalIntegrationGuard("test broken integration");
        var calls = new AtomicInteger();
        assertEquals("native", guard.get(() -> {
            calls.incrementAndGet();
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw (LinkageError) failure;
        }, "native"));
        assertEquals("reloaded native", guard.get(() -> {
            calls.incrementAndGet();
            return "should not run";
        }, "reloaded native"));
        guard.run(calls::incrementAndGet);
        assertEquals(1, calls.get());
        assertEquals("healthy", new OptionalIntegrationGuard("other integration")
                .get(() -> "healthy", "fallback"));
    }

    @Test
    void fatalErrorsAreNotSwallowed() {
        var guard = new OptionalIntegrationGuard("test integration");
        var fatal = new OutOfMemoryError("synthetic fatal error");
        assertSame(fatal, assertThrows(OutOfMemoryError.class,
                () -> guard.get(() -> { throw fatal; }, "fallback")));
        assertEquals("still enabled", guard.get(() -> "still enabled", "fallback"));
    }
}
