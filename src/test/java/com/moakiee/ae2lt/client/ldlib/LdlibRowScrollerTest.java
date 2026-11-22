package com.moakiee.ae2lt.client.ldlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.client.Point;
import org.junit.jupiter.api.Test;

class LdlibRowScrollerTest {
    @Test
    void pressingTheTrackMovesOnePageAndCapturesRelease() {
        var scroller = new LdlibRowScroller(appeng.client.gui.widgets.Scrollbar.SMALL, () -> 15);
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

    @Test
    void collapsingRangeOrHidingThumbCancelsDragging() {
        var scroller = new LdlibRowScroller(appeng.client.gui.widgets.Scrollbar.SMALL, () -> 15);
        scroller.setHeight(95);
        scroller.setRange(0, 20, 3);
        assertTrue(scroller.onMouseDown(new Point(0, 3), 0));
        assertTrue(scroller.onMouseDrag(new Point(0, 83), 0));
        assertEquals(20, scroller.getCurrentScroll());
        scroller.setRange(0, 0, 3);
        assertFalse(scroller.wantsAllMouseUpEvents());

        scroller.setRange(0, 20, 3);
        assertTrue(scroller.onMouseDown(new Point(0, 3), 0));
        scroller.setVisible(false);
        assertFalse(scroller.onMouseDrag(new Point(0, 83), 0));
        scroller.setVisible(true);
        scroller.setHeight(15);
        assertTrue(scroller.onMouseDown(new Point(0, 3), 0));
        assertFalse(scroller.wantsAllMouseUpEvents());
    }
}
