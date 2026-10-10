package com.moakiee.ae2lt.overload.runtime.pattern;

import java.util.Objects;

import com.moakiee.ae2lt.overload.runtime.model.EncodedOverloadPattern;

/** Persisted source pattern, slot match modes and required execution host. */
public final class OverloadPatternPayload {
    private final PatternExecutionHostKind requiredHostKind;
    private final SourcePatternSnapshot sourcePattern;
    private final EncodedOverloadPattern encodedPattern;

    public OverloadPatternPayload(
            PatternExecutionHostKind requiredHostKind,
            SourcePatternSnapshot sourcePattern,
            EncodedOverloadPattern encodedPattern
    ) {
        this.requiredHostKind = Objects.requireNonNull(requiredHostKind, "requiredHostKind");
        this.sourcePattern = Objects.requireNonNull(sourcePattern, "sourcePattern");
        this.encodedPattern = Objects.requireNonNull(encodedPattern, "encodedPattern");
    }

    public PatternExecutionHostKind requiredHostKind() {
        return requiredHostKind;
    }

    public SourcePatternSnapshot sourcePattern() {
        return sourcePattern;
    }

    public EncodedOverloadPattern encodedPattern() {
        return encodedPattern;
    }
}
