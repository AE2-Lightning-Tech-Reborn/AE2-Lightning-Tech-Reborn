package com.moakiee.ae2lt.client.machine;

import com.moakiee.ae2lt.client.ldlib.LdlibAeScreen;
import com.moakiee.ae2lt.client.frequency.FrequencyBindingClient;

import java.util.List;
import java.util.Locale;

import appeng.client.gui.Icon;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import appeng.client.gui.style.ScreenStyle;
import com.moakiee.ae2lt.client.ldlib.MachineGauges;
import com.moakiee.ae2lt.client.gui.LightningStatusLines;
import com.moakiee.ae2lt.blockentity.MiningFactoryBlockEntity;
import com.moakiee.ae2lt.blockentity.MiningFactoryBlockEntity.Status;
import com.moakiee.ae2lt.menu.MiningFactoryMenu;
import com.moakiee.ae2lt.client.gui.LargeStackCountRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class MiningFactoryScreen extends LdlibAeScreen<MiningFactoryMenu> {
    private final LdlibAe2Button configureOutputButton;
    public MiningFactoryScreen(MiningFactoryMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        imageWidth = 176;
        imageHeight = 183;
        widgets.add("miningArea", MachineGauges.verticalProgress(style.getImage("miningOverlay"), menu::getProgress));
        widgets.add("processArea", MachineGauges.horizontalProgress(style.getImage("processOverlay"), menu::getProgress, true));
        widgets.add("energyBar", MachineGauges.energyBar(style.getImage("energyBar"),
                () -> menu.energy, () -> MiningFactoryBlockEntity.ENERGY_CAPACITY,
                "ae2lt.gui.overload_factory.energy.tooltip"));
        widgets.add("lightningStatus", MachineGauges.statusIcon(() -> List.of(
                LightningStatusLines.title(),
                Component.translatable("ae2lt.gui.status.label", statusText()),
                LightningStatusLines.progress(menu.getProgress()),
                Component.translatable("gui.ae2lt.mining_factory.parallel", menu.parallelCapacity),
                Component.translatable("gui.ae2lt.mining_factory.lightning", menu.lightning),
                LightningStatusLines.energy(menu.energy, MiningFactoryBlockEntity.ENERGY_CAPACITY))));
        addToLeftToolbar(FrequencyBindingClient.createToolbarButton(menu));
        addToLeftToolbar(new InputTransferButton(menu));
        addToLeftToolbar(new MachineAutoExportButton("ae2lt.gui.overload_factory.auto_export",
                menu::isAutoExportEnabled, menu::clientToggleAutoExport));
        configureOutputButton = LdlibAe2Button.toolbar(
                Icon.COG, Component.translatable("ae2lt.gui.overload_factory.configure_output"),
                () -> switchToScreen(createOutputConfigScreen()));
        addToLeftToolbar(configureOutputButton);
    }

    public MachineOutputConfigScreen<MiningFactoryMenu, MiningFactoryScreen> createOutputConfigScreen() {
        return new MachineOutputConfigScreen<>(this, Component.translatable("block.ae2lt.mining_factory"));
    }

    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        configureOutputButton.setVisibility(menu.isAutoExportEnabled());
    }

    private Component statusText() {
        String status = Status.values()[Math.clamp(menu.status, 0, Status.values().length - 1)].name().toLowerCase(Locale.ROOT);
        return Component.translatable("gui.ae2lt.mining_factory.status." + status);
    }

    @Override public void renderSlot(GuiGraphics graphics, Slot slot) {
        super.renderSlot(graphics, slot);
        LargeStackCountRenderer.renderSlotCount(graphics, font, slot);
    }

    @Override protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        var lines = super.getTooltipFromContainerItem(stack);
        LargeStackCountRenderer.appendCountTooltip(lines, hoveredSlot);
        return lines;
    }
}
