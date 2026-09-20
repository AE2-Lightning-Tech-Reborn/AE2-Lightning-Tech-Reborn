package com.moakiee.ae2lt.crafting.timewheel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import appeng.api.stacks.KeyCounter;
import appeng.core.AELog;

/** Opt-in boundary tracing. Counts include events omitted by the per-CPU log limit. */
final class CraftingBoundaryDiagnostics {
    private static final int EVENT_LIMIT = 128;
    private static final long WINDOW_TICKS = 200L;
    private final Map<String, Long> counts = new LinkedHashMap<>();
    private UUID jobId;
    private long windowStart;
    private int shown;
    private long omitted;
    private boolean stateReported;

    void snapshot(long tick, UUID id, Supplier<String> state) {
        beginWindow(tick, id);
        if (!stateReported) {
            stateReported = true;
            AELog.warn("[ae2lt][crafting-debug] tick=%d job=%s %s", tick, id, state.get());
        }
    }

    private void beginWindow(long tick, UUID id) {
        if (!id.equals(jobId) || tick < windowStart || tick - windowStart >= WINDOW_TICKS) {
            if (jobId != null) {
                AELog.warn("[ae2lt][crafting-debug] tick=%d job=%s events=%s omitted=%d",
                        tick, jobId, counts, omitted);
            }
            jobId = id;
            windowStart = tick;
            shown = 0;
            omitted = 0L;
            stateReported = false;
            counts.clear();
        }
    }

    void event(long tick, UUID id, String kind, Supplier<String> details) {
        // Also serves callers that execute or return output without a normal CPU tick first.
        beginWindow(tick, id);
        counts.merge(kind, 1L, Long::sum);
        if (shown >= EVENT_LIMIT) {
            omitted++;
            return;
        }
        shown++;
        AELog.warn("[ae2lt][crafting-debug] tick=%d job=%s event=%s %s", tick, id, kind, details.get());
    }

    static String counter(KeyCounter counter) {
        var result = new StringBuilder("[");
        int shown = 0;
        for (var entry : counter) {
            if (entry.getLongValue() <= 0L) continue;
            if (shown++ >= 16) {
                result.append("...truncated");
                break;
            }
            result.append(entry.getKey()).append('=').append(entry.getLongValue()).append(';');
        }
        return result.append(']').toString();
    }
}
