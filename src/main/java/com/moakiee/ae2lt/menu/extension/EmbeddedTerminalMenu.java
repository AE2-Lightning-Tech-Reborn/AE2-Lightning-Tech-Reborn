package com.moakiee.ae2lt.menu.extension;

import appeng.menu.me.common.MEStorageMenu;

/** The outer menu owns network inventory synchronization, energy ticking and the cursor stack. */
public interface EmbeddedTerminalMenu {
    String ACTION_PREFIX = "ae2lt.mest.";
    MEStorageMenu outerMenu();
    String moduleName();

    default String qualifyAction(String action) {
        return ACTION_PREFIX + moduleName() + "." + action;
    }
}
