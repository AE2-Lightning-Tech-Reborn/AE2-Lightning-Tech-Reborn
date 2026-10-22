package com.moakiee.ae2lt.debug;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import appeng.core.definitions.AEBlocks;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.client.provider.OverloadedPatternProviderAdvancedScreen;
import com.moakiee.ae2lt.client.provider.OverloadedPatternProviderScreen;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Opt-in real UI and C2S/S2C acceptance; modifies only its disposable world. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class ProviderAdvancedClientProbe {
    private static final BlockPos POS = new BlockPos(0, 100, 0);
    private static int ticks, phase;
    private static boolean done;
    private static volatile Throwable failure;
    private static volatile boolean serverChecked, languageReady;

    private ProviderAdvancedClientProbe() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done
                || !Boolean.getBoolean("ae2lt.providerAdvancedProbe")) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (++ticks % 20 != 0) return;
        try {
            require(ticks < 1800, "timeout at phase " + phase);
            if (failure != null) throw new AssertionError("server fixture", failure);
            if (mc.player == null || mc.getSingleplayerServer() == null) return;
            switch (phase) {
                case 0 -> {
                    mc.setScreen(null);
                    mc.options.guiScale().set(3);
                    mc.options.renderDistance().set(3);
                    mc.resizeDisplay();
                    var uuid = mc.player.getUUID();
                    server(() -> {
                        var s = mc.getSingleplayerServer();
                        var level = s.overworld();
                        for (var p : BlockPos.betweenClosed(-3, 99, -3, 3, 104, 4))
                            level.setBlockAndUpdate(p, p.getY() == 99
                                    ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                        level.setDayTime(6000);
                        level.setBlockAndUpdate(POS, ModBlocks.OVERLOADED_PATTERN_PROVIDER.get().defaultBlockState());
                        level.setBlockAndUpdate(POS.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                        var player = s.getPlayerList().getPlayer(uuid);
                        player.setGameMode(GameType.CREATIVE);
                        player.teleportTo(level, 0.5, 101, 3.5, Set.of(), 180, 25);
                        player.getInventory().clearContent();
                    });
                }
                case 1 -> {
                    if (!(mc.level.getBlockEntity(POS) instanceof OverloadedPatternProviderBlockEntity)) return;
                    open();
                }
                case 2 -> {
                    if (!(mc.screen instanceof OverloadedPatternProviderScreen<?>)) return;
                    require(!menu().isWirelessMode(), "fixture must start in adjacent mode");
                    require(menu().isMachineAdaptationEnabled(), "new providers must default to ON");
                    click("advancedSettingsButton");
                }
                case 3 -> {
                    advanced();
                    layout();
                    capture("adjacent-en-on.png");
                    click("machineAdaptationButton");
                }
                case 4 -> { synchronizedState(false); }
                case 5 -> {
                    if (!serverChecked) return;
                    capture("adjacent-en-off.png");
                    click("machineAdaptationButton");
                }
                case 6 -> { synchronizedState(true); }
                case 7 -> {
                    if (!serverChecked) return;
                    mc.screen.onClose();
                }
                case 8 -> {
                    require(mc.screen instanceof OverloadedPatternProviderScreen<?>, "back navigation failed");
                    click("modeButton");
                }
                case 9 -> {
                    if (!menu().isWirelessMode()) return;
                    click("advancedSettingsButton");
                }
                case 10 -> {
                    advanced();
                    layout();
                    require(widget("wirelessStrategyButton").active, "wireless controls must activate");
                    capture("wireless-en-on.png");
                    mc.getLanguageManager().setSelected("zh_cn");
                    mc.options.languageCode = "zh_cn";
                    mc.reloadResourcePacks().thenRun(() -> languageReady = true);
                }
                case 11 -> {
                    if (!languageReady || mc.getOverlay() != null) return;
                    advanced();
                    layout();
                    capture("wireless-zh-on.png");
                    click("machineAdaptationButton");
                }
                case 12 -> { synchronizedState(false); }
                case 13 -> {
                    if (!serverChecked) return;
                    capture("wireless-zh-off.png");
                    mc.screen.onClose();
                }
                case 14 -> {
                    click("modeButton");
                }
                case 15 -> {
                    if (menu().isWirelessMode()) return;
                    click("advancedSettingsButton");
                }
                case 16 -> {
                    advanced();
                    layout();
                    require(!widget("wirelessStrategyButton").active, "adjacent wireless controls must disable");
                    capture("adjacent-zh-off.png");
                    mc.player.closeContainer();
                }
                case 17 -> { open(); }
                case 18 -> {
                    if (!(mc.screen instanceof OverloadedPatternProviderScreen<?>)) return;
                    require(!menu().isMachineAdaptationEnabled(), "OFF must survive reopening menu");
                    click("advancedSettingsButton");
                }
                case 19 -> { click("machineAdaptationButton"); }
                case 20 -> { synchronizedState(true); }
                case 21 -> {
                    if (!serverChecked) return;
                    capture("adjacent-zh-restored.png");
                    record("PASS: actual mouse dispatch, adjacent/wireless access, default ON, ON/OFF/ON packets, "
                            + "server BE + menu + client BE sync, reopening persistence, en_us/zh_cn label and layout");
                    done = true;
                    mc.stop();
                }
                default -> throw new AssertionError("unexpected phase " + phase);
            }
            phase++;
        } catch (Throwable t) {
            t.printStackTrace();
            record("FAIL phase=" + phase + ": " + t);
            done = true;
            mc.stop();
        }
    }

    private static void open() {
        var mc = Minecraft.getInstance();
        var uuid = mc.player.getUUID();
        server(() -> require(MenuOpener.open(OverloadedPatternProviderMenu.TYPE,
                mc.getSingleplayerServer().getPlayerList().getPlayer(uuid),
                MenuLocators.forBlockEntity(mc.getSingleplayerServer().overworld().getBlockEntity(POS))), "open menu"));
    }

    private static OverloadedPatternProviderMenu menu() {
        return (OverloadedPatternProviderMenu) Minecraft.getInstance().player.containerMenu;
    }

    private static void advanced() {
        require(Minecraft.getInstance().screen instanceof OverloadedPatternProviderAdvancedScreen<?>, "advanced screen missing");
    }

    private static AbstractWidget widget(String field) throws ReflectiveOperationException {
        var screen = Minecraft.getInstance().screen;
        var f = screen.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return (AbstractWidget) f.get(screen);
    }

    private static void click(String field) throws ReflectiveOperationException {
        var w = widget(field);
        require(w.visible && w.active, field + " unavailable");
        require(Minecraft.getInstance().screen.mouseClicked(w.getX() + w.getWidth() / 2.0,
                w.getY() + w.getHeight() / 2.0, 0), field + " mouse dispatch rejected");
    }

    private static void layout() throws ReflectiveOperationException {
        var mc = Minecraft.getInstance();
        var w = widget("machineAdaptationButton");
        require(w.visible && w.active && w.getX() >= 0 && w.getY() >= 0
                && w.getX() + w.getWidth() <= mc.screen.width
                && w.getY() + w.getHeight() <= mc.screen.height, "adaptation button outside screen");
        require(!w.getMessage().getString().startsWith("ae2lt."), "missing translation");
        require(mc.font.width(w.getMessage()) <= w.getWidth() - 8, "translated label clipped");
        var apply = widget("applyParallelism");
        require(w.getY() + w.getHeight() <= apply.getY(), "adaptation overlaps parallelism");
    }

    private static void synchronizedState(boolean expected) {
        var mc = Minecraft.getInstance();
        require(menu().isMachineAdaptationEnabled() == expected, "client menu sync failed");
        require(((OverloadedPatternProviderBlockEntity) mc.level.getBlockEntity(POS))
                .isMachineAdaptationEnabled() == expected, "client BE sync failed");
        var uuid = mc.player.getUUID();
        serverChecked = false;
        server(() -> {
            var s = mc.getSingleplayerServer();
            require(((OverloadedPatternProviderBlockEntity) s.overworld().getBlockEntity(POS))
                    .isMachineAdaptationEnabled() == expected, "server BE action failed");
            require(((OverloadedPatternProviderMenu) s.getPlayerList().getPlayer(uuid).containerMenu)
                    .isMachineAdaptationEnabled() == expected, "server menu sync failed");
            serverChecked = true;
        });
    }

    private static void server(Runnable action) {
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try { action.run(); } catch (Throwable t) { failure = t; }
        });
    }

    private static Path output() { return Path.of(System.getProperty("ae2lt.providerAdvancedOutput")); }
    private static void capture(String name) throws Exception {
        Files.createDirectories(output());
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(output().resolve(name));
        }
    }
    private static void record(String message) {
        try {
            Files.createDirectories(output());
            Files.writeString(output().resolve("result.txt"), message);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
