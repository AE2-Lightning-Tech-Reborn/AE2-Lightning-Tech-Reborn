package com.moakiee.ae2lt.debug;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises source lifetimes and profiler stack ownership through vanilla command queues. */
@GameTestHolder("ae2lt_phase_command")
@PrefixGameTestTemplate(false)
public final class PhaseCommandSourceGameTests {
    @GameTest(template = "pigmee_station_empty")
    public static void queueExecutionHasNoGuardWrapperAndAuthorizesOnlyItsPlayer(GameTestHelper h) {
        ServerPlayer owner = player(h);
        ServerPlayer other = player(h);
        try {
            boolean[] ran = {false};
            Commands.executeCommandInContext(source(owner), context -> queue(context, () -> {
                assertSource(h, owner, other);
                h.assertTrue(StackWalker.getInstance().walk(frames -> frames.noneMatch(frame ->
                                frame.getClassName().equals(PhaseFlightMovementGuard.class.getName())
                                        || frame.getMethodName().startsWith("ae2lt$bindCommand"))),
                        "command queue is still nested under a phase-guard wrapper");
                var target = owner.position().add(1, 0, 0);
                owner.setPos(target.x, target.y, target.z);
                h.assertTrue(owner.position().equals(target), "self command teleport was blocked");
                var otherPosition = other.position();
                other.setPos(otherPosition.x + 1, otherPosition.y, otherPosition.z);
                h.assertTrue(other.position().equals(otherPosition), "command authorized another player's teleport");
                ran[0] = true;
            }));
            h.assertTrue(ran[0], "vanilla did not execute the queued action");
            assertCleared(h, owner, other);
        } finally {
            clear(owner, other);
        }
        h.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void nestedInitializationRestoresOuterSourceIncludingOnException(GameTestHelper h) {
        ServerPlayer outer = player(h);
        ServerPlayer nested = player(h);
        try {
            boolean[] ran = {false};
            Commands.executeCommandInContext(source(outer), context -> {
                assertSource(h, outer, nested);
                Commands.executeCommandInContext(source(nested), nestedContext -> {
                    h.assertTrue(nestedContext == context, "nested commands must share the vanilla queue");
                    assertSource(h, nested, outer);
                });
                assertSource(h, outer, nested);
                RuntimeException failure = new RuntimeException("nested initialization probe");
                expectSameFailure(h, failure, () -> Commands.executeCommandInContext(source(nested), nestedContext -> {
                    assertSource(h, nested, outer);
                    throw failure;
                }));
                assertSource(h, outer, nested);
                queue(context, () -> {
                    assertSource(h, outer, nested);
                    ran[0] = true;
                });
            });
            h.assertTrue(ran[0], "nested failure prevented outer queue execution");
            assertCleared(h, outer, nested);
        } finally {
            clear(outer, nested);
        }
        h.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void initializationAndQueueFailuresLeaveNoAuthorization(GameTestHelper h) {
        ServerPlayer first = player(h);
        ServerPlayer next = player(h);
        try {
            RuntimeException initializationFailure = new RuntimeException("initialization probe");
            expectSameFailure(h, initializationFailure, () -> Commands.executeCommandInContext(source(first), context -> {
                assertSource(h, first, next);
                throw initializationFailure;
            }));
            assertCleared(h, first, next);

            RuntimeException queueFailure = new RuntimeException("queue probe");
            expectSameFailure(h, queueFailure, () -> Commands.executeCommandInContext(source(first), context ->
                    queue(context, () -> {
                        assertSource(h, first, next);
                        throw queueFailure;
                    })));
            assertCleared(h, first, next);

            boolean[] ran = {false};
            Commands.executeCommandInContext(source(next), context -> queue(context, () -> {
                assertSource(h, next, first);
                ran[0] = true;
            }));
            h.assertTrue(ran[0], "failed queue leaked vanilla's execution context");
            assertCleared(h, first, next);
        } finally {
            clear(first, next);
        }
        h.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void privilegedCommandPolicyAppliesOnlyDuringItsQueue(GameTestHelper h) {
        ServerPlayer owner = player(h);
        ServerPlayer other = player(h);
        try {
            Commands.executeCommandInContext(source(owner).withPermission(Commands.LEVEL_GAMEMASTERS), context ->
                    queue(context, () -> {
                        assertSource(h, owner, other);
                        boolean shouldBlock = !AE2LTCommonConfig.overloadArmorPhaseLockTeleportMode().disablesProtection()
                                && !AE2LTCommonConfig.overloadArmorPhaseLockTeleportMode().ignoresPrivilegedCommands();
                        h.assertTrue(PhaseFlightMovementGuard.blocksExternalTeleports(other) == shouldBlock,
                                "privileged-command teleport policy changed");
                    }));
            assertCleared(h, owner, other);
            h.assertTrue(PhaseFlightMovementGuard.blocksExternalTeleports(other),
                    "privileged command left external teleport protection disabled");
        } finally {
            clear(owner, other);
        }
        h.succeed();
    }

    private static void queue(ExecutionContext<CommandSourceStack> context, Runnable action) {
        context.queueNext(new CommandQueueEntry<>(
                new Frame(0, CommandResultCallback.EMPTY, () -> {}),
                (current, frame) -> action.run()));
    }

    private static ServerPlayer player(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "PhaseCommand"));
        PhaseFlightMovementGuard.updatePhaseLockProtection(player, false, true);
        return player;
    }

    private static CommandSourceStack source(ServerPlayer player) {
        return player.createCommandSourceStack().withPermission(0);
    }

    private static void assertSource(GameTestHelper h, ServerPlayer owner, ServerPlayer other) {
        h.assertTrue(PhaseFlightMovementGuard.isSelfTeleportAuthorized(owner), "initiating player's source was lost");
        h.assertTrue(!PhaseFlightMovementGuard.isSelfTeleportAuthorized(other), "source authorized another player");
    }

    private static void assertCleared(GameTestHelper h, ServerPlayer first, ServerPlayer second) {
        h.assertTrue(!PhaseFlightMovementGuard.isSelfTeleportAuthorized(first)
                        && !PhaseFlightMovementGuard.isSelfTeleportAuthorized(second),
                "command source authorization leaked after return");
    }

    private static void expectSameFailure(GameTestHelper h, RuntimeException expected, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException actual) {
            h.assertTrue(actual == expected, "command failure was replaced: " + actual);
            return;
        }
        h.fail("command failure was swallowed");
    }

    private static void clear(ServerPlayer first, ServerPlayer second) {
        PhaseFlightMovementGuard.clear(first);
        PhaseFlightMovementGuard.clear(second);
    }
}
