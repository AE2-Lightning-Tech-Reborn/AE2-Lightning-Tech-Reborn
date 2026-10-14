package com.moakiee.ae2lt.mixin.mest;

import com.moakiee.ae2lt.integration.mest.MestSlot;
import appeng.menu.slot.FakeSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerMenu.class)
public abstract class EmbeddedPickAllMixin {
    @Inject(method = "canTakeItemForPickAll", at = @At("HEAD"), cancellable = true)
    private void ae2lt$nativeResultPolicy(ItemStack stack, Slot slot, CallbackInfoReturnable<Boolean> cir) {
        if (slot instanceof MestSlot proxy) cir.setReturnValue(!(proxy.original() instanceof FakeSlot)
                && proxy.controller().canTakeItemForPickAll(stack, proxy.original()));
    }
}
