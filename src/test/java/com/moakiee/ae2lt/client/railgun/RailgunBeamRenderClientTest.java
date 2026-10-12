package com.moakiee.ae2lt.client.railgun;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class RailgunBeamRenderClientTest {

    @Test
    void localBeamUpdateUsesServerTrace() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/railgun/RailgunBeamRenderClient.java"));

        assertTrue(source.contains("prev.from = p.from();"));
        assertTrue(source.contains("prev.to = p.to();"));
        assertTrue(source.contains("prev.ehv = p.ehv();"));
        assertTrue(source.contains("new BeamState(p.shooterId(), p.from(), p.to(), p.ehv(), tick)"));
        assertFalse(source.contains("do NOT touch from/to"));
    }

    @Test
    void localBeamRefreshDoesNotPerformClientEntityLock() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/railgun/RailgunBeamRenderClient.java"));
        String refreshLocalBeam = source.substring(source.indexOf("private static void refreshLocalBeam"));

        assertFalse(
                refreshLocalBeam.contains("ProjectileUtil.getEntityHitResult"),
                "Local beam rendering must not visually lock onto a client-only entity hit.");
        assertFalse(
                refreshLocalBeam.contains("lockedTargetPoint"),
                "Local beam endpoint must come from the server trace, not client-only entity locking.");
    }

    @Test
    void beamChainsKeepSeparateHighAndExtremeVoltagePalettes() throws Exception {
        String beamChain = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/railgun/RailgunBeamChainFx.java"));
        String charged = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/railgun/RailgunClientFx.java"));
        String renderer = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/railgun/RailgunArcRenderer.java"));

        assertTrue(beamChain.contains("spawnHighVoltageChain(a, b, 14)"));
        assertTrue(beamChain.contains("if (p.ehv())"));
        assertTrue(beamChain.contains("RailgunArcRenderer.spawnChain(a, b, 14)"));
        assertTrue(charged.contains("RailgunArcRenderer.spawnChain(a, b, chainLife)"));
        assertTrue(renderer.contains("public static void spawnHighVoltageChain("));
        assertTrue(renderer.contains("blue-cyan HV glow"));
    }

    @Test
    void allBeamLayersEndpointAndCrackleKeepTheServerPalette() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/railgun/RailgunBeamRenderClient.java"));

        assertTrue(source.contains("addBeam(bb, matrix, g.origin, g.endpoint, pulse, smoothTime, s.ehv)"));
        assertTrue(source.contains("addEndpointGlow(bb, matrix, g.endpoint, camPos, pulse, s.ehv)"));
        assertTrue(source.contains("ehv ? 1.00F : 0.22F, ehv ? 0.30F : 0.58F, ehv ? 0.60F : 1.00F"));
        assertTrue(source.contains("ehv ? 1.00F : 0.52F, ehv ? 0.50F : 0.88F, ehv ? 0.75F : 1.00F"));
        assertTrue(source.contains("ehv ? 1.00F : 0.95F, ehv ? 0.80F : 1.00F, ehv ? 0.90F : 1.00F"));
        assertTrue(source.contains("ehv ? 1.00F : 0.65F, ehv ? 0.65F : 0.90F, ehv ? 0.80F : 1.00F"));
        assertTrue(source.contains("if (s.ehv)"));
        assertTrue(source.contains("RailgunArcRenderer.spawnImpactSpark(fromArc, toArc, lifetime)"));
        assertTrue(source.contains("RailgunArcRenderer.spawnBeamSpark(fromArc, toArc, lifetime)"));
        assertFalse(source.contains("RailgunSettings"));
    }

    @Test
    void arcRendererBoundsUntrustedGeometryWithoutReducingSupportedRangeDetail() throws Exception {
        String renderer = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/client/railgun/RailgunArcRenderer.java"));

        assertTrue(renderer.contains("private static final double MAX_ARC_SPAN = 512.0D;"));
        assertTrue(renderer.contains("private static final int MAX_SEGMENTS = 384;"));
        assertTrue(renderer.contains("segments = Math.max(2, Math.min(MAX_SEGMENTS, segments));"));
        assertTrue(renderer.contains("Double.isFinite(value.x)"));
        assertTrue(renderer.contains("Float.isFinite(width)"));
    }
}
