package com.moakiee.ae2lt.mixin.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.StyleManager;
import appeng.menu.me.crafting.CraftConfirmMenu;
import com.moakiee.ae2lt.client.AE2LtCraftConfirmScreen;
import com.moakiee.ae2lt.crafting.report.CraftingReportMenuState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leaves other mods' confirmation constructors and routing intact. */
@Mixin(value = CraftConfirmScreen.class, remap = false)
public abstract class CraftConfirmReportRoutingMixin extends AEBaseScreen<CraftConfirmMenu> {
    @Unique
    private Inventory ae2lt$inventory;

    @Unique
    private Component ae2lt$title;

    protected CraftConfirmReportRoutingMixin(
            CraftConfirmMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2lt$captureReportContext(
            CraftConfirmMenu menu, Inventory inventory, Component title, ScreenStyle style, CallbackInfo ci) {
        ae2lt$inventory = inventory;
        ae2lt$title = title;
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void ae2lt$routeReport(CallbackInfo ci) {
        if (menu instanceof CraftingReportMenuState state && state.ae2lt$shouldShowReport()) {
            switchToScreen(new AE2LtCraftConfirmScreen(menu, ae2lt$inventory, ae2lt$title,
                    StyleManager.loadStyleDoc("/screens/ae2lt_craft_confirm.json")));
        }
    }
}
