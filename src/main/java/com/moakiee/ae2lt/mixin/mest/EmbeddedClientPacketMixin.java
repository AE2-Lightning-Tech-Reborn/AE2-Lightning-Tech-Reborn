package com.moakiee.ae2lt.mixin.mest;

import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.core.network.serverbound.SwapSlotsPacket;
import com.moakiee.ae2lt.integration.mest.client.MestTianshuClient;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = PacketDistributor.class, remap = false)
public abstract class EmbeddedClientPacketMixin {
    @ModifyVariable(method = "sendToServer", at = @At("HEAD"), argsOnly = true)
    private static CustomPacketPayload ae2lt$mapSlots(CustomPacketPayload packet) {
        if (packet instanceof InventoryActionPacket action) return new InventoryActionPacket(
                action.action(), MestTianshuClient.mapSlot(action.slot()), action.extraId(), action.slotItem());
        if (packet instanceof SwapSlotsPacket swap) return new SwapSlotsPacket(
                MestTianshuClient.mapSlot(swap.slotA()), MestTianshuClient.mapSlot(swap.slotB()));
        return packet;
    }
}
