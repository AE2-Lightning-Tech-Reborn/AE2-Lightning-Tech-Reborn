package com.moakiee.ae2lt.client.ldlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.client.Point;
import org.junit.jupiter.api.Test;

class LdlibRowScrollerTest {
    @Test
    void pressingTheTrackMovesOnePageAndCapturesRelease() {
        var scroller = new LdlibRowScroller();
        scroller.setPosition(new Point(12, 20));
        scroller.setHeight(95);
        scroller.setRange(0, 20, 3);
        scroller.setCurrentScroll(10);

        assertTrue(scroller.onMouseDown(new Point(13, 21), 0));
        assertEquals(7, scroller.getCurrentScroll());
        assertTrue(scroller.wantsAllMouseUpEvents());
        scroller.onMouseUp(new Point(50, 150), 0);
        assertFalse(scroller.wantsAllMouseUpEvents());

        assertTrue(scroller.onMouseDown(new Point(13, 114), 0));
        assertEquals(10, scroller.getCurrentScroll());
        scroller.setVisible(false);
        assertFalse(scroller.wantsAllMouseUpEvents());
    }
}
