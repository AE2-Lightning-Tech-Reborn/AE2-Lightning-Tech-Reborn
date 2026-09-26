package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EasterEggDisplayStateTest {
    @Test
    void cooldownPreventsRepeatedTriggersAndExpiryIsDelayedByTwoTicks() {
        EasterEggDisplayState state = new EasterEggDisplayState();
        assertTrue(state.trigger());
        assertFalse(state.isVisible());
        assertFalse(state.trigger());

        state.tick();
        state.tick();
        assertTrue(state.isVisible());
        for (int i = 0; i < EasterEggDisplayState.DISPLAY_TICKS - 2; i++) state.tick();
        assertFalse(state.isActive());
    }

    @Test
    void resetAllowsImmediateTrigger() {
        EasterEggDisplayState state = new EasterEggDisplayState();
        assertTrue(state.trigger());
        state.reset();
        assertTrue(state.trigger());
    }
}
