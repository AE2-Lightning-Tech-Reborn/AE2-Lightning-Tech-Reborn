package com.moakiee.ae2lt.mixin.client;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.widgets.Scrollbar;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the Tianshu report reuse the native confirmation controls and addon hooks. */
@Mixin(value = CraftConfirmScreen.class, remap = false)
public interface CraftConfirmScreenAccessor {
    @Accessor("start")
    Button ae2lt$getStart();

    @Accessor("selectCPU")
    Button ae2lt$getSelectCpu();

    @Accessor("scrollbar")
    Scrollbar ae2lt$getScrollbar();
}
