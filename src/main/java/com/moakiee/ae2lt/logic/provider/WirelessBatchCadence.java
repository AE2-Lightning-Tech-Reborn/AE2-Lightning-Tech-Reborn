package com.moakiee.ae2lt.logic.provider;

import com.moakiee.ae2lt.logic.transfer.TransferCadence;

import java.util.HashMap;
import java.util.Map;

import appeng.api.crafting.IPatternDetails;

/** Provider-specific pattern indexing and physical-prefix hints around shared refill timing. */
final class WirelessBatchCadence<T> {
    static final int MAX_COVERAGE_TICKS = TransferCadence.MAX_COVERAGE_TICKS;
    private static final int CAPACITY_AUDIT_INTERVAL = 50;

    private final Map<T, Map<IPatternDetails, State>> states =
            new HashMap<>();

    int recordSuccess(T target, IPatternDetails pattern, long gameTick,
            long ownedCopies, boolean acceptedFullChunk) {
        return recordSuccess(target, pattern, gameTick, ownedCopies,
                acceptedFullChunk, ProviderTarget.BaselineStatus.NONE);
    }

    int recordSuccess(T target, IPatternDetails pattern, long gameTick,
            long ownedCopies, boolean acceptedFullChunk,
            ProviderTarget.BaselineStatus baselineStatus) {
        return recordSuccess(target, pattern, gameTick, ownedCopies,
                acceptedFullChunk, baselineStatus, false);
    }

    int recordSuccess(T target, IPatternDetails pattern, long gameTick,
            long ownedCopies, boolean acceptedFullChunk,
            ProviderTarget.BaselineStatus baselineStatus, boolean requestLimited) {
        if (ownedCopies <= 0L) {
            throw new IllegalArgumentException(
                    "Successful cadence samples must own at least one copy");
        }

        var state = state(target, pattern);
        state.expireIfIdle(gameTick);
        state.finishCapacityAudit(gameTick);
        boolean earlyBulkProbe = state.nextBulkProbe;
        state.nextBulkProbe = false;
        boolean shortSegmentedRefill = state.bulkInterval > 0
                && ownedCopies < state.bulkCapacity && !acceptedFullChunk && !requestLimited
                && (baselineStatus == ProviderTarget.BaselineStatus.PREFIX_COMPLETE
                        || baselineStatus == ProviderTarget.BaselineStatus.RESERVOIR_PREFIX_COMPLETE);
        if (shortSegmentedRefill) {
            // Unlike an atomic rejection, a segmented probe already refilled
            // its accepted prefix. Keep its ownership and timing anchor; a
            // short early probe must not restart the entire capacity learner.
            if (!earlyBulkProbe) {
                state.bulkInterval = Math.min(MAX_COVERAGE_TICKS, state.bulkInterval + 1);
            }
            state.lastBulkSuccessTick = gameTick;
            state.lastBulkAuditTick = gameTick;
            state.bulkRapidProbes = 0;
            state.bulkFailures = 0;
        } else if (state.bulkInterval > 0 && ownedCopies < state.bulkCapacity) {
            // A short success cannot justify the old full-reservoir wait.
            state.clearBulkRefill();
            state.clearStablePrefix();
            // Discard the stale capacity interval in ordinary cadence as well.
            state.timing.reset();
            earlyBulkProbe = false;
        }
        state.observeBaseline(gameTick, ownedCopies, baselineStatus);
        boolean rejectedSinceSuccess = state.timing.wasBlocked();
        int delay = state.timing.success(gameTick, ownedCopies, acceptedFullChunk);
        if (state.bulkInterval > 0) {
            delay = state.bulkInterval;
        }
        if (state.bulkInterval > 0 && ownedCopies >= state.bulkCapacity) {
            if (earlyBulkProbe) {
                int elapsed = (int) Math.max(1L, gameTick - state.lastBulkSuccessTick);
                state.bulkInterval = elapsed;
                state.bulkRapidProbes = 3;
            }
            state.lastBulkSuccessTick = gameTick;
            state.bulkFailures = 0;
            if (state.bulkRapidProbes > 0
                    || gameTick - state.lastBulkAuditTick >= CAPACITY_AUDIT_INTERVAL) {
                state.nextBulkProbe = true;
                state.lastBulkAuditTick = gameTick;
                state.bulkRapidProbes = Math.max(0, state.bulkRapidProbes - 1);
                delay = Math.max(1, (state.bulkInterval * 3 + 3) / 4);
            } else {
                delay = state.bulkInterval;
            }
        }
        state.nextCapacityAudit = state.timing.isEarlyProbe()
                && state.bulkInterval == 0
                && !rejectedSinceSuccess
                && gameTick - state.lastCapacityAuditTick >= CAPACITY_AUDIT_INTERVAL;
        return delay;
    }

    int recordFailure(T target, IPatternDetails pattern, long gameTick) {
        var state = state(target, pattern);
        state.expireIfIdle(gameTick);
        state.finishCapacityAudit(gameTick);
        state.nextCapacityAudit = false;
        if (state.bulkInterval > 0) {
            state.nextBulkProbe = false;
            if (state.bulkRapidProbes > 0) {
                state.bulkRapidProbes = 0;
            }
            if (++state.bulkFailures >= 3) {
                state.clearBulkRefill();
                state.clearStablePrefix();
                return state.timing.blocked(gameTick);
            } else if (state.bulkFailures == 2) {
                // Small corrections avoid prolonged underfeeding after a recipe/speed change.
                state.bulkInterval = Math.min(MAX_COVERAGE_TICKS,
                        state.bulkInterval + 1);
                state.timing.blocked(gameTick);
                return Math.max(1, state.bulkInterval / 4);
            } else if (state.lastBulkSuccessTick != Long.MIN_VALUE) {
                state.timing.blocked(gameTick);
                return Math.max(1, state.bulkInterval
                        - (int) Math.min(state.bulkInterval,
                                gameTick - state.lastBulkSuccessTick));
            }
        }
        return state.timing.blocked(gameTick);
    }

    boolean isExploratoryAttempt(T target, IPatternDetails pattern) {
        var state = existingState(target, pattern);
        return state != null && state.timing.isEarlyProbe();
    }

    boolean shouldReopenReservoirTail(
            T target, IPatternDetails pattern) {
        var state = existingState(target, pattern);
        return state != null && state.nextCapacityAudit;
    }

    boolean usesSingleChunkRefill(T target, IPatternDetails pattern) {
        var state = existingState(target, pattern);
        return state != null && state.singleChunkRefill;
    }

    /** Aggregate refill timing is not evidence of atomic target acceptance. */
    boolean usesBulkRefill(T target, IPatternDetails pattern, int reservoirCapacity) {
        var state = existingState(target, pattern);
        if (state != null) {
            state.reservoirCapacity = reservoirCapacity;
            if (state.bulkInterval > 0 && reservoirCapacity < state.bulkCapacity) {
                state.clearBulkRefill();
            }
        }
        return state != null && state.bulkInterval > 0
                && reservoirCapacity >= state.bulkCapacity;
    }

    boolean shouldPreserveBatchHistory(
            T target, IPatternDetails pattern, long gameTick) {
        var state = existingState(target, pattern);
        if (state == null) {
            return false;
        }
        state.expireIfIdle(gameTick);
        return state.singleChunkRefill || state.timing.isEarlyProbe();
    }

    void removeTarget(T target) {
        states.remove(target);
    }

    void removePattern(T target, IPatternDetails pattern) {
        var byPattern = states.get(target);
        if (byPattern == null) return;
        byPattern.remove(pattern);
        if (byPattern.isEmpty()) states.remove(target);
    }

    void clear() {
        states.clear();
    }

    private State state(T target, IPatternDetails pattern) {
        var byPattern = states.computeIfAbsent(
                target, ignored -> CanonicalPatternMaps.create());
        return byPattern.computeIfAbsent(pattern, ignored -> new State());
    }

    private State existingState(T target, IPatternDetails pattern) {
        var byPattern = states.get(target);
        return byPattern == null ? null : byPattern.get(pattern);
    }

    private static final class State {
        private final TransferCadence timing = new TransferCadence();
        private long lastCapacityAuditTick = Long.MIN_VALUE;
        private boolean nextCapacityAudit;
        private long stablePrefixCopies;
        private int stablePrefixSamples;
        private boolean singleChunkRefill;
        private long stablePrefixStartTick;
        private int bulkInterval;
        private long bulkCapacity;
        private int reservoirCapacity;
        private long lastBulkSuccessTick = Long.MIN_VALUE;
        private long lastBulkAuditTick = Long.MIN_VALUE;
        private int bulkRapidProbes;
        private int bulkFailures;
        private boolean nextBulkProbe;

        private void observeBaseline(
                long gameTick, long ownedCopies,
                ProviderTarget.BaselineStatus baselineStatus) {
            boolean prefix = baselineStatus
                            == ProviderTarget.BaselineStatus.PREFIX_COMPLETE
                    || baselineStatus
                            == ProviderTarget.BaselineStatus.RESERVOIR_PREFIX_COMPLETE;
            if (prefix) {
                if (stablePrefixCopies == ownedCopies) {
                    stablePrefixSamples++;
                } else {
                    stablePrefixCopies = ownedCopies;
                    stablePrefixSamples = 1;
                    stablePrefixStartTick = gameTick;
                }
                singleChunkRefill = stablePrefixSamples >= 4;
                if (stablePrefixSamples == 4 && bulkInterval == 0
                        && gameTick - stablePrefixStartTick <= 25
                        && reservoirCapacity > 0
                        && ownedCopies <= Integer.MAX_VALUE
                        && timing.estimatedCapacity() >= 3L * ownedCopies
                        && timing.estimatedCapacity() <= 4L * ownedCopies) {
                    bulkCapacity = reservoirCapacity;
                    long elapsed = gameTick - stablePrefixStartTick;
                    bulkInterval = (int) Math.max(1L, Math.min(MAX_COVERAGE_TICKS,
                            (elapsed * bulkCapacity + 3L * ownedCopies - 1L)
                                    / (3L * ownedCopies)));
                    lastBulkSuccessTick = gameTick;
                    lastBulkAuditTick = gameTick;
                }
            } else if (singleChunkRefill
                    && (ownedCopies > stablePrefixCopies
                            || baselineStatus
                                    == ProviderTarget.BaselineStatus.GROWTH_COMPLETE)) {
                clearStablePrefix();
            }
        }

        private void clearStablePrefix() {
            stablePrefixCopies = 0L;
            stablePrefixSamples = 0;
            singleChunkRefill = false;
        }

        private void clearBulkRefill() {
            bulkInterval = 0;
            nextBulkProbe = false;
            bulkRapidProbes = 0;
            bulkFailures = 0;
        }

        private void finishCapacityAudit(long gameTick) {
            if (!timing.hasSuccess()) {
                lastCapacityAuditTick = gameTick;
            } else if (nextCapacityAudit) {
                lastCapacityAuditTick = gameTick;
            }
            nextCapacityAudit = false;
        }

        private void expireIfIdle(long gameTick) {
            if (timing.expireIfIdle(gameTick)) {
                lastCapacityAuditTick = Long.MIN_VALUE;
                nextCapacityAudit = false;
                clearStablePrefix();
                clearBulkRefill();
            }
        }
    }
}
