package com.moakiee.ae2lt.logic.energy;

import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.client.gui.FrequencyScreen;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.network.WirelessEnergyMenuSyncPacket;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class WirelessEnergyMenuClientProbe {
    private static int ticks, phase;
    private static boolean done;
    private static volatile Throwable failure;
    private static volatile int frequency;
    private static final BlockPos CONTROLLER = new BlockPos(0, 100, 0);
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.wirelessEnergyMenuProbe") || done) return;
        var mc = Minecraft.getInstance(); mc.options.pauseOnLostFocus = false;
        if (++ticks > 1800) { finish(new AssertionError("timeout phase " + phase)); return; }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || ticks % 30 != 0) return;
        try {
            if (failure != null) throw new AssertionError(failure);
            switch (phase) {
                case 0 -> {
                    mc.getWindow().setTitle("Wireless FE menu QA - disposable world");
                    mc.getWindow().setWindowed(1100, 800); mc.options.guiScale().set(2); mc.resizeDisplay();
                    mc.options.gamma().set(1.0); mc.options.renderDistance().set(4);
                    server(() -> {
                        var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()); var level = p.serverLevel();
                        for (int x = -6; x <= 6; x++) for (int z = -5; z <= 8; z++) {
                            level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState());
                            for (int y = 100; y <= 104; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                        }
                        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE); p.getInventory().clearContent();
                        p.teleportTo(level, .5, 100, -2.5, java.util.Set.of(), 0, 0);
                        level.setBlockAndUpdate(CONTROLLER, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get().defaultBlockState());
                        level.setBlockAndUpdate(CONTROLLER.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                        var controller = (AdvancedWirelessOverloadedControllerBlockEntity) level.getBlockEntity(CONTROLLER);
                        frequency = WirelessFrequencyManager.get(p.server).createFrequency(p, "Energy UI QA", 0xffffff, FrequencySecurityLevel.PRIVATE, "").getId();
                        controller.setFrequency(frequency);
                        for (int i = 0; i < 4; i++) level.setBlockAndUpdate(CONTROLLER.offset(i - 1, 0, 4), Blocks.BARREL.defaultBlockState());
                    });
                }
                case 1 -> server(() -> {
                    var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()); var level = p.serverLevel();
                    for (int i = 0; i < 4; i++) {
                        var target = CONTROLLER.offset(i - 1, 0, 4); p.moveTo(target.getX() + .5, 100, target.getZ() - 2);
                        require(WirelessEnergyEndpoints.get(p.server).setBound(p, frequency, level, target, Direction.NORTH, true).equals("connected"), "fixture bind");
                    }
                    p.teleportTo(level, .5, 100, -2.5, java.util.Set.of(), 0, 0);
                });
                case 2 -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(CONTROLLER), Direction.NORTH, CONTROLLER, false));
                case 3 -> { require(mc.screen instanceof FrequencyScreen, "native controller opens menu"); click(Component.translatable("ae2lt.gui.energy.title").getString()); }
                case 4 -> {
                    require(snapshot().total() == 4 && snapshot().entries().size() == 3 && snapshot().page() == 0, "bounded first page arrived");
                    capture("wireless-fe-page.png"); click(">");
                }
                case 5 -> { require(snapshot().page() == 1 && snapshot().entries().size() == 1, "second page"); click("×"); }
                case 6 -> {
                    require(snapshot().total() == 3 && snapshot().page() == 0, "disconnect returns clamped current page");
                    capture("wireless-fe-after-disconnect.png");
                    server(() -> { var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        require(WirelessEnergyEndpoints.get(p.server).inspect(frequency, p.getUUID()).size() == 3, "server removed exactly one target"); });
                }
                case 7 -> { finish(null); return; }
            }
            phase++;
        } catch (Throwable problem) { finish(problem); }
    }
    private static WirelessEnergyMenuSyncPacket snapshot() throws Exception {
        var field = FrequencyScreen.class.getDeclaredField("energySnapshot"); field.setAccessible(true);
        var value = (WirelessEnergyMenuSyncPacket) field.get(Minecraft.getInstance().screen);
        require(value != null, "energy snapshot arrived"); return value;
    }
    private static void click(String label) {
        var screen = Minecraft.getInstance().screen;
        var button = screen.children().stream().filter(e -> e instanceof Button b && b.active && b.getMessage().getString().equals(label)).map(e -> (Button)e).findFirst().orElseThrow();
        button.onPress();
    }
    private static void capture(String name) throws Exception { var mc = Minecraft.getInstance(); try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(mc.gameDirectory.toPath().resolve(name)); } }
    private static void server(Runnable action) { Minecraft.getInstance().getSingleplayerServer().execute(() -> { try { action.run(); } catch (Throwable t) { failure = t; } }); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void finish(Throwable problem) {
        done = true; String result = problem == null ? "WIRELESS_ENERGY_MENU_CLIENT_PASS: native controller open, bounded live page, pagination, single generation disconnect and clamped page refresh"
                : "WIRELESS_ENERGY_MENU_CLIENT_FAIL phase=" + phase + ": " + problem;
        System.out.println(result); if (problem != null) problem.printStackTrace();
        try { java.nio.file.Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("wireless-energy-menu-result.txt"), result); } catch (Exception ignored) {}
        Minecraft.getInstance().stop();
    }
}
