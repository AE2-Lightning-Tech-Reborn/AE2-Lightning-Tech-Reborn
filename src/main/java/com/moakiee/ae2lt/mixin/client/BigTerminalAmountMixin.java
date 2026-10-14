package com.moakiee.ae2lt.mixin.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import appeng.client.gui.style.ScreenStyle;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.Tooltips;
import appeng.menu.me.common.MEStorageMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.compat.gto.GtoTerminalAmounts;
import com.moakiee.ae2lt.menu.TianshuMaintenanceMenu;
import com.moakiee.thunderbolt.ae2.crafting.ExactAmountFormatter;
import gto_ae.hooks.gui.menu.IRepoSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** GTO moved slot rendering into IRepoSlot; scope the formatting at its screen call boundary. */
@Mixin(value = MEStorageScreen.class, remap = false)
public abstract class BigTerminalAmountMixin extends AEBaseScreen<MEStorageMenu> {
    protected BigTerminalAmountMixin(MEStorageMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @WrapOperation(method = "renderSlot(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V",
            remap = true, at = @At(value = "INVOKE", remap = false,
            target = "Lgto_ae/hooks/gui/menu/IRepoSlot;renderSlot(Lnet/minecraft/client/gui/GuiGraphics;Lappeng/client/gui/me/common/Repo;ZZ)V"))
    private void ae2lt$amount(IRepoSlot slot, GuiGraphics graphics, Repo repo, boolean large, boolean craftOnly,
            Operation<Void> original) {
        var entry = slot.getEntry();
        var exact = menu instanceof TianshuMaintenanceMenu m && entry != null ? m.getBigStock(entry.getWhat()) : null;
        GtoTerminalAmounts.with(entry != null ? entry.getWhat() : null, exact,
                () -> original.call(slot, graphics, repo, large, craftOnly));
    }

    @WrapOperation(method = "renderTooltip", remap = true, at = @At(value = "INVOKE", remap = false,
            target = "Lgto_ae/hooks/gui/menu/IRepoSlot;renderTooltip(Lappeng/menu/me/common/MEStorageMenu;Lnet/minecraft/client/gui/GuiGraphics;IILgto_ae/hooks/gui/menu/IRepoSlot$TooltipHandler;Lgto_ae/hooks/gui/menu/IRepoSlot$TooltipHandler;Z)V"))
    private void ae2lt$tooltip(IRepoSlot slot, MEStorageMenu currentMenu, GuiGraphics graphics, int x, int y,
            IRepoSlot.TooltipHandler emptyDrawer, IRepoSlot.TooltipHandler itemDrawer, boolean craftOnly,
            Operation<Void> original) {
        var entry = slot.getEntry();
        var exact = menu instanceof TianshuMaintenanceMenu m && entry != null ? m.getBigStock(entry.getWhat()) : null;
        if (exact != null) {
            var previousDrawer = itemDrawer;
            var key = entry.getWhat();
            var ordinary = Tooltips.getAmountTooltip(ButtonToolTips.StoredAmount, key, entry.getStoredAmount());
            itemDrawer = (g, px, py, lines) -> {
                var updated = new java.util.ArrayList<Component>(lines);
                int index = updated.indexOf(ordinary);
                if (index >= 0) updated.set(index, ButtonToolTips.StoredAmount.text(
                        ExactAmountFormatter.full(exact, key.getAmountPerUnit())).withStyle(ordinary.getStyle()));
                previousDrawer.drawTooltip(g, px, py, updated);
            };
        }
        original.call(slot, currentMenu, graphics, x, y, emptyDrawer, itemDrawer, craftOnly);
    }
}
