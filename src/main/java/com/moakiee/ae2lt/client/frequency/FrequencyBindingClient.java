package com.moakiee.ae2lt.client.frequency;

import com.moakiee.ae2lt.client.widgets.TextureToggleButton;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ToggleButton;

import java.util.List;

import com.moakiee.ae2lt.menu.FrequencyBindingMenu;
import com.moakiee.ae2lt.network.OpenFrequencyMenuPacket;
import com.moakiee.ae2lt.network.ToggleFrequencyCardAutoConnectPacket;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FrequencyBindingClient {
    private FrequencyBindingClient() {
    }

    public static TextureToggleButton createToolbarButton(FrequencyBindingMenu menu) {
        var button = new TextureToggleButton(
                TextureToggleButton.ButtonType.FREQUENCY_BIND,
                ignored -> PacketDistributor.sendToServer(OpenFrequencyMenuPacket.forBlock()));
        button.setTooltipAt(0, List.of(Component.translatable("ae2lt.gui.frequency.bind")));
        return button;
    }

    public static TextureToggleButton createCardToolbarButton() {
        var button = new TextureToggleButton(
                TextureToggleButton.ButtonType.FREQUENCY_BIND,
                ignored -> PacketDistributor.sendToServer(OpenFrequencyMenuPacket.forCard()));
        button.setTooltipAt(0, List.of(Component.translatable("ae2lt.gui.button.open_frequency_card")));
        return button;
    }

    public static ToggleButton createCardAutoConnectToolbarButton(boolean energy) {
        var button = new ToggleButton(
                Icon.AUTO_EXPORT_ON, Icon.AUTO_EXPORT_OFF,
                state -> PacketDistributor.sendToServer(ToggleFrequencyCardAutoConnectPacket.forTerminalCard(energy)));
        String prefix = energy ? "ae2lt.gui.button.auto_connect_energy_" : "ae2lt.gui.button.auto_connect_";
        var hint = Component.translatable(energy ? "ae2lt.gui.home.auto_energy_hint" : "ae2lt.gui.home.auto_channel_hint");
        button.setTooltipOff(List.of(Component.translatable(prefix + "off"), hint));
        button.setTooltipOn(List.of(Component.translatable(prefix + "on"), hint));
        return button;
    }
}
