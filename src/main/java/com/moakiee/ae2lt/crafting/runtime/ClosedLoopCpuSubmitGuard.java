package com.moakiee.ae2lt.crafting.runtime;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.crafting.execution.CraftingSubmitResult;

import com.moakiee.thunderbolt.core.crafting.plan.LoopCraftingPlan;

/**
 * Closed-loop jobs belong on a TimeWheel host. Thunderbolt's GTL leftover path
 * may still hand a {@link LoopCraftingPlan} to vanilla, Transfinite, Advanced AE
 * or ECO CPUs; those implementations extract {@code plan.usedItems()} and never
 * honor host reusable seeds. Rejecting at {@code trySubmitJob} HEAD turns the
 * leftover into {@code CPU_OFFLINE} (Tianshu maintenance {@code WAITING_CPU})
 * instead of a foreign CPU starting the job.
 * <p>
 * TimeWheel logic is a sibling class, not a {@code CraftingCpuLogic} subclass,
 * so this guard never intercepts a successful TimeWheel submit.
 */
public final class ClosedLoopCpuSubmitGuard {
    private ClosedLoopCpuSubmitGuard() {
    }

    public static boolean isClosedLoop(ICraftingPlan plan) {
        return plan instanceof LoopCraftingPlan;
    }

    public static void rejectIfClosedLoop(
            ICraftingPlan plan, CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (isClosedLoop(plan)) {
            cir.setReturnValue(CraftingSubmitResult.CPU_OFFLINE);
        }
    }
}
