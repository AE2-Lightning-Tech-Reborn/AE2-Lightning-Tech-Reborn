package com.moakiee.ae2lt.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Test;

class Forge120DependencyFloorTest {
    @Test
    void productionUsesCurrentForgeReleaseAndRejectsIncompatibleOlderBetas() throws Exception {
        var properties = properties();
        assertEquals("2.0.3", properties.getProperty("thunderbolt_version"));
        assertEquals("thunderbolt-reborn-forge-1.20.1", properties.getProperty("thunderbolt_artifact_id"));
        var range = VersionRange.createFromVersionSpec(properties.getProperty("thunderbolt_version_range"));
        assertFalse(range.containsVersion(new DefaultArtifactVersion("2.0.3-beta2")));
        assertFalse(range.containsVersion(new DefaultArtifactVersion("2.0.3-beta3")));
        assertFalse(range.containsVersion(new DefaultArtifactVersion("2.0.3-beta4")));
        assertTrue(range.containsVersion(new DefaultArtifactVersion(properties.getProperty("thunderbolt_version"))));
        assertFalse(range.containsVersion(new DefaultArtifactVersion("2.0.3-beta")));
        assertFalse(range.containsVersion(new DefaultArtifactVersion("2.0.3-beta1")));
        assertFalse(range.containsVersion(new DefaultArtifactVersion("2.1.0")));
    }

    @Test
    void forgeFloorMatchesRuntimeWithAdvancedAeLivingBreatheEvent() throws Exception {
        var properties = properties();
        assertEquals("47.1.47", properties.getProperty("forge_version"));
        assertEquals("[47.1.47,)", properties.getProperty("forge_version_range"));
    }

    private static Properties properties() throws Exception {
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("gradle.properties"))) {
            properties.load(reader);
        }
        return properties;
    }
}
