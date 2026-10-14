package com.moakiee.ae2lt.integration.mest;

import com.moakiee.ae2lt.menu.extension.TianshuMenuContainer;

public interface MestTianshuAccess extends TianshuMenuContainer {
    MestTianshuSession ae2lt$tianshuSession();
    @Override default boolean tianshuAccessValid() {
        var outer = (com.lhy.mest.terminal.MESTMenu) this;
        return outer.isClientSide() || outer.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player
                && outer.canUseTerminal(player);
    }
    @Override default com.moakiee.ae2lt.menu.TianshuCraftingTermMenu tianshuCraftingMenu() {
        return ae2lt$tianshuSession().crafting();
    }
    @Override default com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu tianshuPatternMenu() {
        return ae2lt$tianshuSession().pattern();
    }
}
