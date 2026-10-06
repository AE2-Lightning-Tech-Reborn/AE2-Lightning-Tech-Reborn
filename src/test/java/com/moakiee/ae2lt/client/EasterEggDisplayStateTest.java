package com.moakiee.ae2lt.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class EasterEggDisplayStateTest {
    @Test
    void continuousLightningDoesNotRestartOrProlongTheImage() {
        var state = new EasterEggDisplayState();
        assertTrue(state.trigger());
        assertFalse(state.isVisible());
        state.tick();
        assertFalse(state.trigger());
        assertFalse(state.isVisible());
        state.tick();
        assertTrue(state.isVisible());
        for (int index = 2; index < 40; index++) {
            assertFalse(state.trigger());
            state.tick();
        }
        assertFalse(state.isActive());
        for (int index = 40; index < 200; index++) {
            assertFalse(state.trigger());
            state.tick();
        }
        assertTrue(state.trigger());
    }

    @Test
    void leavingRangeDismissesImageWithoutBypassingCooldownAndLogoutResetsIt() {
        var state = new EasterEggDisplayState();
        state.trigger();
        state.dismiss();
        assertFalse(state.isActive());
        assertFalse(state.trigger());
        state.reset();
        assertFalse(state.isActive());
        assertTrue(state.trigger());
    }
}
