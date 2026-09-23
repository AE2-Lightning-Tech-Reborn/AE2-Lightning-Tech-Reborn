package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.menu.Ae2ltSlotBackgrounds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Provides the original empty-slot sprites through the 26.1 slot hook. */
@Mixin(Slot.class)
public abstract class Ae2ltSlotBackgroundMixin {
    @Inject(method = "getNoItemIcon", at = @At("HEAD"), cancellable = true)
    private void ae2lt$emptySlotIcon(CallbackInfoReturnable<Identifier> cir) {
        Identifier sprite = Ae2ltSlotBackgrounds.iconFor((Slot) (Object) this);
        if (sprite != null) cir.setReturnValue(sprite);
    }
}
