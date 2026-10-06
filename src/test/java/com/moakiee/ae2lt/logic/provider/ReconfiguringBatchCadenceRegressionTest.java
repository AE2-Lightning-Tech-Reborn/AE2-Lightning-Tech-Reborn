package com.moakiee.ae2lt.logic.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessDispatchMode;

class ReconfiguringBatchCadenceRegressionTest {
    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void relearnsSlowerPartialDrainWithoutRepeatedReservoirRejections() {
        var dispatch = new ProviderWirelessDispatch();
        var target = new WirelessConnection(Level.OVERWORLD, BlockPos.ZERO, Direction.NORTH);
        var pattern = new ProviderSchedulerRetentionTest.Pattern();
        int[] stored = {0};
        int[] pushes = new int[3000];
        int[] processed = new int[3000];
        int[] theoretical = new int[3000];
        long[] accepted = {0};
        long totalProcessed = 0;
        int[] limits = {10, 2000, 10, 2000, 512, 1024};
        int[] periods = {10, 1, 1, 10, 5, 2};
        for (int tick = 0; tick < 3000; tick++) {
            int stage = tick / 500;
            if (tick % periods[stage] == 0) {
                theoretical[tick] = limits[stage];
                processed[tick] = Math.min(stored[0], limits[stage]);
                totalProcessed += processed[tick];
                stored[0] -= processed[tick];
            }
            dispatch.prepare(List.of(target), tick, false, WirelessDispatchMode.EVEN_DISTRIBUTION);
            int currentTick = tick;
            dispatch.dispatchBatch(WirelessDispatchMode.EVEN_DISTRIBUTION, pattern,
                    Long.MAX_VALUE / 4, tick, false,
                    (connection, allowance, exploratory, preserve) -> {

                        var step = connection.pushPatternStep(pattern, allowance, currentTick,
                                true, preserve, () -> false, copies -> {
                                    pushes[currentTick]++;
                                    if (copies > 2000 - stored[0]) {
                                        return ProviderTarget.BatchChunk.REJECTED;
                                    }
                                    stored[0] += copies;
                                    accepted[0] += copies;
                                    return new ProviderTarget.BatchChunk(copies, true, false);
                                });
                        return new ProviderWirelessDispatch.BatchAttemptResult(step.ownedCopies(),
                                step.attemptedCopies(), step.acceptedFullChunk(), step.requestLimited(),
                                step.baselineStatus(), step.globalAbort() ? WirelessPushOutcome.GLOBAL_ABORT
                                        : step.ownedCopies() > 0 ? WirelessPushOutcome.SUCCESS
                                                : WirelessPushOutcome.SOFT_FAIL);
                    }, ignored -> true, ignored -> { throw new AssertionError(); });
        }
        assertEquals(accepted[0], totalProcessed + stored[0], "ownership must be conserved");
        for (int stage = 0; stage < limits.length; stage++) {
            for (int start = stage * 500 + 100; start + 100 <= (stage + 1) * 500; start++) {
                long actual = 0, possible = 0, calls = 0;
                for (int tick = start; tick < start + 100; tick++) {
                    actual += processed[tick]; possible += theoretical[tick]; calls += pushes[tick];
                }
                assertTrue(actual * 100 >= possible * 80,
                        "stage=" + stage + " start=" + start + " throughput=" + actual + "/" + possible);
                double ideal = Math.max(1.0, possible / 2000.0);
                assertTrue(calls <= 4 * ideal,
                        "stage=" + stage + " start=" + start + " calls=" + calls + " ideal=" + ideal);
            }
        }

    }

}
