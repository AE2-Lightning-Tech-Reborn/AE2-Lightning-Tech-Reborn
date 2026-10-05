package com.moakiee.ae2lt.debug;

import java.nio.file.Files;
import java.util.Set;

import appeng.client.gui.AEBaseScreen;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.client.machine.OverloadedInterfaceScreen;
import com.moakiee.ae2lt.client.provider.OverloadedPatternProviderScreen;
import com.moakiee.ae2lt.client.widgets.PageButton;
import com.moakiee.ae2lt.menu.OverloadedInterfaceMenu;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import com.moakiee.ae2lt.mixin.client.AEBaseScreenAccessor;
import com.moakiee.ae2lt.mixin.client.VerticalButtonBarAccessor;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in native page button rendering, clicks, scrolling and server synchronization. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class PaginationClientProbe {
    private static final BlockPos INTERFACE = new BlockPos(0, 100, 0);
    private static final BlockPos EXTENDED = new BlockPos(2, 100, 0);
    private static final BlockPos REGULAR = new BlockPos(4, 100, 0);
    private static int phase, ticks;
    private static boolean done;
    private static volatile Throwable serverFailure;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.paginationProbe") || done) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.getSingleplayerServer() == null || ++ticks % 25 != 0) return;
        try {
            if (serverFailure != null) throw new AssertionError("server checks", serverFailure);
            switch (phase) {
                case 0 -> {
                    mc.options.guiScale().set(2);
                    mc.getWindow().setWindowed(1000, 800);
                    mc.resizeDisplay();
                    server(() -> {
                        var p = player();
                        var level = p.serverLevel();
                        for (var pos : BlockPos.betweenClosed(-2, 99, -2, 6, 103, 4)) {
                            level.setBlockAndUpdate(pos, (pos.getY() == 99 ? Blocks.WHITE_CONCRETE : Blocks.AIR).defaultBlockState());
                        }
                        level.setBlockAndUpdate(INTERFACE, ModBlocks.OVERLOADED_INTERFACE.get().defaultBlockState());
                        level.setBlockAndUpdate(EXTENDED, ModBlocks.EXTENDED_OVERLOADED_PATTERN_PROVIDER.get().defaultBlockState());
                        level.setBlockAndUpdate(REGULAR, ModBlocks.OVERLOADED_PATTERN_PROVIDER.get().defaultBlockState());
                        p.setGameMode(GameType.CREATIVE);
                        p.teleportTo(level, 2.5, 101, 3.5, Set.of(), 180, 25);
                    });
                }
                case 1 -> open(INTERFACE);
                case 2 -> {
                    require(mc.screen instanceof OverloadedInterfaceScreen, "interface screen binding");
                    expectPage(0);
                    require(button().visible, "interface page button hidden");
                    capture("interface.png");
                    click(0);
                }
                case 3 -> { expectPage(1); capture("interface-page2.png"); click(1); }
                case 4 -> { expectPage(0); scroll(-1); }
                case 5 -> { expectPage(1); scroll(1); }
                case 6 -> { expectPage(0); click(1); }
                case 7 -> { expectPage(0); open(EXTENDED); }
                case 8 -> {
                    require(mc.screen instanceof OverloadedPatternProviderScreen<?>, "provider screen binding");
                    expectPage(0);
                    require(button().visible, "extended provider page button hidden");
                    capture("provider.png");
                    click(0);
                }
                case 9 -> { expectPage(1); capture("provider-page2.png"); click(1); }
                case 10 -> { expectPage(0); scroll(-1); }
                case 11 -> { expectPage(1); scroll(1); }
                case 12 -> { expectPage(0); open(REGULAR); }
                case 13 -> {
                    expectPage(0);
                    require(((OverloadedPatternProviderMenu) mc.player.containerMenu).getTotalPages() == 1,
                            "regular provider fixture should have only one page");
                    require(!button().visible, "single-page button should be hidden");
                    capture("provider-single-page.png");
                }
                case 14 -> {
                    Files.writeString(mc.gameDirectory.toPath().resolve("pagination-result.txt"),
                            "PASS: interface and provider share PageButton; native left/right clicks, "
                                    + "wheel directions and first-page boundary; matching server pages; single-page hiding.");
                    done = true;
                    mc.stop();
                }
                default -> throw new AssertionError("phase " + phase);
            }
            phase++;
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(mc.gameDirectory.toPath().resolve("pagination-result.txt"), "FAIL phase=" + phase + ": " + failure); }
            catch (Exception ignored) { }
            done = true;
            mc.stop();
        }
    }

    private static void open(BlockPos pos) {
        server(() -> {
            var p = player();
            var be = p.serverLevel().getBlockEntity(pos);
            if (be instanceof OverloadedInterfaceBlockEntity host) host.openMenu(p, MenuLocators.forBlockEntity(host));
            else if (be instanceof OverloadedPatternProviderBlockEntity host) host.openMenu(p, MenuLocators.forBlockEntity(host));
            else throw new AssertionError("missing host " + pos);
        });
    }

    private static PageButton button() {
        var screen = (AEBaseScreen<?>) Minecraft.getInstance().screen;
        var bar = ((AEBaseScreenAccessor) screen).ae2lt$getVerticalToolbar();
        var buttons = ((VerticalButtonBarAccessor) bar).ae2lt$getButtons();
        require(buttons.stream().filter(PageButton.class::isInstance).count() == 1, "expected one shared page button");
        return (PageButton) buttons.stream().filter(PageButton.class::isInstance).findFirst().orElseThrow();
    }

    private static void click(int mouseButton) {
        var screen = Minecraft.getInstance().screen;
        var button = button();
        screen.mouseClicked(button.getX() + 8, button.getY() + 8, mouseButton);
        screen.mouseReleased(button.getX() + 8, button.getY() + 8, mouseButton);
    }

    private static void scroll(double delta) {
        var screen = (AEBaseScreen<?>) Minecraft.getInstance().screen;
        screen.mouseScrolled(screen.getGuiLeft() + 80, screen.getGuiTop() + 60, 0, delta);
    }

    private static void expectPage(int expected) {
        require(page(Minecraft.getInstance().player.containerMenu) == expected, "client page mismatch at phase " + phase);
        server(() -> require(page(player().containerMenu) == expected, "server page mismatch " + expected));
    }

    private static int page(Object menu) {
        if (menu instanceof OverloadedInterfaceMenu m) return m.currentPage;
        if (menu instanceof OverloadedPatternProviderMenu m) return m.getCurrentPage();
        throw new AssertionError("unexpected menu " + menu);
    }

    private static void server(Runnable task) {
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try { task.run(); } catch (Throwable failure) { serverFailure = failure; }
        });
    }

    private static ServerPlayer player() {
        var mc = Minecraft.getInstance();
        return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
    }

    private static void capture(String name) throws Exception {
        var mc = Minecraft.getInstance();
        try (var pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            pixels.writeToFile(mc.gameDirectory.toPath().resolve(name));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
