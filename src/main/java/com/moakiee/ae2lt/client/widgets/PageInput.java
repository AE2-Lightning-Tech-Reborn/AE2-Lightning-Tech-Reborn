package com.moakiee.ae2lt.client.widgets;

public final class PageInput {
    private PageInput() {
    }

    public static boolean handleScroll(double scrollY, Runnable previous, Runnable next) {
        if (scrollY > 0) {
            previous.run();
            return true;
        }
        if (scrollY < 0) {
            next.run();
            return true;
        }
        return false;
    }

    public static boolean isNextClick(int button, boolean handlingRightClick) {
        return button == 0 && !handlingRightClick;
    }

    public static boolean isPreviousClick(int button, boolean handlingRightClick) {
        return button == 1 || handlingRightClick;
    }
}
