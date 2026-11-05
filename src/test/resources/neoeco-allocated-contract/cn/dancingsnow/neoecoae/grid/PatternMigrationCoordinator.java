package cn.dancingsnow.neoecoae.grid;

/** Reflection-only fixture for the unrelated migration portion of LT's optional API. */
public final class PatternMigrationCoordinator {
    public static PatternMigrationCoordinator forGrid(Object grid) { throw new UnsupportedOperationException(); }
    public boolean tryAcquire(Object owner) { throw new UnsupportedOperationException(); }
    public boolean isOwner(Object owner) { throw new UnsupportedOperationException(); }
    public void release(Object owner) { throw new UnsupportedOperationException(); }
}
