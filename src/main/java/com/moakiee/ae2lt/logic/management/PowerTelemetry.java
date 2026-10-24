package com.moakiee.ae2lt.logic.management;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import com.moakiee.ae2lt.logic.energy.TargetAccess;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;

/** Receipt-only observation. Never participates in energy accounting or capability simulation. */
public final class PowerTelemetry {
    public static final int MAX_PATHS = 8192;
    public enum Kind { WIRELESS, INDUCTION, ACCESSOR, LT_OUTPUT, ACCESSOR_PULL }
    public record Target(String dimension, BlockPos pos, int face) {}
    public record Key(Kind kind, Target target, UUID endpoint) {}
    public static final class Meter {
        public final UUID id = UUID.randomUUID();
        public final PowerHistory history = new PowerHistory();
        private long snapshotTick = Long.MIN_VALUE;
        private long lastReceipt;
        private double[] snapshot;
        public double[] rates(long now) {
            if (snapshotTick != now) { snapshot = history.rates(now); snapshotTick = now; }
            return snapshot;
        }
    }
    // Histories follow the physical source node through grid splits and merges. Values never retain the node.
    private static final Map<IGridNode, Map<Key, Meter>> SOURCES = new WeakHashMap<>();
    private static int paths;
    private static long incompleteUntil = Long.MIN_VALUE;
    private static long lastCleanup = Long.MIN_VALUE;
    private PowerTelemetry() {}

    public static void clear() { SOURCES.clear(); paths = 0; incompleteUntil = Long.MIN_VALUE; lastCleanup = Long.MIN_VALUE; }
    public static boolean limited(long now) { return now < incompleteUntil; }
    public static Map<Key, Meter> meters(IGridNode node) {
        var result = SOURCES.get(node);
        if (result == null) return Map.of();
        expire(result, node.getLevel().getServer().getTickCount());
        return result;
    }
    private static void expire(Map<Key, Meter> meters, long now) {
        int before = meters.size();
        meters.values().removeIf(m -> now - m.lastReceipt > 1200);
        paths -= before - meters.size();
    }

    public static void record(IGridNode node, Kind kind, Target target, UUID endpoint, long amount) {
        if (node == null || target == null || amount <= 0) return;
        record(node, new Key(kind, target, endpoint), amount);
    }

    /** Fixed endpoint identities can reuse the immutable key without retaining a node or meter. */
    public static void record(IGridNode node, Key key, long amount) {
        if (node == null || key == null || key.target() == null || amount <= 0) return;
        long now = node.getLevel().getServer().getTickCount();
        var meters = SOURCES.computeIfAbsent(node, ignored -> new HashMap<>());
        var meter = meters.get(key);
        if (meter == null) {
            if (paths >= MAX_PATHS && (lastCleanup == Long.MIN_VALUE || now - lastCleanup >= 20)) {
                for (var value : SOURCES.values()) expire(value, now);
                paths = SOURCES.values().stream().mapToInt(Map::size).sum();
                lastCleanup = now;
            }
            // Freeing capacity cannot recover a receipt already missed within the visible one-minute window.
            if (paths >= MAX_PATHS) { incompleteUntil = now + 1201; return; }
            meter = new Meter();
            meters.put(key, meter);
            paths++;
        }
        meter.history.record(now, amount);
        meter.lastReceipt = now;
    }

    public static long record(IActionSource source, TargetAccess target, long accepted) {
        if (accepted > 0 && target != null && target.telemetryTarget() != null)
            record(source, target.telemetryTarget(), accepted, false);
        return accepted;
    }

    public static void record(IActionSource source, Target target, long accepted, boolean pull) {
        if (accepted <= 0 || source == null) return;
        var machine = source.machine().orElse(null);
        var node = machine == null ? null : machine.getActionableNode();
        var location = DeviceLocation.of(node);
        if (location == null) return;
        Kind kind = location.type().contains("flux_accessor") ? (pull ? Kind.ACCESSOR_PULL : Kind.ACCESSOR)
                : location.type().startsWith("ae2lt:") ? Kind.LT_OUTPUT : Kind.INDUCTION;
        record(node, kind, target == null ? new Target(location.dimension(), location.pos(), location.face()) : target, null, accepted);
    }
}
