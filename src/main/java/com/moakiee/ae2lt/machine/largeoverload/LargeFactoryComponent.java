package com.moakiee.ae2lt.machine.largeoverload;

/** Parts accepted by this factory only; exposing an inventory never makes another block a hatch. */
public enum LargeFactoryComponent {
    AIR, OTHER, CONTROLLER, FRAME, CASING,
    CORE_T1, CORE_T2, CORE_T3, CORE_T4, FIRMAMENT_CORE,
    PATTERN_HATCH, EXPANDED_PATTERN_HATCH, CRYSTAL_HATCH, PROCESS_CORE_HATCH, ENERGY_HATCH;

    public boolean isCore() {
        return this == CORE_T1 || this == CORE_T2 || this == CORE_T3
                || this == CORE_T4 || this == FIRMAMENT_CORE;
    }

    public boolean isHatch() {
        return isPatternHatch() || this == CRYSTAL_HATCH
                || this == PROCESS_CORE_HATCH || this == ENERGY_HATCH;
    }

    public boolean isPatternHatch() {
        return this == PATTERN_HATCH || this == EXPANDED_PATTERN_HATCH;
    }

    public int patternSlots() {
        return switch (this) {
            case PATTERN_HATCH -> 36;
            case EXPANDED_PATTERN_HATCH -> 144;
            default -> 0;
        };
    }
}
