package com.moakiee.ae2lt.menu;

/** Shared navigation identifiers. Only the presentation changes when selecting a frequency page. */
public enum FrequencyPage {
    TAB_HOME("ae2lt.gui.tab.home"),
    TAB_SELECTION("ae2lt.gui.tab.selection"),
    TAB_MEMBER("ae2lt.gui.tab.member"),
    TAB_SETTING("ae2lt.gui.tab.setting"),
    TAB_DEVICES("ae2lt.management.devices"),
    TAB_ENERGY("ae2lt.management.energy"),
    TAB_CONNECTION("ae2lt.management.connections"),
    TAB_CREATE("ae2lt.gui.tab.create");

    public static final FrequencyPage[] VALUES = values();
    public static final java.util.List<FrequencyPage> NAVIGATION = java.util.List.of(
            TAB_HOME, TAB_SELECTION, TAB_MEMBER, TAB_DEVICES, TAB_SETTING);
    private final String translationKey;
    FrequencyPage(String translationKey) { this.translationKey = translationKey; }
    public String getTranslationKey() { return translationKey; }
    public boolean isManagement() { return this == TAB_DEVICES || this == TAB_ENERGY || this == TAB_CONNECTION; }
    public FrequencyPage navigationPage() {
        return this == TAB_CREATE ? TAB_SELECTION : isManagement() ? TAB_DEVICES : this;
    }
    public String navigationTranslationKey() {
        return this == TAB_DEVICES ? "ae2lt.management.network" : getTranslationKey();
    }
    public com.moakiee.ae2lt.logic.management.ManagementMode managementMode() {
        return this == TAB_ENERGY ? com.moakiee.ae2lt.logic.management.ManagementMode.ENERGY
                : this == TAB_CONNECTION ? com.moakiee.ae2lt.logic.management.ManagementMode.CONNECTIONS
                : com.moakiee.ae2lt.logic.management.ManagementMode.DEVICES;
    }
    public String widgetId() { return "navigation_" + name().substring(4).toLowerCase(java.util.Locale.ROOT); }
}
