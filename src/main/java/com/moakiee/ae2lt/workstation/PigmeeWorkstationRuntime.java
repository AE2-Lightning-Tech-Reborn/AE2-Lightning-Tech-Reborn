package com.moakiee.ae2lt.workstation;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Explicitly opt-in P0 diagnostics, not the final survival activation or offline policy. */
@EventBusSubscriber(modid = "ae2lt")
public final class PigmeeWorkstationRuntime {
    private static final Map<MinecraftServer, PigmeeActorManager> MANAGERS = new IdentityHashMap<>();

    private PigmeeWorkstationRuntime() { }

    public static boolean enabled() { return Boolean.getBoolean("ae2lt.pigmeeP0"); }

    public static PigmeeActorManager manager(MinecraftServer server) {
        if (!enabled() || !server.isSameThread()) throw new IllegalStateException("P0 disabled or not server thread");
        return MANAGERS.computeIfAbsent(server, PigmeeActorManager::new);
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (enabled()) manager(event.getServer()).restore();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (enabled()) manager(event.getServer()).afterTick();
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        var manager = MANAGERS.get(event.getServer());
        if (manager != null) manager.shutdown();
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        var manager = MANAGERS.remove(event.getServer());
        if (manager != null) manager.finishedStopping();
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (enabled() && event.getEntity() instanceof ServerPlayer viewer && !viewer.isFakePlayer()) {
            manager(viewer.server).informViewer(viewer);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var manager = MANAGERS.get(player.server);
            if (manager != null) manager.controls().releaseOwner(player);
        }
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        if (!enabled()) return;
        var root = Commands.literal("pigmee_p0").requires(source -> source.hasPermission(2));
        root.then(Commands.literal("create").executes(context -> {
            var source = context.getSource();
            var owner = source.getPlayerOrException();
            if (owner.isFakePlayer()) return 0;
            var actor = manager(source.getServer()).create(owner.getUUID(), owner.serverLevel(),
                    owner.position().add(0, 0, 2), owner.getYRot());
            source.sendSuccess(() -> Component.literal("P0 survival body: " + actor.getUUID()), false);
            return 1;
        }));
        root.then(Commands.literal("activate_fumo").then(Commands.argument("pos", BlockPosArgument.blockPos())
                .executes(context -> {
                    try {
                        var actor = PigmeeFumoActivation.activate(context.getSource().getPlayerOrException(),
                                BlockPosArgument.getLoadedBlockPos(context, "pos"));
                        context.getSource().sendSuccess(() -> Component.literal(
                                "Experimental Fumo conversion (controller cost not implemented): " + actor.getUUID()), false);
                        return 1;
                    } catch (IllegalArgumentException | IllegalStateException failed) {
                        context.getSource().sendFailure(Component.literal(failed.getMessage()));
                        return 0;
                    }
                })));
        root.then(Commands.literal("list").executes(context -> {
            var manager = manager(context.getSource().getServer());
            for (UUID id : manager.ids()) {
                context.getSource().sendSuccess(() -> Component.literal(manager.status(id)), false);
            }
            return manager.ids().size();
        }));
        for (String operation : new String[] {"start", "stop"}) {
            root.then(Commands.literal(operation).then(Commands.argument("id", UuidArgument.uuid()).executes(context -> {
                try {
                    var manager = manager(context.getSource().getServer());
                    UUID id = UuidArgument.getUuid(context, "id");
                    if (operation.equals("start")) manager.activate(id);
                    else manager.deactivate(id);
                    context.getSource().sendSuccess(() -> Component.literal(manager.status(id)), false);
                    return 1;
                } catch (IllegalArgumentException | IllegalStateException failed) {
                    context.getSource().sendFailure(Component.literal(failed.getMessage()));
                    return 0;
                }
            })));
        }
        root.then(Commands.literal("walk").then(Commands.argument("id", UuidArgument.uuid())
                .then(Commands.argument("ticks", IntegerArgumentType.integer(0, 200)).executes(context -> {
                    var actor = manager(context.getSource().getServer()).active(UuidArgument.getUuid(context, "id"));
                    if (actor == null) return 0;
                    actor.walkFor(IntegerArgumentType.getInteger(context, "ticks"));
                    return 1;
                }))));
        event.getDispatcher().register(root);
    }
}
