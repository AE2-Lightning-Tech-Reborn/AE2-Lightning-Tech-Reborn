package com.moakiee.ae2lt.config;

/** Terminal-wide policy for selecting a processing-pattern upload target automatically. */
public enum TianshuUploadMatchMode {
    OFF,
    SAVED_ALIAS,
    ANY_MATCH;

    public TianshuUploadMatchMode next() { return values()[(ordinal() + 1) % values().length]; }

    public String query(String savedAlias, String suggestedAlias) {
        String saved = savedAlias == null ? "" : savedAlias.strip();
        return switch (this) {
            case OFF -> "";
            case SAVED_ALIAS -> saved;
            case ANY_MATCH -> !saved.isEmpty() ? saved : suggestedAlias == null ? "" : suggestedAlias.strip();
        };
    }
}
