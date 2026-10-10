package com.moakiee.ae2lt.overload.runtime.cpu;

import java.util.Objects;

import com.moakiee.ae2lt.overload.runtime.pattern.SourcePatternSnapshot;

/** Stable pattern identity and source snapshot for CPU output tracking. */
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
