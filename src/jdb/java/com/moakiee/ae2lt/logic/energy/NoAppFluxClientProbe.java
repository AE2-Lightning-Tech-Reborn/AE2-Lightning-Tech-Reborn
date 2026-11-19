package com.moakiee.ae2lt.logic.energy;

import appeng.core.definitions.AEBlocks;
import com.moakiee.ae2lt.blockentity.AdvancedWirelessOverloadedControllerBlockEntity;
import com.moakiee.ae2lt.client.gui.FrequencyScreen;
import com.moakiee.ae2lt.client.gui.NetworkManagementScreen;
import com.moakiee.ae2lt.grid.FrequencySecurityLevel;
import com.moakiee.ae2lt.grid.WirelessFrequencyManager;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import com.moakiee.ae2lt.menu.FrequencyMenu;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Actual client/server menus and mouse navigation in a disposable world without Applied Flux. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class NoAppFluxClientProbe {
    private static final BlockPos C = new BlockPos(0, 100, 0);
    private static int ticks, phase;
    private static volatile int frequency;
    private static volatile Throwable failure;
    private static boolean done;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.noAppFluxClientProbe") || done) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (++ticks > 1800) { finish(new AssertionError("timeout")); return; }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || ticks % 25 != 0) return;
        try {
            if (failure != null) throw new AssertionError(failure);
            switch (phase) {
                case 0 -> {
                    require(ModList.get().isLoaded("appflux") == withAppFlux() && AppFluxBridge.isAvailable() == withAppFlux(), "wrong AppFlux fixture");
                    mc.getWindow().setTitle("No Applied Flux compatibility QA");
                    mc.getWindow().setWindowed(1100, 800); mc.options.guiScale().set(2); mc.resizeDisplay();
                    mc.options.renderDistance().set(3);
                    server(() -> {
                        var p = player(); var level = p.serverLevel();
                        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 6; z++) {
                            level.setBlockAndUpdate(new BlockPos(x, 98, z), Blocks.STONE.defaultBlockState());
                            for (int y = 99; y <= 104; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                        }
                        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.getInventory().clearContent();
                        p.teleportTo(level, .5, 99, 3, java.util.Set.of(), 180, 0);
                        level.setBlockAndUpdate(C, ModBlocks.ADVANCED_WIRELESS_OVERLOADED_CONTROLLER.get().defaultBlockState());
                        level.setBlockAndUpdate(C.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                        level.setBlockAndUpdate(C.west(), AEBlocks.INTERFACE.block().defaultBlockState());
                        var f = WirelessFrequencyManager.get().createFrequency(p, "AppFlux QA " + java.util.UUID.randomUUID().toString().substring(0, 6), 0xffffff, FrequencySecurityLevel.PRIVATE, "");
                        frequency = f.getId();
                        ((AdvancedWirelessOverloadedControllerBlockEntity) level.getBlockEntity(C)).setFrequency(frequency);
                    });
                }
                case 1 -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(new Vec3(.5, 100.5, 1), Direction.SOUTH, C, false));
                case 2 -> { require(!menu().isCardMode(), "controller menu missing"); capture("controller-home.png"); click("ae2lt.management.network"); }
                case 3 -> { management(); capture("controller-devices.png"); click("ae2lt.management.view.energy"); }
                case 4 -> { management(); capture("controller-energy.png"); click("ae2lt.management.view.connections"); }
                case 5 -> { management(); capture("controller-connections.png"); click("ae2lt.gui.tab.home"); }
                case 6 -> {
                    menu(); mc.screen.onClose();
                    server(() -> {
                        var p = player(); var card = new ItemStack(ModItems.OVERLOADED_FREQUENCY_CARD.get());
                        OverloadedFrequencyCardItem.bindFrequency(card, frequency, p.level().dimension(), C, p.getUUID());
                        OverloadedFrequencyCardItem.setData(card, OverloadedFrequencyCardItem.getData(card).withAutoConnectEnergy(true));
                        p.setItemInHand(InteractionHand.MAIN_HAND, card);
                    });
                }
                case 7 -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                case 8 -> {
                    require(menu().isCardMode() && menu().isAutoConnectEnergy() == withAppFlux(), "wrong effective FE preference");
                    capture("card-home.png");
                    if (withAppFlux()) click("ae2lt.gui.button.auto_connect_energy_on");
                    else {
                        var button = button("ae2lt.gui.button.auto_connect_energy_unavailable");
                        require(!button.active, "unavailable FE button still clickable");
                        // Bypass the disabled widget to verify the server-side menu action guard too.
                        menu().clientToggleAutoConnectEnergy();
                    }
                }
                case 9 -> {
                    require(!menu().isAutoConnectEnergy(), "FE disabled state not synced");
                    server(() -> require(OverloadedFrequencyCardItem.getData(player().getMainHandItem()).autoConnectEnergy() == !withAppFlux(),
                            "FE menu action ignored availability or erased the saved flag"));
                    click("ae2lt.gui.button.auto_connect_off");
                }
                case 10 -> { require(menu().isAutoConnect(), "ME auto flag not synced"); capture("card-home-settings.png"); click("ae2lt.gui.tab.selection"); }
                case 11 -> { menu(); capture("card-selection.png"); click("ae2lt.gui.tab.member"); }
                case 12 -> { menu(); capture("card-members.png"); click("ae2lt.gui.tab.setting"); }
                case 13 -> { menu(); capture("card-settings.png"); click("ae2lt.management.network"); }
                case 14 -> { management(); capture("card-devices.png"); click("ae2lt.management.view.energy"); }
                case 15 -> { management(); capture("card-energy.png"); click("ae2lt.management.view.connections"); }
                case 16 -> { management(); capture("card-connections.png"); click("ae2lt.gui.tab.home"); }
                case 17 -> {
                    require(menu().isCardMode() && menu().getCurrentFrequencyId() == frequency, "return lost card frequency");
                    mc.screen.onClose();
                    server(() -> require(WirelessEnergyEndpoints.get(player().server).inspect(frequency, player().getUUID()).isEmpty(),
                            "unexpected FE endpoints"));
                }
                case 18 -> server(() -> {
                    var p = player();
                    var terminal = new ItemStack(ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get());
                    var item = (appeng.items.tools.powered.WirelessTerminalItem) terminal.getItem();
                    item.injectAEPower(terminal, item.getAEMaxPower(terminal), appeng.api.config.Actionable.MODULATE);
                    ((appeng.api.upgrades.IUpgradeableItem) item).getUpgrades(terminal).setItemDirect(0, p.getMainHandItem().copy());
                    p.setItemInHand(InteractionHand.MAIN_HAND, terminal);
                    p.inventoryMenu.broadcastChanges();
                    appeng.menu.MenuOpener.open(FrequencyMenu.TYPE, p,
                            appeng.menu.locator.MenuLocators.forInventorySlot(p.getInventory().selected));
                });
                case 19 -> { require(menu().hasParentMenu(), "terminal card lost return path"); click("ae2lt.gui.button.return_to_terminal"); }
                case 20 -> {
                    require(mc.screen instanceof appeng.client.gui.AEBaseScreen<?>
                            && mc.screen instanceof de.mari_023.ae2wtlib.api.terminal.IUniversalTerminalCapable,
                            "AE2WTLib-compatible wireless terminal missing");
                    var toolbar = ((com.moakiee.ae2lt.mixin.client.AEBaseScreenAccessor) mc.screen).ae2lt$getVerticalToolbar();
                    var buttons = ((com.moakiee.ae2lt.mixin.client.VerticalButtonBarAccessor) toolbar).ae2lt$getButtons();
                    String cardLabel = Component.translatable("ae2lt.gui.button.open_frequency_card").getString();
                    var cardButtons = buttons.stream().filter(b -> b.visible
                            && b instanceof com.moakiee.ae2lt.client.widgets.TextureToggleButton texture
                            && texture.getTooltipMessage().stream().anyMatch(t -> t.getString().equals(cardLabel))).toList();
                    require(cardButtons.size() == 1, "expected one frequency-card toolbar entry, got " + cardButtons.size());
                    require(buttons.stream().noneMatch(b -> b.visible && java.util.List.of(
                            "ae2lt.gui.button.auto_connect_on", "ae2lt.gui.button.auto_connect_off",
                            "ae2lt.gui.button.auto_connect_energy_on", "ae2lt.gui.button.auto_connect_energy_off")
                            .stream().anyMatch(key -> b.getMessage().getString().equals(Component.translatable(key).getString()))),
                            "automatic switches still present in terminal toolbar");
                    var entry = cardButtons.getFirst();
                    var base = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class;
                    var top = base.getDeclaredField("topPos"); top.setAccessible(true);
                    var height = base.getDeclaredField("imageHeight"); height.setAccessible(true);
                    require(entry.getY() + entry.getHeight() <= top.getInt(mc.screen) + height.getInt(mc.screen), "frequency entry protrudes below terminal");
                    capture("terminal-single-entry.png");
                    clickButton(entry);
                }
                case 21 -> {
                    require(menu().isCardMode() && menu().hasParentMenu() && menu().isAutoConnect(), "single entry did not open persisted card settings");
                    require(!menu().isAutoConnectEnergy(), "terminal FE state wrong");
                    capture("terminal-card-home.png");
                    click("ae2lt.gui.button.return_to_terminal");
                }
                case 22 -> { finish(null); return; }
            }
            phase++;
        } catch (Throwable t) { finish(t); }
    }
    private static FrequencyMenu menu() {
        require(Minecraft.getInstance().screen instanceof FrequencyScreen, "expected FrequencyScreen");
        return (FrequencyMenu) Minecraft.getInstance().player.containerMenu;
    }
    private static void management() { require(Minecraft.getInstance().screen instanceof NetworkManagementScreen, "expected NetworkManagementScreen"); }
    private static void click(String key) {
        var button = button(key);
        require(button.active, "button disabled " + key);
        clickButton(button);
    }
    private static Button button(String key) {
        var screen = Minecraft.getInstance().screen;
        var name = Component.translatable(key).getString();
        return screen.children().stream().filter(e -> e instanceof Button b && b.visible && b.getMessage().getString().equals(name))
                .map(e -> (Button) e).findFirst().orElseThrow(() -> new AssertionError("missing button " + key));
    }
    private static void clickButton(Button button) {
        var screen = Minecraft.getInstance().screen;
        double x = button.getX() + button.getWidth() / 2., y = button.getY() + button.getHeight() / 2.;
        require(screen.mouseClicked(x, y, 0), "mouse click failed"); screen.mouseReleased(x, y, 0);
    }
    private static net.minecraft.server.level.ServerPlayer player() { var mc = Minecraft.getInstance(); return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()); }
    private static void server(Runnable action) { Minecraft.getInstance().getSingleplayerServer().execute(() -> { try { action.run(); } catch (Throwable t) { failure = t; } }); }
    private static void require(boolean condition, String text) { if (!condition) throw new AssertionError(text); }
    private static boolean withAppFlux() { return Boolean.getBoolean("ae2lt.appFluxPresentTest"); }
    private static void capture(String name) throws Exception { var mc = Minecraft.getInstance(); try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(mc.gameDirectory.toPath().resolve(name)); } }
    private static void finish(Throwable problem) {
        done = true;
        String result = problem == null ? "OPTIONAL_APPFLUX_CLIENT_PASS present=" + withAppFlux() + ": advanced controller and all card/management pages; FE availability and server menu guard; ME toggle; single terminal toolbar entry within panel; settings and return navigation"
                : "NO_APPFLUX_CLIENT_FAIL phase=" + phase + ": " + problem;
        System.out.println(result); if (problem != null) problem.printStackTrace();
        try {
            if (problem != null) capture("failure.png");
            java.nio.file.Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("no-appflux-result.txt"), result);
        } catch (Exception ignored) {}
        Minecraft.getInstance().stop();
    }
}
