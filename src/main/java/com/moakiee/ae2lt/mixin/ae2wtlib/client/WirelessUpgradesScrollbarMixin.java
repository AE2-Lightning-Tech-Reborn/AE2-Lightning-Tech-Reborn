package com.moakiee.ae2lt.mixin.ae2wtlib.client;

import appeng.client.gui.WidgetContainer;
import appeng.client.gui.widgets.Scrollbar;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.client.ldlib.LdlibTerminalScrollbar;
import com.moakiee.ae2lt.integration.ae2wtlib.client.WirelessUpgradesPanel;
import de.mari_023.ae2wtlib.api.gui.ScrollingUpgradesPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ScrollingUpgradesPanel.class, remap = false)
public abstract class WirelessUpgradesScrollbarMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/WidgetContainer;addScrollBar(Ljava/lang/String;Lappeng/client/gui/widgets/Scrollbar$Style;)Lappeng/client/gui/widgets/Scrollbar;"), require = 1)
    private Scrollbar ae2lt$createScrollbar(WidgetContainer widgets, String id, Scrollbar.Style style,
                                          Operation<Scrollbar> original) {
        return (Object) this instanceof WirelessUpgradesPanel ? LdlibTerminalScrollbar.add(widgets, id, style)
                : original.call(widgets, id, style);
    }
}
