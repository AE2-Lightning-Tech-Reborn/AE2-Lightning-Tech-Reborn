package com.moakiee.ae2lt.logic.management;

import appeng.api.networking.security.IActionSource;

/** Scoped to a native AppFlux send callback; nested sends restore their caller's context. */
public final class AppFluxTelemetry {
    private record Context(IActionSource source, PowerTelemetry.Target target) {}
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private AppFluxTelemetry() {}

    public static void send(IActionSource source, PowerTelemetry.Target target, Runnable action) {
        var previous = CURRENT.get();
        CURRENT.set(new Context(source, target));
        try { action.run(); }
        finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static void record(long accepted) {
        var context = CURRENT.get();
        if (context != null) PowerTelemetry.record(context.source(), context.target(), accepted, false);
    }
}
