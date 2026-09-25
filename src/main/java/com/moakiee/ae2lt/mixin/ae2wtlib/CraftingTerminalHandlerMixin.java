package com.moakiee.ae2lt.mixin.ae2wtlib;

import appeng.menu.locator.MenuLocator;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuCraftingLocatorScope;
import de.mari_023.ae2wtlib.wct.CraftingTerminalHandler;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = CraftingTerminalHandler.class, remap = false)
public abstract class CraftingTerminalHandlerMixin implements com.moakiee.ae2lt.integration.ae2wtlib.BoundCraftingTerminalHandler {
    @org.spongepowered.asm.mixin.Unique private net.minecraft.world.item.ItemStack ae2lt$boundTerminal;
    @Override public void ae2lt$bindTerminal(net.minecraft.world.item.ItemStack stack) { ae2lt$boundTerminal = stack; }
    @org.spongepowered.asm.mixin.injection.Inject(method = "getCraftingTerminal", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable = true)
    private void ae2lt$boundTerminal(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.item.ItemStack> cir) {
        if (ae2lt$boundTerminal != null) cir.setReturnValue(ae2lt$boundTerminal);
    }

    @WrapMethod(method = "getMenuHost")
    private de.mari_023.ae2wtlib.terminal.WTMenuHost ae2lt$recognizeCraftingTerminal(Operation<de.mari_023.ae2wtlib.terminal.WTMenuHost> original) {
        boolean previous = TianshuCraftingLocatorScope.enter();
        try { return original.call(); }
        finally { TianshuCraftingLocatorScope.restore(previous); }
    }
}
