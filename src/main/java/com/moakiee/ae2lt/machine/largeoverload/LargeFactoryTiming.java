package com.moakiee.ae2lt.machine.largeoverload;

import net.minecraft.world.level.block.entity.BlockEntity;

/** Optional diagnostics for complete factory calls. Inactive in normal play; nested work is counted once. */
public final class LargeFactoryTiming {
    @FunctionalInterface public interface Receiver {
        void sample(BlockEntity host, String section, long nanos);
    }
    private static volatile Receiver receiver;
    private static final ThreadLocal<Boolean> RUNNING = ThreadLocal.withInitial(() -> false);
    private LargeFactoryTiming() { }
    public static void setReceiver(Receiver next) { receiver = next; }
    public static long begin() {
        if (receiver == null || RUNNING.get()) return 0;
        RUNNING.set(true);
        return System.nanoTime();
    }
    public static void end(BlockEntity host, String section, long start) {
        if (start == 0) return;
        long elapsed = System.nanoTime() - start;
        RUNNING.set(false);
        var current = receiver;
        if (current != null) current.sample(host, section, elapsed);
    }
}
