package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.client.widgets.PageInput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class PageInputTest {
    @Test
    void positiveDeltaSelectsPreviousPage() {
        var previous = new AtomicInteger();
        var next = new AtomicInteger();

        assertTrue(PageInput.handleScroll(1, previous::incrementAndGet, next::incrementAndGet));
        assertEquals(1, previous.get());
        assertEquals(0, next.get());
    }

    @Test
    void negativeDeltaSelectsNextPage() {
        var previous = new AtomicInteger();
        var next = new AtomicInteger();

        assertTrue(PageInput.handleScroll(-1, previous::incrementAndGet, next::incrementAndGet));
        assertEquals(0, previous.get());
        assertEquals(1, next.get());
    }

    @Test
    void zeroDeltaDoesNotConsumeTheEvent() {
        var calls = new AtomicInteger();

        assertFalse(PageInput.handleScroll(0, calls::incrementAndGet, calls::incrementAndGet));
        assertEquals(0, calls.get());
    }

    @Test
    void ordinaryLeftClickSelectsNextPage() {
        assertTrue(PageInput.isNextClick(0, false));
        assertFalse(PageInput.isPreviousClick(0, false));
    }

    @Test
    void directRightClickSelectsPreviousPage() {
        assertFalse(PageInput.isNextClick(1, false));
        assertTrue(PageInput.isPreviousClick(1, false));
    }

    @Test
    void ae2RemappedRightClickStillSelectsPreviousPage() {
        assertFalse(PageInput.isNextClick(0, true));
        assertTrue(PageInput.isPreviousClick(0, true));
    }
}
