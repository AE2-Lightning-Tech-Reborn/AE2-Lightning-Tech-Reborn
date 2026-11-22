package com.moakiee.ae2lt.client.ldlib;

import net.minecraft.client.gui.screens.Screen;

/** Opts a container screen into LDLib drawing for inherited slot decorations. */
public interface LdlibSlotHost {
    static boolean usesLdlibSlotDecorations(Screen screen) {
        return screen instanceof LdlibSlotHost || LdlibToolbarHost.usesLdlibControls(screen);
    }
}
