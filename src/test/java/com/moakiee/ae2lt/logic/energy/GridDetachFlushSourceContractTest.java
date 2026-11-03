package com.moakiee.ae2lt.logic.energy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class GridDetachFlushSourceContractTest {
    @Test
    void normalDistributorFlushesOwnedBufferWhenGridDisappears() throws Exception {
        var source = Files.readString(
                Path.of("src/main/java/com/moakiee/ae2lt/logic/energy/WirelessEnergyDistributor.java"),
                StandardCharsets.UTF_8);
        assertNoGridBranchFlushes(source, "public boolean tickNormal", "normal grid detach");
    }

    @Test
    void overloadDistributorFlushesOwnedBufferWhenGridDisappears() throws Exception {
        var source = Files.readString(
                Path.of("src/main/java/com/moakiee/ae2lt/logic/energy/OverloadedPowerSupplyLogic.java"),
                StandardCharsets.UTF_8);
        assertNoGridBranchFlushes(source, "private boolean tick(ServerLevel serverLevel)", "overload grid detach");
    }

    private static void assertNoGridBranchFlushes(String source, String methodMarker, String label) {
        int method = source.indexOf(methodMarker);
        assertTrue(method >= 0, label + " method is missing");
        int noGrid = source.indexOf("if (!mainNode.isActive() || mainNode.getGrid() == null)", method);
        assertTrue(noGrid > method, label + " guard is missing");
        int blockEnd = findClosingBrace(source, source.indexOf('{', noGrid));
        String block = source.substring(noGrid, blockEnd + 1);
        assertTrue(block.contains("enterIdleState(Status.NO_GRID, true)"),
                label + " must flush owned FE before returning");
        assertTrue(block.contains("return false"), label + " must stop the detached tick");
    }

    private static int findClosingBrace(String source, int opening) {
        int depth = 0;
        for (int i = opening; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }
}
