package com.moakiee.ae2lt.integration.mest;

import appeng.menu.me.common.MEStorageMenu;
import com.lhy.mest.terminal.MESTMenu;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.menu.TianshuMaintenanceSession;
import com.moakiee.ae2lt.menu.extension.EmbeddedTerminalMenu;
import net.minecraft.world.item.ItemStack;

/** Native controllers without another open container or another copy of the cursor stack. */
final class MestTianshuMenus {
    private MestTianshuMenus() {}

    static final class Crafting extends TianshuCraftingTermMenu implements EmbeddedTerminalMenu {
        private final MESTMenu outer;
        Crafting(MESTMenu outer) {
            super(outer.containerId, outer.getPlayerInventory(), new MestTianshuHosts.Crafting(outer.getMestHost()));
            this.outer = outer;
            setLocator(outer.getMestHost().getLocator());
        }
        @Override public MEStorageMenu outerMenu() { return outer; }
        @Override public String moduleName() { return "crafting"; }
        @Override public ItemStack getCarried() { return outer == null ? super.getCarried() : outer.getCarried(); }
        @Override public void setCarried(ItemStack stack) {
            if (outer == null) super.setCarried(stack); else outer.setCarried(stack);
        }
        @Override public boolean isValidMenu() { return outer == null ? super.isValidMenu() : outer.isValidMenu(); }
    }

    static final class Pattern extends TianshuPatternEncodingTermMenu implements EmbeddedTerminalMenu {
        private final MESTMenu outer;
        private final Crafting crafting;
        Pattern(MESTMenu outer, Crafting crafting) {
            super(outer.containerId, outer.getPlayerInventory(), new MestTianshuHosts.Pattern(outer.getMestHost()));
            this.outer = outer;
            this.crafting = crafting;
            setLocator(outer.getMestHost().getLocator());
        }
        @Override public MEStorageMenu outerMenu() { return outer; }
        @Override public String moduleName() { return "pattern"; }
        @Override public ItemStack getCarried() { return outer == null ? super.getCarried() : outer.getCarried(); }
        @Override public void setCarried(ItemStack stack) {
            if (outer == null) super.setCarried(stack); else outer.setCarried(stack);
        }
        @Override public boolean isValidMenu() { return outer == null ? super.isValidMenu() : outer.isValidMenu(); }
        @Override public java.math.BigInteger getBigStock(appeng.api.stacks.AEKey key) {
            return crafting == null ? super.getBigStock(key) : crafting.getBigStock(key);
        }
        @Override public TianshuMaintenanceSession getMaintenanceSession() {
            return crafting == null ? super.getMaintenanceSession() : crafting.getMaintenanceSession();
        }
        @Override public int getTianshuSelectionRevision() {
            return crafting == null ? super.getTianshuSelectionRevision() : crafting.getTianshuSelectionRevision();
        }
    }
}
