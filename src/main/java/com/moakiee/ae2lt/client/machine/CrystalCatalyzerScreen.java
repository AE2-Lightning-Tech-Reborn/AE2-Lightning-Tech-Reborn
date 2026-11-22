package com.moakiee.ae2lt.client.machine;

import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.moakiee.ae2lt.client.ldlib.BackgroundLayer;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;

import com.moakiee.ae2lt.client.frequency.FrequencyBindingClient;
import com.moakiee.ae2lt.client.widgets.TextureToggleButton;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.client.gui.Icon;
import appeng.client.gui.style.ScreenStyle;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;

import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import com.moakiee.ae2lt.client.ldlib.MachineGauges;
import com.moakiee.ae2lt.client.gui.LightningStatusLines;
import com.moakiee.ae2lt.machine.crystalcatalyzer.recipe.Mode;
import com.moakiee.ae2lt.menu.CrystalCatalyzerMenu;

public class CrystalCatalyzerScreen extends RedstoneMachineScreen<CrystalCatalyzerMenu> {
    static final LdlibStyleSprite ATLAS = new LdlibStyleSprite(
            ResourceLocation.parse("ae2lt:textures/guis/crystal_catalyzer.png"), 256, 256);
    private static final SpriteTexture FE_COVER = ATLAS.slice(150, 28, 10, 22);
    private static final SpriteTexture MATRIX_COVER = ATLAS.slice(150, 28, 24, 33);

    private final LdlibAe2Button configureOutputButton;
    private final BackgroundLayer pigmeeCovers;
    private final TextureToggleButton modeButton;
    private final TextureToggleButton frequencyButton;
    private final CrystalCatalyzerFluidWidget fluidWidget;

    public CrystalCatalyzerScreen(
            CrystalCatalyzerMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.imageWidth = 176;
        this.imageHeight = 190;
        // Hide the shared background's FE scale and unused matrix branch on the Pigmee variant.
        pigmeeCovers = new BackgroundLayer(imageWidth, imageHeight)
                .add(FE_COVER, 138, 28, 10, 22)
                .add(MATRIX_COVER, 80, 40, 24, 33);

        this.frequencyButton = FrequencyBindingClient.createToolbarButton(menu);
        addToLeftToolbar(this.frequencyButton);

        widgets.add("energyBar", MachineGauges.energyBar(style.getImage("energyBar"),
                menu::getStoredEnergy, menu::getEnergyCapacity,
                "ae2lt.gui.crystal_catalyzer.energy.tooltip", () -> !menu.isPigmeeVariant()));
        this.fluidWidget = new CrystalCatalyzerFluidWidget(
                menu,
                menu::getFluid,
                menu::getFluidCapacity);
        widgets.add("fluidBar", this.fluidWidget);
        widgets.add("processArea", MachineGauges.horizontalProgress(style.getImage("processOverlay"), menu::getProgress, false));

        addToLeftToolbar(new MachineAutoExportButton("ae2lt.gui.crystal_catalyzer.auto_export",
                menu::isAutoExportEnabled, menu::clientToggleAutoExport));

        this.configureOutputButton = LdlibAe2Button.toolbar(
                Icon.COG, Component.translatable("ae2lt.gui.crystal_catalyzer.configure_output"),
                () -> {
                    var next = new CrystalCatalyzerOutputConfigScreen(this);
                    pigmeeCovers.onRemoved();
                    releaseUpgradePanels();
                    releaseBackground();
                    switchToScreen(next);
                });
        addToLeftToolbar(this.configureOutputButton);

        this.modeButton = new TextureToggleButton(
                TextureToggleButton.ButtonType.CRYSTAL_CATALYZER_MODE, btn -> menu.clientCycleMode());
        this.modeButton.setTooltipOff(List.of(
                Component.translatable("ae2lt.gui.crystal_catalyzer.mode.title"),
                Component.translatable("ae2lt.gui.crystal_catalyzer.mode.crystal"),
                Component.translatable("ae2lt.gui.crystal_catalyzer.mode.tooltip.crystal")));
        this.modeButton.setTooltipOn(List.of(
                Component.translatable("ae2lt.gui.crystal_catalyzer.mode.title"),
                Component.translatable("ae2lt.gui.crystal_catalyzer.mode.dust"),
                Component.translatable("ae2lt.gui.crystal_catalyzer.mode.tooltip.dust")));
        addToLeftToolbar(this.modeButton);

        widgets.add("lightningStatus", MachineGauges.statusIcon(() -> menu.isPigmeeVariant()
                ? List.of(
                        LightningStatusLines.title(),
                        LightningStatusLines.status(menu.isWorking()),
                        LightningStatusLines.progress(menu.getProgress()))
                : List.of(
                        LightningStatusLines.title(),
                        menu.redstonePaused
                                ? Component.translatable("ae2lt.gui.status.label",
                                        Component.translatable("ae2lt.gui.status.redstone_paused"))
                                : LightningStatusLines.status(menu.isWorking()),
                        LightningStatusLines.progress(menu.getProgress()),
                        LightningStatusLines.energy(menu.getStoredEnergy(), menu.getEnergyCapacity()),
                        LightningStatusLines.highVoltage(menu.getHighVoltageAvailable()),
                        LightningStatusLines.extremeHighVoltage(menu.getExtremeHighVoltageAvailable()))));
    }

    @Override
    protected void init() {
        pigmeeCovers.onRemoved();
        super.init();
    }

    @Override
    public void removed() {
        try {
            pigmeeCovers.onRemoved();
        } finally {
            super.removed();
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        setTextContent("dialog_title", Component.translatable(menu.isPigmeeVariant()
                ? "block.ae2lt.pigmee_crystal_catalyzer"
                : "block.ae2lt.crystal_catalyzer"));
        this.configureOutputButton.setVisibility(menu.isAutoExportEnabled());
        this.frequencyButton.setVisibility(!menu.isPigmeeVariant());
        this.modeButton.setVisibility(!menu.isPigmeeVariant());
        this.modeButton.setState(menu.getMode() == Mode.DUST);
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY,
                       int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        if (menu.isPigmeeVariant()) {
            pigmeeCovers.render(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Forward every mouse button to the tank before AE2's widget dispatch.
        return (fluidWidget != null && fluidWidget.mouseClicked(mouseX, mouseY, button))
                || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        super.renderSlot(guiGraphics, slot);
        LargeStackCountRenderer.renderSlotCount(guiGraphics, font, slot);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        var lines = super.getTooltipFromContainerItem(stack);
        LargeStackCountRenderer.appendCountTooltip(lines, hoveredSlot);
        return lines;
    }
}
