package com.moakiee.ae2lt.client.gui;

import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.StyleManager;

/** AE2 resource-pack colors for screens that do not inherit AEBaseScreen. */
public final class AE2GuiPalette {
    private AE2GuiPalette() {
    }

    public static int color(PaletteColor color) {
        // StyleManager caches documents and invalidates them on resource reload.
        return StyleManager.loadStyleDoc("/screens/common/common.json").getColor(color).toARGB();
    }
}
