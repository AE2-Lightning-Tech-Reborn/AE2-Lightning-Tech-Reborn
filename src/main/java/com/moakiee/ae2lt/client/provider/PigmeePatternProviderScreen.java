package com.moakiee.ae2lt.client.provider;

import com.moakiee.ae2lt.client.ldlib.LdlibAeScreen;
import appeng.client.gui.style.ScreenStyle;
import com.moakiee.ae2lt.menu.PigmeePatternProviderMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class PigmeePatternProviderScreen extends LdlibAeScreen<PigmeePatternProviderMenu> {
    public PigmeePatternProviderScreen(
            PigmeePatternProviderMenu menu,
            Inventory playerInventory,
            Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected boolean shouldAddToolbar() {
        return false;
    }
}
