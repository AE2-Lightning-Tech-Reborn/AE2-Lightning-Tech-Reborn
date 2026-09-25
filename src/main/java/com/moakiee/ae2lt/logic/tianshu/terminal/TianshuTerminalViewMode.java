package com.moakiee.ae2lt.logic.tianshu.terminal;

import appeng.api.config.ViewItems;

/** One terminal selection, including the maintenance filter and its underlying AE2 view. */
public enum TianshuTerminalViewMode {
    ALL(ViewItems.ALL),
    STORED(ViewItems.STORED),
    CRAFTABLE(ViewItems.CRAFTABLE),
    MAINTAINABLE(ViewItems.ALL);

    private final ViewItems viewItems;

    TianshuTerminalViewMode(ViewItems viewItems) {
        this.viewItems = viewItems;
    }

    public ViewItems viewItems() {
        return viewItems;
    }

    public TianshuTerminalViewMode next(boolean reverse) {
        var modes = values();
        return modes[Math.floorMod(ordinal() + (reverse ? -1 : 1), modes.length)];
    }

    public static TianshuTerminalViewMode from(ViewItems viewItems, boolean maintainable) {
        if (maintainable) return MAINTAINABLE;
        return switch (viewItems) {
            case ALL -> ALL;
            case STORED -> STORED;
            case CRAFTABLE -> CRAFTABLE;
        };
    }
}
