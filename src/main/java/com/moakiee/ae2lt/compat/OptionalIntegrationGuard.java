package com.moakiee.ae2lt.compat;

import java.util.function.Supplier;

import com.mojang.logging.LogUtils;

/**
 * Isolates an optional integration that can safely be omitted, such as borrowed recipe views.
 * Callers must keep optional class resolution inside the supplied lambda. Operations that mutate
 * inventories or other persistent state need their own transaction handling before using this guard.
 */
public final class OptionalIntegrationGuard {
    private final String name;
    private boolean disabled;

    public OptionalIntegrationGuard(String name) {
        this.name = name;
    }

    /** A failed integration stays disabled for this process, including across recipe reloads. */
    public synchronized <T> T get(Supplier<T> operation, T fallback) {
        if (disabled) return fallback;
        try {
            return operation.get();
        } catch (RuntimeException | LinkageError failure) {
            disabled = true;
            LogUtils.getLogger().warn("AE2LT disabled optional integration {} for this session; "
                    + "check the installed mod versions. Other features will continue.", name, failure);
            return fallback;
        }
    }

    public void run(Runnable operation) {
        get(() -> {
            operation.run();
            return true;
        }, false);
    }
}
