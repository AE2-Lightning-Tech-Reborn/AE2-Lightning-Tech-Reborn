package com.moakiee.ae2lt.compat.neoeco;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Actual release archives are supplied locally and never included in the mod. */
final class NeoEcoPublishedFixtures {
    private NeoEcoPublishedFixtures() {}

    static Stream<Release> releases() {
        var original = new Release(Path.of("build/neoeco-compat/neoeco-release.jar"), false);
        String fixedJar = System.getProperty("ae2lt.neoeco.fixedReleaseJar");
        if (fixedJar == null) return Stream.of(original);
        Path fixed = Path.of(fixedJar);
        assertTrue(Files.isRegularFile(fixed), "configured NeoECO release archive is missing: " + fixed);
        return Stream.of(original, new Release(fixed, true));
    }

    record Release(Path jar, boolean upstreamIsolation) {
        Path requireJar() {
            assumeTrue(Files.isRegularFile(jar), "requires the checksum-verified NeoECO release: " + jar);
            return jar;
        }

        @Override public String toString() {
            return jar.getFileName() + (upstreamIsolation ? " (upstream isolation)" : " (legacy reader)");
        }
    }
}
