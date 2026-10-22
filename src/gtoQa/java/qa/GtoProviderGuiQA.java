package qa;

import java.nio.file.Files;
import java.nio.file.Path;

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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in end-to-end GUI/network check in the disposable singleplayer QA world. */
@Mod.EventBusSubscriber(modid = "gto_compat_qa", value = Dist.CLIENT)
public final class GtoProviderGuiQA {
    private static final BlockPos POS = new BlockPos(50, 110, 50);
    private static volatile OverloadedPatternProviderBlockEntity provider;
    private static volatile Throwable serverFailure;
    private static volatile boolean serverEnabled;
    private static boolean started, done;
    private static int ticks, phase, phaseTicks;

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("ae2lt.providerGuiProbe")) return;
        if (provider != null) serverEnabled = provider.isMachineAdaptationEnabled();
    }

    private static void server(java.util.function.Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.execute(() -> {
            try { action.accept(server.getPlayerList().getPlayer(id)); }
            catch (Throwable failure) { serverFailure = failure; }
        });
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done || !Boolean.getBoolean("ae2lt.providerGuiProbe")) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        try {
            if (serverFailure != null) throw new AssertionError("server fixture", serverFailure);
            require(++ticks < 1200, "GUI probe timed out at phase " + phase);
            phaseTicks++;
            if (ticks % 10 != 0) return;
            if (!started) {
                started = true;
                server(player -> {
                    player.setGameMode(GameType.CREATIVE);
                    player.teleportTo(50.5, 111, 48.5);
                    var level = player.serverLevel();
                    level.setBlockAndUpdate(POS, ModBlocks.OVERLOADED_PATTERN_PROVIDER.get().defaultBlockState());
                    provider = (OverloadedPatternProviderBlockEntity) level.getBlockEntity(POS);
                    provider.setMachineAdaptationEnabled(true);
                    provider.setProviderMode(OverloadedPatternProviderBlockEntity.ProviderMode.WIRELESS);
                });
                return;
            }
            if (provider == null || phaseTicks < 40) return;
            switch (phase) {
                case 0 -> {
                    server(player -> require(MenuOpener.open(OverloadedPatternProviderMenu.TYPE, player,
                            MenuLocators.forBlockEntity(provider)), "server rejected provider menu"));
                    next();
                }
                case 1, 6 -> {
                    if (!(mc.screen instanceof OverloadedPatternProviderScreen<?> screen)) return;
                    click(screen, widget(screen, "advancedSettingsButton"));
                    next();
                }
                case 2 -> {
                    if (!(mc.screen instanceof OverloadedPatternProviderAdvancedScreen<?> screen)) return;
                    require(menu().isMachineAdaptationEnabled() && serverEnabled, "default true synchronized");
                    checkLabel(screen, true);
                    shot("provider-adaptation-on.png");
                    click(screen, widget(screen, "machineAdaptationButton"));
                    next();
                }
                case 3 -> {
                    if (menu().isMachineAdaptationEnabled() || serverEnabled) return;
                    var screen = mc.screen;
                    checkLabel(screen, false);
                    shot("provider-adaptation-off.png");
                    click(screen, widget(screen, "machineAdaptationButton"));
                    next();
                }
                case 4 -> {
                    if (!menu().isMachineAdaptationEnabled() || !serverEnabled) return;
                    checkLabel(mc.screen, true);
                    // Close the container and open a fresh one, rather than reuse local GUI fields.
                    mc.player.closeContainer();
                    next();
                }
                case 5 -> {
                    server(player -> require(MenuOpener.open(OverloadedPatternProviderMenu.TYPE, player,
                            MenuLocators.forBlockEntity(provider)), "server rejected reopened provider menu"));
                    next();
                }
                case 7 -> {
                    if (!(mc.screen instanceof OverloadedPatternProviderAdvancedScreen<?>)) return;
                    require(menu().isMachineAdaptationEnabled() && serverEnabled, "reopened menu synchronized true");
                    checkLabel(mc.screen, true);
                    Files.writeString(Path.of("provider-gui-result.txt"),
                            "PASS: actual provider toolbar and advanced screen; visible translated button; mouse clicks off/on; server state and GuiSync; fresh container reopen.\n");
                    finish();
                }
                default -> throw new AssertionError("Unknown phase " + phase);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(Path.of("provider-gui-result.txt"), "FAIL: " + failure + "\n"); }
            catch (Exception ignored) { }
            finish();
        }
    }

    private static OverloadedPatternProviderMenu menu() {
        return (OverloadedPatternProviderMenu) Minecraft.getInstance().player.containerMenu;
    }

    private static AbstractWidget widget(Object screen, String name) throws ReflectiveOperationException {
        var field = screen.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (AbstractWidget) field.get(screen);
    }

    private static void checkLabel(Screen screen, boolean enabled) throws ReflectiveOperationException {
        var button = widget(screen, "machineAdaptationButton");
        var key = enabled ? "ae2lt.gui.machine_adaptation.on" : "ae2lt.gui.machine_adaptation.off";
        require(button.visible && button.active, "adaptation button visible and active");
        require(button.getMessage().getString().equals(net.minecraft.network.chat.Component.translatable(key).getString())
                && !button.getMessage().getString().equals(key), "translated state label");
        require(button.getX() >= 0 && button.getY() >= 0
                && button.getX() + button.getWidth() <= screen.width
                && button.getY() + button.getHeight() <= screen.height, "rendered button within screen");
    }

    private static void click(Screen screen, AbstractWidget widget) {
        require(widget.visible && widget.active, "click target available");
        double x = widget.getX() + widget.getWidth() / 2.0;
        double y = widget.getY() + widget.getHeight() / 2.0;
        require(screen.mouseClicked(x, y, 0), "screen accepted mouse click");
        screen.mouseReleased(x, y, 0);
    }

    private static void shot(String name) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), ignored -> {});
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void next() { phase++; phaseTicks = 0; }
    private static void finish() { done = true; Minecraft.getInstance().stop(); }
}
