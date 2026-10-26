package com.moakiee.ae2lt.logic.craft.migration;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.WeakHashMap;
import org.junit.jupiter.api.Test;

public class EcoMigrationLeaseTest {
    @Test void competingMigrationsShareOneLeaseAndCloseReleasesIt() throws Exception {
        Object grid = new Object(), first = new Object(), second = new Object();
        try (var lease = EcoMigrationLease.acquire(Coordinator.class, grid, first)) {
            assertTrue(lease.owned());
            assertNull(EcoMigrationLease.acquire(Coordinator.class, grid, second));
            lease.close();
            assertFalse(lease.owned());
            try (var next = EcoMigrationLease.acquire(Coordinator.class, grid, second)) {
                assertTrue(next.owned());
                lease.close();
                assertTrue(next.owned());
            }
        }
    }
    @Test void ownershipLossIsObservedAndGridsAreIndependent() throws Exception {
        Object grid = new Object(), owner = new Object();
        try (var lease = EcoMigrationLease.acquire(Coordinator.class, grid, owner);
                var other = EcoMigrationLease.acquire(Coordinator.class, new Object(), new Object())) {
            assertTrue(other.owned());
            Coordinator.forGrid(grid).release(owner);
            assertFalse(lease.owned());
        }
    }
    public static class Coordinator {
        private static final Map<Object, Coordinator> ALL = new WeakHashMap<>();
        Object owner;
        public static Coordinator forGrid(Object grid) { return ALL.computeIfAbsent(grid, ignored -> new Coordinator()); }
        public boolean tryAcquire(Object owner) {
            if (this.owner != null && this.owner != owner) return false;
            this.owner = owner; return true;
        }
        public boolean isOwner(Object owner) { return this.owner == owner; }
        public void release(Object owner) { if (this.owner == owner) this.owner = null; }
    }

    @Test void migrationWaitsForEveryLinkedWorkerIncludingUndeliveredThreads() {
        var bus = new Bus();
        assertTrue(PatternMigrationSources.ecoBusy(bus));
        bus.cluster = new Cluster();
        assertFalse(PatternMigrationSources.ecoBusy(bus));
        var linked = new Cluster();
        var thread = new ThreadState();
        var worker = new Worker(); worker.craftingThreads.add(thread); linked.workers.add(worker);
        bus.cluster.network = new Network(java.util.List.of(bus.cluster, linked));
        assertTrue(PatternMigrationSources.ecoBusy(bus));
        thread.free = true;
        assertFalse(PatternMigrationSources.ecoBusy(bus));
    }
    public static class Bus { Cluster cluster; public Cluster getCluster() { return cluster; } }
    public static class Cluster {
        Network network;
        final java.util.List<Worker> workers = new java.util.ArrayList<>();
        public Network getNetworkCluster() { return network; }
        public java.util.List<Worker> getWorkers() { return workers; }
    }
    public record Network(java.util.List<Cluster> members) {
        public java.util.List<Cluster> getMembers() { return members; }
    }
    public static class Worker { private final java.util.List<ThreadState> craftingThreads = new java.util.ArrayList<>(); }
    public static class ThreadState { boolean free; public boolean isFree() { return free; } }
}
