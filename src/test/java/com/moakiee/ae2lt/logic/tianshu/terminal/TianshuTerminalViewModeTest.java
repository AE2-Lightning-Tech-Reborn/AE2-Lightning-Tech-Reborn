package com.moakiee.ae2lt.logic.tianshu.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.api.config.ViewItems;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TianshuTerminalViewModeTest {
    @Test
    void forwardAndReverseTraverseAllFourViewsAndWrap() {
        assertEquals(List.of("STORED", "CRAFTABLE", "MAINTAINABLE", "ALL"), cycle(false));
        assertEquals(List.of("MAINTAINABLE", "CRAFTABLE", "STORED", "ALL"), cycle(true));
    }

    @Test
    void maintainableKeepsStoredCountsAndDoesNotRequireACraftingPattern() {
        assertEquals(ViewItems.ALL, TianshuTerminalViewMode.MAINTAINABLE.viewItems());
        for (var view : ViewItems.values()) {
            assertEquals(TianshuTerminalViewMode.MAINTAINABLE, TianshuTerminalViewMode.from(view, true));
            assertEquals(view, TianshuTerminalViewMode.from(view, false).viewItems());
        }
    }

    private static List<String> cycle(boolean reverse) {
        var result = new ArrayList<String>();
        var mode = TianshuTerminalViewMode.ALL;
        for (int i = 0; i < 4; i++) {
            mode = mode.next(reverse);
            result.add(mode.name());
        }
        return result;
    }
}
