package com.moakiee.ae2lt.crafting.runtime;

/** Server-thread execution gate, shared by both CPU backends. Delivery may occur inside a run. */
public final class CraftingExecutionState {
    private boolean executing;
    private boolean delivering;

    public boolean busy() {
        return executing || delivering;
    }

    public boolean tryEnter() {
        if (busy()) return false;
        executing = true;
        return true;
    }

    public void leave() {
        executing = false;
    }

    public boolean tryEnterDelivery() {
        if (delivering) return false;
        delivering = true;
        return true;
    }

    public void leaveDelivery() {
        delivering = false;
    }
}
