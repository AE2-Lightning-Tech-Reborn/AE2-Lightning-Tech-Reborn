package com.moakiee.ae2lt.mixin.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.CraftAmountScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.menu.me.crafting.CraftAmountMenu;

import com.moakiee.ae2lt.client.widgets.BigNumberEntry;
import com.moakiee.ae2lt.crafting.big.BigAmountMenu;
import com.moakiee.ae2lt.network.tianshu.ConfirmBigAmountPacket;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import com.moakiee.ae2lt.network.NetworkInit;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.math.BigInteger;

@Mixin(value = CraftAmountScreen.class, remap = false)
public abstract class BigCraftAmountScreenMixin extends AEBaseScreen<CraftAmountMenu> {
    @Shadow @Final private NumberEntryWidget amountToCraft;
    @Shadow @Final private Button next;
    @Unique private boolean ae2lt$restored;

    protected BigCraftAmountScreenMixin(
            CraftAmountMenu m, Inventory i, Component t, ScreenStyle s) {
        super(m, i, t, s);
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void ae2lt$update(CallbackInfo ci) {
        if (!(menu instanceof BigAmountMenu big)) return;
        var widget = (BigNumberEntry) amountToCraft;
        widget.ae2lt$setExtendedRange(big.ae2lt$longAvailable(), big.ae2lt$bigAvailable());
        if (!big.ae2lt$longAvailable()) return;
        if (!ae2lt$restored && !big.ae2lt$initialAmount().isEmpty()) {
            widget.ae2lt$setBig(new BigInteger(big.ae2lt$initialAmount()));
            ae2lt$restored = true;
        }
        next.active = widget.ae2lt$getBig().isPresent();
    }

    @Inject(method = "confirm", at = @At("HEAD"), cancellable = true)
    private void ae2lt$confirm(CallbackInfo ci) {
        if (!(menu instanceof BigAmountMenu big) || !big.ae2lt$longAvailable()) return;
        var n = ((BigNumberEntry) amountToCraft).ae2lt$getBig();
        if (n.isEmpty()) {
            ci.cancel();
            return;
        }
        // AE2's amount packet is int-sized. Our packet transports larger values, but the
        // server still uses the normal selected planner/CPU path up to Long.MAX_VALUE.
        if (n.get().compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            NetworkInit.sendToServer(
                    new ConfirmBigAmountPacket(
                            menu.containerId,
                            n.get(),
                            amountToCraft.startsWithEquals(),
                            hasShiftDown()));
            ci.cancel();
        }
    }
}
