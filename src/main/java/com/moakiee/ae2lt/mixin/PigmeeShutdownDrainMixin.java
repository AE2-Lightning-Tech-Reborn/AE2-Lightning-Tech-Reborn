package com.moakiee.ae2lt.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.workstation.PigmeeWorkstationRuntime;
import java.util.Queue;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the native shutdown loop able to advance generation/save futures after proxy removal. */
@Mixin(ChunkMap.class)
public abstract class PigmeeShutdownDrainMixin {
    @Shadow @Final private ServerLevel level;
    @Unique private int ae2lt$unloadPolls;

    @Inject(method = "processUnloads", at = @At("HEAD"))
    private void ae2lt$resetDrainBudget(BooleanSupplier time, CallbackInfo ci) { ae2lt$unloadPolls = 0; }

    @WrapOperation(method = "processUnloads", at = @At(value = "INVOKE", target = "Ljava/util/Queue;poll()Ljava/lang/Object;"))
    private Object ae2lt$yieldShutdownDrain(Queue<?> queue, Operation<Object> original) {
        // stopServer passes an always-true time supplier. A not-ready save callback may
        // synchronously requeue itself forever, starving the native outer loop's task pump.
        // Leave every queued callback intact; the next native iteration resumes draining.
        if (PigmeeWorkstationRuntime.drainingShutdown(level.getServer()) && ++ae2lt$unloadPolls > 512) {
            PigmeeWorkstationRuntime.noteShutdownYield(level.getServer());
            return null;
        }
        return original.call(queue);
    }
}
