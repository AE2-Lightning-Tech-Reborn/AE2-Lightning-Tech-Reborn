package com.moakiee.ae2lt.mixin.ae2wtlib;

import appeng.menu.locator.MenuLocator;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuCraftingLocatorScope;
import de.mari_023.ae2wtlib.wct.CraftingTerminalHandler;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = CraftingTerminalHandler.class, remap = false)
public abstract class CraftingTerminalHandlerMixin {
    @WrapMethod(method = "getLocator")
    private MenuLocator ae2lt$recognizeCraftingTerminal(Operation<MenuLocator> original) {
        boolean previous = TianshuCraftingLocatorScope.enter();
        try { return original.call(); }
        finally { TianshuCraftingLocatorScope.restore(previous); }
    }
}
