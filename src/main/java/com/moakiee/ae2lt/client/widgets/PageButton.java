package com.moakiee.ae2lt.client.widgets;

import java.util.function.BooleanSupplier;
import com.moakiee.ae2lt.api.client.PageNavigationButton;

/** Compatibility entry point for AE2LT screens. */
public final class PageButton extends PageNavigationButton {
    public PageButton(BooleanSupplier handlingRightClick, Runnable previous, Runnable next) {
        super(handlingRightClick, previous, next);
    }
}
