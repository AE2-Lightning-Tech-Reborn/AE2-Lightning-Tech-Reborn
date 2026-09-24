package com.moakiee.ae2lt.client;

import appeng.api.config.Settings;
import appeng.api.config.ViewItems;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.client.gui.me.common.Repo;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.common.GridInventoryEntry;
import com.moakiee.ae2lt.logic.tianshu.maintenance.InventoryMaintenanceStatus;
import com.moakiee.ae2lt.logic.tianshu.maintenance.ReservedStockMatchMode;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuTerminalViewMode;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import com.moakiee.ae2lt.menu.TianshuMaintenanceMenu;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.network.tianshu.MaintenanceSummarySyncPacket;
import com.moakiee.ae2lt.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Opt-in JDB acceptance probe; excluded from the published JAR. Only use in TerminalViewQA. */
public final class TerminalViewClientProbe {
    private static volatile String report = "idle";
    private static final List<String> checks = new ArrayList<>();

    public static String openWorld() {
        Minecraft.getInstance().tell(() -> {
            var mc = Minecraft.getInstance();
            mc.options.pauseOnLostFocus = false;
            mc.options.guiScale().set(2);
            mc.getWindow().setWindowed(1280, 900);
            mc.resizeDisplay();
            mc.createWorldOpenFlows().openWorld("TerminalViewQA", () -> report = "world open failed");
            report = "opening QA world";
        });
        return "queued world";
    }

    public static String openTerminal(boolean pattern) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return "server not ready";
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.closeContainer();
            var item = pattern ? ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get()
                    : ModItems.TIANSHU_WIRELESS_CRAFTING_TERMINAL.get();
            var terminal = new ItemStack(item);
            terminal.set(AEComponents.STORED_ENERGY, 1000000.0);
            player.getInventory().setItem(0, terminal);
            player.inventoryMenu.broadcastChanges();
            item.open(player, MenuLocators.forInventorySlot(0), false);
            report = "opened " + player.containerMenu.getClass().getSimpleName();
        });
        return "queued terminal";
    }

    public static String verify() {
        Minecraft.getInstance().tell(() -> {
            try {
                var screen = (TianshuMaintenanceTermScreen<?>) Minecraft.getInstance().screen;
                var menu = (TianshuMaintenanceMenu) screen.getMenu();
                screen.updateBeforeRender();
                menu.setTerminalViewMode(TianshuTerminalViewMode.ALL);
                var repoField = appeng.client.gui.me.common.MEStorageScreen.class.getDeclaredField("repo");
                repoField.setAccessible(true);
                var repo = (Repo) repoField.get(screen);
                repo.setSearchString("");
                repo.setEnabled(true);
                repo.setPaused(false);
                var diamond = AEItemKey.of(Items.DIAMOND);
                var gold = AEItemKey.of(Items.GOLD_INGOT);
                var iron = AEItemKey.of(Items.IRON_INGOT);
                var emerald = AEItemKey.of(Items.EMERALD);
                menu.maintenanceMenu().getClientRepo().handleUpdate(true, List.of(
                        new GridInventoryEntry(1001, diamond, 10, 0, false),
                        new GridInventoryEntry(1002, gold, 0, 0, true),
                        new GridInventoryEntry(1003, iron, 16, 0, true)));
                menu.receiveMaintenanceSummary(menu.getTianshuSelectionRevision(), 100000, false,
                        List.of(summary(diamond, 10), summary(emerald, 0)));
                var cycle = TianshuMaintenanceTermScreen.class.getDeclaredMethod("cycleViewMode", boolean.class);
                cycle.setAccessible(true);
                var expected = new int[] {2, 2, 2, 3};
                for (int round = 0; round < 3; round++) {
                    for (int count : expected) {
                        cycle.invoke(screen, false);
                        require(repo.size() == count, "forward " + menu.getTerminalViewMode() + " entries=" + repo.size());
                    }
                }
                for (int count : new int[] {2, 2, 2, 3}) {
                    repo.setPaused(true);
                    cycle.invoke(screen, true);
                    require(repo.size() == count, "reverse while Shift-frozen " + menu.getTerminalViewMode());
                    require(repo.isPaused(), "Shift inventory freeze restored");
                }
                menu.setTerminalViewMode(TianshuTerminalViewMode.MAINTAINABLE);
                screen.updateBeforeRender();
                require(repo.size() == 2, "maintainable includes missing-pattern zero-stock entry");
                var listener = Repo.class.getDeclaredField("updateViewListener");
                listener.setAccessible(true);
                var original = (Runnable) listener.get(repo);
                var updates = new int[1];
                repo.setUpdateViewListener(() -> { updates[0]++; original.run(); });
                var reconcile = TianshuMaintenanceTermScreen.class.getDeclaredMethod("syncSyntheticMaintenanceEntries");
                reconcile.setAccessible(true);
                for (int i = 0; i < 100; i++) reconcile.invoke(screen);
                require(updates[0] == 0, "100 unchanged frames cause no inventory rebuild");
                menu.maintenanceMenu().getClientRepo().handleUpdate(false,
                        List.of(new GridInventoryEntry(1004, emerald, 5, 0, false)));
                reconcile.invoke(screen);
                require(repo.getAllEntries().size() == 4, "real stock replaces synthetic without duplicate");
                menu.maintenanceMenu().getClientRepo().handleUpdate(false,
                        List.of(new GridInventoryEntry(1004, null, 0, 0, false)));
                reconcile.invoke(screen);
                require(repo.getAllEntries().size() == 4, "missing stock returns as synthetic");
                menu.setTerminalViewMode(TianshuTerminalViewMode.CRAFTABLE);
                // Emulate older, in-flight server echoes after a newer user selection.
                menu.maintenanceMenu().getConfigManager().putSetting(Settings.VIEW_MODE, ViewItems.STORED);
                if (menu instanceof TianshuCraftingTermMenu crafting) crafting.maintainableView = true;
                if (menu instanceof TianshuPatternEncodingTermMenu pattern) pattern.maintainableView = true;
                require(menu.getTerminalViewMode() == TianshuTerminalViewMode.CRAFTABLE
                        && !menu.isMaintainableView() && screen.getSortDisplay() == ViewItems.CRAFTABLE,
                        "stale server echoes cannot roll back newest selection");
                screen.updateBeforeRender();
                checks.add(screen.getClass().getSimpleName() + ": all native view checks passed");
                report = String.join("\n", checks);
                Files.writeString(Path.of("terminal-view-probe.txt"), report);
            } catch (Throwable e) {
                report = "FAILED: " + e;
                e.printStackTrace();
            }
        });
        return "queued native assertions";
    }

    private static MaintenanceSummarySyncPacket.Entry summary(AEItemKey key, long stored) {
        return new MaintenanceSummarySyncPacket.Entry(key, true, InventoryMaintenanceStatus.MISSING_PATTERN,
                stored, 16, 64, 1, 0, ReservedStockMatchMode.EXACT, false, false, false);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static String screenshot() {
        Minecraft.getInstance().tell(() -> {
            var mc = Minecraft.getInstance();
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), ignored -> {});
        });
        return "queued screenshot";
    }

    public static String status() { return report; }
}
