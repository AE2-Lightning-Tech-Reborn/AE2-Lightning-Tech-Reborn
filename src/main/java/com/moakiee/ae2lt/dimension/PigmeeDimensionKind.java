package com.moakiee.ae2lt.dimension;

public enum PigmeeDimensionKind {
    ODD, EVEN, CUSTOM;

    public static PigmeeDimensionKind load(String value, boolean legacyEven) {
        if (value.isEmpty()) return legacyEven ? EVEN : ODD;
        try { return valueOf(value); }
        catch (IllegalArgumentException ignored) { return legacyEven ? EVEN : ODD; }
    }
}
