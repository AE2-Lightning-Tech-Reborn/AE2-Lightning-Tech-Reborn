package com.moakiee.ae2lt.client.ldlib;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ToolboxPanel;
import net.minecraft.network.chat.Component;

/** Keeps native AE2 toolbox painting and tooltip behavior in the terminal compatibility host. */
public final class LdlibToolboxPanel extends ToolboxPanel {
    public LdlibToolboxPanel(ScreenStyle style, Component name) {
        super(style, name);
    }
}
