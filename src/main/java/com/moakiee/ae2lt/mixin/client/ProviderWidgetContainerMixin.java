package com.moakiee.ae2lt.mixin.client;

import java.util.Map;

import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.WidgetContainer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.client.provider.ProviderPanelMerge;
import com.moakiee.ae2lt.client.provider.SharedProviderPanels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = WidgetContainer.class, remap = false)
public abstract class ProviderWidgetContainerMixin implements SharedProviderPanels {
    @Unique private boolean ae2lt$sharedProviderPanels;

    @Override
    public void ae2lt$enableSharedProviderPanels() {
        ae2lt$sharedProviderPanels = true;
    }

    @WrapOperation(method = "add(Ljava/lang/String;Lappeng/client/gui/ICompositeWidget;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object ae2lt$mergeSharedProviderPanels(Map<String, ICompositeWidget> panels,
            Object id, Object incoming, Operation<Object> original) {
        if (ae2lt$sharedProviderPanels) {
            var merged = ProviderPanelMerge.merge((String) id, panels.get(id), (ICompositeWidget) incoming);
            if (merged != null) {
                original.call(panels, id, merged);
                return null;
            }
        }
        return original.call(panels, id, incoming);
    }
}
