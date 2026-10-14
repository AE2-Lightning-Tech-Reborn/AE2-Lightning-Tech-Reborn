package com.moakiee.ae2lt.mixin.mest;

import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantics;
import com.lhy.mest.terminal.MESTMenu;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.moakiee.ae2lt.menu.extension.EmbeddedTerminalMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** MEST automatically reserves up to a stack of network blanks in its own physical input slot. */
@Mixin(value = TianshuPatternEncodingTermMenu.class, remap = false)
public abstract class MestReservedBlankMixin {
    @Inject(method = "stageNetworkBlankPattern", at = @At("HEAD"), cancellable = true)
    private void ae2lt$sharedBlankSupply(CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof EmbeddedTerminalMenu embedded)
                || !(embedded.outerMenu() instanceof MESTMenu outer) || !outer.getLinkStatus().connected()) return;
        var menu = (TianshuPatternEncodingTermMenu) (Object) this;
        var output = menu.getSlots(SlotSemantics.ENCODED_PATTERN).getFirst();
        var supply = outer.getBlankPatternSlot();
        if (!output.hasItem() && AEItems.BLANK_PATTERN.is(supply.getItem())) {
            output.set(supply.remove(1));
            // The native transaction encodes this one real blank, or refunds it to ME on failure/duplicate.
            cir.setReturnValue(true);
        }
    }
}
