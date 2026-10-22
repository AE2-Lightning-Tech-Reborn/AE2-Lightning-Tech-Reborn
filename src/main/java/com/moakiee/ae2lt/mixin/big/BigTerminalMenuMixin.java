package com.moakiee.ae2lt.mixin.big;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.menu.me.common.MEStorageMenu;
import com.moakiee.ae2lt.crafting.big.BigTerminalMenu;
import com.moakiee.ae2lt.crafting.big.BigTerminalStock;
import java.math.BigInteger;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MEStorageMenu.class, remap = false)
public abstract class BigTerminalMenuMixin implements BigTerminalMenu {
    @Shadow protected abstract KeyCounter getPreviousAvailableStacks();
    @Unique private final BigTerminalStock ae2lt$bigStock = new BigTerminalStock();

    @Override public BigInteger ae2lt$getBigStock(AEKey key) {
        return ae2lt$bigStock.get(key);
    }

    @Override public void ae2lt$applyBigStock(Map<AEKey, BigInteger> changes) {
        if (((MEStorageMenu) (Object) this).isClientSide()) ae2lt$bigStock.apply(changes);
    }

    @Inject(method = "broadcastChanges()V", at = @At("TAIL"), remap = true)
    private void ae2lt$syncBigStock(CallbackInfo ci) {
        var menu = (MEStorageMenu) (Object) this;
        if (menu.isClientSide()) return;
        ae2lt$bigStock.sync(menu, getPreviousAvailableStacks());
    }
}
