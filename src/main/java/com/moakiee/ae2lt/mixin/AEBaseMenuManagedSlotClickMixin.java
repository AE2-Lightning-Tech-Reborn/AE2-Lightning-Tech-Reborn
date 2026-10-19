package com.moakiee.ae2lt.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;

import com.moakiee.ae2lt.menu.LargeStackAppEngSlot;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Central click boundary for AE2LT's managed AE2 menu slots.
 *
 * <p>AE2 12 inherits clicked from the vanilla container menu. The handler
 * first checks the menu type, then declines every slot that is not a
 * {@link LargeStackAppEngSlot}, or the offhand-swap rule for the overloaded
 * pattern provider's return slots. This keeps the intervention local to
 * AE2LT's explicit managed slots while also covering future machine menus
 * without a per-menu {@code clicked()} override.</p>
 */
@Mixin(net.minecraft.world.inventory.AbstractContainerMenu.class)
public abstract class AEBaseMenuManagedSlotClickMixin {

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void ae2lt$handleManagedSlotClick(
            int slotId,
            int button,
            ClickType clickType,
            Player player,
            CallbackInfo ci) {
        if (!((Object) this instanceof AEBaseMenu menu)) return;
        if (LargeStackAppEngSlot.handleMenuInteraction(menu, slotId, button, clickType, player)) {
            if (!menu.isClientSide()) {
                menu.broadcastChanges();
            }
            ci.cancel();
            return;
        }

        if (isOverloadedReturnSlotOffhandSwap(menu, slotId, button, clickType)) {
            ci.cancel();
        }
    }

    private static boolean isOverloadedReturnSlotOffhandSwap(
            AEBaseMenu menu,
            int slotId,
            int button,
            ClickType clickType) {
        if (!(menu instanceof OverloadedPatternProviderMenu)
                || clickType != ClickType.SWAP
                || button != 40
                || slotId < 0
                || slotId >= menu.slots.size()) {
            return false;
        }

        return menu.getSlotSemantic(menu.getSlot(slotId)) == SlotSemantics.STORAGE;
    }
}
