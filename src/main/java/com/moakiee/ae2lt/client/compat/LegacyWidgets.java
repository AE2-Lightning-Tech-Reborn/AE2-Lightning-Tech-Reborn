package com.moakiee.ae2lt.client.compat;

import net.minecraft.client.gui.components.AbstractWidget;

public final class LegacyWidgets {
    private LegacyWidgets() {}
    public static void position(AbstractWidget widget, int x, int y) { widget.x = x; widget.y = y; }
}
