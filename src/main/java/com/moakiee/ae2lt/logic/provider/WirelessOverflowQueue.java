package com.moakiee.ae2lt.logic.provider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.logic.transfer.TransferPollSchedule;

/** Wireless overflow ownership, learned receipt cadence and compact pattern references. */
final class WirelessOverflowQueue {
    private static final int MAX_BUCKETS = 1024;
    private static final int REARM_BUCKETS = 768;
    private static final int MAX_IDLE_DELAY = 20;
    private static final int UNAVAILABLE_RETRY_DELAY = 20;

    enum OverflowAttemptResult {
        CLEARED(true, false, true),
        PROGRESSED(false, true, true),
        BLOCKED(false, true, false);

        private final boolean removeBucket;
        private final boolean reschedule;
        private final boolean persistentStateChanged;

        OverflowAttemptResult(
                boolean removeBucket,
                boolean reschedule,
                boolean persistentStateChanged) {
            this.removeBucket = removeBucket;
            this.reschedule = reschedule;
            this.persistentStateChanged = persistentStateChanged;
        }

        boolean removeBucket() {
            return removeBucket;
        }

        boolean reschedule() {
            return reschedule;
        }

        boolean persistentStateChanged() {
            return persistentStateChanged;
        }
    }

    /** Strong owners, including removed orphan connections with pending work. */
    private final Map<WirelessConnection, WirelessConnection> ownersByAddress =
            new HashMap<>();
    private final DueTaskQueue<WirelessConnection> retries = new DueTaskQueue<>();
    private final WirelessOverflowPatternTable patterns =
            new WirelessOverflowPatternTable();
    private long lastFlushTick = Long.MIN_VALUE;
    private boolean backpressured;

    boolean isEmpty() {
        return ownersByAddress.isEmpty();
    }

    boolean contains(WirelessConnection connection) {
        return ownersByAddress.containsKey(connection);
    }

    int size() {
        return ownersByAddress.size();
    }

    Set<WirelessConnection> connections() {
        return Set.copyOf(ownersByAddress.values());
    }

    Iterable<Bucket> buckets() {
        var result = new ArrayList<Bucket>(ownersByAddress.size());
        for (var owner : ownersByAddress.values()) {
            var bucket = owner.wirelessOverflow();
            if (bucket != null) {
                result.add(bucket);
            }
        }
        return result;
    }

    @Nullable
    Bucket get(WirelessConnection connection) {
        var owner = canonical(connection);
        return owner == null ? null : owner.wirelessOverflow();
    }

    /**
     * Returns the existing overflow-owned instance for an equal complete
     * address. This runs only during load/topology refresh, never per push.
     */
    WirelessConnection adopt(WirelessConnection connection) {
        var owner = canonical(connection);
        return owner == null ? connection : owner;
    }

    boolean isBackpressured() {
        return backpressured;
    }

    Bucket store(
            WirelessConnection connection,
            IPatternDetails pattern,
            List<GenericStack> overflow,
            boolean forceFallback,
            long gameTick) {
        short patternId = patterns.intern(pattern, this::buckets);
        Bucket bucket;
        if (!forceFallback
                && WirelessOverflowPatternTable.isCompactEligible(pattern)) {
            var inputs = pattern.getInputs();
            var first = overflow.get(0);
            int stuckIndex = WirelessOverflowPatternTable.findSlotIndex(
                    inputs, first.what());
            if (stuckIndex >= 0
                    && WirelessOverflowPatternTable.verifySequentialOverflow(
                            inputs, stuckIndex, overflow)) {
                bucket = Bucket.compact(
                        patternId, (short) stuckIndex, first.amount());
            } else {
                bucket = Bucket.fallback(patternId, overflow);
            }
        } else {
            bucket = Bucket.fallback(patternId, overflow);
        }
        put(connection, bucket, gameTick);
        return bucket;
    }

    Bucket storeRouted(
            WirelessConnection connection,
            IPatternDetails pattern,
            List<RoutedPatternOverflow.Entry> overflow,
            long gameTick) {
        var bucket = Bucket.routedFallback(
                patterns.intern(pattern, this::buckets), overflow);
        put(connection, bucket, gameTick);
        return bucket;
    }

    void restoreBucket(
            WirelessConnection connection, Bucket bucket, long gameTick) {
        put(connection, bucket, gameTick);
    }

    @Nullable
    Bucket remove(WirelessConnection connection) {
        var owner = canonical(connection);
        if (owner == null) {
            return null;
        }
        retries.remove(owner);
        ownersByAddress.remove(owner);
        var removed = owner.wirelessOverflow();
        owner.setWirelessOverflow(null);
        refreshBackpressure();
        return removed;
    }

    boolean beginFlush(long gameTick) {
        if (ownersByAddress.isEmpty() || lastFlushTick == gameTick) {
            return false;
        }
        lastFlushTick = gameTick;
        return true;
    }

    @Nullable
    WirelessConnection pollDue(long gameTick) {
        return retries.pollDue(gameTick);
    }

    void rescheduleUnavailable(
            WirelessConnection connection, Bucket bucket, long gameTick) {
        // An unloaded/missing target is not evidence about its consumption rate.
        bucket.retrySchedule.reset();
        bucket.observationPending = true;
        schedule(connection, bucket, gameTick + UNAVAILABLE_RETRY_DELAY);
    }

    void reschedule(
            WirelessConnection connection,
            Bucket bucket,
            long gameTick,
            OverflowAttemptResult result) {
        if (!result.reschedule()) return;
        if (bucket.observationPending || gameTick > bucket.dueTick) {
            // A late budget-limited visit cannot measure the machine's period either.
            bucket.retrySchedule.beginObservation(gameTick);
            bucket.observationPending = false;
        }
        int delay = result == OverflowAttemptResult.PROGRESSED
                ? bucket.retrySchedule.success(gameTick)
                : bucket.retrySchedule.failure(gameTick, MAX_IDLE_DELAY);
        schedule(connection, bucket, gameTick + delay);
    }

    long nextDueTick() {
        return retries.nextDueTick();
    }

    @Nullable
    IPatternDetails pattern(int unsignedId) {
        return patterns.get(unsignedId);
    }

    void restorePattern(int id, IPatternDetails pattern) {
        patterns.restore(id, pattern);
    }

    void clear() {
        for (var owner : ownersByAddress.values()) {
            owner.setWirelessOverflow(null);
        }
        ownersByAddress.clear();
        retries.clear();
        patterns.clear();
        lastFlushTick = Long.MIN_VALUE;
        backpressured = false;
    }

    void refreshBackpressure() {
        int total = ownersByAddress.size();
        if (backpressured) {
            if (total <= REARM_BUCKETS) {
                backpressured = false;
            }
        } else if (total >= MAX_BUCKETS) {
            backpressured = true;
        }
    }

    private void put(
            WirelessConnection connection, Bucket bucket, long gameTick) {
        var owner = adopt(connection);
        owner.setWirelessOverflow(bucket);
        ownersByAddress.putIfAbsent(owner, owner);
        bucket.retrySchedule.beginObservation(gameTick);
        bucket.observationPending = false;
        schedule(connection, bucket, gameTick + 1);
        refreshBackpressure();
    }

    private void schedule(
            WirelessConnection connection, Bucket bucket, long dueTick) {
        var owner = canonical(connection);
        if (owner != null && owner.wirelessOverflow() == bucket) {
            bucket.dueTick = dueTick;
            retries.schedule(owner, dueTick);
        }
    }

    @Nullable
    private WirelessConnection canonical(WirelessConnection connection) {
        return ownersByAddress.get(connection);
    }

    static final class Bucket
            implements WirelessOverflowPatternTable.PatternReference {
        final boolean compactMode;
        short patternId;
        short stuckIndex;
        long remaining;
        final RoutedPatternOverflow fallback;
        final TransferPollSchedule retrySchedule = new TransferPollSchedule();
        long dueTick;
        boolean observationPending;

        private Bucket(
                boolean compactMode,
                short patternId,
                short stuckIndex,
                long remaining,
                RoutedPatternOverflow fallback) {
            this.compactMode = compactMode;
            this.patternId = patternId;
            this.stuckIndex = stuckIndex;
            this.remaining = remaining;
            this.fallback = fallback;
        }

        static Bucket compact(
                short patternId, short stuckIndex, long remaining) {
            return new Bucket(
                    true,
                    patternId,
                    stuckIndex,
                    remaining,
                    RoutedPatternOverflow.unrouted(List.of()));
        }

        static Bucket fallback(
                short patternId, List<GenericStack> overflow) {
            return new Bucket(
                    false,
                    patternId,
                    (short) 0,
                    0L,
                    RoutedPatternOverflow.unrouted(overflow));
        }

        static Bucket routedFallback(
                short patternId, List<RoutedPatternOverflow.Entry> overflow) {
            return new Bucket(
                    false,
                    patternId,
                    (short) 0,
                    0L,
                    RoutedPatternOverflow.routed(overflow));
        }

        @Override
        public boolean usesPatternDefinition() {
            return compactMode;
        }

        @Override
        public int unsignedPatternId() {
            return Short.toUnsignedInt(patternId);
        }

        @Override
        public void setPatternId(short patternId) {
            this.patternId = patternId;
        }
    }
}
