package com.moakiee.ae2lt.overload.runtime.cpu;

import java.util.Objects;

import com.moakiee.ae2lt.overload.runtime.pattern.SourcePatternSnapshot;

/** Per-job pattern identity and source snapshot used by CPU overload tracking. */
public record OverloadPatternReference(
        String patternIdentity,
        SourcePatternSnapshot sourcePattern
) {
    public OverloadPatternReference {
        Objects.requireNonNull(patternIdentity, "patternIdentity");
        if (patternIdentity.isBlank()) {
            throw new IllegalArgumentException("patternIdentity must not be blank");
        }
        Objects.requireNonNull(sourcePattern, "sourcePattern");
    }
}
