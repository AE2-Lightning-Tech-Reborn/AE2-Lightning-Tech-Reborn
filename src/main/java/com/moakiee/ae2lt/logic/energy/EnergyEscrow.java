package com.moakiee.ae2lt.logic.energy;

/** Single-owner, already-extracted FE. Never infer a balance from simulated IO. */
public final class EnergyEscrow {
    private long available;
    private long uncertain;
    private final Runnable changed;

    public EnergyEscrow(long available, long uncertain, Runnable changed) {
        if (available < 0 || uncertain < 0 || available > Long.MAX_VALUE - uncertain) {
            throw new IllegalArgumentException("Invalid escrow balance");
        }
        this.available = available;
        this.uncertain = uncertain;
        this.changed = changed;
    }
    public long available() { return available; }
    public long uncertain() { return uncertain; }
    public long room() { return Long.MAX_VALUE - available - uncertain; }
    /** Move ownership before notifying either persistence callback. */
    public void transferTo(EnergyEscrow destination) {
        if (destination == this) return;
        if (available > destination.room() - uncertain) throw new IllegalArgumentException("Escrow overflow");
        destination.available += available;
        destination.uncertain += uncertain;
        available = 0;
        uncertain = 0;
        try { changed.run(); } finally { destination.changed.run(); }
    }
    public void credit(long amount) {
        if (amount < 0 || amount > room()) throw new IllegalArgumentException("Escrow overflow");
        if (amount != 0) { available += amount; changed.run(); }
    }
    public long debit(long amount) {
        long taken = Math.min(Math.max(0, amount), available);
        if (taken != 0) { available -= taken; changed.run(); }
        return taken;
    }
    /** An external receiver that throws may already have consumed FE. Never replay it. */
    public void quarantine(long amount) {
        if (amount < 0 || amount > room()) throw new IllegalArgumentException("Escrow overflow");
        if (amount != 0) { uncertain += amount; changed.run(); }
    }
    public void settle(long offered, long accepted) {
        if (offered < 0 || offered > uncertain || accepted < 0 || accepted > offered) {
            throw new IllegalArgumentException("Invalid receipt");
        }
        uncertain -= offered;
        available += offered - accepted;
        changed.run();
    }
}
