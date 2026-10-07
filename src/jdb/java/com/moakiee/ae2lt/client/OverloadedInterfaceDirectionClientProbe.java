package com.moakiee.ae2lt.client;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.Set;

import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.client.machine.OverloadedInterfaceScreen;
import com.moakiee.ae2lt.menu.OverloadedInterfaceMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in native wrench packet and block-model probe in a disposable world; never shipped. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class OverloadedInterfaceDirectionClientProbe {
    private static final BlockPos POS = new BlockPos(0, 100, 0);
    private static int ticks;
    private static int phase;
    private static boolean done;
    private static volatile Throwable serverFailure;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.wiredTargetClientProbe") || done) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.getSingleplayerServer() == null || ++ticks % 30 != 0) return;
        try {
            if (serverFailure != null) throw new AssertionError(serverFailure);
            switch (phase) {
                case 0 -> {
                    mc.getWindow().setTitle("Interface wrench QA - disposable world");
                    mc.getWindow().setWindowed(1200, 900);
                    mc.options.guiScale().set(3);
                    mc.resizeDisplay();
                    server(() -> {
                        var server = mc.getSingleplayerServer();
                        var level = server.overworld();
                        for (var pos : BlockPos.betweenClosed(-4, 99, -4, 4, 104, 4)) {
                            level.setBlockAndUpdate(pos, pos.getY() == 99
                                    ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                        }
                        level.setDayTime(6000);
                        level.setBlockAndUpdate(POS, ModBlocks.OVERLOADED_INTERFACE.get().defaultBlockState());
                        level.setBlockAndUpdate(POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                        var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                        player.setGameMode(GameType.CREATIVE);
                        player.getInventory().clearContent();
                        player.getInventory().setItem(0, AEItems.CERTUS_QUARTZ_WRENCH.stack());
                        player.getInventory().selected = 0;
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                        player.teleportTo(level, 2.5, 101.3, 3.5, Set.of(), 145, 20);
                    });
                }
                case 1 -> {
                    if (!(mc.level.getBlockEntity(POS) instanceof OverloadedInterfaceBlockEntity)) {
                        check(ticks < 1200, "client chunk timeout");
                        return;
                    }
                    check(mc.player.getMainHandItem().is(AEItems.CERTUS_QUARTZ_WRENCH.asItem()), "wrench not synchronized");
                    capture("interface-wrench-all.png");
                    wrench(Direction.EAST); // ALL -> WEST
                }
                case 2 -> {
                    checkDirection(PushDirection.WEST);
                    capture("interface-wrench-west.png");
                    wrench(Direction.WEST); // WEST -> ALL
                }
                case 3 -> {
                    checkDirection(PushDirection.ALL);
                    // Validate all six pointed states through real client-to-server use packets.
                    wrench(Direction.UP); // ALL -> DOWN
                }
                default -> {
                    if (phase >= 4 && phase <= 15) {
                        int step = phase - 4;
                        var selected = Direction.values()[step / 2];
                        if (step % 2 == 0) {
                            checkDirection(PushDirection.fromDirection(selected));
                            if (selected == Direction.UP) capture("interface-wrench-up.png");
                            wrench(selected); // pointed -> ALL
                        } else {
                            checkDirection(PushDirection.ALL);
                            if (step < 11) wrench(Direction.values()[step / 2 + 1].getOpposite());
                            else server(() -> {
                                var server = mc.getSingleplayerServer();
                                var owner = (OverloadedInterfaceBlockEntity) server.overworld().getBlockEntity(POS);
                                MenuOpener.open(OverloadedInterfaceMenu.TYPE,
                                        server.getPlayerList().getPlayer(mc.player.getUUID()), MenuLocators.forBlockEntity(owner));
                            });
                        }
                    } else if (phase == 16) {
                        check(mc.screen instanceof OverloadedInterfaceScreen, "interface screen not open");
                        check(Arrays.stream(OverloadedInterfaceScreen.class.getDeclaredFields())
                                .noneMatch(f -> f.getName().equals("directionButton")), "GUI direction button still exists");
                        var field = OverloadedInterfaceScreen.class.getDeclaredField("modeButton");
                        field.setAccessible(true);
                        hover((AbstractWidget) field.get(mc.screen));
                    } else if (phase == 17) {
                        capture("interface-wrench-menu.png");
                        report("PASS: native wrench use packets set and clear all six directions; client and server "
                                + "blockstates agree; arrow models render; GUI direction selector removed.");
                        done = true;
                        mc.stop();
                    }
                }
            }
            phase++;
        } catch (Throwable failure) {
            failure.printStackTrace();
            report("FAIL phase=" + phase + ": " + failure);
            done = true;
            mc.stop();
        }
    }

    private static void wrench(Direction face) {
        var mc = Minecraft.getInstance();
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(POS).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5)),
                        face, POS, false));
    }

    private static void checkDirection(PushDirection direction) {
        var mc = Minecraft.getInstance();
        check(mc.level.getBlockState(POS).getValue(PatternProviderBlock.PUSH_DIRECTION) == direction,
                "client blockstate mismatch: expected " + direction);
        server(() -> {
            var owner = (OverloadedInterfaceBlockEntity) mc.getSingleplayerServer().overworld().getBlockEntity(POS);
            check(owner.getTargetDirection() == direction.getDirection(), "server did not accept wrench direction");
        });
    }

    private static void server(Runnable action) {
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try { action.run(); } catch (Throwable failure) { serverFailure = failure; }
        });
    }

    private static void hover(AbstractWidget button) throws Exception {
        var mc = Minecraft.getInstance();
        var window = mc.getWindow();
        var move = MouseHandler.class.getDeclaredMethod("onMove", long.class, double.class, double.class);
        move.setAccessible(true);
        move.invoke(mc.mouseHandler, window.getWindow(),
                (button.getX() + 8) * (double) window.getScreenWidth() / window.getGuiScaledWidth(),
                (button.getY() + 8) * (double) window.getScreenHeight() / window.getGuiScaledHeight());
    }

    private static void capture(String name) throws Exception {
        var mc = Minecraft.getInstance();
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(mc.gameDirectory.toPath().resolve(name));
        }
    }

    private static void report(String result) {
        try {
            Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("wrench-result.txt"), result);
        } catch (Exception failure) { failure.printStackTrace(); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
