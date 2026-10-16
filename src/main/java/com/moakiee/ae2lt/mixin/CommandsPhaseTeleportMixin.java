package com.moakiee.ae2lt.mixin;

import java.util.function.Supplier;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.util.profiling.ProfilerFiller;

import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;

/**
 * Uses 1.20.1's profiler push/finally-pop lifetime in place of 1.21's execution context.
 * Parsing keeps its own short scope; dispatched commands have no guard wrapper on their stack.
 */
@Mixin(Commands.class)
public abstract class CommandsPhaseTeleportMixin {
    @WrapOperation(
            method = "performPrefixedCommand(Lnet/minecraft/commands/CommandSourceStack;Ljava/lang/String;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/CommandDispatcher;parse(Ljava/lang/String;Ljava/lang/Object;)"
                            + "Lcom/mojang/brigadier/ParseResults;",
                    remap = false))
    private ParseResults<CommandSourceStack> ae2lt$bindCommandParseSource(
            CommandDispatcher<CommandSourceStack> dispatcher,
            String command,
            Object source,
            Operation<ParseResults<CommandSourceStack>> original) {
        CommandSourceStack previous = PhaseFlightMovementGuard.bindCommandSource((CommandSourceStack) source);
        try {
            return original.call(dispatcher, command, source);
        } finally {
            PhaseFlightMovementGuard.restoreCommandSource(previous);
        }
    }

    @WrapOperation(
            method = "performCommand(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiling/ProfilerFiller;push(Ljava/util/function/Supplier;)V"))
    private void ae2lt$bindCommandContextSource(
            ProfilerFiller profiler,
            Supplier<String> name,
            Operation<Void> original,
            @Local(argsOnly = true) ParseResults<CommandSourceStack> parseResults,
            @Share("previousCommandSource") LocalRef<CommandSourceStack> previousSource) {
        original.call(profiler, name);
        previousSource.set(PhaseFlightMovementGuard.bindCommandSource(parseResults.getContext().getSource()));
    }

    @WrapOperation(
            method = "performCommand(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)I",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;pop()V"),
            require = 2)
    private void ae2lt$restoreCommandContextSource(
            ProfilerFiller profiler,
            Operation<Void> original,
            @Share("previousCommandSource") LocalRef<CommandSourceStack> previousSource) {
        try {
            original.call(profiler);
        } finally {
            PhaseFlightMovementGuard.restoreCommandSource(previousSource.get());
        }
    }
}
