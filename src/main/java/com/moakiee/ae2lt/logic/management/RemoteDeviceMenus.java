package com.moakiee.ae2lt.logic.management;

import appeng.blockentity.storage.IOPortBlockEntity;
import appeng.menu.implementations.IOPortMenu;
import com.moakiee.ae2lt.blockentity.OverloadedIOPortBlockEntity;
import com.moakiee.ae2lt.menu.OverloadedIOPortMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** Native menus whose hosts do not implement AE2's submenu-return interface. */
final class RemoteDeviceMenus {
    private RemoteDeviceMenus() {}

    @Nullable
    static MenuType<?> type(Object owner) {
        if (owner instanceof IOPortBlockEntity) return IOPortMenu.TYPE;
        if (owner instanceof OverloadedIOPortBlockEntity) return OverloadedIOPortMenu.TYPE;
        return ModList.get().isLoaded("extendedae") ? ExtendedAe.type(owner) : null;
    }

    /** Keep optional ExtendedAE types out of the enclosing class's linkage. */
    private static final class ExtendedAe {
        @Nullable
        static MenuType<?> type(Object owner) {
            return owner instanceof com.glodblock.github.extendedae.common.tileentities.TileWirelessConnector
                    ? com.glodblock.github.extendedae.container.ContainerWirelessConnector.TYPE : null;
        }
    }
}
