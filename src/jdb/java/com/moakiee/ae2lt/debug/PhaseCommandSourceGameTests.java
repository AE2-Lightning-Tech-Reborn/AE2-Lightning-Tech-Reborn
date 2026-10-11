package com.moakiee.ae2lt.debug;

import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.moakiee.ae2lt.celestweave.PhaseFlightMovementGuard;
import com.moakiee.ae2lt.config.AE2LTCommonConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Exercises 1.20.1's synchronous dispatcher, return codes and finally-based source lifetime. */
@GameTestHolder("ae2lt_phase_command")
@PrefixGameTestTemplate(false)
public final class PhaseCommandSourceGameTests {
    @GameTest(template = "pigmee_station_empty")
    public static void commandExecutionHasNoGuardWrapperAndAuthorizesOnlyItsPlayer(GameTestHelper h) {
        ServerPlayer owner = player(h);
        ServerPlayer other = player(h);
        try {
            String command = register(h, context -> {
                assertSource(h, owner, other);
                h.assertTrue(StackWalker.getInstance().walk(frames -> frames.noneMatch(frame ->
                                frame.getClassName().equals(PhaseFlightMovementGuard.class.getName())
                                        || frame.getMethodName().startsWith("ae2lt$bindCommand"))),
                        "command execution is still nested under a phase-guard wrapper");
                var target = owner.position().add(1, 0, 0);
                owner.setPos(target.x, target.y, target.z);
                h.assertTrue(owner.position().equals(target), "self command teleport was blocked");
                var otherPosition = other.position();
                other.setPos(otherPosition.x + 1, otherPosition.y, otherPosition.z);
                h.assertTrue(other.position().equals(otherPosition), "command authorized another player's teleport");
                return 37;
            });
            h.assertTrue(execute(h, owner, "/" + command) == 37, "prefixed command's return code changed");
            assertCleared(h, owner, other);
            var commands = h.getLevel().getServer().getCommands();
            h.assertTrue(commands.performCommand(commands.getDispatcher().parse(command, source(owner)), command) == 37,
                    "direct parsed-command execution changed");
            assertCleared(h, owner, other);
        } finally {
            clear(owner, other);
        }
        h.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void nestedCommandsRestoreOuterSourceIncludingOnException(GameTestHelper h) {
        ServerPlayer outer = player(h);
        ServerPlayer nested = player(h);
        try {
            String inner = register(h, context -> {
                assertSource(h, nested, outer);
                return 23;
            });
            AssertionError failure = new AssertionError("nested command probe");
            String failing = register(h, context -> {
                assertSource(h, nested, outer);
                throw failure;
            });
            String command = register(h, context -> {
                assertSource(h, outer, nested);
                h.assertTrue(execute(h, nested, inner) == 23, "nested command's return code changed");
                assertSource(h, outer, nested);
                expectSameError(h, failure, () -> execute(h, nested, failing));
                assertSource(h, outer, nested);
                return 17;
            });
            h.assertTrue(execute(h, outer, command) == 17, "nested failure prevented outer execution");
            assertCleared(h, outer, nested);
        } finally {
            clear(outer, nested);
        }
        h.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void parseAndDispatchFailuresLeaveNoAuthorization(GameTestHelper h) {
        ServerPlayer first = player(h);
        ServerPlayer next = player(h);
        try {
            AssertionError parseFailure = new AssertionError("unhandled argument parsing probe");
            String parsing = name();
            ArgumentType<String> argument = reader -> {
                assertSource(h, first, next);
                throw parseFailure;
            };
            h.getLevel().getServer().getCommands().getDispatcher().register(Commands.literal(parsing)
                    .then(Commands.argument("value", argument).executes(context -> 1)));
            expectSameError(h, parseFailure, () -> execute(h, first, parsing + " value"));
            assertCleared(h, first, next);

            // Brigadier converts a parser's RuntimeException into a command syntax failure.
            boolean[] handledParseSourceValid = {false};
            String handledParsing = name();
            ArgumentType<String> handledArgument = reader -> {
                assertSource(h, first, next);
                handledParseSourceValid[0] = true;
                throw new IllegalStateException("handled argument parsing probe");
            };
            h.getLevel().getServer().getCommands().getDispatcher().register(Commands.literal(handledParsing)
                    .then(Commands.argument("value", handledArgument).executes(context -> 1)));
            h.assertTrue(execute(h, first, handledParsing + " value") == 0,
                    "vanilla's handled-parse-failure result changed");
            h.assertTrue(handledParseSourceValid[0], "argument parser did not observe its initiating source");
            assertCleared(h, first, next);

            boolean[] handledCommandSourceValid = {false};
            String handled = register(h, context -> {
                assertSource(h, first, next);
                handledCommandSourceValid[0] = true;
                throw new IllegalStateException("handled command probe");
            });
            h.assertTrue(execute(h, first, handled) == 0, "vanilla's handled-exception result changed");
            h.assertTrue(handledCommandSourceValid[0], "failed command did not observe its initiating source");
            assertCleared(h, first, next);

            AssertionError failure = new AssertionError("unhandled command probe");
            String failing = register(h, context -> {
                assertSource(h, first, next);
                throw failure;
            });
            expectSameError(h, failure, () -> execute(h, first, failing));
            assertCleared(h, first, next);
            String following = register(h, context -> {
                assertSource(h, next, first);
                return 19;
            });
            h.assertTrue(execute(h, next, following) == 19, "failed command leaked its source to the next command");
            assertCleared(h, first, next);
        } finally {
            clear(first, next);
        }
        h.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void privilegedCommandPolicyAppliesOnlyDuringItsExecution(GameTestHelper h) {
        ServerPlayer owner = player(h);
        ServerPlayer other = player(h);
        try {
            String command = register(h, context -> {
                assertSource(h, owner, other);
                boolean shouldBlock = !AE2LTCommonConfig.overloadArmorPhaseLockTeleportMode().disablesProtection()
                        && !AE2LTCommonConfig.overloadArmorPhaseLockTeleportMode().ignoresPrivilegedCommands();
                h.assertTrue(PhaseFlightMovementGuard.blocksExternalTeleports(other) == shouldBlock,
                        "privileged-command teleport policy changed");
                return 11;
            });
            h.assertTrue(h.getLevel().getServer().getCommands().performPrefixedCommand(
                            source(owner).withPermission(Commands.LEVEL_GAMEMASTERS), command) == 11,
                    "privileged command failed");
            assertCleared(h, owner, other);
            h.assertTrue(PhaseFlightMovementGuard.blocksExternalTeleports(other),
                    "privileged command left external teleport protection disabled");
        } finally {
            clear(owner, other);
        }
        h.succeed();
    }

    @GameTest(template = "pigmee_station_empty")
    public static void forgeCommandCancellationKeepsItsResultAndClearsSource(GameTestHelper h) {
        ServerPlayer owner = player(h);
        ServerPlayer other = player(h);
        String command = register(h, context -> {
            throw new AssertionError("cancelled command reached the dispatcher");
        });
        Consumer<CommandEvent> listener = event -> {
            if (event.getParseResults().getReader().getString().equals(command)) {
                assertSource(h, owner, other);
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            h.assertTrue(execute(h, owner, command) == 1, "Forge cancellation's return code changed");
            assertCleared(h, owner, other);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            clear(owner, other);
        }
        h.succeed();
    }

    private static String register(GameTestHelper h, Command<CommandSourceStack> action) {
        String name = name();
        h.getLevel().getServer().getCommands().getDispatcher().register(Commands.literal(name).executes(action));
        return name;
    }

    private static String name() {
        return "ae2lt_scope_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static int execute(GameTestHelper h, ServerPlayer player, String command) {
        return h.getLevel().getServer().getCommands().performPrefixedCommand(source(player), command);
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

    private static void expectSameError(GameTestHelper h, AssertionError expected, Runnable action) {
        try {
            action.run();
        } catch (AssertionError actual) {
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
