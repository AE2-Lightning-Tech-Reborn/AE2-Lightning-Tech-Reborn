package com.moakiee.ae2lt.mixin.client;

import java.util.List;
import java.util.function.Supplier;

import appeng.client.gui.widgets.UpgradesPanel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = UpgradesPanel.class, remap = false)
public interface UpgradesPanelAccessor {
    @Accessor("slots")
    List<Slot> ae2lt$getSlots();

    @Accessor("tooltipSupplier")
    Supplier<List<Component>> ae2lt$getTooltipSupplier();
}
