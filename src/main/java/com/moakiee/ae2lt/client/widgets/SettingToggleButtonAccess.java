package com.moakiee.ae2lt.client.widgets;

import appeng.api.config.Setting;

/**
 * Exposes the setting represented by an AE2 setting toggle button so injected
 * third-party buttons can be filtered without hiding AE2's native controls.
 */
public interface SettingToggleButtonAccess {
    Setting<?> ae2lt$getSetting();
}
