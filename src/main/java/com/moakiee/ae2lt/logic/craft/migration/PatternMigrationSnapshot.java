package com.moakiee.ae2lt.logic.craft.migration;

/** Small, immutable menu report. Slot contents and optional-mod types never cross the wire. */
public record PatternMigrationSnapshot(Stage stage, Reason reason, int tier, long scanned, long total,
        long moved, long recovered, long incompatible, long noSpace, long refundBlocked,
        long unavailable, long unsupported, long disks, long lastSliceNanos, long budgetHits) {
    public static final PatternMigrationSnapshot IDLE = new PatternMigrationSnapshot(
            Stage.IDLE, Reason.NONE, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public boolean active() {
        return switch (stage) {
            case CHECKING, INDEXING, SCANNING, MIGRATING, WAITING -> true;
            default -> false;
        };
    }

    public enum Stage { IDLE, CHECKING, INDEXING, SCANNING, MIGRATING, WAITING, COMPLETE, STOPPED, REJECTED }
    public enum Reason { NONE, BUSY, LEASE_BUSY, OFFLINE, TARGET_CHANGED, CANCELLED, RECOVERY,
        ADAPTER_FAILED, NO_NETWORK }
}
