package com.moakiee.ae2lt.client;

import java.util.List;
import java.util.Locale;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ToggleButton;
import appeng.client.gui.widgets.ActionButton;
import appeng.api.config.ActionItems;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.Blitter;
import com.moakiee.ae2lt.client.gui.LightningStatusIconWidget;
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

public final class MiningFactoryScreen extends AEBaseScreen<MiningFactoryMenu> {
    private final Blitter workArea;
    private final Blitter slotFrame;
    private final Blitter processBase;
    private final Blitter energyFrame;
    private final ToggleButton autoExportButton;
    private final ActionButton configureOutputButton;
    public MiningFactoryScreen(MiningFactoryMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        imageWidth = 176;
        imageHeight = 178;
        workArea = style.getImage("workArea");
        slotFrame = style.getImage("slotFrame");
        processBase = style.getImage("processBase");
        energyFrame = style.getImage("energyFrame");
        widgets.add("processArea", new OverloadProcessingFactoryProgressWidget(menu::getProgress, style.getImage("processOverlay")));
        widgets.add("energyBar", new OverloadProcessingFactoryEnergyBar(() -> menu.energy, () -> 1_000_000L, style.getImage("energyBar")));
        widgets.add("lightningStatus", new LightningStatusIconWidget(() -> List.of(
                LightningStatusLines.title(),
                Component.translatable("ae2lt.gui.status.label", statusText()),
                LightningStatusLines.progress(menu.getProgress()),
                Component.translatable("gui.ae2lt.mining_factory.duration", MiningFactoryBlockEntity.PROCESSING_TICKS),
                Component.translatable("gui.ae2lt.mining_factory.parallel", menu.parallelCapacity),
                Component.translatable("gui.ae2lt.mining_factory.lightning", menu.lightning),
                LightningStatusLines.energy(menu.energy, 1_000_000))));
        addToLeftToolbar(FrequencyBindingClient.createToolbarButton(menu));
        autoExportButton = new ToggleButton(Icon.AUTO_EXPORT_ON, Icon.AUTO_EXPORT_OFF,
                state -> menu.clientToggleAutoExport());
        autoExportButton.setTooltipOn(List.of(Component.translatable("ae2lt.gui.overload_factory.auto_export.title"),
                Component.translatable("ae2lt.gui.overload_factory.auto_export.on")));
        autoExportButton.setTooltipOff(List.of(Component.translatable("ae2lt.gui.overload_factory.auto_export.title"),
                Component.translatable("ae2lt.gui.overload_factory.auto_export.off")));
        addToLeftToolbar(autoExportButton);
        configureOutputButton = new ActionButton(ActionItems.COG, () -> switchToScreen(createOutputConfigScreen()));
        configureOutputButton.setMessage(Component.translatable("ae2lt.gui.overload_factory.configure_output"));
        addToLeftToolbar(configureOutputButton);
    }

    public MachineOutputConfigScreen<MiningFactoryMenu, MiningFactoryScreen> createOutputConfigScreen() {
        return new MachineOutputConfigScreen<>(this, Component.translatable("block.ae2lt.mining_factory"));
    }

    @Override protected void updateBeforeRender() {
        super.updateBeforeRender();
        autoExportButton.setState(menu.isAutoExportEnabled());
        configureOutputButton.setVisibility(menu.isAutoExportEnabled());
    }

    @Override public void drawBG(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, x, y, mouseX, mouseY, partialTicks);
        // Recompose the existing factory texture; the player inventory and outer frame stay shared.
        workArea.copy().dest(x + 7, y + 20, 162, 60).blit(graphics);
        for (Slot slot : menu.slots) {
            if (slot.isActive() && slot.index < com.moakiee.ae2lt.machine.miningfactory.MiningFactoryInventory.SIZE) {
                slotFrame.copy().dest(x + slot.x - 1, y + slot.y - 1).blit(graphics);
            }
        }
        processBase.copy().dest(x + 80, y + 46).blit(graphics);
        energyFrame.copy().dest(x + 8, y + 40).blit(graphics);
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
