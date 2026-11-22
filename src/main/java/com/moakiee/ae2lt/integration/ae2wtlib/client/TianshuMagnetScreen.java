package com.moakiee.ae2lt.integration.ae2wtlib.client;


import appeng.api.config.IncludeExclude;
import com.moakiee.ae2lt.client.ldlib.LdlibAeScreen;
import appeng.client.gui.style.StyleManager;
import appeng.menu.SlotSemantics;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuEnhancedWirelessCraftingMenu;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuEnhancedWirelessCraftingMenu.WirelessPage;
import de.mari_023.ae2wtlib.api.TextConstants;
import de.mari_023.ae2wtlib.api.gui.AE2wtlibSlotSemantics;
import de.mari_023.ae2wtlib.api.gui.Icon;
import de.mari_023.ae2wtlib.wct.magnet_card.MagnetMenu;
import net.minecraft.network.chat.Component;

/** WT magnet filters hosted in the current wireless terminal session. */
public final class TianshuMagnetScreen extends LdlibAeScreen<MagnetMenu> {
    private final TianshuEnhancedWirelessCraftingScreen parent;
    private final TianshuEnhancedWirelessCraftingMenu features;

    TianshuMagnetScreen(TianshuEnhancedWirelessCraftingScreen parent) {
        super(parent.features().getMagnetMenu().asClientView(), parent.getMenu().getPlayerInventory(),
                parent.getTitle(), StyleManager.loadStyleDoc("/screens/wtlib/magnet.json"));
        this.parent = parent;
        features = parent.features();
        features.setWirelessPage(WirelessPage.MAGNET);
        widgets.add("back", LdlibAe2Button.back(menu.getHost().getMainMenuIcon().getHoverName(),
                this::returnToTerminal));
        var host = menu.getMagnetHost();
        if (host != null) {
            widgets.add("pickup_mode", new WirelessIconButton(() -> modeIcon(host.getPickupMode()),
                    () -> TextConstants.getPickupMode(host.getPickupMode()), menu::togglePickupMode));
            widgets.add("insert_mode", new WirelessIconButton(() -> modeIcon(host.getInsertMode()),
                    () -> TextConstants.getInsertMode(host.getInsertMode()), menu::toggleInsertMode));
            widgets.add("copy_up", new WirelessIconButton(() -> Icon.UP, () -> TextConstants.COPY_PICKUP, menu::copyUp));
            widgets.add("copy_down", new WirelessIconButton(() -> Icon.DOWN, () -> TextConstants.COPY_INSERT, menu::copyDown));
            widgets.add("switch", new WirelessIconButton(() -> Icon.SWITCH, () -> TextConstants.SWITCH,
                    menu::switchInsertPickup));
        }
    }

    private static Icon modeIcon(IncludeExclude mode) {
        return mode == IncludeExclude.WHITELIST ? Icon.YES : Icon.NO;
    }

    private void returnToTerminal() {
        features.setWirelessPage(WirelessPage.MAIN);
        switchToScreen(parent);
    }

    @Override public void onClose() { returnToTerminal(); }
    @Override protected boolean shouldAddToolbar() { return false; }
    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        if (features.wirelessPage != WirelessPage.MAGNET) {
            switchToScreen(parent);
            return;
        }
        for (var slot : menu.slots) {
            var semantic = menu.getSlotSemantic(slot);
            if (semantic != null) setSlotsHidden(semantic,
                    semantic != SlotSemantics.PLAYER_INVENTORY && semantic != SlotSemantics.PLAYER_HOTBAR
                            && semantic != AE2wtlibSlotSemantics.PICKUP_CONFIG && semantic != AE2wtlibSlotSemantics.INSERT_CONFIG);
        }
    }
}
