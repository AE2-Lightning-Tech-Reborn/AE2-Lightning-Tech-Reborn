package com.moakiee.ae2lt.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

class NaturalLightningCaptureContractTest {
    @Test
    void checksTheCompleteRodCubeAndStopsOnlyAfterSuccessfulCapture() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/moakiee/ae2lt/event/NaturalLightningTransformationHandler.java"));
        int capture = source.indexOf("private static void tryCaptureLightning(");
        int transform = source.indexOf("private static void tryTransformFromNearbyLightningRod(", capture);

        assertTrue(capture >= 0);
        assertTrue(transform > capture);
        String method = source.substring(capture, transform);
        assertTrue(method.contains("BlockPos.withinManhattan(lightningPos, 1, 1, 1)"));
        assertFalse(method.contains("lightningPos.below(2)"));
        assertTrue(method.contains("level.hasChunkAt(rodPos)"));
        assertTrue(method.contains("&& collector.canCaptureLightning()"));
        assertTrue(method.contains("&& collector.captureLightning(naturalWeatherLightning)"));
        assertTrue(method.indexOf("return;") > method.indexOf("&& collector.captureLightning("));

        // This API enumerates the entire box in distance order, including edges and corners.
        // Verify its semantics so switching to a Manhattan-radius diamond cannot lose cells.
        var center = new BlockPos(17, 64, -23);
        var visited = new HashSet<BlockPos>();
        int previousDistance = -1;
        for (BlockPos candidate : BlockPos.withinManhattan(center, 1, 1, 1)) {
            int x = candidate.getX() - center.getX();
            int y = candidate.getY() - center.getY();
            int z = candidate.getZ() - center.getZ();
            assertTrue(Math.abs(x) <= 1 && Math.abs(y) <= 1 && Math.abs(z) <= 1);
            assertTrue(visited.add(candidate.immutable()), "A rod cell was visited twice");
            int distance = x * x + y * y + z * z;
            assertTrue(distance >= previousDistance, "Closer collectors must be tried first");
            previousDistance = distance;
        }
        assertEquals(27, visited.size());
        for (Direction direction : Direction.values()) {
            assertFalse(visited.contains(center.relative(direction, 2)),
                    "A rod outside the documented cube must not be searched");
        }
    }
}
