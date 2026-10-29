package com.moakiee.ae2lt.logic.energy;

import java.util.UUID;

/** A same-tick budget lineage. Splits share it; merges union it without counting it twice. */
final class EnergyTickBudget {
    final UUID id = UUID.randomUUID();
    final long tick;
    private EnergyTickBudget parent;
    private int rank;
    private long spent;
    private int visits;

    EnergyTickBudget(long tick) { this.tick = tick; }
    private EnergyTickBudget root() {
        var root = this;
        while (root.parent != null) root = root.parent;
        return root;
    }
    void merge(EnergyTickBudget other) {
        if (tick != other.tick) throw new IllegalArgumentException("Different budget ticks");
        var a = root(); var b = other.root();
        if (a == b) return;
        if (a.rank < b.rank) { var swap = a; a = b; b = swap; }
        a.spent = a.spent > Long.MAX_VALUE - b.spent ? Long.MAX_VALUE : a.spent + b.spent;
        a.visits = a.visits > Integer.MAX_VALUE - b.visits ? Integer.MAX_VALUE : a.visits + b.visits;
        b.parent = a;
        if (a.rank == b.rank) a.rank++;
    }
    long preview(long request, long feLimit, int visitLimit, long reservedFe, int reservedVisits) {
        var r = root();
        if (r.visits >= Math.max(0, visitLimit - reservedVisits)) return 0;
        long remaining = Math.max(0, feLimit - r.spent);
        return Math.min(Math.max(0, request), Math.max(0, remaining - Math.min(remaining, reservedFe)));
    }
    long reserve(long request, long feLimit, int visitLimit, long reservedFe, int reservedVisits) {
        long allowed = preview(request, feLimit, visitLimit, reservedFe, reservedVisits);
        var r = root();
        if (r.visits < Math.max(0, visitLimit - reservedVisits)) r.visits++;
        r.spent += allowed;
        return allowed;
    }
}
