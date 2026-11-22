package com.moakiee.ae2lt.client.provider;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.moakiee.ae2lt.client.ldlib.BackgroundLayer;
import com.moakiee.ae2lt.client.ldlib.LdlibToolbarHost;
import com.moakiee.ae2lt.client.frequency.FrequencyBindingClient;
import com.moakiee.ae2lt.client.widgets.TextureToggleButton;
import com.moakiee.ae2lt.client.widgets.PageButton;
import com.moakiee.ae2lt.client.widgets.PageInput;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.Icon;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import appeng.menu.SlotSemantics;

import com.moakiee.ae2lt.api.client.PatternProviderToolbarButtonHider;
import com.moakiee.ae2lt.api.patternprovider.PatternSlotPages;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import com.moakiee.ae2lt.mixin.client.AEBaseScreenAccessor;
import com.moakiee.ae2lt.mixin.client.PatternProviderScreenAccessor;
import com.moakiee.ae2lt.mixin.client.VerticalButtonBarAccessor;

public class OverloadedPatternProviderScreen<M extends OverloadedPatternProviderMenu>
        extends PatternProviderScreen<M> implements LdlibToolbarHost {

    private static final List<Component> RETURN_TIP_OFF =
            List.of(Component.translatable("ae2lt.gui.return_mode.off"));
    private static final List<Component> RETURN_TIP_AUTO =
            List.of(Component.translatable("ae2lt.gui.return_mode.auto"));
    private static final List<Component> RETURN_TIP_EJECT =
            List.of(Component.translatable("ae2lt.gui.return_mode.eject"));
    private static final List<Component> ADAPTIVE_BATCH_TIP_ON =
            List.of(Component.translatable("ae2lt.gui.adaptive_batch.on"));
    private static final List<Component> ADAPTIVE_BATCH_TIP_OFF =
            List.of(Component.translatable("ae2lt.gui.adaptive_batch.off"));

    private final TextureToggleButton modeButton;
    private final TextureToggleButton autoReturnButton;
    private final ProviderBlockingModeButton blockingModeButton;
    private final TextureToggleButton adaptiveBatchButton;
    private final LdlibAe2Button advancedSettingsButton;
    private final PageButton pageButton;
    private final @Nullable BackgroundLayer background;

    public OverloadedPatternProviderScreen(M menu, Inventory playerInventory,
                                           Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        background = BackgroundLayer.fromStyle(style);
        LdlibAe2Button.addOpenPriority(widgets);

        removeHiddenToolbarButtons();
        removeVanillaBlockingModeButton();

        this.blockingModeButton = new ProviderBlockingModeButton(menu::clientCycleBlockingMode);
        addToLeftToolbar(this.blockingModeButton);

        addToLeftToolbar(FrequencyBindingClient.createToolbarButton(menu));

        this.pageButton = new PageButton(
                this::isHandlingRightClick, menu::clientPrevPage, menu::clientNextPage);
        addToLeftToolbar(this.pageButton);

        this.autoReturnButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.AUTO_RETURN,
                btn -> menu.clientToggleAutoReturn());
        addToLeftToolbar(this.autoReturnButton);

        this.modeButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.MODE,
                btn -> menu.clientToggleMode());
        this.modeButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.provider_mode.wireless")));
        this.modeButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.provider_mode.normal")));
        addToLeftToolbar(this.modeButton);

        this.adaptiveBatchButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.ADAPTIVE_BATCH,
                state -> menu.clientToggleAdaptiveBatch());
        this.adaptiveBatchButton.setTooltipOn(ADAPTIVE_BATCH_TIP_ON);
        this.adaptiveBatchButton.setTooltipOff(ADAPTIVE_BATCH_TIP_OFF);
        addToLeftToolbar(this.adaptiveBatchButton);

        this.advancedSettingsButton = LdlibAe2Button.toolbar(
                Icon.COG, Component.translatable("ae2lt.gui.provider_advanced.open"),
                () -> {
                    var next = new OverloadedPatternProviderAdvancedScreen<>(this);
                    releaseProviderLayers();
                    switchToScreen(next);
                });
        addToLeftToolbar(this.advancedSettingsButton);
    }

    private void releaseProviderLayers() {
        var previous = foreground;
        foreground = null;
        try {
            if (previous != null) {
                previous.onRemoved();
            }
        } finally {
            if (background != null) {
                background.onRemoved();
            }
        }
    }

    @Override
    public void removed() {
        try {
            releaseProviderLayers();
        } finally {
            super.removed();
        }
    }

    @Override
    protected void init() {
        releaseProviderLayers();
        super.init();

        PatternSlotPages.alignSlots(menu.getSlots(SlotSemantics.ENCODED_PATTERN));
        var root = new UIElement();
        pageIndicator = LdlibScreens.pageIndicator(root, imageWidth, 33,
                style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB());
        foreground = LdlibScreens.localLayer(root, imageWidth, imageHeight);
    }

    private ModularUI foreground;
    private Label pageIndicator;

    @Override
    public void drawBG(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float ticks) {
        if (background == null) {
            super.drawBG(graphics, x, y, mouseX, mouseY, ticks);
        } else {
            background.render(graphics, x, y, mouseX, mouseY, ticks);
        }
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);
        foreground.getWidget().render(guiGraphics, mouseX - leftPos, mouseY - topPos, 0);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        this.menu.showPage(this.menu.getCurrentPage());

        setTextContent("dialog_title", Component.translatable(this.menu.getTitleTranslationKey()));
        this.blockingModeButton.setState(this.menu.getBlockingModeOrdinal());
        this.blockingModeButton.setVisibility(this.menu.isBlockingModeVisible());

        this.modeButton.setState(this.menu.isWirelessMode());
        this.modeButton.setVisibility(this.menu.isModeSwitchVisible());

        this.autoReturnButton.setTooltipAt(ReturnMode.OFF.ordinal(), RETURN_TIP_OFF);
        this.autoReturnButton.setTooltipAt(ReturnMode.AUTO.ordinal(), RETURN_TIP_AUTO);
        this.autoReturnButton.setTooltipAt(ReturnMode.EJECT.ordinal(), RETURN_TIP_EJECT);
        this.autoReturnButton.setStateIndex(this.menu.getReturnModeOrdinal());

        this.adaptiveBatchButton.setState(this.menu.isAdaptiveBatchEnabled());
        this.advancedSettingsButton.setVisibility(true);

        this.pageButton.setPage(this.menu.getCurrentPage(), this.menu.getTotalPages());
        int total = menu.getTotalPages();
        pageIndicator.setVisible(total > 1);
        if (total > 1) pageIndicator.setText(Component.literal((menu.getCurrentPage() + 1) + "/" + total));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.menu.getTotalPages() > 1
                && PageInput.handleScroll(
                        scrollY, this.menu::clientPrevPage, this.menu::clientNextPage)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void removeVanillaBlockingModeButton() {
        var toolbar = ((AEBaseScreenAccessor) this).ae2lt$getVerticalToolbar();
        var buttons = ((VerticalButtonBarAccessor) toolbar).ae2lt$getButtons();
        buttons.remove(((PatternProviderScreenAccessor) this).ae2lt$getBlockingModeButton());
    }

    private void removeHiddenToolbarButtons() {
        var toolbar = ((AEBaseScreenAccessor) this).ae2lt$getVerticalToolbar();
        var buttons = ((VerticalButtonBarAccessor) toolbar).ae2lt$getButtons();
        PatternProviderToolbarButtonHider.removeHiddenToolbarButtons(buttons);
    }

}
