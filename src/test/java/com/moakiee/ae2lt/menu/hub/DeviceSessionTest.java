package com.moakiee.ae2lt.menu.hub;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DeviceSessionTest {
    @Test void equalReplacementAndSlotChangesInvalidateQueuedActions() {
        var session = new DeviceSession();
        var first = new String("device"); var replacement = new String("device");
        var token = session.bind(first, 0);
        assertTrue(session.matches(token, first, 0));
        assertFalse(session.matches(token, replacement, 0));
        assertFalse(session.matches(token, first, 40));
        var next = session.bind(replacement, 0);
        assertNotEquals(token, next);
        assertFalse(session.matches(token, replacement, 0));
        session.clear();
        assertFalse(session.matches(next, replacement, 0));
    }
}
