package com.moakiee.ae2lt.logic.energy;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.api.upgrades.IUpgradeableItem;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.client.gui.FrequencyScreen;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import com.moakiee.ae2lt.item.TerminalCardAccess;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import com.moakiee.ae2lt.menu.FrequencyPage;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Disposable-world probe: native use packets, mouse clicks, and Ctrl payloads. Physical keyboard input is not synthesized. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class FrequencyCardClientProbe {
    private static final BlockPos C = new BlockPos(0, 100, 0), TARGET = new BlockPos(0, 100, 5);
    private static final String NAME = "Card QA " + java.util.UUID.randomUUID().toString().substring(0, 6);
    private static int ticks, phase;
    private static boolean done;
    private static volatile Throwable failure;
    private static volatile int frequency;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.frequencyCardClientProbe") || done) return;
        var mc = Minecraft.getInstance(); mc.options.pauseOnLostFocus = false;
        if (++ticks > 1600) { finish(new AssertionError("timeout")); return; }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || ticks % 25 != 0) return;
        try {
            if (failure != null) throw new AssertionError(failure);
            switch (phase) {
                case 0 -> {
                    mc.getWindow().setTitle("Frequency card interaction QA - disposable world");
                    mc.getWindow().setWindowed(1100, 800); mc.options.guiScale().set(2); mc.resizeDisplay();
                    mc.options.renderDistance().set(3); mc.options.gamma().set(1.0);
                    server(() -> {
                        var p = player(); var level = p.serverLevel();
                        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 8; z++) {
                            level.setBlockAndUpdate(new BlockPos(x, 98, z), Blocks.STONE.defaultBlockState());
                            for (int y = 99; y <= 104; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                        }
                        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE); p.getInventory().clearContent();
                        p.teleportTo(level, .5, 99, 3, java.util.Set.of(), 0, 0);
                        level.setBlockAndUpdate(C, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get().defaultBlockState());
                        level.setBlockAndUpdate(C.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                        frequency = WirelessFrequencyManager.get().createFrequency(p, NAME, 0xffffff, FrequencySecurityLevel.PRIVATE, "").getId();
                        ((AdvancedWirelessOverloadedControllerBlockEntity) level.getBlockEntity(C)).setFrequency(frequency);
                        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.OVERLOADED_FREQUENCY_CARD.get()));
                    });
                }
                case 1 -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                case 2 -> {
                    require(menu().isCardMode() && !menu().hasParentMenu() && !menu().isAutoConnect(), "main-hand air menu");
                    capture("held-card-home.png"); clickKey("ae2lt.gui.button.auto_connect_off");
                }
                case 3 -> {
                    require(menu().isAutoConnect(), "auto switch did not synchronize");
                    server(() -> require(OverloadedFrequencyCardItem.getData(player().getMainHandItem()).autoConnect(), "auto switch not persisted"));
                    clickKey(FrequencyPage.TAB_SELECTION.navigationTranslationKey());
                }
                case 4 -> clickContaining(NAME);
                case 5 -> {
                    require(menu().getCurrentFrequencyId() == frequency, "selected frequency did not sync");
                    capture("held-card-frequency.png"); mc.screen.onClose();
                    server(() -> {
                        var p = player(); require(OverloadedFrequencyCardItem.getData(p.getMainHandItem()).frequencyId() == frequency, "frequency did not persist");
                        var card = p.getMainHandItem(); p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); p.setItemInHand(InteractionHand.OFF_HAND, card);
                    });
                }
                case 6 -> mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
                case 7 -> {
                    require(menu().getCurrentFrequencyId() == frequency && menu().isAutoConnect() && !menu().hasParentMenu(), "offhand reopen lost data");
                    capture("held-card-offhand.png"); clickKey(FrequencyPage.TAB_SELECTION.navigationTranslationKey());
                }
                case 8 -> clickKey("ae2lt.gui.button.disconnect");
                case 9 -> {
                    require(menu().getCurrentFrequencyId() <= 0 && menu().isAutoConnect(), "disconnect did not preserve auto");
                    clickContaining(NAME);
                }
                case 10 -> {
                    require(menu().getCurrentFrequencyId() == frequency, "offhand reconnect"); mc.screen.onClose();
                    server(() -> {
                        var p = player(); var level = p.serverLevel(); var card = p.getOffhandItem();
                        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); p.setItemInHand(InteractionHand.MAIN_HAND, card);
                        level.setBlockAndUpdate(TARGET, WirelessEnergyTestMachine.BLOCK.defaultBlockState());
                        level.setBlockAndUpdate(TARGET.east(), WirelessEnergyTestMachine.BLOCK.defaultBlockState());
                        ((WirelessEnergyTestMachine) level.getBlockEntity(TARGET)).inputFace = Direction.SOUTH;
                        ((WirelessEnergyTestMachine) level.getBlockEntity(TARGET.east())).inputFace = Direction.UP;
                        p.teleportTo(level, .5, 99, 3, java.util.Set.of(), 0, 0);
                    });
                }
                case 11 -> { /* Wait for the held card and fixtures to synchronize. */ }
                case 12 -> { useTarget(); }
                case 13 -> {
                    server(() -> {
                        var p = player(); require(WirelessEnergyEndpoints.get(p.server).inspect(frequency, p.getUUID()).size() == 2, "held-card Ctrl failed");
                        var terminal = new ItemStack(AEItems.WIRELESS_TERMINAL.asItem());
                        ((IUpgradeableItem) terminal.getItem()).getUpgrades(terminal).setItemDirect(0, p.getMainHandItem().copy());
                        require(TerminalCardAccess.hasCard(terminal), "terminal fixture missing card");
                        p.setItemInHand(InteractionHand.MAIN_HAND, terminal);
                    });
                }
                case 14 -> { /* Wait for the installed terminal card to synchronize. */ }
                case 15 -> { useTarget(); }
                case 16 -> {
                    server(() -> {
                        var p = player(); require(WirelessEnergyEndpoints.get(p.server).inspect(frequency, p.getUUID()).isEmpty(), "terminal Ctrl duplicate toggle or failed disconnect");
                        appeng.menu.MenuOpener.open(FrequencyMenu.TYPE, p, appeng.menu.locator.MenuLocators.forInventorySlot(p.getInventory().selected));
                    });
                }
                case 17 -> {
                    require(menu().isCardMode() && menu().hasParentMenu() && menu().getCurrentFrequencyId() == frequency, "terminal card menu regression");
                    clickKey("ae2lt.gui.button.auto_connect_on");
                }
                case 18 -> {
                    require(!menu().isAutoConnect(), "terminal switch did not synchronize");
                    server(() -> require(!TerminalCardAccess.readCardData(player().getMainHandItem()).autoConnect(), "terminal switch not persisted"));
                }
                case 19 -> { capture("terminal-card-regression.png"); finish(null); return; }
            }
            phase++;
        } catch (Throwable problem) { finish(problem); }
    }
    private static FrequencyMenu menu() {
        var mc = Minecraft.getInstance(); require(mc.screen instanceof FrequencyScreen && mc.player.containerMenu instanceof FrequencyMenu, "frequency screen missing");
        return (FrequencyMenu) mc.player.containerMenu;
    }
    private static void useTarget() {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.moakiee.ae2lt.network.FrequencyCardUsePacket(
                InteractionHand.MAIN_HAND, TARGET, Direction.NORTH, TARGET.getX() + .5, TARGET.getY() + .5, TARGET.getZ(), false, true));
    }
    private static net.minecraft.server.level.ServerPlayer player() { var mc = Minecraft.getInstance(); return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()); }
    private static void clickKey(String key) { click(Component.translatable(key).getString(), false); }
    private static void clickContaining(String name) { click(name, true); }
    private static void click(String text, boolean contains) {
        var screen = Minecraft.getInstance().screen;
        var button = screen.children().stream().filter(e -> e instanceof Button b && b.visible && b.active
                && (contains ? b.getMessage().getString().contains(text) : b.getMessage().getString().equals(text)))
                .map(e -> (Button)e).findFirst().orElseThrow(() -> new AssertionError("missing button " + text));
        double x = button.getX() + button.getWidth() / 2., y = button.getY() + button.getHeight() / 2.;
        require(screen.mouseClicked(x, y, 0), "mouse click failed"); screen.mouseReleased(x, y, 0);
    }
    private static void server(Runnable action) { Minecraft.getInstance().getSingleplayerServer().execute(() -> { try { action.run(); } catch (Throwable t) { failure = t; } }); }
    private static void capture(String name) throws Exception { var mc = Minecraft.getInstance(); try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(mc.gameDirectory.toPath().resolve(name)); } }
    private static void require(boolean condition, String text) { if (!condition) throw new AssertionError(text); }
    private static void finish(Throwable problem) {
        done = true;
        String result = problem == null ? "FREQUENCY_CARD_CLIENT_PASS: main/offhand air GUI, mouse auto switch, frequency selection/disconnect/reconnect, persisted card data, Ctrl payload held-card and terminal group toggle, terminal GUI regression"
                : "FREQUENCY_CARD_CLIENT_FAIL phase=" + phase + ": " + problem;
        System.out.println(result); if (problem != null) problem.printStackTrace();
        try { java.nio.file.Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("frequency-card-result.txt"), result); } catch (Exception ignored) {}
        Minecraft.getInstance().stop();
    }
}
