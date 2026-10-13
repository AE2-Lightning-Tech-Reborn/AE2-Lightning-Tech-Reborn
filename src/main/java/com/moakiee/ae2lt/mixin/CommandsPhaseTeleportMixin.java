package com.moakiee.ae2lt.mixin;

import java.util.function.Consumer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;

/** Shares vanilla's command-context lifetime without wrapping execution of the command queue. */
@Mixin(Commands.class)
public abstract class CommandsPhaseTeleportMixin {
    @WrapOperation(
            method = "executeCommandInContext(Lnet/minecraft/commands/CommandSourceStack;"
                    + "Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/ThreadLocal;set(Ljava/lang/Object;)V"),
            require = 2)
    private static void ae2lt$bindCommandContextSource(
            ThreadLocal<?> currentContext,
            Object context,
            Operation<Void> original,
            @Local(argsOnly = true) CommandSourceStack source,
            @Share("previousCommandSource") LocalRef<CommandSourceStack> previousSource) {
        if (context != null) {
            previousSource.set(PhaseFlightMovementGuard.bindCommandSource(source));
            original.call(currentContext, context);
        } else {
            // Vanilla reaches this call from finally, including when queue execution throws.
            try {
                original.call(currentContext, null);
            } finally {
                PhaseFlightMovementGuard.restoreCommandSource(previousSource.get());
            }
        }
    }

    @WrapOperation(
            method = "executeCommandInContext(Lnet/minecraft/commands/CommandSourceStack;"
                    + "Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"),
            require = 2)
    private static void ae2lt$bindCommandInitializationSource(
            Consumer<?> command,
            Object context,
            Operation<Void> original,
            @Local(argsOnly = true) CommandSourceStack source) {
        CommandSourceStack previous = PhaseFlightMovementGuard.bindCommandSource(source);
        try {
            original.call(command, context);
        } finally {
            PhaseFlightMovementGuard.restoreCommandSource(previous);
        }
    }
}
