package com.moakiee.ae2lt.crafting.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingSubmitResult;

import com.moakiee.thunderbolt.core.crafting.plan.LoopCraftingPlan;

class ClosedLoopCpuSubmitGuardTest {
    @Test
    void ordinaryPlansPassAndClosedLoopPlansGoOffline() {
        var seed = new LoopCraftingPlanTest.TestKey("guard");
        var pattern = new LoopCraftingPlanTest.TestLoopPattern(seed, UUID.randomUUID(), false);
        var patternTimes = new LinkedHashMap<IPatternDetails, Long>();
        patternTimes.put(pattern, 1L);
        var nativePlan = new CraftingPlan(
                new GenericStack(seed, 1L), 0L, false, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), patternTimes);
        var loopPlan = LoopCraftingPlan.wrapIfNeeded(nativePlan);

        assertFalse(ClosedLoopCpuSubmitGuard.isClosedLoop(nativePlan));
        assertTrue(ClosedLoopCpuSubmitGuard.isClosedLoop(loopPlan));

        var nativeCir = new CallbackInfoReturnable<ICraftingSubmitResult>("trySubmitJob", true);
        ClosedLoopCpuSubmitGuard.rejectIfClosedLoop(nativePlan, nativeCir);
        assertFalse(nativeCir.isCancelled());

        var loopCir = new CallbackInfoReturnable<ICraftingSubmitResult>("trySubmitJob", true);
        ClosedLoopCpuSubmitGuard.rejectIfClosedLoop(loopPlan, loopCir);
        assertTrue(loopCir.isCancelled());
        assertSame(CraftingSubmitResult.CPU_OFFLINE, loopCir.getReturnValue());
    }
}
