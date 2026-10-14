package com.moakiee.ae2lt.integration.mest.client;

import appeng.client.gui.AEBaseScreen;
import appeng.menu.me.common.MEStorageMenu;
import com.lhy.mest.api.client.RegisterMestModulesEvent;
import com.lhy.mest.client.MESTScreen;
import com.moakiee.ae2lt.integration.mest.MestSlot;
import com.moakiee.ae2lt.integration.mest.MestTianshuSession;
import com.moakiee.ae2lt.menu.extension.EmbeddedTerminalMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.IEventBus;

/** Client state is bound to the actual open screen/menu, never just its reusable container id. */
public final class MestTianshuClient {
    private static final ThreadLocal<MEStorageMenu> INPUT = new ThreadLocal<>();
    private static MESTScreen root;
    private static final List<TianshuModulePanel> panels = new ArrayList<>();
    private static TianshuModulePanel focused;
    private MestTianshuClient() {}
    public static void register(IEventBus bus) { bus.addListener(MestTianshuClient::registerModules); }
    private static void registerModules(RegisterMestModulesEvent event) {
        event.register(MestTianshuSession.CRAFTING_MODULE, context -> new TianshuModulePanel(context, false));
        event.register(MestTianshuSession.PATTERN_MODULE, context -> new TianshuModulePanel(context, true));
        event.register(MestTianshuSession.MAINTENANCE_MODULE, TianshuMaintenancePanel::new);
    }
    static void attach(TianshuModulePanel panel) {
        if (root != panel.context.screen()) { root = panel.context.screen(); panels.clear(); focused = null; }
        panels.removeIf(old -> old.id().equals(panel.id()));
        panels.add(panel);
    }
    static void focus(TianshuModulePanel panel) { focused = panel; }
    public static TianshuModulePanel focused(MESTScreen screen) {
        return root == screen && focused != null && focused.visible ? focused : null;
    }
    public static TianshuModulePanel recipePanel(com.lhy.mest.terminal.MESTMenu menu) {
        return root != null && root.getMenu() == menu && Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.containerMenu == menu ? focused(root) : null;
    }
    public static void clearFocus(MESTScreen screen) {
        if (root == screen) { if (focused != null) focused.source.setFocused(null); focused = null; }
    }
    public static TianshuModulePanel crafting(MESTScreen screen) {
        return root == screen ? panels.stream().filter(p -> !p.encoding).findFirst().orElse(null) : null;
    }
    public static <T> T input(MEStorageMenu menu, Supplier<T> action) {
        var previous = INPUT.get(); INPUT.set(menu);
        try { return action.get(); } finally { if (previous == null) INPUT.remove(); else INPUT.set(previous); }
    }
    private static MEStorageMenu inputMenu() {
        var menu = INPUT.get();
        if (menu != null) return menu;
        return Minecraft.getInstance().screen instanceof AEBaseScreen<?> screen
                && screen.getMenu() instanceof MEStorageMenu storage
                && storage instanceof EmbeddedTerminalMenu ? storage : null;
    }
    public static int mapSlot(int index) {
        var menu = inputMenu();
        if (!(menu instanceof EmbeddedTerminalMenu embedded) || index < 0) return index;
        if (index >= menu.slots.size()) return -1;
        var original = menu.slots.get(index);
        for (var slot : embedded.outerMenu().slots) {
            if (slot instanceof MestSlot proxy && proxy.controller() == menu && proxy.original() == original) return slot.index;
            if (original.container == menu.getPlayerInventory() && slot.container == original.container
                    && slot.getContainerSlot() == original.getContainerSlot()) return slot.index;
        }
        return -1;
    }
    public static AEBaseScreen<?> returnTarget(AEBaseScreen<?> screen) {
        if (root == null || Minecraft.getInstance().player == null
                || Minecraft.getInstance().player.containerMenu != root.getMenu()) return screen;
        return panels.stream().anyMatch(p -> p.source == screen) ? root : screen;
    }
}
