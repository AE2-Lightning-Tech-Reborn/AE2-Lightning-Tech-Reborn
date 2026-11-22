package com.moakiee.ae2lt.integration.ae2wtlib.client;

import java.util.HashMap;
import java.util.Map;

import appeng.client.gui.style.ScreenStyle;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Widget;
import com.moakiee.ae2lt.client.tianshu.TianshuWirelessCraftingTermScreen;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuEnhancedWirelessCraftingMenu;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuEnhancedWirelessCraftingMenu.WirelessPage;
import com.moakiee.ae2lt.menu.TianshuWirelessCraftingTermMenu;
import de.mari_023.ae2wtlib.api.TextConstants;
import de.mari_023.ae2wtlib.api.gui.AE2wtlibSlotSemantics;
import de.mari_023.ae2wtlib.api.gui.Icon;
import de.mari_023.ae2wtlib.wct.ArmorSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Uses WCT's original crafting layout; optional classes load only with the full WT implementation. */
public final class TianshuEnhancedWirelessCraftingScreen extends TianshuWirelessCraftingTermScreen<TianshuWirelessCraftingTermMenu> {
    private static final int PLAYER_PREVIEW_WIDTH = 46;
    private static final int PLAYER_PREVIEW_HEIGHT = 70;
    private final Map<Icon, SpriteTexture> armorIcons = new HashMap<>();
    private final LdlibAe2Button magnetButton;

    public TianshuEnhancedWirelessCraftingScreen(TianshuWirelessCraftingTermMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        widgets.add("wirelessTerminalSettingsButton",
                new WirelessIconButton(Icon.TERMINAL_SETTINGS, TextConstants.TERMINAL_SETTINGS,
                        () -> switchToScreen(new TianshuWirelessToolsScreen(this, WirelessPage.SETTINGS))));
        magnetButton = new WirelessIconButton(Icon.MAGNET, TextConstants.MAGNET_FILTER,
                () -> switchToScreen(new TianshuMagnetScreen(this)));
        widgets.add("magnetCardMenuButton", magnetButton);
        widgets.add("trashButton", new WirelessIconButton(Icon.TRASH, TextConstants.TRASH,
                () -> switchToScreen(new TianshuWirelessToolsScreen(this, WirelessPage.TRASH))));
        widgets.add("player", playerPreview(menu.getPlayer()));
    }

    private static LdlibAe2Widget playerPreview(LivingEntity player) {
        var preview = new UIElement();
        preview.setAllowHitTest(false);
        preview.style(style -> style.background((graphics, mouseX, mouseY, x, y, width, height, ticks) ->
                InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, (int) x, (int) y,
                        (int) x + PLAYER_PREVIEW_WIDTH, (int) y + PLAYER_PREVIEW_HEIGHT,
                        30, 0.0625F, mouseX, mouseY, player)));
        return new LdlibAe2Widget(PLAYER_PREVIEW_WIDTH, PLAYER_PREVIEW_HEIGHT, preview);
    }

    public TianshuEnhancedWirelessCraftingMenu features() { return (TianshuEnhancedWirelessCraftingMenu) menu; }

    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        magnetButton.setVisibility(features().hasMagnetCard());
        for (var semantic : TianshuEnhancedWirelessCraftingMenu.ARMOR_SLOTS) setSlotsHidden(semantic, false);
        setSlotsHidden(AE2wtlibSlotSemantics.PICKUP_CONFIG, true);
        setSlotsHidden(AE2wtlibSlotSemantics.INSERT_CONFIG, true);
        setSlotsHidden(AE2wtlibSlotSemantics.TRASH, true);
    }

    @Override protected Rect2i workAreaBounds() {
        return new Rect2i(89, imageHeight - 170, 99, 72);
    }

    @Override
    public void renderSlot(GuiGraphics graphics, Slot slot) {
        if (slot instanceof ArmorSlot armor && !armor.hasItem() && armor.isSlotEnabled()) {
            var icon = armor.icon();
            var hint = armorIcons.computeIfAbsent(icon, WirelessIconButton::sprite);
            int alpha = (int) (armor.getOpacityOfIcon() * 255);
            hint.setColor(alpha << 24 | 0xFFFFFF).draw(graphics, 0, 0,
                    slot.x, slot.y, icon.width(), icon.height(), 0);
            graphics.flush();
        }
        super.renderSlot(graphics, slot);
    }
}
