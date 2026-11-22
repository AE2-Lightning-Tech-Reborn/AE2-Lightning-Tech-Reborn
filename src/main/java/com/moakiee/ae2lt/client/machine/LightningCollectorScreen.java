package com.moakiee.ae2lt.client.machine;

import com.moakiee.ae2lt.client.ldlib.LdlibAeScreen;
import com.moakiee.ae2lt.client.frequency.FrequencyBindingClient;

import java.util.List;

import appeng.client.gui.style.ScreenStyle;

import com.moakiee.ae2lt.client.ldlib.MachineGauges;
import com.moakiee.ae2lt.client.gui.LightningStatusLines;
import com.moakiee.ae2lt.menu.LightningCollectorMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class LightningCollectorScreen extends LdlibAeScreen<LightningCollectorMenu> {
    public LightningCollectorScreen(
            LightningCollectorMenu menu,
            Inventory playerInventory,
            Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
        addToLeftToolbar(FrequencyBindingClient.createToolbarButton(menu));

        widgets.add("progressBar", MachineGauges.verticalFilledProgress(style.getImage("progressBar"), menu,
                () -> List.of(Component.literal(menu.getCurrentProgress() * 100L
                        / Math.max(1, menu.getMaxProgress()) + "%"))));

        widgets.add("lightningStatus", MachineGauges.statusIcon(() -> List.of(
                LightningStatusLines.title(),
                Component.translatable(
                        "gui.ae2lt.lightning_collector.high_output",
                        formatRange(menu.previewHighMin, menu.previewHighMax)),
                Component.translatable(
                        "gui.ae2lt.lightning_collector.extreme_output.simple",
                        formatRange(menu.previewExtremeMin, menu.previewExtremeMax)))));
    }

    private static String formatRange(int min, int max) {
        return min == max ? Integer.toString(min) : min + "~" + max;
    }
}
