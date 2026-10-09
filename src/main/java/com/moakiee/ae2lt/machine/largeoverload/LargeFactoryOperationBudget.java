package com.moakiee.ae2lt.machine.largeoverload;

/**
 * One server-thread instance per factory, shared by all hatches and active/passive dispatches.
 * Completed operations stay charged until the next server tick for finite cores; pending reservations block reentry.
 */
public final class LargeFactoryOperationBudget {
    public static final long FIRMAMENT_OPERATIONS_PER_TICK = 1_024;
    /** Internal unlimited sentinel; each individual transaction still fits in the long amount API. */
    public static final long UNLIMITED = Long.MAX_VALUE;
    private long capacity;
    private boolean initialized;
    private long tick;
    private long used;
    private long reserved;
    private int openReservations;

    public LargeFactoryOperationBudget(long capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Factory operation capacity must be positive");
        this.capacity = capacity;
    }

    private void beginTick(long currentTick) {
        if (initialized && currentTick < tick) throw new IllegalArgumentException("Stale factory tick");
        if (!initialized || currentTick > tick) {
            if (openReservations != 0) throw new IllegalStateException("Reservation escaped its server tick");
            tick = currentTick;
            used = 0;
            initialized = true;
        }
    }

    public long remainingOperations(long currentTick) {
        beginTick(currentTick);
        return capacity == UNLIMITED ? Long.MAX_VALUE - reserved : Math.max(0, capacity - used - reserved);
    }

    /** Config reloads change the ceiling without refunding work already completed this tick. */
    public void reconfigure(long currentTick, long capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Factory operation capacity must be positive");
        beginTick(currentTick);
        if (openReservations != 0) throw new IllegalStateException("Cannot reconfigure during a transaction");
        this.capacity = capacity;
    }

    public Reservation reserve(long currentTick, long requestedCopies, long sourceOperationsPerCopy) {
        if (requestedCopies < 0 || sourceOperationsPerCopy <= 0) {
            throw new IllegalArgumentException("Invalid factory pattern multiplicity");
        }
        long copies = Math.min(requestedCopies, remainingOperations(currentTick) / sourceOperationsPerCopy);
        long operations = Math.multiplyExact(copies, sourceOperationsPerCopy);
        reserved = Math.addExact(reserved, operations);
        openReservations = Math.incrementExact(openReservations);
        return new Reservation(currentTick, copies, sourceOperationsPerCopy, operations);
    }

    public final class Reservation implements AutoCloseable {
        private final long reservationTick;
        private final long copies;
        private final long sourceOperationsPerCopy;
        private final long operations;
        private boolean closed;

        private Reservation(long reservationTick, long copies, long sourceOperationsPerCopy, long operations) {
            this.reservationTick = reservationTick;
            this.copies = copies;
            this.sourceOperationsPerCopy = sourceOperationsPerCopy;
            this.operations = operations;
        }

        public long copies() { return copies; }
        public long sourceOperations() { return operations; }

        /** Call only when resource and product ownership for these copies has committed. */
        public void commit(long completedCopies) {
            if (closed || reservationTick != tick) throw new IllegalStateException("Closed or stale reservation");
            if (completedCopies < 0 || completedCopies > copies) {
                throw new IllegalArgumentException("Completion exceeds reserved pattern copies");
            }
            long completed = Math.multiplyExact(completedCopies, sourceOperationsPerCopy);
            // Unlimited cores may finish more than Long.MAX_VALUE operations across separate transactions.
            // Keep saturated usage so a same-tick change back to a finite cap cannot refund earlier work.
            used = completed > Long.MAX_VALUE - used ? Long.MAX_VALUE : used + completed;
            reserved -= operations;
            openReservations--;
            closed = true;
        }

        @Override
        public void close() {
            if (!closed) {
                if (reservationTick != tick) throw new IllegalStateException("Stale factory reservation");
                reserved -= operations;
                openReservations--;
                closed = true;
            }
        }
    }
}
