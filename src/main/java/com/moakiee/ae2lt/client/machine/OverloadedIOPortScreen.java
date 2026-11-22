package com.moakiee.ae2lt.client.machine;

import java.util.List;
import java.util.Locale;

import appeng.client.gui.style.ScreenStyle;
import appeng.menu.SlotSemantics;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.moakiee.ae2lt.client.ldlib.BackgroundLayer;
import com.moakiee.ae2lt.client.ldlib.LdlibAeScreen;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;
import com.moakiee.ae2lt.client.ldlib.LdlibSettingButton;
import com.moakiee.ae2lt.client.ldlib.LdlibToolboxPanel;
import com.moakiee.ae2lt.client.ldlib.LdlibUpgradesPanel;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.moakiee.ae2lt.client.ldlib.MachineGauges;
import com.moakiee.ae2lt.client.gui.LightningStatusLines;
import com.moakiee.ae2lt.menu.OverloadedIOPortMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class OverloadedIOPortScreen extends LdlibAeScreen<OverloadedIOPortMenu> {
    private final BackgroundLayer decorations;
    private final LdlibSettingButton redstone;
    public OverloadedIOPortScreen(OverloadedIOPortMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        widgets.add("upgrades", LdlibUpgradesPanel.forMachine(menu.getSlots(SlotSemantics.UPGRADE),
                () -> menu.getUpgrades().getUpgradableItem()));
        if (menu.getToolbox().isPresent()) {
            widgets.add("toolbox", new LdlibToolboxPanel(style, menu.getToolbox().getName()));
        }
        var frame = LdlibStyleSprite.from(style.getImage("filterSlotFrame"));
        var filterSlotFrame = frame.full();
        decorations = new BackgroundLayer(imageWidth, imageHeight)
                .add(filterSlotFrame, 65, 52, frame.width(), frame.height())
                .add(filterSlotFrame, 91, 52, frame.width(), frame.height())
                .add(new ItemStackTexture(AEItems.ITEM_CELL_1K.stack()), 58, 17, 16, 16)
                .add(new ItemStackTexture(AEBlocks.DRIVE.stack()), 102, 17, 16, 16);
        addToLeftToolbar(LdlibSettingButton.fullness(() -> menu.fullness, this::isHandlingRightClick));
        redstone = LdlibSettingButton.redstone(menu::getRedStoneMode, this::isHandlingRightClick);
        addToLeftToolbar(redstone);
        widgets.add("operationMode",
                LdlibSettingButton.transferDirection(() -> menu.operation, this::isHandlingRightClick));
        widgets.add("lightningStatus", MachineGauges.statusIcon(() -> List.of(
                LightningStatusLines.title(),
                Component.translatable("ae2lt.gui.status.label", Component.translatable(
                        "ae2lt.gui.overloaded_io_port.status." + menu.status.name().toLowerCase(Locale.ROOT))),
                Component.translatable("ae2lt.gui.overloaded_io_port.cap", String.format("%,d", menu.transferCap)))));
    }
    @Override protected void init() {
        decorations.onRemoved();
        super.init();
    }
    @Override public void removed() {
        try {
            decorations.onRemoved();
        } finally {
            super.removed();
        }
    }
    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        redstone.setVisibility(menu.hasUpgrade(AEItems.REDSTONE_CARD));
    }
    @Override public void drawBG(GuiGraphics graphics, int x, int y, int mx, int my, float partial) {
        super.drawBG(graphics, x, y, mx, my, partial);
        decorations.render(graphics, x, y, mx, my, partial);
    }
}
