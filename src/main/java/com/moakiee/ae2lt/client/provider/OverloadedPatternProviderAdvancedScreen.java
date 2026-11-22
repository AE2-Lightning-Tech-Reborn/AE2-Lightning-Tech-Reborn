package com.moakiee.ae2lt.client.provider;

import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.moakiee.ae2lt.client.widgets.TextureToggleButton;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import com.moakiee.ae2lt.client.ldlib.LdlibAeSubScreen;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2TextField;
import appeng.menu.SlotSemantics;
import appeng.client.gui.style.PaletteColor;

import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;

public final class OverloadedPatternProviderAdvancedScreen<M extends OverloadedPatternProviderMenu>
        extends LdlibAeSubScreen<M, OverloadedPatternProviderScreen<M>> {
    private static final int LABEL_X = 38;
    private static final int STRATEGY_Y = 33;
    private static final int SPEED_Y = 57;
    private static final int FILTER_Y = 81;

    private final TextureToggleButton wirelessStrategyButton;
    private final TextureToggleButton wirelessSpeedButton;
    private final TextureToggleButton filteredImportButton;
    private final LdlibAe2Button machineAdaptationButton;
    private final LdlibAe2TextField machineParallelism;
    private final LdlibAe2Button applyParallelism;
    private int lastSyncedParallelism;
    private ModularUI foreground;
    private List<Label> wirelessLabels;
    private Label filterLabel;
    private Label parallelismHint;
    private Label wirelessHint;

    public OverloadedPatternProviderAdvancedScreen(OverloadedPatternProviderScreen<M> parent) {
        super(parent, "/screens/overloaded_pattern_provider_advanced.json");

        var backLabel = Component.translatable(parent.getMenu().getTitleTranslationKey());
        widgets.add("return", LdlibAe2Button.back(backLabel, this::returnToParent));

        this.wirelessStrategyButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.WIRELESS_STRATEGY,
                state -> menu.clientToggleWirelessDispatchMode());
        this.wirelessStrategyButton.setTooltipOn(
                List.of(Component.translatable("ae2lt.gui.wireless_strategy.even")));
        this.wirelessStrategyButton.setTooltipOff(
                List.of(Component.translatable("ae2lt.gui.wireless_strategy.single")));
        widgets.add("wirelessStrategy", this.wirelessStrategyButton);

        this.wirelessSpeedButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.SPEED,
                state -> menu.clientToggleWirelessSpeedMode());
        this.wirelessSpeedButton.setTooltipOn(
                List.of(Component.translatable("ae2lt.gui.wireless_speed.fast")));
        this.wirelessSpeedButton.setTooltipOff(
                List.of(Component.translatable("ae2lt.gui.wireless_speed.normal")));
        widgets.add("wirelessSpeed", this.wirelessSpeedButton);

        this.filteredImportButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.FILTERED_IMPORT,
                state -> menu.clientToggleFilteredImport());
        this.filteredImportButton.setTooltipOn(
                List.of(Component.translatable("ae2lt.gui.filtered_import.on")));
        this.filteredImportButton.setTooltipOff(
                List.of(Component.translatable("ae2lt.gui.filtered_import.off")));
        widgets.add("filteredImport", this.filteredImportButton);

        machineAdaptationButton = LdlibAe2Button.addText(widgets, style, "machineAdaptation",
                Component.empty(), menu::clientToggleMachineAdaptation);
        machineAdaptationButton.setTooltip(Tooltip.create(Component.translatable(
                "ae2lt.gui.machine_adaptation.tooltip")));

        machineParallelism = LdlibAe2TextField.add(widgets, style, "machineParallelism");
        machineParallelism.setMaxLength(10);
        machineParallelism.setFilter(OverloadedPatternProviderAdvancedScreen::isParallelismDraft);
        lastSyncedParallelism = menu.machineParallelism;
        machineParallelism.setValue(Integer.toString(lastSyncedParallelism));
        machineParallelism.setTooltip(Tooltip.create(Component.translatable(
                "ae2lt.gui.provider_advanced.machine_parallelism_tooltip")));
        applyParallelism = LdlibAe2Button.addText(widgets, style, "applyParallelism",
                Component.translatable("gui.done"), this::applyParallelism);
    }

    private static boolean isParallelismDraft(String text) {
        if (text.isEmpty()) return true;
        try {
            return text.chars().allMatch(c -> c >= '0' && c <= '9')
                    && Integer.parseInt(text) >= 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private int draftedParallelism() {
        try {
            return Integer.parseInt(machineParallelism.getValue());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void applyParallelism() {
        int value = draftedParallelism();
        if (value > 0) {
            menu.clientSetMachineParallelism(value);
        }
    }

    @Override
    protected boolean shouldAddToolbar() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        setSlotsHidden(SlotSemantics.TOOLBOX, true);
        var root = new UIElement();
        int textColor = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        wirelessLabels = List.of(
                addLabel(root, "distribution", LABEL_X, STRATEGY_Y, textColor),
                addLabel(root, "probe", LABEL_X, SPEED_Y, textColor),
                addLabel(root, "machine_parallelism", 14, 129, textColor));
        filterLabel = addLabel(root, "input_filter", LABEL_X, FILTER_Y, textColor);
        parallelismHint = addLabel(root, "parallelism_hint", 14, 171, textColor);
        wirelessHint = addLabel(root, "wireless_hint", 14, 185, textColor);
        foreground = LdlibScreens.localLayer(root, imageWidth, imageHeight);
    }

    private Label addLabel(UIElement root, String key, int x, int y, int color) {
        var label = LdlibScreens.label(Component.translatable("ae2lt.gui.provider_advanced." + key), color);
        label.setAllowHitTest(false);
        label.textStyle(style -> style.textWrap(TextWrap.NONE));
        root.addChild(LdlibScreens.at(label, x, y, imageWidth - x, 9));
        return label;
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        boolean wirelessTuningVisible = menu.isWirelessTuningVisible();
        boolean wirelessTuningActive = wirelessTuningVisible && menu.isWirelessMode();

        this.wirelessStrategyButton.setState(menu.isEvenDistributionMode());
        this.wirelessStrategyButton.setVisibility(wirelessTuningVisible);
        this.wirelessStrategyButton.active = wirelessTuningActive;

        this.wirelessSpeedButton.setState(menu.isFastSpeedMode());
        this.wirelessSpeedButton.setVisibility(wirelessTuningVisible);
        this.wirelessSpeedButton.active = wirelessTuningActive;

        machineAdaptationButton.setMessage(Component.translatable(menu.isMachineAdaptationEnabled()
                ? "ae2lt.gui.machine_adaptation.on" : "ae2lt.gui.machine_adaptation.off"));

        this.filteredImportButton.setState(menu.isFilteredImport());
        this.filteredImportButton.setVisibility(menu.isFilteredImportVisible());

        if (lastSyncedParallelism != menu.machineParallelism) {
            if (!machineParallelism.isFocused()
                    || draftedParallelism() == lastSyncedParallelism) {
                machineParallelism.setValue(Integer.toString(menu.machineParallelism));
            }
            lastSyncedParallelism = menu.machineParallelism;
        }
        machineParallelism.setVisible(wirelessTuningVisible);
        machineParallelism.setEditable(wirelessTuningActive, true);
        applyParallelism.visible = wirelessTuningVisible;
        applyParallelism.active = wirelessTuningActive
                && draftedParallelism() > 0
                && draftedParallelism() != menu.machineParallelism;
        for (var label : wirelessLabels) {
            label.setVisible(wirelessTuningVisible);
        }
        filterLabel.setVisible(menu.isFilteredImportVisible());
        parallelismHint.setVisible(wirelessTuningVisible);
        wirelessHint.setVisible(wirelessTuningVisible && !wirelessTuningActive);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);
        foreground.getWidget().render(guiGraphics, mouseX, mouseY, 0);
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (machineParallelism.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            if (applyParallelism.active) applyParallelism();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                || !machineParallelism.isFocused()
                        && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            returnToParent();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
