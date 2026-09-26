package com.moakiee.ae2lt.client;

final class EasterEggDisplayState {
    static final int DISPLAY_TICKS = 40;
    static final int COOLDOWN_TICKS = 200;

    private int remaining;
    private int cooldown;

    boolean trigger() {
        if (cooldown > 0) return false;
        remaining = DISPLAY_TICKS;
        cooldown = COOLDOWN_TICKS;
        return true;
    }

    void tick() {
        if (remaining > 0) remaining--;
        if (cooldown > 0) cooldown--;
    }

    boolean isActive() { return remaining > 0; }

    boolean isVisible() { return remaining > 0 && remaining <= DISPLAY_TICKS - 2; }

    void dismiss() { remaining = 0; }

    void reset() {
        remaining = 0;
        cooldown = 0;
    }
}
