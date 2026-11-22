package com.moakiee.ae2lt.client.machine;

import java.util.List;
import java.util.function.BooleanSupplier;

import appeng.client.gui.Icon;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Icons;
import net.minecraft.network.chat.Component;

final class MachineAutoExportButton extends LdlibAe2Button {
    private final BooleanSupplier enabled;
    private final List<Component> enabledTooltip;
    private final List<Component> disabledTooltip;

    MachineAutoExportButton(String translationPrefix, BooleanSupplier enabled, Runnable toggle) {
        super(16, Component.translatable(translationPrefix + ".title"), toggle);
        this.enabled = enabled;
        enabledTooltip = List.of(getMessage(), Component.translatable(translationPrefix + ".on"));
        disabledTooltip = List.of(getMessage(), Component.translatable(translationPrefix + ".off"));
        setMessage(Component.empty());
        toolbarFace(IGuiTexture.dynamic(() -> LdlibAe2Icons.texture(
                enabled.getAsBoolean() ? Icon.AUTO_EXPORT_ON : Icon.AUTO_EXPORT_OFF)), false);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return enabled.getAsBoolean() ? enabledTooltip : disabledTooltip;
    }
}
