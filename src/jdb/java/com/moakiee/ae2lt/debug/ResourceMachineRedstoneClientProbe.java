package com.moakiee.ae2lt.debug;

import java.util.Set;

import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.core.definitions.AEItems;
import appeng.menu.MenuOpener;
import appeng.menu.SlotSemantics;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.client.machine.RedstoneMachineScreen;
import com.moakiee.ae2lt.menu.CrystalCatalyzerMenu;
import com.moakiee.ae2lt.menu.RedstoneMachineMenu;
import com.moakiee.ae2lt.menu.TeslaCoilMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Real screen, slot click and client/server setting packets, in an opt-in disposable world. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class ResourceMachineRedstoneClientProbe {
    private static final BlockPos POS = new BlockPos(0, 100, 0);
    private static int ticks, phase, startupTicks;
    private static boolean done, prepared;
    private static volatile Throwable failure;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.redstoneClientProbe") || done) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.player == null || mc.getSingleplayerServer() == null) {
            if (++startupTicks % 200 == 0) {
                System.out.println("RESOURCE_REDSTONE_CLIENT waiting: " + mc.screen);
                Screenshot.grab(mc.gameDirectory, "redstone-startup.png", mc.getMainRenderTarget(), message -> {});
            }
            if (startupTicks > 2400) {
                System.out.println("RESOURCE_REDSTONE_CLIENT FAIL: test world did not load");
                done = true;
                mc.stop();
            }
            return;
        }
        if (++ticks % 30 != 0) return;
        try {
            if (failure != null) throw new AssertionError(failure);
            int step = phase % 7;
            boolean tesla = phase >= 7;
            if (phase == 14) {
                System.out.println("RESOURCE_REDSTONE_CLIENT PASS: both slots, shift-click install/remove, setting packets, three modes, screenshots");
                done = true;
                mc.stop();
                return;
            }
            if (step == 0) {
                if (prepared) {
                    server(() -> {
                        var level = mc.getSingleplayerServer().overworld();
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        MenuOpener.open(tesla ? TeslaCoilMenu.TYPE : CrystalCatalyzerMenu.TYPE,
                                player, MenuLocators.forBlockEntity(level.getBlockEntity(POS)));
                    });
                    prepared = false;
                    phase++;
                    return;
                }
                server(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    player.closeContainer();
                    level.setBlockAndUpdate(POS, (tesla ? ModBlocks.TESLA_COIL : ModBlocks.CRYSTAL_CATALYZER)
                            .get().defaultBlockState());
                    player.setGameMode(GameType.CREATIVE);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                    player.teleportTo(level, 0.5, 101, 2.5, Set.of(), 180, 25);
                    player.getInventory().clearContent();
                    player.getInventory().setItem(0, AEItems.REDSTONE_CARD.stack());
                });
                // Allow teleport, chunk tracking and block updates to arrive before opening the menu.
                prepared = true;
                return;
            } else {
                require(mc.screen instanceof RedstoneMachineScreen<?>, "wrong screen: " + mc.screen);
                var screen = (RedstoneMachineScreen<?>) mc.screen;
                var menu = (RedstoneMachineMenu<?>) mc.player.containerMenu;
                require(menu.getSlots(SlotSemantics.UPGRADE).size() == 1, "upgrade slot missing");
                var button = screen.children().stream()
                        .filter(child -> child instanceof SettingToggleButton<?> b && b.getSetting() == Settings.REDSTONE_CONTROLLED)
                        .map(child -> (SettingToggleButton<?>) child).findFirst().orElseThrow();
                switch (step) {
                    case 1 -> {
                        require(!button.visible && !menu.redstoneCardInstalled, "button shown before installation");
                        var source = menu.getSlots(SlotSemantics.PLAYER_HOTBAR).getFirst();
                        mc.gameMode.handleInventoryMouseClick(menu.containerId, source.index, 0, ClickType.QUICK_MOVE, mc.player);
                    }
                    case 2 -> {
                        require(button.visible && menu.redstoneCardInstalled && menu.redstoneMode == RedstoneMode.IGNORE,
                                "card installation/mode sync failed");
                        button.mouseClicked(button.getX() + 5, button.getY() + 5, 0);
                    }
                    case 3 -> {
                        require(menu.redstoneMode == RedstoneMode.HIGH_SIGNAL && menu.redstonePaused,
                                "high-signal button packet failed");
                        Screenshot.grab(mc.gameDirectory, tesla ? "tesla-redstone.png" : "catalyzer-redstone.png",
                                mc.getMainRenderTarget(), message -> {});
                        button.mouseClicked(button.getX() + 5, button.getY() + 5, 0);
                    }
                    case 4 -> {
                        require(menu.redstoneMode == RedstoneMode.LOW_SIGNAL && !menu.redstonePaused,
                                "low-signal mode failed");
                        button.mouseClicked(button.getX() + 5, button.getY() + 5, 0);
                    }
                    case 5 -> {
                        require(menu.redstoneMode == RedstoneMode.IGNORE, "mode cycle included unsupported pulse");
                        mc.gameMode.handleInventoryMouseClick(menu.containerId, menu.getSlots(SlotSemantics.UPGRADE).getFirst().index,
                                0, ClickType.QUICK_MOVE, mc.player);
                    }
                    case 6 -> require(!button.visible && !menu.redstoneCardInstalled
                            && mc.player.getInventory().countItem(AEItems.REDSTONE_CARD.asItem()) == 1,
                            "removing card lost item or left controls active");
                }
            }
            phase++;
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("RESOURCE_REDSTONE_CLIENT FAIL phase=" + phase + ": " + t);
            done = true;
            mc.stop();
        }
    }

    private static void server(Runnable action) {
        Minecraft.getInstance().getSingleplayerServer().execute(() -> {
            try { action.run(); } catch (Throwable t) { failure = t; }
        });
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
