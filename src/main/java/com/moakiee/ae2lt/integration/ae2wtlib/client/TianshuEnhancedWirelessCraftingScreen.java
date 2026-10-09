package com.moakiee.ae2lt.integration.ae2wtlib.client;

import appeng.client.gui.style.ScreenStyle;
import com.moakiee.ae2lt.client.tianshu.TianshuWirelessCraftingTermScreen;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuEnhancedWirelessCraftingMenu;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuEnhancedWirelessCraftingMenu.WirelessPage;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuWorkPage;
import com.moakiee.ae2lt.menu.TianshuWirelessCraftingTermMenu;
import de.mari_023.ae2wtlib.TextConstants;
import de.mari_023.ae2wtlib.AE2wtlibSlotSemantics;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import de.mari_023.ae2wtlib.terminal.ArmorSlot;
import de.mari_023.ae2wtlib.wct.PlayerEntityWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Uses WCT's original crafting layout; optional classes load only with the full WT implementation. */
public final class TianshuEnhancedWirelessCraftingScreen extends TianshuWirelessCraftingTermScreen<TianshuWirelessCraftingTermMenu> {
    private final IconButton settingsButton, magnetButton, trashButton;
    private final PlayerEntityWidget playerPreview;

    public TianshuEnhancedWirelessCraftingScreen(TianshuWirelessCraftingTermMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        settingsButton = new IconButton(button -> switchToScreen(new TianshuWirelessToolsScreen(this, WirelessPage.SETTINGS))) { @Override protected Icon getIcon() { return Icon.WRENCH; } };
        settingsButton.setMessage(TextConstants.TERMINAL_SETTINGS);
        widgets.add("wirelessTerminalSettingsButton", settingsButton);
        magnetButton = new IconButton(button -> switchToScreen(new TianshuMagnetScreen(this))) { @Override protected Icon getIcon() { return Icon.ARROW_UP; } };
        magnetButton.setMessage(TextConstants.MAGNET_FILTER);
        widgets.add("magnetCardMenuButton", magnetButton);
        trashButton = new IconButton(button -> switchToScreen(new TianshuWirelessToolsScreen(this, WirelessPage.TRASH))) { @Override protected Icon getIcon() { return Icon.BACKGROUND_TRASH; } };
        trashButton.setMessage(TextConstants.TRASH);
        widgets.add("trashButton", trashButton);
        playerPreview = new PlayerEntityWidget(menu.getPlayer());
        widgets.add("player", playerPreview);
    }

    public TianshuEnhancedWirelessCraftingMenu features() { return (TianshuEnhancedWirelessCraftingMenu) menu; }

    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        settingsButton.visible = true;
        magnetButton.visible = features().hasMagnetCard();
        trashButton.visible = true;
        playerPreview.visible = true;
        for (var semantic : TianshuEnhancedWirelessCraftingMenu.ARMOR_SLOTS) setSlotsHidden(semantic, false);
        setSlotsHidden(AE2wtlibSlotSemantics.PICKUP_CONFIG, true);
        setSlotsHidden(AE2wtlibSlotSemantics.INSERT_CONFIG, true);
        setSlotsHidden(AE2wtlibSlotSemantics.TRASH, true);
    }

    @Override protected void drawWorkAreaBackground(GuiGraphics graphics, int offsetX, int offsetY) {
        // The player, armor, offhand and WT buttons remain unchanged on every work page.
        drawWorkAreaFrame(graphics, offsetX + 89, offsetY + imageHeight - 170, 99, 72);
    }

    @Override public void renderSlot(GuiGraphics graphics, Slot slot) {
        super.renderSlot(graphics, slot);
    }
}
