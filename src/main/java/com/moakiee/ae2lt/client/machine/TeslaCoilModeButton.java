package com.moakiee.ae2lt.client.machine;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import com.moakiee.ae2lt.client.ldlib.LdlibStyleSprite;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.machine.teslacoil.TeslaCoilMode;

public class TeslaCoilModeButton extends LdlibAe2Button {
    private static final ResourceLocation HV_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/buttons/lightning.png");
    private static final ResourceLocation EHV_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            AE2LightningTech.MODID, "textures/gui/buttons/lightning_high_voltage.png");

    private TeslaCoilMode mode = TeslaCoilMode.HIGH_VOLTAGE;

    public TeslaCoilModeButton(Runnable action) {
        super(16, Component.translatable("block.ae2lt.tesla_coil"), action);
        setMessage(Component.empty());
        updateFace();
    }

    public void setMode(TeslaCoilMode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            updateFace();
        }
    }

    private void updateFace() {
        boolean extreme = mode == TeslaCoilMode.EXTREME_HIGH_VOLTAGE;
        toolbarFace(new LdlibStyleSprite(extreme ? EHV_TEXTURE : HV_TEXTURE, 16, 16).full(), extreme, false);
    }

    @Override
    public List<Component> getTooltipMessage() {
        var current = Component.translatable("ae2lt.gui.tesla_coil.mode."
                + (mode == TeslaCoilMode.EXTREME_HIGH_VOLTAGE ? "extreme_high_voltage" : "high_voltage"));
        var next = Component.translatable("ae2lt.gui.tesla_coil.mode."
                + (mode == TeslaCoilMode.EXTREME_HIGH_VOLTAGE ? "high_voltage" : "extreme_high_voltage"));
        return List.of(
                Component.translatable("ae2lt.gui.tesla_coil.mode.button", current),
                Component.translatable("ae2lt.gui.tesla_coil.mode.click_to_switch", next));
    }
}
