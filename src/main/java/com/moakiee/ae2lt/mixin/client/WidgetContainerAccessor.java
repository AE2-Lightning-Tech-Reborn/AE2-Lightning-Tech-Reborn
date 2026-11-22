package com.moakiee.ae2lt.mixin.client;

import java.util.Map;

import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.WidgetContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = WidgetContainer.class, remap = false)
public interface WidgetContainerAccessor {
    @Accessor("compositeWidgets")
    Map<String, ICompositeWidget> ae2lt$getCompositeWidgets();
}
