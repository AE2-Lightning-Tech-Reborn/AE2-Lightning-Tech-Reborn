package com.moakiee.ae2lt.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.moakiee.ae2lt.celestweave.BaseCelestweaveArmorItem;
import com.moakiee.ae2lt.celestweave.CelestweaveArmorState;
import com.moakiee.ae2lt.celestweave.phase.PhaseLockService;
import com.moakiee.ae2lt.item.PhaseLockProjectionItem;

/**
 * Rejects stale creative-inventory echoes of UUID-bound armor at the server slot mutation.
 * The authoritative stack supplies armor state; the server's private slot identifies projections
 * even when an uploaded link tag is missing or corrupt. This prevents state loss and false equips.
 * Empty stacks, different items and different armor UUIDs retain vanilla move/equip/remove behavior.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerCelestweaveCreativeSyncMixin {
    @WrapOperation(
            method = "handleSetCreativeModeSlot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/Slot;setByPlayer(Lnet/minecraft/world/item/ItemStack;)V"))
    private void ae2lt$rejectStaleCelestweaveCreativeEcho(
            Slot slot,
            ItemStack uploaded,
            Operation<Void> original) {
        var listener = (ServerGamePacketListenerImpl) (Object) this;
        ItemStack authoritative = slot.getItem();
        if (slot.index < 5 || slot.index > 8
                || !ae2lt$isSameCelestweaveEquipmentEcho(listener.player, authoritative, uploaded)) {
            original.call(slot, uploaded);
            return;
        }

        /*
         * handleSetCreativeModeSlot calls broadcastChanges immediately after this invocation.
         * Invalidate only this remote cache entry so that broadcast sends the authoritative stack
         * back even when the server had already queued an earlier version for the client.
         */
        listener.player.inventoryMenu.setRemoteSlot(slot.index, ItemStack.EMPTY);
    }

    @Unique
    private static boolean ae2lt$isSameCelestweaveEquipmentEcho(
            ServerPlayer player,
            ItemStack authoritative,
            ItemStack uploaded) {
        if (authoritative.isEmpty() || uploaded.isEmpty()) {
            return false;
        }

        if (authoritative.getItem() instanceof BaseCelestweaveArmorItem
                && uploaded.getItem() instanceof PhaseLockProjectionItem) {
            return true;
        }
        if (authoritative.getItem() != uploaded.getItem()) return false;

        if (authoritative.getItem() instanceof BaseCelestweaveArmorItem) {
            UUID authoritativeId = CelestweaveArmorState.getArmorId(authoritative);
            UUID uploadedId = CelestweaveArmorState.getArmorId(uploaded);
            return authoritativeId != null && authoritativeId.equals(uploadedId);
        }

        if (authoritative.getItem() instanceof PhaseLockProjectionItem projection) {
            // The projection is derived state. Its server vault entry is the authority; requiring
            // the client-uploaded link made the previous fix fail open when that tag was absent.
            return PhaseLockService.hasPrivateArmor(player, projection.equipmentSlot());
        }

        return false;
    }
}
