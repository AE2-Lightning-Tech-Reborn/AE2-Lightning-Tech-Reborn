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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ClientTickEvent;

/** Disposable-world probe: native use packets, mouse clicks, and Ctrl payloads. Physical keyboard input is not synthesized. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class FrequencyCardClientProbe {
    private static final BlockPos C = new BlockPos(0, 100, 0), TARGET = new BlockPos(0, 100, 5);
    private static final String NAME = "Card QA " + java.util.UUID.randomUUID().toString().substring(0, 6);
    private static final String OTHER_NAME = "频率列表长名称选中标记布局测试" + NAME.substring(NAME.length() - 6);
    private static int ticks, phase, airClickCase;
    private static boolean done;
    private static volatile Throwable failure;
    private static volatile int frequency, otherFrequency;
    @SubscribeEvent public static void tick(ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (!Boolean.getBoolean("ae2lt.frequencyCardClientProbe") || done) return;
        var mc = Minecraft.getInstance(); mc.options.pauseOnLostFocus = false;
        mc.options.keyAttack.setDown(false);
        if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
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
                        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.getInventory().clearContent();
                        p.teleportTo(level, .5, 99, 3, java.util.Set.of(), 0, 0);
                        level.setBlockAndUpdate(C, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get().defaultBlockState());
                        level.setBlockAndUpdate(C.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                        frequency = WirelessFrequencyManager.get().createFrequency(p, NAME, 0xffffff, FrequencySecurityLevel.PRIVATE, "").getId();
                        otherFrequency = WirelessFrequencyManager.get().createFrequency(p, OTHER_NAME, 0x66ffcc, FrequencySecurityLevel.PRIVATE, "").getId();
                        ((AdvancedWirelessOverloadedControllerBlockEntity) level.getBlockEntity(C)).setFrequency(frequency);
                        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.OVERLOADED_FREQUENCY_CARD.get()));
                    });
                    if (Boolean.getBoolean("ae2lt.cardAirOnly")) phase = 28;
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
                case 4 -> { assertBoundRow(null); clickContaining(NAME); }
                case 5 -> {
                    require(menu().getCurrentFrequencyId() == frequency, "selected frequency did not sync");
                    assertBoundRow(NAME);
                    mc.screen.setFocused(button(OTHER_NAME, true));
                }
                case 6 -> {
                    assertBoundRow(NAME); capture("held-card-frequency-focused-other.png"); clickContaining(OTHER_NAME);
                }
                case 7 -> {
                    require(menu().getCurrentFrequencyId() == otherFrequency, "second frequency did not sync");
                    assertBoundRow(OTHER_NAME); capture("held-card-frequency-long-bound.png");
                    clickContaining(NAME);
                }
                case 8 -> {
                    require(menu().getCurrentFrequencyId() == frequency, "original frequency did not restore");
                    assertBoundRow(NAME); search(NAME.substring(0, NAME.length() - 1));
                }
                case 9 -> { assertBoundRow(NAME); search(""); }
                case 10 -> {
                    assertBoundRow(NAME); capture("held-card-frequency.png"); mc.screen.onClose();
                    server(() -> {
                        var p = player(); require(OverloadedFrequencyCardItem.getData(p.getMainHandItem()).frequencyId() == frequency, "frequency did not persist");
                        var card = p.getMainHandItem(); p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); p.setItemInHand(InteractionHand.OFF_HAND, card);
                    });
                }
                case 11 -> mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
                case 12 -> {
                    require(menu().getCurrentFrequencyId() == frequency && menu().isAutoConnect() && !menu().hasParentMenu(), "offhand reopen lost data");
                    capture("held-card-offhand.png"); clickKey(FrequencyPage.TAB_SELECTION.navigationTranslationKey());
                }
                case 13 -> { assertBoundRow(NAME); clickKey("ae2lt.gui.button.disconnect"); }
                case 14 -> {
                    require(menu().getCurrentFrequencyId() <= 0 && menu().isAutoConnect(), "disconnect did not preserve auto");
                    assertBoundRow(null);
                    clickContaining(NAME);
                }
                case 15 -> {
                    require(menu().getCurrentFrequencyId() == frequency, "offhand reconnect"); assertBoundRow(NAME); mc.screen.onClose();
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
                case 16 -> { /* Wait for the held card and fixtures to synchronize. */ }
                case 17 -> { useTarget(); }
                case 18 -> {
                    server(() -> {
                        var p = player(); require(WirelessEnergyEndpoints.get(p.server).inspect(frequency, p.getUUID()).size() == 2, "held-card Ctrl failed");
                        var terminal = new ItemStack(AEItems.WIRELESS_TERMINAL.asItem());
                        ((IUpgradeableItem) terminal.getItem()).getUpgrades(terminal).setItemDirect(0, p.getMainHandItem().copy());
                        require(TerminalCardAccess.hasCard(terminal), "terminal fixture missing card");
                        p.setItemInHand(InteractionHand.MAIN_HAND, terminal);
                    });
                }
                case 19 -> { /* Wait for the installed terminal card to synchronize. */ }
                case 20 -> { useTarget(); }
                case 21 -> {
                    server(() -> {
                        var p = player(); require(WirelessEnergyEndpoints.get(p.server).inspect(frequency, p.getUUID()).isEmpty(), "terminal Ctrl duplicate toggle or failed disconnect");
                        appeng.menu.MenuOpener.open(FrequencyMenu.TYPE, p, appeng.menu.locator.MenuLocators.forInventorySlot(p.getInventory().selected));
                    });
                }
                case 22 -> {
                    require(menu().isCardMode() && menu().hasParentMenu() && menu().getCurrentFrequencyId() == frequency, "terminal card menu regression");
                    clickKey("ae2lt.gui.button.auto_connect_on");
                }
                case 23 -> {
                    require(!menu().isAutoConnect(), "terminal switch did not synchronize");
                    server(() -> require(!TerminalCardAccess.readCardData(player().getMainHandItem()).autoConnect(), "terminal switch not persisted"));
                }
                case 24 -> { capture("terminal-card-regression.png"); clickKey(FrequencyPage.TAB_SELECTION.navigationTranslationKey()); }
                case 25 -> { assertBoundRow(NAME); capture("terminal-card-frequency.png"); clickKey(FrequencyPage.TAB_HOME.navigationTranslationKey()); }
                case 26 -> {
                    require(!menu().isAutoConnect() && !menu().isAutoConnectEnergy(), "terminal card initial independent state");
                    clickKey("ae2lt.gui.button.auto_connect_energy_off");
                }
                case 27 -> {
                    require(!menu().isAutoConnect() && menu().isAutoConnectEnergy(), "terminal FE setting changed channels");
                    server(() -> require(TerminalCardAccess.readCardData(player().getMainHandItem()).autoConnectEnergy()
                            && !TerminalCardAccess.readCardData(player().getMainHandItem()).autoConnect(), "terminal FE setting not persisted"));
                    capture("terminal-card-fe-only.png");
                    clickKey("ae2lt.gui.button.auto_connect_off");
                }
                case 28 -> {
                    require(menu().isAutoConnect() && menu().isAutoConnectEnergy(), "terminal channel setting changed FE");
                    mc.screen.onClose();
                }
                case 29 -> {
                    mc.options.keyShift.setDown(false);
                    server(() -> {
                        var p = player();
                        p.getInventory().clearContent();
                        p.serverLevel().setBlockAndUpdate(TARGET, (airClickCase < 2 ? Blocks.STONE : Blocks.CRAFTING_TABLE).defaultBlockState());
                        var card = new ItemStack(ModItems.OVERLOADED_FREQUENCY_CARD.get());
                        OverloadedFrequencyCardItem.bindFrequency(card, frequency, p.level().dimension(), C, p.getUUID());
                        OverloadedFrequencyCardItem.setData(card, OverloadedFrequencyCardItem.getData(card).withAutoConnectEnergy(true));
                        p.setItemInHand(airHand(), card);
                        p.inventoryMenu.broadcastChanges();
                    });
                }
                case 30 -> useNonBlockEntity();
                case 31 -> {
                    require(menu().isCardMode() && menu().getCurrentFrequencyId() == frequency
                            && menu().isAutoConnectEnergy(), "non-BE native click failed: " + airClickCase);
                    capture("non-be-card-" + airClickCase + ".png");
                    mc.screen.onClose();
                    mc.options.keyShift.setDown(true);
                }
                case 32 -> {
                    require(mc.player.isShiftKeyDown(), "native sneak key did not take effect");
                    useNonBlockEntity();
                }
                case 33 -> {
                    require(mc.screen == null && !OverloadedFrequencyCardItem.getData(mc.player.getItemInHand(airHand())).isBound(),
                            "shift non-BE click opened a screen or retained binding: " + airClickCase);
                    server(() -> {
                        var data = OverloadedFrequencyCardItem.getData(player().getItemInHand(airHand()));
                        require(!data.isBound() && data.autoConnectEnergy() && !data.autoConnect(), "native clear lost independent preferences");
                    });
                    mc.options.keyShift.setDown(false);
                }
                case 34 -> {
                    if (++airClickCase < 4) phase = 28;
                    else server(() -> com.moakiee.ae2lt.menu.NetworkManagementMenu.open(player(),
                            (AdvancedWirelessOverloadedControllerBlockEntity) player().serverLevel().getBlockEntity(C), false));
                }
                case 35 -> {
                    require(mc.screen instanceof com.moakiee.ae2lt.client.gui.NetworkManagementScreen, "management screen missing");
                    capture("management-selected-devices.png");
                    clickKey("ae2lt.management.view.energy");
                }
                case 36 -> {
                    capture("management-selected-energy.png");
                    clickKey("ae2lt.management.view.connections");
                }
                case 37 -> {
                    capture("management-selected-connections.png");
                    finish(null); return;
                }
            }
            phase++;
        } catch (Throwable problem) { finish(problem); }
    }
    private static FrequencyMenu menu() {
        var mc = Minecraft.getInstance(); require(mc.screen instanceof FrequencyScreen && mc.player.containerMenu instanceof FrequencyMenu, "frequency screen missing");
        return (FrequencyMenu) mc.player.containerMenu;
    }
    private static void useTarget() {
        com.moakiee.ae2lt.network.PacketSender.sendToServer(new com.moakiee.ae2lt.network.FrequencyCardUsePacket(
                InteractionHand.MAIN_HAND, TARGET, Direction.NORTH, TARGET.getX() + .5, TARGET.getY() + .5, TARGET.getZ(), false, true));
    }
    private static InteractionHand airHand() { return (airClickCase & 1) == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND; }
    private static void useNonBlockEntity() {
        var mc = Minecraft.getInstance();
        require(mc.level.getBlockEntity(TARGET) == null, "non-BE fixture has a block entity");
        var result = mc.gameMode.useItemOn(mc.player, airHand(), new net.minecraft.world.phys.BlockHitResult(
                new net.minecraft.world.phys.Vec3(TARGET.getX() + .5, TARGET.getY() + .5, TARGET.getZ()), Direction.NORTH, TARGET, false));
        require(result.consumesAction(), "native non-BE click was not consumed");
    }
    private static net.minecraft.server.level.ServerPlayer player() { var mc = Minecraft.getInstance(); return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()); }
    private static void clickKey(String key) { click(Component.translatable(key).getString(), false); }
    private static void clickContaining(String name) { click(name, true); }
    private static Button button(String text, boolean contains) {
        var screen = Minecraft.getInstance().screen;
        return screen.children().stream().filter(e -> e instanceof Button b && b.visible && b.active
                && (contains ? b.getMessage().getString().contains(text) : b.getMessage().getString().equals(text)))
                .map(e -> (Button)e).findFirst().orElseThrow(() -> new AssertionError("missing button " + text));
    }
    private static void click(String text, boolean contains) {
        var screen = Minecraft.getInstance().screen; var button = button(text, contains);
        double x = button.getX() + button.getWidth() / 2., y = button.getY() + button.getHeight() / 2.;
        require(screen.mouseClicked(x, y, 0), "mouse click failed"); screen.mouseReleased(x, y, 0);
    }
    private static void search(String query) throws Exception {
        var screen = Minecraft.getInstance().screen; var field = FrequencyScreen.class.getDeclaredField("selectionSearchField");
        field.setAccessible(true); ((appeng.client.gui.widgets.AETextField) field.get(screen)).setValue(query);
    }
    private static void assertBoundRow(String name) throws Exception {
        int marked = 0;
        for (var child : Minecraft.getInstance().screen.children()) {
            if (!child.getClass().getSimpleName().equals("RowSpriteButton")) continue;
            var bound = child.getClass().getDeclaredField("bound"); bound.setAccessible(true);
            if (bound.getBoolean(child)) {
                marked++;
                var label = ((Button) child).getMessage().getString();
                require(name != null && (label.equals(name) || label.startsWith(name + " [")), "wrong bound marker: " + label);
            }
        }
        require(marked == (name == null ? 0 : 1), "missing or duplicate bound marker: " + marked);
    }
    private static void server(Runnable action) { Minecraft.getInstance().getSingleplayerServer().execute(() -> { try { action.run(); } catch (Throwable t) { failure = t; } }); }
    private static void capture(String name) throws Exception { var mc = Minecraft.getInstance(); try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(mc.gameDirectory.toPath().resolve(name)); } }
    private static void require(boolean condition, String text) { if (!condition) throw new AssertionError(text); }
    private static void finish(Throwable problem) {
        done = true;
        String result = problem == null ? "FREQUENCY_CARD_CLIENT_PASS: " + (Boolean.getBoolean("ae2lt.cardAirOnly")
                ? "focused native non-BE interactions"
                : "main/offhand air GUI, mouse auto switch, current frequency badge persists across other-row focus/search/reopen; badge moves on selection and clears on disconnect; long-name layout; persisted card data, Ctrl payload held-card and terminal group toggle, terminal GUI regression")
                + "; main/offhand native stone and crafting-table clicks open card GUI, Shift clears with independent preferences preserved; all three management mode screenshots"
                : "FREQUENCY_CARD_CLIENT_FAIL phase=" + phase + ": " + problem;
        System.out.println(result); if (problem != null) problem.printStackTrace();
        Minecraft.getInstance().options.keyShift.setDown(false);
        if (problem != null) try { capture("frequency-card-failure.png"); } catch (Exception ignored) {}
        try { java.nio.file.Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("frequency-card-result.txt"), result); } catch (Exception ignored) {}
        Minecraft.getInstance().stop();
    }
}
