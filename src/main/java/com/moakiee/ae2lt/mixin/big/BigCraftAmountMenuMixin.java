package com.moakiee.ae2lt.mixin.big;

import appeng.api.storage.ISubMenuHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.crafting.*;

import com.moakiee.ae2lt.crafting.big.*;
import com.moakiee.thunderbolt.core.storage.big.*;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.math.BigInteger;

@Mixin(value = CraftAmountMenu.class, remap = false)
public abstract class BigCraftAmountMenuMixin extends AEBaseMenu implements BigAmountMenu {
    @Shadow
    public abstract ISubMenuHost getHost();

    @Shadow
    public abstract appeng.api.stacks.GenericStack getWhatToCraft();

    @Unique
    @GuiSync(30200)
    private boolean ae2lt$bigAvailable;

    @Unique
    @GuiSync(30201)
    private String ae2lt$initialAmount = "";

    protected BigCraftAmountMenuMixin(MenuType<?> type, int id, Inventory inv, Object host) {
        super(type, id, inv, host);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2lt$init(int id, Inventory inv, ISubMenuHost host, CallbackInfo ci) {
        if (!isClientSide()) ae2lt$bigAvailable = BigCraftingMenus.available(host) != null;
    }

    public boolean ae2lt$bigAvailable() {
        return ae2lt$bigAvailable;
    }

    public String ae2lt$initialAmount() {
        return ae2lt$initialAmount;
    }

    public void ae2lt$initialAmount(BigInteger amount) {
        ae2lt$initialAmount = amount.toString();
    }

    public void ae2lt$confirm(BigInteger amount, boolean missing, boolean autoStart) {
        if (!(getPlayer() instanceof ServerPlayer player)
                || !stillValid(player)
                || BigCraftingMenus.available(getHost()) == null
                || getWhatToCraft() == null) return;
        BigAmounts.nonNegative(amount);
        var key = getWhatToCraft().what();
        if (amount.signum() <= 0) return;
        if (missing) {
            var terminal =
                    (com.moakiee.ae2lt.logic.tianshu.terminal.TianshuPatternTerminalHost) getHost();
            var stock =
                    BigStorageOps.snapshot(
                            terminal.getActionableNode()
                                    .getGrid()
                                    .getStorageService()
                                    .getInventory(),
                            getActionSource());
            amount = amount.subtract(stock.getOrDefault(key, BigInteger.ZERO));
        }
        if (amount.signum() <= 0) {
            getHost().returnToMainMenu(player, (CraftAmountMenu) (Object) this);
            return;
        }
        if (MenuOpener.open(CraftConfirmMenu.TYPE, player, getLocator())) {
            var confirm = (CraftConfirmMenu) player.containerMenu;
            confirm.setAutoStart(autoStart);
            ((BigConfirmMenu) confirm).ae2lt$planBig(key, amount);
        }
    }
}
