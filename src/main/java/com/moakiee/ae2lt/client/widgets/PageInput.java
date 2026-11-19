package com.moakiee.ae2lt.client.widgets;

/** Compatibility entry point for shared paging input semantics. */
public final class PageInput {
    private PageInput() {}

    public static boolean handleScroll(double scrollY, Runnable previous, Runnable next) {
        return com.moakiee.ae2lt.api.client.PageInput.handleScroll(scrollY, previous, next);
    }

    public static boolean isNextClick(int button, boolean handlingRightClick) {
        return com.moakiee.ae2lt.api.client.PageInput.isNextClick(button, handlingRightClick);
    }

    public static boolean isPreviousClick(int button, boolean handlingRightClick) {
        return com.moakiee.ae2lt.api.client.PageInput.isPreviousClick(button, handlingRightClick);
    }
}
