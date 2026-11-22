package com.moakiee.ae2lt.client.machine;

import com.moakiee.ae2lt.client.ldlib.LdlibAeScreen;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Icons;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.moakiee.ae2lt.client.frequency.FrequencyBindingClient;
import com.moakiee.ae2lt.client.widgets.TextureToggleButton;
import com.moakiee.ae2lt.client.ldlib.LdlibSettingButton;
import com.moakiee.ae2lt.client.widgets.PageButton;
import com.moakiee.ae2lt.client.widgets.PageInput;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.menu.OverloadedInterfaceMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.client.gui.Icon;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import com.moakiee.ae2lt.client.ldlib.LdlibToolboxPanel;
import com.moakiee.ae2lt.client.ldlib.LdlibUpgradesPanel;
import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantics;


public class OverloadedInterfaceScreen extends LdlibAeScreen<OverloadedInterfaceMenu> {

    private static final int AMT_BTN_SIZE = 16;
    private static final int SLOTS_PER_PAGE = 18;
    private static final int COLS = 9;
    private static final int SLOT_SPACING = 18;
    private static final int AMT_ROW1_Y = 35;
    private static final int AMT_ROW2_Y = 95;
    private static final int AMT_START_X = 8;
    private static final int PAGE_INDICATOR_Y = 18;

    private final LdlibSettingButton fuzzyMode;
    private final TextureToggleButton modeButton;
    private final TextureToggleButton exportModeButton;
    private final TextureToggleButton importModeButton;
    private final TextureToggleButton speedButton;
    private final PageButton pageButton;
    private final List<SetAmountButton> amountButtons = new ArrayList<>();
    private final List<Slot> configSlots;
    private final List<Label> unlimitedLabels = new ArrayList<>();
    private ModularUI foreground;
    private Label pageIndicator;

    private int lastKnownPage = -1;

    public OverloadedInterfaceScreen(OverloadedInterfaceMenu menu, Inventory playerInventory,
                                     Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        var filterSlot = menu.getFilterSlot();
        var upgradeSlots = menu.getSlots(SlotSemantics.UPGRADE).stream()
                .filter(slot -> slot != filterSlot)
                .collect(Collectors.toList());
        widgets.add("upgrades", LdlibUpgradesPanel.forMachine(upgradeSlots,
                () -> menu.getUpgrades().getUpgradableItem()));
        if (filterSlot != null) {
            widgets.add("overloadedFilter", new LdlibUpgradesPanel(List.of(filterSlot), List::of));
        }
        if (menu.getToolbox().isPresent()) {
            this.widgets.add("toolbox", new LdlibToolboxPanel(style, menu.getToolbox().getName()));
        }

        addToLeftToolbar(FrequencyBindingClient.createToolbarButton(menu));

        this.fuzzyMode = LdlibSettingButton.fuzzy(menu::getFuzzyMode, this::isHandlingRightClick);
        addToLeftToolbar(this.fuzzyMode);

        this.pageButton = new PageButton(
                this::isHandlingRightClick, menu::clientPrevPage, menu::clientNextPage);
        addToLeftToolbar(this.pageButton);

        this.modeButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.MODE, btn -> menu.cycleInterfaceMode());
        this.modeButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.interface_mode.wireless")));
        this.modeButton.setTooltipOff(List.of(
                Component.translatable("ae2lt.gui.interface_mode.normal"),
                Component.translatable("ae2lt.gui.interface_mode.normal_hint"),
                Component.translatable("ae2lt.gui.interface_mode.normal_wrench")));
        addToLeftToolbar(this.modeButton);

        this.exportModeButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.AUTO_EXPORT, btn -> menu.cycleExportMode());
        this.exportModeButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.export_mode.auto")));
        this.exportModeButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.export_mode.off")));
        addToLeftToolbar(this.exportModeButton);

        this.importModeButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.AUTO_IMPORT, btn -> menu.cycleImportMode());
        addToLeftToolbar(this.importModeButton);

        this.speedButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.SPEED, btn -> menu.cycleIOSpeed());
        this.speedButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.io_speed.fast")));
        this.speedButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.io_speed.normal")));
        addToLeftToolbar(this.speedButton);

        LdlibAe2Button.addOpenPriority(widgets);

        this.configSlots = menu.getAllConfigSlots();
        for (int i = 0; i < configSlots.size(); i++) {
            final int slotIdx = i;
            var button = new SetAmountButton(() -> {
                if (hasShiftDown()) {
                    menu.toggleUnlimited(configSlots.get(slotIdx).getContainerSlot());
                } else {
                    menu.openSetAmountMenu(configSlots.get(slotIdx).getContainerSlot());
                }
            });
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("ae2lt.gui.set_amount.tooltip")));
            amountButtons.add(button);
        }
    }

    private void releaseForeground() {
        var previous = foreground;
        foreground = null;
        if (previous != null) {
            previous.onRemoved();
        }
    }

    @Override
    public void removed() {
        try {
            releaseForeground();
        } finally {
            super.removed();
        }
    }

    @Override
    protected void init() {
        releaseForeground();
        super.init();

        for (var btn : amountButtons) {
            addRenderableWidget(btn);
        }
        var root = new UIElement();
        pageIndicator = LdlibScreens.pageIndicator(root, imageWidth, PAGE_INDICATOR_Y,
                style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB());
        unlimitedLabels.clear();
        for (var slot : configSlots) {
            var label = LdlibScreens.label(Component.literal("∞"), 0xFF00FF00);
            label.setAllowHitTest(false);
            label.textStyle(style -> style.textShadow(true));
            root.addChild(LdlibScreens.at(label, slot.x + 10, slot.y - 10, font.width("∞") + 1, 9));
            unlimitedLabels.add(label);
        }
        foreground = LdlibScreens.localLayer(root, imageWidth, imageHeight);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.fuzzyMode.setVisibility(menu.hasUpgrade(AEItems.FUZZY_CARD));

        this.modeButton.setState(menu.interfaceMode == 1);
        this.exportModeButton.setState(menu.exportMode == OverloadedInterfaceBlockEntity.ExportMode.AUTO.ordinal());
        this.speedButton.setState(menu.ioSpeedMode == 1);

        var impModes = OverloadedInterfaceBlockEntity.ImportMode.values();
        int importModeIndex = Math.max(0, Math.min(menu.importMode, impModes.length - 1));
        importModeButton.setTooltipAt(impModes[0].ordinal(),
                List.of(Component.translatable("ae2lt.gui.import_mode.off")));
        importModeButton.setTooltipAt(impModes[1].ordinal(),
                List.of(Component.translatable("ae2lt.gui.import_mode.auto")));
        importModeButton.setTooltipAt(impModes[2].ordinal(),
                List.of(Component.translatable("ae2lt.gui.import_mode.eject")));
        importModeButton.setStateIndex(importModeIndex);

        if (menu.currentPage != lastKnownPage) {
            lastKnownPage = menu.currentPage;
            menu.showPage(menu.currentPage);
        }

        int page = menu.currentPage;
        int start = page * SLOTS_PER_PAGE;
        int end = Math.min(start + SLOTS_PER_PAGE, configSlots.size());

        for (int i = 0; i < amountButtons.size(); i++) {
            var button = amountButtons.get(i);
            if (i >= start && i < end) {
                int inPage = i - start;
                int col = inPage % COLS;
                int row = inPage / COLS;
                button.setPosition(
                        this.leftPos + AMT_START_X + col * SLOT_SPACING,
                        this.topPos + (row == 0 ? AMT_ROW1_Y : AMT_ROW2_Y));
                var item = configSlots.get(i).getItem();
                button.visible = !item.isEmpty();
            } else {
                button.visible = false;
            }
            unlimitedLabels.get(i).setVisible(button.visible && menu.isSlotUnlimited(i));
        }

        this.pageButton.setPage(page, menu.totalPages);
        pageIndicator.setVisible(menu.totalPages > 1);
        if (menu.totalPages > 1) pageIndicator.setText(Component.literal((page + 1) + "/" + menu.totalPages));
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY,
                        int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);
        foreground.getWidget().render(guiGraphics, mouseX, mouseY, 0);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.totalPages > 1
                && PageInput.handleScroll(scrollY, menu::clientPrevPage, menu::clientNextPage)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static final class SetAmountButton extends LdlibAe2Button {
        SetAmountButton(Runnable action) {
            super(AMT_BTN_SIZE, Component.translatable("ae2lt.gui.set_amount.message"), action);
            var focused = LdlibAe2Icons.texture(Icon.COG);
            var normal = LdlibAe2Icons.texture(Icon.COG_DISABLED);
            toolbarGlyphOnly(IGuiTexture.dynamic(() -> isHoveredOrFocused() ? focused : normal));
        }
    }
}
