package com.moakiee.ae2lt.logic.batch;

import com.moakiee.ae2lt.api.crafting.IndeterminateSubmissionException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Server-thread-only, bounded receipts. Requests must be immutable value objects. */
public final class SubmissionHistory<R, T> {
    private final int limit;
    private final Map<UUID, Entry<R, T>> entries = new LinkedHashMap<>();

    public SubmissionHistory(int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        this.limit = limit;
    }

    public boolean conflicts(UUID nonce, R request) {
        var entry = entries.get(nonce);
        return entry != null && !entry.request.equals(request);
    }

    public T execute(UUID nonce, R request, Supplier<T> dispatch, Predicate<T> retryable) {
        if (conflicts(nonce, request)) throw new IllegalArgumentException("nonce belongs to a different request");
        var existing = entries.get(nonce);
        if (existing != null) {
            if (existing.failure != null) throw existing.failure;
            if (existing.result != null) return existing.result;
            throw new IndeterminateSubmissionException(nonce, null);
        }
        trim();
        if (entries.size() >= limit) {
            var iterator = entries.entrySet().iterator();
            while (iterator.hasNext()) {
                var candidate = iterator.next().getValue();
                if (candidate.result != null && candidate.failure == null) {
                    iterator.remove();
                    break;
                }
            }
            if (entries.size() >= limit) {
                throw new IllegalStateException("Submission history requires reconciliation before new dispatch");
            }
        }
        var entry = new Entry<R, T>(request);
        entries.put(nonce, entry);
        try {
            T result = Objects.requireNonNull(dispatch.get());
            entry.result = result;
            if (retryable.test(result)) entries.remove(nonce);
            trim();
            return result;
        } catch (RuntimeException failure) {
            entry.failure = new IndeterminateSubmissionException(nonce, failure);
            throw entry.failure;
        }
    }

    private void trim() {
        var iterator = entries.entrySet().iterator();
        while (entries.size() > limit && iterator.hasNext()) {
            var entry = iterator.next().getValue();
            if (entry.result != null && entry.failure == null) iterator.remove();
        }
    }

    private static final class Entry<R, T> {
        final R request;
        T result;
        IndeterminateSubmissionException failure;

        Entry(R request) {
            this.request = Objects.requireNonNull(request);
        }
    }
}
