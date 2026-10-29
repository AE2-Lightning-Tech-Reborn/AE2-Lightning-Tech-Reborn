package com.moakiee.ae2lt.logic.management;

/** Three views of the same controller, selected inside the network management page. */
public enum ManagementMode {
    DEVICES("devices"), ENERGY("energy"), CONNECTIONS("connections");
    private final String translationKey;
    ManagementMode(String key) { translationKey = "ae2lt.management." + key; }
    public String translationKey() { return translationKey; }
    public ManagementMode next() { return values()[(ordinal() + 1) % values().length]; }
}
