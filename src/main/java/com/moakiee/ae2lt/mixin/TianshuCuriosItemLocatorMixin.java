package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.integration.ae2wtlib.TianshuCuriosStackAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The 26.1 resource locator returns snapshots; terminal state must update the equipped stack. */
@Mixin(targets = "appeng.menu.locator.CuriosItemLocator")
public abstract class TianshuCuriosItemLocatorMixin {
    @Shadow @Final private int curioSlot;

    @Inject(method = "locateItem", at = @At("HEAD"), cancellable = true)
    private void ae2lt$locateLiveTianshuStack(Player player, CallbackInfoReturnable<ItemStack> cir) {
        if (ModList.get().isLoaded("curios")) {
            var stack = TianshuCuriosStackAccess.locate(player, curioSlot);
            if (stack != null) cir.setReturnValue(stack);
        }
    }
}
