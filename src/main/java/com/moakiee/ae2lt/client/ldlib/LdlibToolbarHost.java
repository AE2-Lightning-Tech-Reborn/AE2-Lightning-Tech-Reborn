package com.moakiee.ae2lt.client.ldlib;

import appeng.client.gui.me.common.TerminalSettingsScreen;
import com.moakiee.ae2lt.menu.TianshuMaintenanceMenu;
import net.minecraft.client.gui.screens.Screen;

/** Opts an AE2 screen into LDLib rendering for its inherited controls. */
public interface LdlibToolbarHost {
    static boolean usesLdlibControls(Screen screen) {
        return screen instanceof LdlibToolbarHost
                || (screen instanceof TerminalSettingsScreen<?> settings
                        && settings.getMenu() instanceof TianshuMaintenanceMenu);
    }
}
