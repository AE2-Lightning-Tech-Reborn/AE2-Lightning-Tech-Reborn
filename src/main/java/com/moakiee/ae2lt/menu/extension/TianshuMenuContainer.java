package com.moakiee.ae2lt.menu.extension;

import appeng.menu.me.common.MEStorageMenu;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import com.moakiee.ae2lt.menu.TianshuMaintenanceMenu;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

/** A single network container can own multiple native Tianshu menu controllers. */
public interface TianshuMenuContainer {
    default boolean tianshuAccessValid() { return true; }
    TianshuCraftingTermMenu tianshuCraftingMenu();
    TianshuPatternEncodingTermMenu tianshuPatternMenu();

    @Nullable
    static TianshuPatternEncodingTermMenu pattern(AbstractContainerMenu menu) {
        if (menu instanceof TianshuMenuContainer container && !container.tianshuAccessValid()) return null;
        return menu instanceof TianshuPatternEncodingTermMenu pattern ? pattern
                : menu instanceof TianshuMenuContainer container ? container.tianshuPatternMenu() : null;
    }

    @Nullable
    static TianshuMaintenanceMenu maintenance(AbstractContainerMenu menu) {
        if (menu instanceof TianshuMenuContainer container && !container.tianshuAccessValid()) return null;
        return menu instanceof TianshuMaintenanceMenu maintenance ? maintenance
                : menu instanceof TianshuMenuContainer container ? container.tianshuCraftingMenu() : null;
    }

    @Nullable
    default MEStorageMenu tianshuMenu(String module) {
        return switch (module) {
            case "crafting" -> tianshuCraftingMenu();
            case "pattern" -> tianshuPatternMenu();
            default -> null;
        };
    }
}
