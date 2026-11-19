package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.upgrades.IUpgradeableItem;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.client.gui.AEBaseScreen;
import appeng.core.definitions.AEBlocks;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.client.widgets.TextureToggleButton;
import com.moakiee.ae2lt.menu.FrequencyMenu;
import com.moakiee.ae2lt.mixin.client.AEBaseScreenAccessor;
import com.moakiee.ae2lt.mixin.client.VerticalButtonBarAccessor;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.datafixers.util.Unit;
import de.mari_023.ae2wtlib.api.AE2wtlibAPI;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import de.mari_023.ae2wtlib.api.terminal.IUniversalTerminalCapable;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import de.mari_023.ae2wtlib.api.terminal.WUTHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Real client geometry and packet regression; enabled only in a disposable probe world. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class FrequencyToolbarClientProbe {
    private static final BlockPos BASE = new BlockPos(0, 100, 0);
    private static final String SELECTOR = "de.mari_023.ae2wtlib.api.terminal.TerminalSelectionButton";
    private static List<WTDefinition> modes;
    private static DriveBlockEntity drive;
    private static WirelessAccessPointBlockEntity access;
    private static int ticks, phase, scenario, selectorY;
    private static boolean done, modernSelector;
    private static volatile Throwable serverFailure;
    private static String selectedMode;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.frequencyToolbarProbe") || done) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (++ticks % 20 != 0) return;
        try {
            require(ticks < 6000, "client probe timed out");
            if (mc.player == null || mc.getSingleplayerServer() == null) return;
            if (serverFailure != null) throw new AssertionError("server action failed", serverFailure);
            switch (phase++) {
                case 0 -> {
                    record("Versions: " + ModList.get().getMods().stream()
                            .filter(m -> Set.of("ae2", "ae2lt", "ae2wtlib", "ae2wtlib_api", "neoforge", "thunderbolt").contains(m.getModId()))
                            .map(m -> m.getModId() + "=" + m.getVersion()).toList());
                    modes = WTDefinition.wirelessTerminals().stream().toList();
                    require(modes.size() >= 5, "expected native and Tianshu terminal modes");
                    modernSelector = setAlwaysVisible(false);
                    mc.setScreen(null);
                    mc.getWindow().setWindowed(1440, 1080);
                    server(p -> {
                        var level = p.serverLevel();
                        level.getChunkAt(BASE);
                        level.setBlockAndUpdate(BASE, AEBlocks.DRIVE.block().defaultBlockState());
                        level.setBlockAndUpdate(BASE.above(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                        level.setBlockAndUpdate(BASE.offset(2, 0, 0), AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
                        drive = (DriveBlockEntity) level.getBlockEntity(BASE);
                        access = (WirelessAccessPointBlockEntity) level.getBlockEntity(BASE.offset(2, 0, 0));
                        p.setGameMode(GameType.CREATIVE);
                        p.teleportTo(level, 0.5, 103, 2.5, Set.of(), 0, 0);
                        p.getAbilities().flying = true;
                        p.onUpdateAbilities();
                        p.getInventory().clearContent();
                    });
                }
                case 1 -> server(p -> GridHelper.createConnection(drive.getMainNode().getNode(), access.getMainNode().getNode()));
                case 2 -> {
                    mc.options.guiScale().set(2);
                    mc.resizeDisplay();
                    setAlwaysVisible(false);
                    server(FrequencyToolbarClientProbe::openTerminal);
                }
                case 3 -> {
                    assertTerminal(mode().terminalName());
                    assertLayout(true);
                    var selector = selector();
                    if (universal() && modernSelector) {
                        require(selector != null && selector.getHeight() > 0, "collapsed selector missing");
                        click(selector);
                        require((boolean) selectorField(selector, "menuOpen"), "selector did not expand on mouse click");
                        assertPanelClear(selector, cardButton());
                    } else if (!universal()) require(selector == null, "standalone terminal has a WUT selector");
                    setAlwaysVisible(true);
                }
                case 4 -> {
                    assertLayout(true);
                    if (universal() && modernSelector) {
                        var selector = selector();
                        require(selector.getWidth() == 0 && selector.getHeight() == -6, "always-visible selector is not active");
                        if (scenario == 0) assertOriginalOrderOverlaps();
                    }
                    capture("scale2");
                    mc.options.guiScale().set(3);
                    mc.resizeDisplay();
                }
                case 5 -> {
                    assertLayout(true);
                    capture("scale3");
                    click(cardButton());
                }
                case 6 -> {
                    require(mc.player.containerMenu instanceof FrequencyMenu menu && menu.isCardMode() && menu.hasParentMenu(),
                            "configuration click did not open the terminal card menu");
                    clickLabel("ae2lt.gui.button.return_to_terminal");
                }
                case 7 -> {
                    assertTerminal(mode().terminalName());
                    assertLayout(true);
                    if (universal()) {
                        var selector = selector();
                        if (modernSelector) {
                            @SuppressWarnings("unchecked")
                            var installed = (List<WTDefinition>) selectorField(selector, "terminals");
                            int next = (installed.indexOf(mode()) + 1) % installed.size();
                            selectedMode = installed.get(next).terminalName();
                            clickAt(selectorMethod(selector, "buttonX", next) + 9,
                                    selectorMethod(selector, "buttonY", next) + 10);
                        } else {
                            selectedMode = null;
                            click(legacySelector());
                        }
                    } else selectedMode = mode().terminalName();
                }
                case 8 -> {
                    if (selectedMode != null) assertTerminal(selectedMode);
                    else require(!currentMode().equals(mode().terminalName()), "legacy selector click did not switch terminal");
                    assertLayout(true);
                    selectorY = selector() == null ? 0 : selector().getY();
                    clickUpgradeSlot();
                }
                case 9 -> {
                    assertLayout(false);
                    if (universal() && modernSelector) require(selector().getY() == selectorY - 22,
                            "hidden card button still reserves toolbar space");
                    server(p -> require(host(p).getUpgrades().getStackInSlot(0).isEmpty(), "card removal packet did not reach server"));
                    clickUpgradeSlot();
                }
                case 10 -> {
                    assertLayout(true);
                    server(p -> {
                        var stack = p.getInventory().getItem(0);
                        require(((IUpgradeableItem) stack.getItem()).getUpgrades(stack).getStackInSlot(0)
                                .is(ModItems.OVERLOADED_FREQUENCY_CARD.get()), "card reinsertion packet did not reach server");
                    });
                    record("PASS: " + mode().terminalName() + " universal=" + universal()
                            + " selector=" + (modernSelector ? "modern" : "legacy")
                            + "; scales 2/3, resize, configuration round trip, card removal/reinsertion"
                            + (universal() ? ", terminal selection click" : ""));
                    mc.screen.onClose();
                    if (++scenario < modes.size() * 2) phase = 2;
                    else {
                        setAlwaysVisible(false);
                        record("PASS: frequency toolbar regression complete");
                        done = true;
                        mc.stop();
                    }
                }
                default -> throw new AssertionError("unexpected phase");
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            record("FAIL: phase=" + (phase - 1) + " scenario=" + scenario + " " + failure);
            done = true;
            mc.stop();
        }
    }

    private static void openTerminal(ServerPlayer p) {
        p.closeContainer();
        var definition = mode();
        var stack = universal() ? AE2wtlibAPI.makeWUT(definition.componentType()) : new ItemStack(definition.item());
        if (universal()) for (var installed : modes) stack.set(installed.componentType(), Unit.INSTANCE);
        var item = (WirelessTerminalItem) stack.getItem();
        WirelessTerminalItem.LINKABLE_HANDLER.link(stack, GlobalPos.of(p.level().dimension(), access.getBlockPos()));
        item.injectAEPower(stack, item.getAEMaxPower(stack), Actionable.MODULATE);
        ((IUpgradeableItem) item).getUpgrades(stack).setItemDirect(0, new ItemStack(ModItems.OVERLOADED_FREQUENCY_CARD.get()));
        p.getInventory().setItem(0, stack);
        p.getInventory().selected = 0;
        if (universal()) WUTHandler.setCurrentTerminal(p, MenuLocators.forInventorySlot(0), stack, definition);
        p.inventoryMenu.broadcastChanges();
        require(((ItemWT) item).open(p, MenuLocators.forInventorySlot(0), false), "terminal did not open");
    }

    private static WTDefinition mode() { return modes.get(scenario % modes.size()); }
    private static boolean universal() { return scenario < modes.size(); }
    private static WTMenuHost host(ServerPlayer p) { return (WTMenuHost) ((AEBaseMenu) p.containerMenu).getTarget(); }
    private static String currentMode() {
        return WTDefinition.of(((IUniversalTerminalCapable) Minecraft.getInstance().screen).getHost().getItemStack()).terminalName();
    }
    private static void assertTerminal(String expected) {
        var screen = Minecraft.getInstance().screen;
        require(screen instanceof AEBaseScreen<?> && screen instanceof IUniversalTerminalCapable, "wireless terminal screen missing");
        require(currentMode().equals(expected), "wrong terminal mode: " + currentMode() + " expected " + expected);
    }
    private static List<Button> toolbarButtons() {
        var toolbar = ((AEBaseScreenAccessor) Minecraft.getInstance().screen).ae2lt$getVerticalToolbar();
        return ((VerticalButtonBarAccessor) toolbar).ae2lt$getButtons();
    }
    private static Button selector() {
        return toolbarButtons().stream().filter(b -> b.getClass().getName().equals(SELECTOR)).findFirst().orElse(null);
    }
    private static Button legacySelector() {
        return toolbarButtons().stream().filter(b -> b instanceof de.mari_023.ae2wtlib.api.gui.IconButton).findFirst()
                .orElseThrow(() -> new AssertionError("legacy selector missing"));
    }
    private static TextureToggleButton cardButton() {
        String label = Component.translatable("ae2lt.gui.button.open_frequency_card").getString();
        var entries = toolbarButtons().stream().filter(b -> b instanceof TextureToggleButton texture
                && texture.getTooltipMessage().stream().anyMatch(t -> t.getString().equals(label))).toList();
        require(entries.size() == 1, "missing or duplicate configuration button: " + entries.size());
        return (TextureToggleButton) entries.getFirst();
    }
    private static void assertLayout(boolean installed) throws ReflectiveOperationException {
        var card = cardButton();
        require(card.visible == installed && card.active == installed, "card button visibility did not synchronize");
        require(Minecraft.getInstance().screen.children().stream().filter(b -> b == card).count() == 1,
                "configuration button not registered exactly once after init/resize");
        if (installed && selector() != null) assertPanelClear(selector(), card);
    }
    private static void assertPanelClear(Button selector, Button card) throws ReflectiveOperationException {
        int x = selectorMethod(selector, "panelX"), y = selectorMethod(selector, "panelY");
        int width = selectorMethod(selector, "panelWidth"), height = selectorMethod(selector, "panelHeight");
        require(!overlaps(card, x - 1, y - 1, width + 2, height + 2), "frequency button overlaps selector panel");
    }
    private static boolean overlaps(Button card, int x, int y, int width, int height) {
        // Include the full painted toolbar frame, which extends beyond the widget's 16x16 hit box.
        return card.getX() - 1 < x + width && card.getX() + 17 > x
                && card.getY() < y + height && card.getY() + 20 > y;
    }
    private static void assertOriginalOrderOverlaps() throws ReflectiveOperationException {
        var buttons = toolbarButtons();
        var card = cardButton();
        int index = buttons.indexOf(card);
        var toolbar = ((AEBaseScreenAccessor) Minecraft.getInstance().screen).ae2lt$getVerticalToolbar();
        try {
            buttons.remove(card);
            buttons.add(card);
            toolbar.updateBeforeRender();
            var selector = selector();
            require(overlaps(card, selectorMethod(selector, "panelX"), selectorMethod(selector, "panelY"),
                    selectorMethod(selector, "panelWidth"), selectorMethod(selector, "panelHeight")),
                    "negative control did not reproduce the original overlap");
            record("PASS: original append order reproduces overlapping painted bounds");
        } finally {
            buttons.remove(card);
            buttons.add(index, card);
            toolbar.updateBeforeRender();
        }
        assertLayout(true);
    }
    private static Object selectorField(Button selector, String name) throws ReflectiveOperationException {
        var field = selector.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(selector);
    }
    private static int selectorMethod(Button selector, String name, int... argument) throws ReflectiveOperationException {
        var method = argument.length == 0 ? selector.getClass().getDeclaredMethod(name)
                : selector.getClass().getDeclaredMethod(name, int.class);
        method.setAccessible(true);
        return (int) (argument.length == 0 ? method.invoke(selector) : method.invoke(selector, argument[0]));
    }
    private static boolean setAlwaysVisible(boolean value) throws ReflectiveOperationException {
        try {
            var configClass = Class.forName("de.mari_023.ae2wtlib.AE2wtlibClientConfig");
            var config = configClass.getField("CONFIG").get(null);
            var setting = (net.neoforged.neoforge.common.ModConfigSpec.BooleanValue)
                    configClass.getMethod("alwaysShowTerminalSelectorValue").invoke(config);
            setting.set(value);
            return true;
        } catch (ClassNotFoundException oldVersion) { return false; }
    }
    private static void clickLabel(String key) {
        String label = Component.translatable(key).getString();
        var button = Minecraft.getInstance().screen.children().stream()
                .filter(b -> b instanceof Button widget && widget.visible && widget.active && widget.getMessage().getString().equals(label))
                .map(b -> (Button) b).findFirst().orElseThrow(() -> new AssertionError("missing button " + key));
        click(button);
    }
    private static void click(Button button) { clickAt(button.getX() + button.getWidth() / 2., button.getY() + button.getHeight() / 2.); }
    private static void clickUpgradeSlot() {
        var mc = Minecraft.getInstance();
        var menu = (AEBaseMenu) mc.player.containerMenu;
        var slot = menu.getSlots(SlotSemantics.UPGRADE).getFirst();
        require(slot.isActive(), "upgrade slot is outside the visible scroll window");
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, ClickType.PICKUP, mc.player);
    }
    private static void clickAt(double x, double y) {
        var screen = Minecraft.getInstance().screen;
        require(x >= 0 && x < screen.width && y >= 0 && y < screen.height, "click target is outside the viewport");
        require(screen.mouseClicked(x, y, 0), "mouse click was not consumed at " + x + "," + y);
        screen.mouseReleased(x, y, 0);
    }
    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        var uuid = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            try { action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(uuid)); }
            catch (Throwable failure) { serverFailure = failure; }
        });
    }
    private static void capture(String suffix) {
        if (!universal()) return;
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "frequency-toolbar-" + scenario + "-" + suffix + ".png", mc.getMainRenderTarget(), ignored -> {});
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void record(String message) {
        System.out.println("FREQUENCY_TOOLBAR_PROBE " + message);
        try {
            var path = Path.of(System.getProperty("ae2lt.frequencyToolbarReport"));
            Files.createDirectories(path.getParent());
            Files.writeString(path, message + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
    }
}
