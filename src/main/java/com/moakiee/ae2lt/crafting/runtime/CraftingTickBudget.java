package com.moakiee.ae2lt.crafting.runtime;

/** Physical-tick accounting. Callers choose what counts as an operation; BI quantities stay outside. */
public final class CraftingTickBudget {
    private long tick = Long.MIN_VALUE;
    private int usedOperations;
    private long usedCopies;
    private int operationLimit;
    private long copyLimit;

    /** Repeated visits, task replacement and snapshot loading must not replenish this budget. */
    public boolean begin(long now, int operations, long copies) {
        boolean advanced = tick != now;
        if (advanced) {
            tick = now;
            usedOperations = 0;
            usedCopies = 0;
        }
        operationLimit = Math.max(0, operations);
        copyLimit = Math.max(0, copies);
        return advanced;
    }

    public int usedOperations(long now) {
        return tick == now ? usedOperations : 0;
    }

    public int remainingOperations() {
        return Math.max(0, operationLimit - usedOperations);
    }

    public long remainingCopies() {
        return copyLimit == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0, copyLimit - usedCopies);
    }

    public void consume(int operations, long copies) {
        if (operations < 0 || copies < 0 || operations > remainingOperations() || copies > remainingCopies())
            throw new IllegalStateException("Crafting exceeded its physical tick budget");
        usedOperations += operations;
        usedCopies = copies > Long.MAX_VALUE - usedCopies ? Long.MAX_VALUE : usedCopies + copies;
    }

    public boolean tryOperation() {
        if (remainingOperations() == 0) return false;
        consume(1, 0);
        return true;
    }

}
